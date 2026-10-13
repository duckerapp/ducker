package convert

import (
	"os"
	"reflect"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
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

// What the conversion must carry over untouched: comments of every kind to drop, an anchor
// with a merge, a list written in one line, and `proxies` at the bottom.
const orderedTemplate = `# A header comment.
x-anchors:
  provider: &provider
    type: http      # a comment at the end of a line
    interval: 86400

mode: rule
allow-lan: false
dns:
  enable: true
  nameserver: [1.1.1.1, 8.8.8.8]
rule-providers:
  ads:
    <<: *provider
    behavior: domain
proxy-groups:
  - name: Основной
    type: select
    include-all-proxies: true
rules:
  # A comment before a rule.
  - RULE-SET,ads,REJECT
  - MATCH,Основной

# A comment before the last key.
proxies:
  - name: DIRECTv4
    type: direct
`

// topKeys lists the keys of the first level in the order they are written.
func topKeys(text string) string {
	keys := []string{}

	for _, line := range strings.Split(text, "\n") {
		if line == "" || strings.ContainsAny(line[:1], " -#") {
			continue
		}

		keys = append(keys, strings.SplitN(line, ":", 2)[0])
	}

	return strings.Join(keys, ",")
}

func hasComments(node *yaml.Node) bool {
	if node.HeadComment != "" || node.LineComment != "" || node.FootComment != "" {
		return true
	}

	for _, child := range node.Content {
		if hasComments(child) {
			return true
		}
	}

	return false
}

func TestTemplateKeepsItsOrderWithoutComments(t *testing.T) {
	out, _, err := ApplyTemplate(links, orderedTemplate, "sub.example.net")
	if err != nil {
		t.Fatal(err)
	}

	// A person opening their config sees the template's order, not the alphabet (D-288).
	if keys := topKeys(out); keys != "x-anchors,mode,allow-lan,dns,rule-providers,proxy-groups,rules,proxies" {
		t.Fatalf("keys of the first level: %s", keys)
	}

	for _, piece := range []string{
		// The order below the first level, and a one-line list left as written.
		"dns:\n  enable: true\n  nameserver: [1.1.1.1, 8.8.8.8]\n",
		// The anchor and its reference, not a copy in every place.
		"provider: &provider\n    type: http\n    interval: 86400\n",
		"    <<: *provider\n",
		"rules:\n  - DOMAIN,sub.example.net,DIRECT\n  - RULE-SET,ads,REJECT\n",
		// A node from a link begins with what a person looks for.
		"  - name: Франкфурт\n    type: vless\n    server: example.com\n    port: \"443\"\n",
	} {
		if !strings.Contains(out, piece) {
			t.Fatalf("not in the result: %q\n%s", piece, out)
		}
	}

	if strings.Contains(out, "#") {
		t.Fatalf("a comment went into the config:\n%s", out)
	}

	// The merge still gives the core what it gave before.
	var document struct {
		Providers map[string]map[string]any `yaml:"rule-providers"`
	}
	if err := yaml.Unmarshal([]byte(out), &document); err != nil {
		t.Fatal(err)
	}

	if document.Providers["ads"]["type"] != "http" {
		t.Fatalf("the merged provider: %v", document.Providers["ads"])
	}
}

// The built-in template itself: whatever is written into it next has to survive the same way.
func TestBuiltInTemplateMeansTheSame(t *testing.T) {
	source, err := os.ReadFile("../../../../../../app/src/main/assets/ducker/template.yaml")
	if err != nil {
		t.Fatal(err)
	}

	out, _, err := ApplyTemplate(links, string(source), "sub.example.net")
	if err != nil {
		t.Fatal(err)
	}

	if before, after := topKeys(string(source)), topKeys(out); before != after {
		t.Fatalf("keys of the first level:\n%s\nin the template:\n%s", after, before)
	}

	if !strings.HasSuffix(topKeys(out), ",proxies") {
		t.Fatal("`proxies` is not the last key")
	}

	// The encoder writes a key over 128 characters as `? key` with `: value` on the next line.
	// The core reads that, a person does not: such a key is to be split in the template.
	for _, line := range strings.Split(out, "\n") {
		if strings.HasPrefix(strings.TrimLeft(line, " "), "? ") {
			t.Fatalf("a key too long for one line: %s", line)
		}
	}

	var tree yaml.Node
	if err := yaml.Unmarshal([]byte(out), &tree); err != nil {
		t.Fatal(err)
	}

	if hasComments(&tree) {
		t.Fatal("a comment went into the config")
	}

	// To the core the result is the template with two lists longer: nodes at the end of one,
	// a rule at the start of the other.
	var before, after map[string]any
	if err := yaml.Unmarshal(source, &before); err != nil {
		t.Fatal(err)
	}
	if err := yaml.Unmarshal([]byte(out), &after); err != nil {
		t.Fatal(err)
	}

	own, _ := before["proxies"].([]any)
	proxies, _ := after["proxies"].([]any)

	if len(proxies) != len(own)+2 || !reflect.DeepEqual(proxies[:len(own)], own) {
		t.Fatalf("the template's own nodes changed: %v", proxies)
	}

	rules, _ := after["rules"].([]any)

	if len(rules) == 0 || rules[0] != "DOMAIN,sub.example.net,DIRECT" || !reflect.DeepEqual(rules[1:], before["rules"]) {
		t.Fatal("the template's rules changed")
	}

	delete(before, "proxies")
	delete(after, "proxies")
	delete(before, "rules")
	delete(after, "rules")

	if !reflect.DeepEqual(before, after) {
		t.Fatal("the rest of the template reads differently")
	}
}
