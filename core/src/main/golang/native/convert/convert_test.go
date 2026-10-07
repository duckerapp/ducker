package convert

import (
	"strings"
	"testing"

	"github.com/metacubex/mihomo/common/yaml"
)

// Шаблон нарочно похож на встроенный (D-29): служебный узел, на который ссылается
// группа, и наполнение группы нативным ключом ядра.
const template = `
mode: rule
proxies:
  - name: DIRECTv4
    type: direct
proxy-groups:
  - name: Основной
    type: select
    include-all-proxies: true
    proxies:
      - DIRECTv4
rules:
  - MATCH,Основной
`

const links = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=tls&sni=example.com#%D0%A4%D1%80%D0%B0%D0%BD%D0%BA%D1%84%D1%83%D1%80%D1%82\n" +
	"trojan://secret@example.org:8443?sni=example.org#Amsterdam\n"

func TestApplyTemplateAddsNodesKeepingTemplate(t *testing.T) {
	out, _, err := ApplyTemplate(links, template, "")
	if err != nil {
		t.Fatalf("конвертация не прошла: %v", err)
	}

	var document map[string]any
	if err := yaml.Unmarshal([]byte(out), &document); err != nil {
		t.Fatalf("на выходе не YAML: %v", err)
	}

	proxies, ok := document["proxies"].([]any)
	if !ok {
		t.Fatal("в результате нет ключа proxies")
	}

	// Служебный узел шаблона плюс два узла из ссылок.
	if len(proxies) != 3 {
		t.Fatalf("ожидалось 3 узла (1 из шаблона + 2 из ссылок), получено %d", len(proxies))
	}

	if first, _ := proxies[0].(map[string]any); first["name"] != "DIRECTv4" {
		t.Fatalf("узел шаблона потерялся или уехал: %v", proxies[0])
	}

	// Остальное содержимое шаблона обязано остаться нетронутым: группы наполняются
	// ключами ядра, и если шаблон поехал, узлы в них не попадут (F-18).
	if _, ok := document["proxy-groups"]; !ok {
		t.Fatal("из шаблона пропали группы")
	}
	if document["mode"] != "rule" {
		t.Fatalf("из шаблона пропал режим: %v", document["mode"])
	}

	if !strings.Contains(out, "Франкфурт") {
		t.Fatal("имя узла из ссылки не доехало до конфига")
	}
}

func TestBase64SubscriptionIsAccepted(t *testing.T) {
	// Подписки часто отдаются в base64 — ConvertsV2Ray распознаёт это сам (F-23).
	encoded := "dmxlc3M6Ly8xMTExMTExMS0yMjIyLTMzMzMtNDQ0NC01NTU1NTU1NTU1NTVAZXhhbXBsZS5jb206NDQzP3R5cGU9dGNwJnNlY3VyaXR5PXRscyZzbmk9ZXhhbXBsZS5jb20jTm9kZQ=="

	out, _, err := ApplyTemplate(encoded, template, "")
	if err != nil {
		t.Fatalf("base64-подписка не разобралась: %v", err)
	}

	if !strings.Contains(out, "example.com") {
		t.Fatal("узел из base64-подписки не доехал до конфига")
	}
}

func TestDirectHostRuleGoesFirst(t *testing.T) {
	// Иначе при мёртвых узлах подписку не обновить — а именно тогда это и нужно (F-53).
	out, _, err := ApplyTemplate(links, template, "sub.example.net")
	if err != nil {
		t.Fatalf("конвертация не прошла: %v", err)
	}

	var document map[string]any
	if err := yaml.Unmarshal([]byte(out), &document); err != nil {
		t.Fatalf("на выходе не YAML: %v", err)
	}

	rules, ok := document["rules"].([]any)
	if !ok || len(rules) == 0 {
		t.Fatal("правила потерялись")
	}

	if rules[0] != "DOMAIN,sub.example.net,DIRECT" {
		t.Fatalf("правило для домена подписки не первое: %v", rules[0])
	}

	if rules[len(rules)-1] != "MATCH,Основной" {
		t.Fatalf("правила шаблона потерялись: %v", rules)
	}
}

func TestNodesWithoutNameGetOne(t *testing.T) {
	// Якорь в ссылке необязателен, а узел без имени нельзя выбрать: группы адресуют
	// участников по имени.
	const noName = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=tls&sni=example.com\n" +
		"vless://11111111-2222-3333-4444-555555555556@example.com:443?type=tcp&security=tls&sni=example.com\n"

	out, _, err := ApplyTemplate(noName, template, "")
	if err != nil {
		t.Fatalf("конвертация не прошла: %v", err)
	}

	var document map[string]any
	if err := yaml.Unmarshal([]byte(out), &document); err != nil {
		t.Fatalf("на выходе не YAML: %v", err)
	}

	names := map[string]bool{}
	for _, item := range document["proxies"].([]any) {
		name, _ := item.(map[string]any)["name"].(string)

		if strings.TrimSpace(name) == "" {
			t.Fatal("узел остался без имени")
		}

		if names[name] {
			t.Fatalf("имена узлов совпали: %s", name)
		}

		names[name] = true
	}
}

func TestGarbageIsRejected(t *testing.T) {
	if _, _, err := ApplyTemplate("совершенно не ссылки", template, ""); err == nil {
		t.Fatal("мусор должен отвергаться, иначе получится пустой конфиг без узлов")
	}
}

// Конфиг провайдера с ошибкой в правиле: ядро его не примет, но узлы в нём целы.
const brokenConfig = `
mode: rule
proxies:
  - {name: "Франкфурт", type: ss, server: example.com, port: 8388, cipher: aes-128-gcm, password: secret}
  - {name: "Amsterdam", type: trojan, server: example.org, port: 8443, password: secret}
  - {type: ss, server: nameless.example, port: 1}
  - {name: "Опечатка в шифре", type: ss, server: example.net, port: 8388, cipher: no-such-cipher, password: secret}
proxy-groups:
  - {name: Main, type: select, proxies: ["Франкфурт", "Amsterdam"]}
rules:
  - MATCH,NoSuchGroup
`

func TestBrokenConfigGivesItsNodes(t *testing.T) {
	out, fromConfig, err := ApplyTemplate(brokenConfig, template, "sub.example.net")
	if err != nil {
		t.Fatalf("конфиг с целыми узлами должен дать шаблон с этими узлами: %v", err)
	}

	if !fromConfig {
		t.Fatal("служба должна узнать, что узлы взяты из конфига: от этого зависит, пускать ли шаблон")
	}

	var document struct {
		Proxies []map[string]any `yaml:"proxies"`
		Rules   []string         `yaml:"rules"`
	}
	if err := yaml.Unmarshal([]byte(out), &document); err != nil {
		t.Fatal(err)
	}

	names := []string{}
	for _, proxy := range document.Proxies {
		names = append(names, proxy["name"].(string))
	}

	// Служебный узел шаблона и два узла провайдера. Запись без имени и узел, который ядро
	// собрать не может, не берутся: остальные из-за них пропадать не должны.
	if strings.Join(names, ",") != "DIRECTv4,Франкфурт,Amsterdam" {
		t.Fatalf("узлы: %v", names)
	}

	// Правила — шаблона, а не провайдера: в них-то и была ошибка.
	if strings.Contains(out, "NoSuchGroup") {
		t.Fatal("в готовый конфиг попало правило провайдера")
	}

	if len(document.Rules) == 0 || document.Rules[0] != "DOMAIN,sub.example.net,DIRECT" {
		t.Fatalf("первое правило: %v", document.Rules)
	}
}

func TestLinksAreNotTakenForConfig(t *testing.T) {
	if _, fromConfig, err := ApplyTemplate(links, template, ""); err != nil || fromConfig {
		t.Fatalf("список ссылок — не конфиг: fromConfig=%v, err=%v", fromConfig, err)
	}
}

func TestConfigWithoutNodesIsRejected(t *testing.T) {
	for name, content := range map[string]string{
		"без узлов":       "mode: rule\nproxies: []\nrules:\n  - MATCH,DIRECT\n",
		"страница ошибки": "<html><body><h1>502 Bad Gateway</h1></body></html>",
		"битый YAML":      "proxies:\n  - {name: [broken\nrules: !!!\n",
		"пусто":           "",
	} {
		if _, _, err := ApplyTemplate(content, template, ""); err == nil {
			t.Fatalf("%s: должно отвергаться, узлов взять неоткуда", name)
		}
	}
}
