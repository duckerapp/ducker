package config

import (
	"os"
	"path/filepath"
	"testing"

	C "github.com/metacubex/mihomo/constant"

	findprocess "github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/config"
)

// Свои правила человека (D-123): встают первыми, «через VPN» ведёт в группу с ручным
// выбором, кривое значение пропускается, а не роняет профиль.
func TestUserRulesGoFirst(t *testing.T) {
	cfg := &config.RawConfig{
		ProxyGroup: []map[string]any{
			{"name": "AUTO", "type": "fallback"},
			{"name": "Основной", "type": "select"},
		},
		Rule:            []string{"MATCH,Основной"},
		FindProcessMode: findprocess.FindProcessOff,
	}

	if err := SetUserRules(`[
		{"type":"site","value":"sberbank.ru","target":"direct"},
		{"type":"app","value":"ru.gosuslugi.app","target":"direct"},
		{"type":"ip","value":"192.168.1.5","target":"block"},
		{"type":"ip","value":"10.0.0.0/8","target":"proxy"},
		{"type":"site","value":"bad,rule","target":"direct"},
		{"type":"site","value":"x.ru","target":"nowhere"}
	]`); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = SetUserRules("") })

	if err := patchUserRules(cfg, ""); err != nil {
		t.Fatal(err)
	}

	want := []string{
		"DOMAIN-SUFFIX,sberbank.ru,DIRECT",
		"PROCESS-NAME,ru.gosuslugi.app,DIRECT",
		"IP-CIDR,192.168.1.5/32,REJECT,no-resolve",
		"IP-CIDR,10.0.0.0/8,Основной,no-resolve",
		"MATCH,Основной",
	}

	if len(cfg.Rule) != len(want) {
		t.Fatalf("правила %q, ждали %q", cfg.Rule, want)
	}
	for i := range want {
		if cfg.Rule[i] != want[i] {
			t.Fatalf("правило %d: %q, ждали %q", i, cfg.Rule[i], want[i])
		}
	}

	if cfg.FindProcessMode != findprocess.FindProcessStrict {
		t.Fatal("правило на приложение без поиска процессов не сработает")
	}
}

func TestNoUserRulesLeavesConfig(t *testing.T) {
	cfg := &config.RawConfig{Rule: []string{"MATCH,DIRECT"}}

	if err := patchUserRules(cfg, ""); err != nil {
		t.Fatal(err)
	}

	if len(cfg.Rule) != 1 {
		t.Fatalf("правила %q", cfg.Rule)
	}
}

// Слово, категория и страна (D-126): строки mihomo собираются, кривое пропускается — категория
// с чужими знаками не доходит даже до разбора.
func TestUserRulesKeywordCategoryCountry(t *testing.T) {
	cfg := &config.RawConfig{Rule: []string{"MATCH,DIRECT"}}

	if err := SetUserRules(`[
		{"type":"keyword","value":"yandex","target":"direct"},
		{"type":"keyword","value":"y","target":"direct"},
		{"type":"keyword","value":"bad,rule","target":"direct"},
		{"type":"geoip","value":"ru","target":"direct"},
		{"type":"geoip","value":"rus","target":"direct"},
		{"type":"geosite","value":"bad,rule","target":"block"}
	]`); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = SetUserRules("") })

	if err := patchUserRules(cfg, ""); err != nil {
		t.Fatal(err)
	}

	want := []string{
		"DOMAIN-KEYWORD,yandex,DIRECT",
		"GEOIP,RU,DIRECT",
		"MATCH,DIRECT",
	}

	if len(cfg.Rule) != len(want) {
		t.Fatalf("правила %q, ждали %q", cfg.Rule, want)
	}
	for i := range want {
		if cfg.Rule[i] != want[i] {
			t.Fatalf("правило %d: %q, ждали %q", i, cfg.Rule[i], want[i])
		}
	}
}

// С настоящей базой (D-126): известная категория встаёт, неизвестная пропускается, а не
// роняет разбор. База кладётся рядом с тестом (`adb push geosite.dat`), без неё — пропуск.
func TestUserRulesCategoryFromBase(t *testing.T) {
	dir, _ := os.Getwd()
	if _, err := os.Stat(filepath.Join(dir, "geosite.dat")); err != nil {
		t.Skip("нет geosite.dat рядом с тестом")
	}
	C.SetHomeDir(dir)

	cfg := &config.RawConfig{Rule: []string{"MATCH,DIRECT"}}

	if err := SetUserRules(`[
		{"type":"geosite","value":"youtube","target":"direct"},
		{"type":"geosite","value":"no-such-category","target":"direct"}
	]`); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = SetUserRules("") })

	if err := patchUserRules(cfg, ""); err != nil {
		t.Fatal(err)
	}

	want := []string{"GEOSITE,youtube,DIRECT", "MATCH,DIRECT"}

	if len(cfg.Rule) != len(want) || cfg.Rule[0] != want[0] || cfg.Rule[1] != want[1] {
		t.Fatalf("правила %q, ждали %q", cfg.Rule, want)
	}
}

