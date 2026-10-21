package config

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"math"
	"net/netip"
	"os"
	P "path"
	"runtime"
	"strings"

	"cfa/native/app"

	"github.com/metacubex/bbolt"
	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/log"
	"go4.org/netipx"
)

func logDns(cfg *config.RawConfig) {
	bytes, err := yaml.Marshal(&cfg.DNS)
	if err != nil {
		log.Warnln("Marshal dns: %s", err.Error())

		return
	}

	log.Infoln("dns:")

	for _, line := range strings.Split(string(bytes), "\n") {
		log.Infoln("  %s", line)
	}
}

func UnmarshalAndPatch(profilePath string) (*config.RawConfig, error) {
	configPath := P.Join(profilePath, "config.yaml")

	configData, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}

	rawConfig, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		return nil, err
	}

	if err := process(rawConfig, profilePath); err != nil {
		return nil, err
	}

	return rawConfig, nil
}

func Parse(rawConfig *config.RawConfig) (*config.Config, error) {
	cfg, err := config.ParseRawConfig(rawConfig)
	if err != nil {
		return nil, err
	}

	return cfg, nil
}

// ValidateConfig разбирает конфиг из каталога так же, как загрузка, но ничего не применяет
// и не качает (D-124): ошибка YAML приходит с номером строки, ошибка содержимого — с именем
// узла или группы. Провайдеры не скачиваются — их проверит загрузка.
func ValidateConfig(path string) error {
	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return err
	}

	_, err = validate(rawCfg)

	return err
}

// validate parses a config that will not be applied. Its fake-ip pool is made in memory: a
// pool on the core's cache file works on the table of the running core, and making one
// deleted that table (F-285).
func validate(rawConfig *config.RawConfig) (*config.Config, error) {
	rawConfig.Profile.StoreFakeIP = false

	return Parse(rawConfig)
}

// keptFakeIPNames bounds a fake-ip table kept across starts of the tunnel: a start that finds
// more names than this empties the table (D-303). Half of the template's /16, so a kept table
// does not wrap before it unless a single run asks more new names than that.
var keptFakeIPNames uint64 = 32768

// What the wrapper relies on inside the core's cache file and fake-ip pool
// (clash/component/profile/cachefile/cache.go, clash/component/fakeip/pool.go). Pinned by
// TestFakeIPCoreLayout.
const (
	fakeIPBucket   = "fakeip"
	fakeIPBucket6  = "fakeip6"
	fakeIPPlaceKey = "key-offset-fake-ip"
	fakeIPWrapKey  = "key-cycle-fake-ip"
)

// keepFakeIP prepares the fake-ip table in the cache file for the pool the coming parse makes
// (F-285). The core writes where allocation stands only in executor.Shutdown, which is never
// called here; a pool that finds no such record deletes the table, and one that finds an old
// record hands out addresses that are taken.
//
// A reload: the place of the running pool is written, the new pool goes on from it, and the
// addresses apps hold keep their names. A start with no core running: the table of the previous
// run is kept where that is sound — resumeFakeIP. A name asked between this call and the takeover
// keeps its place too: the takeover copies it from the old pool (mihomo#3283).
func keepFakeIP(rawCfg *config.RawConfig) {
	if resolver.DefaultHostMapper != nil {
		resolver.StoreFakePoolState()

		return
	}

	cache := cachefile.Cache()
	if cache.DB == nil {
		return
	}

	// The conditions the core makes each pool under (clash/config/config.go, parseIPV6 and
	// parseDNS). It also drops the IPv6 pool when the system has no IPv6; a table kept for that
	// case waits unused.
	fakeIP := rawCfg.DNS.EnhancedMode == C.DNSFakeIP
	resumeFakeIP(cache.FakeIpStore(), fakeIPBucket, fakeIP, rawCfg.DNS.FakeIPRange)
	resumeFakeIP(cache.FakeIpStore6(), fakeIPBucket6, fakeIP && rawCfg.IPv6, rawCfg.DNS.FakeIPRange6)
}

// resumeFakeIP lets the coming pool go on with the table of the previous run (D-303). A stop
// writes nothing, so the place stored in the table dates from the last reload; but a pool deletes
// only at the address it is about to give, so a table that never wrapped is filled from its first
// address up, and its highest taken address is where allocation stood. Where that cannot be
// relied on, the table is emptied, as every start did before.
func resumeFakeIP(store *cachefile.FakeIpStore, bucket string, enabled bool, ipRange string) {
	prefix, err := netip.ParsePrefix(ipRange)
	if !enabled || err != nil {
		_ = store.FlushFakeIP()

		return
	}

	// The pool's own bounds (fakeip.New): it starts at the network + 4 and never gives the last.
	first := prefix.Masked().Addr().Next().Next().Next().Next()
	last := netipx.PrefixLastIP(prefix)
	if !first.IsValid() || !first.Less(last) {
		_ = store.FlushFakeIP()

		return
	}

	_, wrapped := store.GetByHost(fakeIPWrapKey)
	top := fakeIPTop(store.DB, bucket, first, last)
	names := fakeIPCount(first, top)

	why := ""
	switch {
	case wrapped:
		why = ": it wrapped"
	case !top.IsValid():
	case top.Next() == last:
		why = ": it is full"
	case names > keptFakeIPNames:
		why = fmt.Sprintf(": %d names, more than %d", names, keptFakeIPNames)
	default:
		if place, found := store.GetByHost(fakeIPPlaceKey); !found || place != top {
			store.PutByHost(fakeIPPlaceKey, top)
		}
		log.Infoln("[FakeIP] %s: names kept: %d", prefix, names)

		return
	}

	_ = store.FlushFakeIP()
	log.Infoln("[FakeIP] %s: a clean table%s", prefix, why)
}

// fakeIPTop is the highest address of [first, last) taken in the table. The keys of a bucket are
// sorted bytes, and an address is a key of its own length next to the names.
func fakeIPTop(db *bbolt.DB, bucket string, first, last netip.Addr) (top netip.Addr) {
	firstKey, lastKey := first.AsSlice(), last.AsSlice()

	_ = db.View(func(tx *bbolt.Tx) error {
		b := tx.Bucket([]byte(bucket))
		if b == nil {
			return nil
		}

		c := b.Cursor()
		k, _ := c.Seek(lastKey)
		if k == nil {
			k, _ = c.Last()
		} else {
			k, _ = c.Prev()
		}
		for ; k != nil && bytes.Compare(k, firstKey) >= 0; k, _ = c.Prev() {
			if len(k) == len(firstKey) {
				top, _ = netip.AddrFromSlice(k)

				return nil
			}
		}

		return nil
	})

	return
}

// fakeIPCount is the number of addresses from first to top, both included; none for no top.
// Only its comparison with the bound matters, so a count past 64 bits reads as the most there is.
func fakeIPCount(first, top netip.Addr) uint64 {
	if !top.IsValid() {
		return 0
	}

	a, b := first.As16(), top.As16()
	if !bytes.Equal(a[:8], b[:8]) {
		return math.MaxUint64
	}

	return binary.BigEndian.Uint64(b[8:]) - binary.BigEndian.Uint64(a[8:]) + 1
}

func Load(path string) error {
	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	logDns(rawCfg)

	keepFakeIP(rawCfg)

	cfg, err := Parse(rawCfg)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	// like hub.Parse()
	hub.ApplyConfig(cfg)

	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)

	runtime.GC()

	return nil
}

func LoadDefault() {
	cfg, err := config.Parse([]byte{})
	if err != nil {
		panic(err.Error())
	}

	hub.ApplyConfig(cfg)
}
