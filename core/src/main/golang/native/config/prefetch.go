package config

import (
	"context"
	"errors"
	"net/http"
	U "net/url"
	"os"
	"sync"
	"sync/atomic"

	"cfa/native/app"
	"cfa/native/panicguard"

	"github.com/metacubex/mihomo/log"
	RB "github.com/metacubex/mihomo/rules/bundle"
)

const prefetchAtOnce = 4

// FetchMissingProviders downloads the provider files the subscription in path has none of, the rule sets that go
// through a node among them: FetchAndValid leaves those to the core (D-229), which downloads them on the
// subscription's first load while the tunnel drops every connection. While the VPN is up, through the core, and
// directly if that fails; while it is down, directly. Returns how many are still missing.
func FetchMissingProviders(path string) (int, error) {
	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return 0, err
	}

	type file struct {
		name   string
		url    *U.URL
		path   string
		header http.Header
	}
	var missing []file
	forEachProviders(rawCfg, func(_ int, _ int, name string, provider map[string]any, _ string) {
		u, _ := provider["url"].(string)
		p, _ := provider["path"].(string)
		if u == "" || p == "" {
			return
		}
		if _, err := os.Stat(p); err == nil {
			return
		}
		// The core takes such a file from the bundle, with no network.
		if pib, _ := provider["path-in-bundle"].(string); pib != "" {
			if f, err := RB.Open(pib); err == nil {
				_ = f.Close()
				return
			}
		}
		url, err := U.Parse(u)
		if err != nil || (url.Scheme != "http" && url.Scheme != "https") {
			return
		}
		missing = append(missing, file{name, url, p, providerHeader(provider, rawCfg.GlobalUA)})
	})

	var left atomic.Int32
	var wg sync.WaitGroup
	limit := make(chan struct{}, prefetchAtOnce)
	for _, f := range missing {
		wg.Add(1)
		limit <- struct{}{}
		go func(f file) {
			defer func() { <-limit; wg.Done() }()

			err := panicguard.Run("fetchMissingProviders", func() error {
				err := fetchProvider(f.url, f.path, route{}, f.header)
				if err != nil && app.TunRunning() {
					err = fetchProvider(f.url, f.path, route{direct: true}, f.header)
				}
				return err
			})
			if err != nil {
				left.Add(1)
				// The reason without the link: a provider's link can carry the person's token.
				var linked *U.Error
				if errors.As(err, &linked) {
					err = linked.Err
				}
				log.Warnln("[Prefetch] provider %s: %s", f.name, err.Error())
			}
		}(f)
	}
	wg.Wait()

	log.Infoln("[Prefetch] %d of %d missing provider files fetched", len(missing)-int(left.Load()), len(missing))

	return int(left.Load()), nil
}

func fetchProvider(url *U.URL, file string, r route, header http.Header) error {
	ctx, cancel := context.WithTimeout(context.Background(), fetchTimeout)
	defer cancel()

	reader, _, err := openUrl(ctx, url.String(), false, r, header)
	if err != nil {
		return err
	}

	return writeDownload(file, reader, false)
}
