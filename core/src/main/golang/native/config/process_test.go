package config

import (
	"slices"
	"testing"

	"github.com/metacubex/mihomo/config"
)

// A profile without a `dns` section gets our defaults: global resolvers and the DNS of
// the phone's network, nothing tied to one country.
func TestDefaultDNSForProfileWithoutSection(t *testing.T) {
	cfg := config.DefaultRawConfig()

	if err := patchDns(cfg, t.TempDir()); err != nil {
		t.Fatal(err)
	}

	want := []string{"8.8.4.4", "1.0.0.1", "system://"}

	if !cfg.DNS.Enable || !slices.Equal(cfg.DNS.NameServer, want) {
		t.Fatalf("enable=%v nameserver=%v, want %v", cfg.DNS.Enable, cfg.DNS.NameServer, want)
	}
}
