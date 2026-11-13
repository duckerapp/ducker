package config

import (
	"net/http"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/component/age"
)

// Remnawave's response rule encrypts the body to the user's age key, armored (D-442): without the key the
// download fails with the text the service tells apart; with the subscription's key it is a config again.
func TestEncryptedSubscription(t *testing.T) {
	noLastResort(t)
	secret, public, err := GenX25519KeyPair()
	if err != nil {
		t.Fatal(err)
	}
	body, err := age.EncryptBytes([]byte("proxies:\n  - {name: a, type: socks5, server: 127.0.0.1, port: 1080}\n"), public)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(string(body), "-----BEGIN AGE ENCRYPTED FILE-----") {
		t.Fatalf("not armored: %.40q", body)
	}
	panel := mirrorServer(t, func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write(body) })
	defer SetGlobalSecretKeys()

	other, _, _ := GenX25519KeyPair()
	for _, keys := range [][]string{nil, {other}} {
		SetGlobalSecretKeys(keys...)
		err = FetchAndValid(t.TempDir(), panel.url, true, func(string) {})
		if err == nil || !strings.HasPrefix(err.Error(), "decrypt config error") {
			t.Fatalf("keys %v: %v", keys, err)
		}
	}

	// The service sends every subscription's key in one string, a line each (D-503).
	for _, keys := range []string{secret, other + "\n" + secret, secret + "\n" + other} {
		SetGlobalSecretKeys(keys)
		if err = FetchAndValid(t.TempDir(), panel.url, true, func(string) {}); err != nil {
			t.Fatalf("keys %q: %v", keys, err)
		}
	}
}
