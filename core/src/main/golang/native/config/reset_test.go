// Not under the race detector, as `fakeip_test.go`: the core races in its own `ApplyConfig`.

//go:build !race

package config

import (
	"errors"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/tunnel"
)

// A stop resets the core without waiting for a load in flight (D-460, F-449). Whichever ends first,
// the core is left as the reset made it: no subscription nodes, no DNS port.
func TestResetDuringLoadWins(t *testing.T) {
	ClearOverride(OverrideSlotSession)

	probe, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	dnsAddr := probe.LocalAddr().String()
	_ = probe.Close()

	// Enough nodes for the parse to last long enough to reset in the middle of it.
	var config strings.Builder
	fmt.Fprintf(&config, "dns:\n  enable: true\n  listen: %s\n  nameserver:\n    - 1.1.1.1\nproxies:\n", dnsAddr)
	for i := 0; i < 4000; i++ {
		fmt.Fprintf(&config, "  - {name: node-%d, type: socks5, server: 127.0.0.1, port: 1080}\n", i)
	}
	config.WriteString("proxy-groups:\n  - {name: pick, type: select, include-all-proxies: true}\nrules:\n  - MATCH,pick\n")

	profile := newProfile(t, "")
	if err := os.WriteFile(filepath.Join(profile, "config.yaml"), []byte(config.String()), 0o600); err != nil {
		t.Fatal(err)
	}

	start := time.Now()
	load(t, profile)
	took := time.Since(start)
	LoadDefault()

	voided := 0
	for round := 0; round < 10; round++ {
		done := make(chan error, 1)
		go func() { done <- Load(profile) }()

		time.Sleep(took / 4)
		LoadDefault()

		if err := <-done; errors.Is(err, errReset) {
			voided++
		} else if err != nil {
			t.Fatalf("round %d: %v", round, err)
		}

		if _, ok := tunnel.Proxies()["node-1"]; ok {
			t.Fatalf("round %d: the subscription's nodes are in the core after the reset", round)
		}

		conn, err := net.ListenPacket("udp", dnsAddr)
		if err != nil {
			t.Fatalf("round %d: the DNS port is bound after the reset: %v", round, err)
		}
		_ = conn.Close()
	}

	// Each round must still end clean when the reset comes after the check; none voided means the
	// test no longer reaches the window it is about.
	if voided == 0 {
		t.Fatalf("no reset came during a parse in 10 rounds (a load takes %s)", took)
	}
	t.Logf("%d of 10 loads voided by the reset (a load takes %s)", voided, took)
}
