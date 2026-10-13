// Not under the race detector: these tests load a config several times, and the core itself
// races there — `log.SetLevel` in `executor.ApplyConfig` against the finalizer that closes a
// replaced proxy and logs it (`adapter/outbound/base.go`).

//go:build !race

package config

import (
	"context"
	"net/netip"
	"os"
	"path/filepath"
	"testing"

	"github.com/metacubex/mihomo/component/resolver"
	C "github.com/metacubex/mihomo/constant"

	D "github.com/miekg/dns"
)

const fakeIPProfile = `
dns:
  enable: true
  enhanced-mode: fake-ip
  fake-ip-range: 198.18.0.1/16
  nameserver:
    - 1.1.1.1
proxies:
  - {name: node, type: socks5, server: 127.0.0.1, port: 1080}
rules:
  - MATCH,DIRECT
`

// newFakeIPProfile makes a profile directory and an empty home for the core.
func newFakeIPProfile(t *testing.T) string {
	t.Helper()

	oldHome := C.Path.HomeDir()
	C.SetHomeDir(t.TempDir())
	t.Cleanup(func() { C.SetHomeDir(oldHome) })

	profile := t.TempDir()
	if err := os.WriteFile(filepath.Join(profile, "config.yaml"), []byte(fakeIPProfile), 0o600); err != nil {
		t.Fatal(err)
	}

	return profile
}

// fakeIPOf asks the loaded core for a name, as an app does through the tunnel's DNS.
func fakeIPOf(t *testing.T, host string) netip.Addr {
	t.Helper()

	query := new(D.Msg)
	query.SetQuestion(D.Fqdn(host), D.TypeA)

	answer, err := resolver.ServeMsg(context.Background(), query)
	if err != nil {
		t.Fatalf("%s: %v", host, err)
	}
	if len(answer.Answer) != 1 {
		t.Fatalf("%s: %d answers", host, len(answer.Answer))
	}

	ip, _ := netip.AddrFromSlice(answer.Answer[0].(*D.A).A)

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

// A core loaded with none running before it starts a clean table: where the previous one stood
// is known only as of its last reload, and a table continued from there would hand out
// addresses that are still taken.
func TestFakeIPStartsCleanWithoutRunningCore(t *testing.T) {
	profile := newFakeIPProfile(t)

	if err := Load(profile); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(LoadDefault)

	first := fakeIPOf(t, "first.test")

	if err := Load(profile); err != nil {
		t.Fatal(err)
	}
	second := fakeIPOf(t, "second.test")

	// The tunnel stops and starts again.
	LoadDefault()
	if err := Load(profile); err != nil {
		t.Fatal(err)
	}

	for _, ip := range []netip.Addr{first, second} {
		if host, found := resolver.FindHostByIP(ip); found {
			t.Fatalf("%s still leads to %q after a restart", ip, host)
		}
	}
	if other := fakeIPOf(t, "other.test"); other != first {
		t.Fatalf("the first name after a restart got %s, expected the first address %s", other, first)
	}
}
