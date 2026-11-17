package config

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

const mobileNodes = `
proxies:
  - {name: NL-1, type: ss, server: 127.0.0.1, port: 1, cipher: aes-128-gcm, password: x}
  - {name: LTE-1, type: ss, server: 127.0.0.1, port: 2, cipher: aes-128-gcm, password: x}
  - {name: LTE-2, type: ss, server: 127.0.0.1, port: 3, cipher: aes-128-gcm, password: x}
`

func writeFiles(t *testing.T, files map[string]string) string {
	dir := t.TempDir()
	for name, text := range files {
		if err := os.WriteFile(filepath.Join(dir, name), []byte(text), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	return dir
}

// D-534: the key or the header names a group; its members that the main group has, in the group's order.
func TestQueryMobilePreset(t *testing.T) {
	cases := []struct {
		name, yaml, header string
		want               []string
	}{
		{"hidden group, nodes in main", `
ducker: {mobile-group: LTE}
proxy-groups:
  - {name: Proxy, type: select, proxies: [NL-1, LTE-1, LTE-2]}
  - {name: LTE, type: fallback, hidden: true, proxies: [LTE-2, LTE-1, NL-9]}
`, "", []string{"LTE-2", "LTE-1"}},
		{"the key wins over the header", `
ducker: {mobile-group: LTE}
proxy-groups:
  - {name: Proxy, type: select, proxies: [NL-1, LTE-1, LTE-2]}
  - {name: LTE, type: fallback, proxies: [LTE-1]}
  - {name: Other, type: select, proxies: [NL-1]}
`, "Other", []string{"LTE-1"}},
		{"the header alone", `
proxy-groups:
  - {name: Proxy, type: select, proxies: [NL-1, LTE-1, LTE-2]}
  - {name: LTE, type: url-test, proxies: [LTE-1, LTE-2]}
`, " LTE ", []string{"LTE-1", "LTE-2"}},
		{"nodes not in main, the group is", `
ducker: {mobile-group: LTE}
proxy-groups:
  - {name: Proxy, type: select, proxies: [NL-1, LTE]}
  - {name: LTE, type: fallback, proxies: [LTE-1, LTE-2]}
`, "", []string{"LTE"}},
		{"neither in main", `
ducker: {mobile-group: LTE}
proxy-groups:
  - {name: Proxy, type: select, proxies: [NL-1]}
  - {name: LTE, type: fallback, proxies: [LTE-1, LTE-2]}
`, "", []string{}},
		{"no such group", `
ducker: {mobile-group: Нет}
proxy-groups:
  - {name: Proxy, type: select, proxies: [NL-1, LTE-1]}
`, "", []string{}},
		{"no preset", `
proxy-groups:
  - {name: Proxy, type: select, proxies: [NL-1, LTE-1]}
  - {name: LTE, type: fallback, proxies: [LTE-1]}
`, "", []string{}},
	}

	for _, c := range cases {
		dir := writeFiles(t, map[string]string{"config.yaml": mobileNodes + c.yaml})
		if got := QueryMobilePreset(dir, c.header); !reflect.DeepEqual(got, c.want) {
			t.Errorf("%s: got %v, want %v", c.name, got, c.want)
		}
	}
}

// A configuration template's config has its own groups: the key and the group come from the provider's download.
func TestQueryMobilePresetTemplate(t *testing.T) {
	dir := writeFiles(t, map[string]string{
		"source.yaml": mobileNodes + `
ducker: {mobile-group: LTE}
proxy-groups:
  - {name: Proxy, type: select, proxies: [NL-1]}
  - {name: LTE, type: fallback, hidden: true, proxies: [LTE-2, LTE-1]}
`,
		"config.yaml": mobileNodes + `
proxy-groups:
  - {name: Основной профиль, type: select, include-all-proxies: true}
`,
	})

	if got := QueryMobilePreset(dir, ""); !reflect.DeepEqual(got, []string{"LTE-2", "LTE-1"}) {
		t.Fatalf("got %v", got)
	}
}
