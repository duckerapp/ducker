package config

import (
	"bufio"
	"context"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	P "path"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

// D-345: the subscription is tried at its addresses in turn. Every server here is on loopback.

const mirrorTestConfig = "proxies: []\n"

type mirrorTestServer struct {
	url  string
	hits atomic.Int32
}

func mirrorServer(t *testing.T, handler http.HandlerFunc) *mirrorTestServer {
	t.Helper()
	m := &mirrorTestServer{}
	s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		m.hits.Add(1)
		handler(w, r)
	}))
	t.Cleanup(s.Close)
	m.url = s.URL + "/sub/TOKEN"
	return m
}

func configServer(t *testing.T) *mirrorTestServer {
	return mirrorServer(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("profile-title", "mirror")
		_, _ = w.Write([]byte(mirrorTestConfig))
	})
}

// closedAddress is a loopback port nobody listens on.
func closedAddress(t *testing.T) string {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	address := "http://" + l.Addr().String() + "/sub/TOKEN"
	_ = l.Close()
	return address
}

// silentAddress accepts connections and never answers, like a blocked domain that swallows them.
func silentAddress(t *testing.T) string {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = l.Close() })
	go func() {
		for {
			conn, err := l.Accept()
			if err != nil {
				return
			}
			t.Cleanup(func() { _ = conn.Close() })
		}
	}()
	return "http://" + l.Addr().String() + "/sub/TOKEN"
}

func setMirrorPlan(t *testing.T, source string, order ...string) {
	t.Helper()
	plan, _ := json.Marshal(mirrorPlan{Source: source, Order: order})
	if err := SetSubscriptionMirrors(string(plan)); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = SetSubscriptionMirrors("") })
}

func shortAnswerTimeout(t *testing.T, d time.Duration) {
	t.Helper()
	old := subscriptionAnswerTimeout
	subscriptionAnswerTimeout = d
	t.Cleanup(func() { subscriptionAnswerTimeout = old })
}

func noLastResort(t *testing.T) {
	t.Helper()
	old := lastResortRoutes
	lastResortRoutes = func(string) []route { return nil }
	t.Cleanup(func() { lastResortRoutes = old })
}

type statusLog struct{ statuses []Status }

func (l *statusLog) report(raw string) {
	var s Status
	_ = json.Unmarshal([]byte(raw), &s)
	l.statuses = append(l.statuses, s)
}

func (l *statusLog) source() []string {
	for _, s := range l.statuses {
		if s.Action == "SubscriptionSource" {
			return s.Args
		}
	}
	return nil
}

func previousConfig(t *testing.T) string {
	t.Helper()
	file := P.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(file, []byte("previous"), 0600); err != nil {
		t.Fatal(err)
	}
	return file
}

func readConfig(t *testing.T, file string) string {
	t.Helper()
	data, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func TestSubscriptionMirrorAnswersWhenOwnLinkFails(t *testing.T) {
	cases := map[string]func(t *testing.T) string{
		"closed port": closedAddress,
		"silent":      silentAddress,
		"503": func(t *testing.T) string {
			return mirrorServer(t, func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(503) }).url
		},
		"web page": func(t *testing.T) string {
			return mirrorServer(t, func(w http.ResponseWriter, r *http.Request) {
				_, _ = w.Write([]byte("\xef\xbb\xbf\n  <!DOCTYPE html><html>blocked</html>"))
			}).url
		},
		// The test server's certificate is not among the trusted ones, like a block page's.
		"wrong certificate": func(t *testing.T) string {
			s := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				_, _ = w.Write([]byte(mirrorTestConfig))
			}))
			t.Cleanup(s.Close)
			return s.URL + "/sub/TOKEN"
		},
	}
	for name, own := range cases {
		t.Run(name, func(t *testing.T) {
			noLastResort(t)
			shortAnswerTimeout(t, 300*time.Millisecond)
			source := own(t)
			mirror := configServer(t)
			setMirrorPlan(t, source, source, mirror.url)
			file := previousConfig(t)
			log := &statusLog{}

			header, err := fetchSubscription(source, file, log.report)
			if err != nil {
				t.Fatal(err)
			}
			if got := readConfig(t, file); got != mirrorTestConfig {
				t.Fatalf("config %q", got)
			}
			if header.All["profile-title"] != "mirror" {
				t.Fatalf("headers are not the mirror's: %v", header.All)
			}
			if got := log.source(); len(got) != 2 || got[0] != mirror.url || got[1] != "" {
				t.Fatalf("source %v", got)
			}
		})
	}
}

// The address that answered last time comes first, and then nothing else is asked.
func TestSubscriptionOrderIsFollowed(t *testing.T) {
	noLastResort(t)
	own := configServer(t)
	mirror := configServer(t)
	setMirrorPlan(t, own.url, mirror.url, own.url)

	if _, err := fetchSubscription(own.url, previousConfig(t), (&statusLog{}).report); err != nil {
		t.Fatal(err)
	}
	if mirror.hits.Load() != 1 || own.hits.Load() != 0 {
		t.Fatalf("mirror %d, own %d", mirror.hits.Load(), own.hits.Load())
	}
}

// A plan set for another subscription is not tried with this one.
func TestSubscriptionPlanForAnotherLinkIsIgnored(t *testing.T) {
	noLastResort(t)
	mirror := configServer(t)
	source := closedAddress(t)
	setMirrorPlan(t, "http://other.invalid/sub", mirror.url)

	if _, err := fetchSubscription(source, previousConfig(t), (&statusLog{}).report); err == nil {
		t.Fatal("another subscription's mirror answered")
	}
	if mirror.hits.Load() != 0 {
		t.Fatal("another subscription's mirror was asked")
	}
}

// The device limit is about the account: every mirror would say the same.
func TestSubscriptionDeviceLimitStopsAtOnce(t *testing.T) {
	noLastResort(t)
	own := mirrorServer(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("x-hwid-max-devices-reached", "true")
		w.WriteHeader(403)
	})
	mirror := configServer(t)
	setMirrorPlan(t, own.url, own.url, mirror.url)

	header, err := fetchSubscription(own.url, previousConfig(t), (&statusLog{}).report)
	if err == nil || !strings.HasPrefix(err.Error(), "DOWNLOAD_FAILED: profile: HTTP 403") || strings.Contains(err.Error(), "backups") {
		t.Fatalf("error %v", err)
	}
	if header.All["x-hwid-max-devices-reached"] != "true" {
		t.Fatal("the panel's headers lost")
	}
	if mirror.hits.Load() != 0 {
		t.Fatal("a mirror was asked after the device limit")
	}
}

// When all fail, the own link's status is shown, with the number of mirrors tried, and the
// previous config stays.
func TestSubscriptionAllFailShowsTheMostTellingError(t *testing.T) {
	noLastResort(t)
	shortAnswerTimeout(t, 300*time.Millisecond)
	own := mirrorServer(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("announce", "base64:c29ycnk=")
		w.WriteHeader(404)
	})
	// The own link last: the last try waits the whole fetchTimeout, and a silent one would hold the test.
	setMirrorPlan(t, own.url, silentAddress(t), closedAddress(t), own.url)
	file := previousConfig(t)

	header, err := fetchSubscription(own.url, file, (&statusLog{}).report)
	if err == nil || err.Error() != "DOWNLOAD_FAILED: profile: HTTP 404 Not Found (+2 backups)" {
		t.Fatalf("error %v", err)
	}
	if header.All["announce"] != "base64:c29ycnk=" {
		t.Fatal("headers are not those of the error shown")
	}
	if got := readConfig(t, file); got != "previous" {
		t.Fatalf("previous config lost: %q", got)
	}
}

func lastResortThrough(t *testing.T, target string) *atomic.Int32 {
	t.Helper()
	used := &atomic.Int32{}
	oldRoutes, oldDial := lastResortRoutes, dialThroughProxy
	lastResortRoutes = func(string) []route { return []route{{proxy: "TEST"}} }
	dialThroughProxy = func(ctx context.Context, _ string, proxy string) (net.Conn, error) {
		used.Add(1)
		if proxy != "TEST" {
			t.Errorf("proxy %q", proxy)
		}
		return (&net.Dialer{}).DialContext(ctx, "tcp", strings.TrimPrefix(target, "http://")[:strings.Index(strings.TrimPrefix(target, "http://"), "/")])
	}
	t.Cleanup(func() { lastResortRoutes, dialThroughProxy = oldRoutes, oldDial })
	return used
}

// Q-81: every address silent — the first one is tried through the subscription's own node.
func TestSubscriptionLastResortThroughTheNode(t *testing.T) {
	shortAnswerTimeout(t, 300*time.Millisecond)
	node := configServer(t)
	used := lastResortThrough(t, node.url)
	source := closedAddress(t)
	log := &statusLog{}

	if _, err := fetchSubscription(source, previousConfig(t), log.report); err != nil {
		t.Fatal(err)
	}
	if used.Load() != 1 {
		t.Fatal("the node was not used")
	}
	if got := log.source(); len(got) != 2 || got[0] != source || got[1] != "proxy" {
		t.Fatalf("source %v", got)
	}
}

// The server answered with a status: another way to it would hear the same.
func TestSubscriptionLastResortSkippedAfterAStatus(t *testing.T) {
	node := configServer(t)
	used := lastResortThrough(t, node.url)
	own := mirrorServer(t, func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(404) })

	if _, err := fetchSubscription(own.url, previousConfig(t), (&statusLog{}).report); err == nil {
		t.Fatal("404 reported success")
	}
	if used.Load() != 0 {
		t.Fatal("the node was used after the server's answer")
	}
}

// The last try is not cut short: a slow but working panel still gives the subscription.
func TestSubscriptionLastTryWaitsTheWholeTimeout(t *testing.T) {
	noLastResort(t)
	shortAnswerTimeout(t, 100*time.Millisecond)
	slow := mirrorServer(t, func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(400 * time.Millisecond)
		_, _ = w.Write([]byte(mirrorTestConfig))
	})

	if _, err := fetchSubscription(slow.url, previousConfig(t), (&statusLog{}).report); err != nil {
		t.Fatal(err)
	}
}

func TestLooksLikeWebPage(t *testing.T) {
	for body, want := range map[string]bool{
		"<html>":                   true,
		"\r\n\t <!doctype html>":   true,
		"\xef\xbb\xbf<?xml":        true,
		"proxies:\n  - name: a":    false,
		"dmxlc3M6Ly8=":             false,
		"vless://id@host:443#a":    false,
		"{\"outbounds\": []}":      false,
		"":                         false,
		"# comment <not a page>\n": false,
	} {
		if got := looksLikeWebPage(bufio.NewReader(strings.NewReader(body))); got != want {
			t.Errorf("%q: %v", body, got)
		}
	}
}
