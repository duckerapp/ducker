// Not under the race detector: these tests load a config several times, and the core itself
// races there — `log.SetLevel` in `executor.ApplyConfig` against the finalizer that closes a
// replaced proxy and logs it (`adapter/outbound/base.go`).

//go:build !race

package config

import (
	"context"
	"fmt"
	"net/netip"
	"os"
	"path/filepath"
	"testing"

	"github.com/metacubex/bbolt"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/component/resolver"
	C "github.com/metacubex/mihomo/constant"

	D "github.com/miekg/dns"
)

// fakeIPProfile takes the lines of the DNS section that set the mode and the ranges.
const fakeIPProfile = `
dns:
  enable: true
%s  nameserver:
    - 1.1.1.1
proxies:
  - {name: node, type: socks5, server: 127.0.0.1, port: 1080}
rules:
  - MATCH,DIRECT
`

func fakeIPRange(ipRange string) string {
	return "  enhanced-mode: fake-ip\n  fake-ip-range: " + ipRange + "\n"
}

// newFakeIPProfile makes a profile directory and an empty home for the core.
func newFakeIPProfile(t *testing.T) string {
	t.Helper()

	return newProfile(t, fakeIPRange("198.18.0.1/16"))
}

// newProfile makes a profile directory with the given DNS lines and an empty home for the core,
// and starts the test with no core running and empty fake-ip tables: the cache file is opened
// once per process and outlives each test's home.
func newProfile(t *testing.T, dns string) string {
	t.Helper()

	oldHome := C.Path.HomeDir()
	C.SetHomeDir(t.TempDir())
	t.Cleanup(func() { C.SetHomeDir(oldHome) })

	LoadDefault()
	cache := cachefile.Cache()
	if cache.DB == nil {
		t.Fatal("the core's cache file did not open")
	}
	_ = cache.FakeIpStore().FlushFakeIP()
	_ = cache.FakeIpStore6().FlushFakeIP()

	return writeProfile(t, dns)
}

// writeProfile makes another profile directory for the same home.
func writeProfile(t *testing.T, dns string) string {
	t.Helper()

	profile := t.TempDir()
	if err := os.WriteFile(filepath.Join(profile, "config.yaml"), []byte(fmt.Sprintf(fakeIPProfile, dns)), 0o600); err != nil {
		t.Fatal(err)
	}

	return profile
}

func load(t *testing.T, profile string) {
	t.Helper()

	if err := Load(profile); err != nil {
		t.Fatal(err)
	}
}

// restart stops the core and loads the profile again, as a stop and a start of the tunnel do;
// a stop writes nothing to the table, just as a killed service does not.
func restart(t *testing.T, profile string) {
	t.Helper()

	LoadDefault()
	load(t, profile)
}

// fakeIPOf asks the loaded core for a name, as an app does through the tunnel's DNS.
func fakeIPOf(t *testing.T, host string) netip.Addr {
	t.Helper()

	return askFake(t, host, D.TypeA)
}

func fakeIP6Of(t *testing.T, host string) netip.Addr {
	t.Helper()

	return askFake(t, host, D.TypeAAAA)
}

func askFake(t *testing.T, host string, qtype uint16) netip.Addr {
	t.Helper()

	query := new(D.Msg)
	query.SetQuestion(D.Fqdn(host), qtype)

	answer, err := resolver.ServeMsg(context.Background(), query)
	if err != nil {
		t.Fatalf("%s: %v", host, err)
	}
	if len(answer.Answer) != 1 {
		t.Fatalf("%s: %d answers", host, len(answer.Answer))
	}

	var ip netip.Addr
	switch rr := answer.Answer[0].(type) {
	case *D.A:
		ip, _ = netip.AddrFromSlice(rr.A)
	case *D.AAAA:
		ip, _ = netip.AddrFromSlice(rr.AAAA)
	}

	return ip.Unmap()
}

// expectHost: a connection an app opens to ip is taken by the core for host.
func expectHost(t *testing.T, when string, ip netip.Addr, host string) {
	t.Helper()

	if got, _ := resolver.FindHostByIP(ip); got != host {
		t.Fatalf("%s: %s leads to %q, expected %q", when, ip, got, host)
	}
}

// An app keeps the address it was given. An update of a subscription — a validation of the
// downloaded config, then its load — must not make that address lead elsewhere (F-285).
func TestFakeIPSurvivesValidationAndReload(t *testing.T) {
	profile := newFakeIPProfile(t)

	if err := Load(profile); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(LoadDefault)

	first := fakeIPOf(t, "first.test")
	second := fakeIPOf(t, "second.test")

	if err := ValidateConfig(profile); err != nil {
		t.Fatal(err)
	}
	expectHost(t, "after a validation", first, "first.test")

	if err := FetchAndValid(profile, "", false, func(string) {}); err != nil {
		t.Fatal(err)
	}
	expectHost(t, "after a fetch with its validation", second, "second.test")

	if err := Load(profile); err != nil {
		t.Fatal(err)
	}
	expectHost(t, "after a reload", first, "first.test")
	expectHost(t, "after a reload", second, "second.test")

	// A name asked for the first time after the reload gets an address of its own.
	third := fakeIPOf(t, "third.test")
	if third == first || third == second {
		t.Fatalf("a new name got %s, the address of a name asked before the reload", third)
	}
	expectHost(t, "after a new name", first, "first.test")
	expectHost(t, "after a new name", second, "second.test")

	if again := fakeIPOf(t, "first.test"); again != first {
		t.Fatalf("first.test moved from %s to %s", first, again)
	}
}

// expectGone: the table no longer holds ip, whatever range the running pool has.
func expectGone(t *testing.T, when string, ip netip.Addr) {
	t.Helper()

	store := cachefile.Cache().FakeIpStore()
	if ip.Is6() {
		store = cachefile.Cache().FakeIpStore6()
	}
	if host, found := store.GetByIP(ip); found {
		t.Fatalf("%s: %s still leads to %q", when, ip, host)
	}
}

// The tunnel stops and starts again — an update of the app, a reboot, a killed service: the
// addresses apps hold keep their names, and a name given after the place the table last stored
// is not given out again (D-303).
func TestFakeIPKeptAcrossRestart(t *testing.T) {
	profile := newFakeIPProfile(t)
	load(t, profile)
	t.Cleanup(LoadDefault)

	first := fakeIPOf(t, "first.test")
	load(t, profile) // a reload stores the place
	second := fakeIPOf(t, "second.test")

	restart(t, profile)
	expectHost(t, "after a restart", first, "first.test")
	expectHost(t, "after a restart", second, "second.test")

	if third := fakeIPOf(t, "third.test"); third != second.Next() {
		t.Fatalf("a new name after a restart got %s, expected %s, the next after the last given", third, second.Next())
	}
	if again := fakeIPOf(t, "first.test"); again != first {
		t.Fatalf("first.test moved from %s to %s", first, again)
	}
	expectHost(t, "after a new name", second, "second.test")

	// The table is kept again at the next start, with the names given since.
	restart(t, profile)
	expectHost(t, "after a second restart", second, "second.test")
	if fourth := fakeIPOf(t, "fourth.test"); fourth != second.Next().Next() {
		t.Fatalf("a new name after a second restart got %s, expected %s", fourth, second.Next().Next())
	}
}

// The nodes shown while the tunnel is off come from a load whose DNS the app switched off
// (ClashManager.loadForPreview). Such a core answers no names and leaves the tunnel's table alone.
func TestFakeIPKeptAcrossPreview(t *testing.T) {
	profile := newFakeIPProfile(t)
	load(t, profile)
	t.Cleanup(LoadDefault)
	t.Cleanup(func() { ClearOverride(OverrideSlotSession) })

	a := fakeIPOf(t, "a.test")

	LoadDefault()
	WriteOverride(OverrideSlotSession, `{"dns":{"enable":false}}`)
	load(t, profile)
	ClearOverride(OverrideSlotSession)

	restart(t, profile)
	expectHost(t, "after the nodes were shown with the tunnel off", a, "a.test")
	if b := fakeIPOf(t, "b.test"); b != a.Next() {
		t.Fatalf("a new name after the preview got %s, expected %s", b, a.Next())
	}
}

// A table past the bound starts clean, so it cannot grow without end in a wide range.
func TestFakeIPCleanPastBound(t *testing.T) {
	old := keptFakeIPNames
	keptFakeIPNames = 2
	t.Cleanup(func() { keptFakeIPNames = old })

	profile := newFakeIPProfile(t)
	load(t, profile)
	t.Cleanup(LoadDefault)

	a := fakeIPOf(t, "a.test")
	b := fakeIPOf(t, "b.test")

	restart(t, profile)
	expectHost(t, "two names, at the bound", b, "b.test")

	c := fakeIPOf(t, "c.test")
	restart(t, profile)
	for _, ip := range []netip.Addr{a, b, c} {
		expectGone(t, "three names, past the bound", ip)
	}
	if other := fakeIPOf(t, "other.test"); other != a {
		t.Fatalf("the first name of a clean table got %s, expected the first address %s", other, a)
	}
}

// In a full or wrapped table every address is taken, and where allocation stood is not known
// after a run that ended without a reload: such a table starts clean. A /29 holds three names:
// the pool starts at the network + 4 and never gives the last address.
func TestFakeIPCleanWhenFullOrWrapped(t *testing.T) {
	profile := newProfile(t, fakeIPRange("198.18.0.0/29"))
	load(t, profile)
	t.Cleanup(LoadDefault)

	given := []netip.Addr{fakeIPOf(t, "a.test"), fakeIPOf(t, "b.test"), fakeIPOf(t, "c.test")}
	for i, want := range []string{"198.18.0.4", "198.18.0.5", "198.18.0.6"} {
		if given[i] != netip.MustParseAddr(want) {
			t.Fatalf("name %d got %s, expected %s", i, given[i], want)
		}
	}

	restart(t, profile)
	for _, ip := range given {
		expectGone(t, "a full table", ip)
	}

	// Wrapped, with the mark a reload stores.
	for _, host := range []string{"a.test", "b.test", "c.test"} {
		fakeIPOf(t, host)
	}
	if again := fakeIPOf(t, "d.test"); again != given[0] {
		t.Fatalf("the fourth name of a /29 got %s, expected the first address again", again)
	}
	load(t, profile)
	if _, found := cachefile.Cache().FakeIpStore().GetByHost(fakeIPWrapKey); !found {
		t.Fatalf("a reload of a wrapped pool stored no %q", fakeIPWrapKey)
	}
	restart(t, profile)
	expectGone(t, "a wrapped table", given[0])
	expectGone(t, "a wrapped table", given[1])
}

// A wider range that holds the table keeps it; a range with nothing of it starts clean.
func TestFakeIPRangeChange(t *testing.T) {
	narrow := newProfile(t, fakeIPRange("198.18.0.1/16"))
	load(t, narrow)
	t.Cleanup(LoadDefault)

	a := fakeIPOf(t, "a.test")

	wide := writeProfile(t, fakeIPRange("198.18.0.1/15"))
	restart(t, wide)
	expectHost(t, "in a wider range", a, "a.test")
	if b := fakeIPOf(t, "b.test"); b != a.Next() {
		t.Fatalf("a new name in the wider range got %s, expected %s", b, a.Next())
	}

	restart(t, writeProfile(t, fakeIPRange("198.19.0.1/16")))
	expectGone(t, "in another range", a)
}

// A config without fake-ip makes no pool, and the table of the previous one goes.
func TestFakeIPEmptiedWithoutFakeIP(t *testing.T) {
	profile := newFakeIPProfile(t)
	load(t, profile)
	t.Cleanup(LoadDefault)

	a := fakeIPOf(t, "a.test")

	restart(t, writeProfile(t, "  enhanced-mode: redir-host\n"))
	expectGone(t, "without fake-ip", a)
}

// The IPv6 table is kept the same way as the IPv4 one, by its own range.
func TestFakeIP6KeptAcrossRestart(t *testing.T) {
	t.Setenv("SKIP_SYSTEM_IPV6_CHECK", "true")

	profile := newProfile(t, "  ipv6: true\n"+fakeIPRange("198.18.0.1/16")+"  fake-ip-range6: fdfe:dcba:9876::1/64\n")
	load(t, profile)
	t.Cleanup(LoadDefault)

	first := fakeIP6Of(t, "first.test")
	if !first.Is6() {
		t.Fatalf("an AAAA question got %s", first)
	}
	load(t, profile)
	second := fakeIP6Of(t, "second.test")

	restart(t, profile)
	expectHost(t, "after a restart", first, "first.test")
	expectHost(t, "after a restart", second, "second.test")
	if third := fakeIP6Of(t, "third.test"); third != second.Next() {
		t.Fatalf("a new IPv6 name after a restart got %s, expected %s", third, second.Next())
	}
}

// What resumeFakeIP relies on inside the core: the buckets the stores write to and the key of
// the place. The wrap mark is pinned in TestFakeIPCleanWhenFullOrWrapped.
func TestFakeIPCoreLayout(t *testing.T) {
	profile := newFakeIPProfile(t)
	cache := cachefile.Cache()

	for _, c := range []struct {
		store  *cachefile.FakeIpStore
		bucket string
		ip     netip.Addr
	}{
		{cache.FakeIpStore(), fakeIPBucket, netip.MustParseAddr("198.18.0.4")},
		{cache.FakeIpStore6(), fakeIPBucket6, netip.MustParseAddr("fdfe:dcba:9876::4")},
	} {
		c.store.PutByIP(c.ip, "layout.test")
		var host []byte
		_ = cache.DB.View(func(tx *bbolt.Tx) error {
			if b := tx.Bucket([]byte(c.bucket)); b != nil {
				host = append(host, b.Get(c.ip.AsSlice())...)
			}
			return nil
		})
		if string(host) != "layout.test" {
			t.Fatalf("%s is not in the bucket %q", c.ip, c.bucket)
		}
		_ = c.store.FlushFakeIP()
	}

	load(t, profile)
	t.Cleanup(LoadDefault)
	a := fakeIPOf(t, "a.test")
	resolver.StoreFakePoolState()
	if place, _ := cache.FakeIpStore().GetByHost(fakeIPPlaceKey); place != a {
		t.Fatalf("the pool stored its place as %s under %q, expected %s", place, fakeIPPlaceKey, a)
	}
}
