// Not under the race detector, as `fakeip_test.go`: the core races in its own `ApplyConfig`.

//go:build !race

package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/tunnel"
)

// A parse sets the core's live general settings to its own and rolls them back (D-500, F-486). A
// validation that spans a whole load of another subscription must not roll that load's settings back.
func TestValidationDuringLoadKeepsLoadedSettings(t *testing.T) {
	ClearOverride(OverrideSlotSession)
	newProfile(t, "")

	profileOf := func(mode string, nodes int) string {
		var config strings.Builder
		fmt.Fprintf(&config, "mode: %s\nproxies:\n", mode)
		for i := 0; i < nodes; i++ {
			fmt.Fprintf(&config, "  - {name: node-%d, type: socks5, server: 127.0.0.1, port: 1080}\n", i)
		}
		config.WriteString("proxy-groups:\n  - {name: pick, type: select, include-all-proxies: true}\nrules:\n  - MATCH,pick\n")

		profile := writeProfile(t, "")
		if err := os.WriteFile(filepath.Join(profile, "config.yaml"), []byte(config.String()), 0o600); err != nil {
			t.Fatal(err)
		}

		return profile
	}

	previous := profileOf("global", 1)
	// Enough nodes for the validation to outlast a whole load of the small profile.
	validated := profileOf("direct", 8000)
	next := profileOf("rule", 1)

	// Only the parse holds the window: the settings are swapped after reading the YAML, which takes
	// most of a validation, so the YAML is read ahead and the parse alone is timed.
	unmarshal := func() *config.RawConfig {
		rawCfg, err := UnmarshalAndPatch(validated)
		if err != nil {
			t.Fatal(err)
		}

		return rawCfg
	}

	rawCfg := unmarshal()
	start := time.Now()
	if _, err := validate(rawCfg); err != nil {
		t.Fatal(err)
	}
	took := time.Since(start)

	for round := 0; round < 5; round++ {
		load(t, previous)

		rawCfg := unmarshal()
		done := make(chan error, 1)
		go func() {
			_, err := validate(rawCfg)
			done <- err
		}()

		time.Sleep(took / 4)
		load(t, next)

		if err := <-done; err != nil {
			t.Fatalf("round %d: %v", round, err)
		}
		if mode := tunnel.Mode(); mode != tunnel.Rule {
			t.Fatalf("round %d: the loaded subscription runs in mode %s, its own is %s (a validation takes %s)",
				round, mode, tunnel.Rule, took)
		}
	}
}
