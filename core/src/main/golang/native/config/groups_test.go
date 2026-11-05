package config

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

// The main group of the built-in template takes its nodes by `include-all-proxies` and drops service lines by
// `exclude-filter`: read from the file, it lists them as the core does (D-424).
func TestQueryGroupsExpandsIncludeAll(t *testing.T) {
	dir := t.TempDir()
	yaml := `
proxies:
  - {name: "Москва БС-1", type: ss, server: 127.0.0.1, port: 1, cipher: aes-128-gcm, password: x}
  - {name: "Истекает 30.11", type: ss, server: 127.0.0.1, port: 2, cipher: aes-128-gcm, password: x}
  - {name: "Франкфурт", type: ss, server: 127.0.0.1, port: 3, cipher: aes-128-gcm, password: x}
proxy-groups:
  - {name: Основной профиль, type: select, include-all-proxies: true, exclude-filter: "Истекает|Трафик", proxies: [⚡ AUTO]}
  - {name: Россия, type: select, include-all: true, filter: "БС"}
  - {name: Скрытая, type: select, hidden: true, proxies: [Франкфурт]}
  - {name: Своя, type: select, proxies: [Франкфурт, Франкфурт], exclude-filter: "[bad"}
  - {name: ⚡ AUTO, type: fallback, include-all-proxies: true}
`
	if err := os.WriteFile(filepath.Join(dir, "config.yaml"), []byte(yaml), 0o644); err != nil {
		t.Fatal(err)
	}

	got := map[string][]string{}
	var order []string
	for _, group := range QueryGroups(dir) {
		got[group.Name] = group.Proxies
		order = append(order, group.Name)
	}

	want := map[string][]string{
		"Основной профиль": {"⚡ AUTO", "Москва БС-1", "Франкфурт"},
		"Россия":           {"Москва БС-1"},
		"Своя":             {"Франкфурт"},
		"⚡ AUTO":           {"Москва БС-1", "Истекает 30.11", "Франкфурт"},
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("groups:\n got %v\nwant %v", got, want)
	}

	if !reflect.DeepEqual(order, []string{"Основной профиль", "Россия", "Своя", "⚡ AUTO"}) {
		t.Fatalf("order: %v", order)
	}
}
