package config

import (
	"encoding/json"
	"net/netip"
	"strings"
	"sync"

	findprocess "github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/rules"
)

// ducker: свои правила человека поверх правил провайдера (D-123). Служба передаёт их
// перед каждой загрузкой профиля: хранятся они у приложения, по подписке, и переживают
// её обновление, при котором конфиг перекачивается целиком.

// UserRule — правило, как его хранит приложение: что и куда. Строку правила mihomo
// собираем здесь сами: из свободного текста в конфиг не попадает ничего, кроме значения,
// проверенного по своему типу, — кривое правило уронило бы разбор всего профиля.
type UserRule struct {
	Type   string `json:"type"`   // site, app, ip, keyword, geosite, geoip
	Value  string `json:"value"`  // домен, имя пакета, адрес или сеть, слово, категория, страна
	Target string `json:"target"` // direct, proxy, block
}

var (
	userRulesLock sync.Mutex
	userRules     []UserRule
)

// SetUserRules заменяет правила для следующих загрузок. Пустая строка — правил нет.
func SetUserRules(data string) error {
	var rules []UserRule

	if strings.TrimSpace(data) != "" {
		if err := json.Unmarshal([]byte(data), &rules); err != nil {
			return err
		}
	}

	userRulesLock.Lock()
	userRules = rules
	userRulesLock.Unlock()

	return nil
}

func currentUserRules() []UserRule {
	userRulesLock.Lock()
	defer userRulesLock.Unlock()

	return userRules
}

// patchUserRules ставит правила человека перед правилами провайдера: правила mihomo
// проверяются сверху вниз, и первое совпадение решает.
func patchUserRules(cfg *config.RawConfig, _ string) error {
	rules := currentUserRules()
	if len(rules) == 0 {
		return nil
	}

	proxy := mainTarget(cfg)
	prepared := make([]string, 0, len(rules))
	apps := false

	for _, rule := range rules {
		line, ok := userRuleLine(rule, proxy)
		if !ok {
			log.Warnln("[APP] Skip user rule %s %q", rule.Type, rule.Value)

			continue
		}

		if rule.Type == "app" {
			apps = true
		}

		prepared = append(prepared, line)
	}

	// Правило на приложение работает, только если ядро ищет, чьё соединение. Провайдер
	// мог выключить поиск ради скорости; strict ищет лишь для правил, которым это нужно.
	if apps && cfg.FindProcessMode == findprocess.FindProcessOff {
		cfg.FindProcessMode = findprocess.FindProcessStrict
	}

	cfg.Rule = append(prepared, cfg.Rule...)

	log.Infoln("[APP] User rules: %d", len(prepared))

	return nil
}

// mainTarget — куда ведёт «через VPN»: первая группа с ручным выбором, как на главной,
// иначе первая группа, иначе первый узел. Имён групп провайдера приложение не знает.
func mainTarget(cfg *config.RawConfig) string {
	for _, group := range cfg.ProxyGroup {
		if kind, _ := group["type"].(string); strings.EqualFold(kind, "select") {
			if name, _ := group["name"].(string); name != "" {
				return name
			}
		}
	}

	for _, group := range cfg.ProxyGroup {
		if name, _ := group["name"].(string); name != "" {
			return name
		}
	}

	for _, proxy := range cfg.Proxy {
		if name, _ := proxy["name"].(string); name != "" {
			return name
		}
	}

	return ""
}

func userRuleLine(rule UserRule, proxy string) (string, bool) {
	var target string

	switch rule.Target {
	case "direct":
		target = "DIRECT"
	case "block":
		target = "REJECT"
	case "proxy":
		target = proxy
	}

	if target == "" || strings.ContainsAny(target, ",\n") {
		return "", false
	}

	value := strings.TrimSpace(rule.Value)

	switch rule.Type {
	case "site":
		if !validDomain(value) {
			return "", false
		}

		return "DOMAIN-SUFFIX," + value + "," + target, true
	case "app":
		if !validPackage(value) {
			return "", false
		}

		return "PROCESS-NAME," + value + "," + target, true
	case "ip":
		prefix, err := netip.ParsePrefix(value)
		if err != nil {
			addr, err := netip.ParseAddr(value)
			if err != nil {
				return "", false
			}

			prefix = netip.PrefixFrom(addr, addr.BitLen())
		}

		kind := "IP-CIDR"
		if prefix.Addr().Is6() {
			kind = "IP-CIDR6"
		}

		return kind + "," + prefix.Masked().String() + "," + target + ",no-resolve", true
	case "keyword":
		if !validKeyword(value) {
			return "", false
		}

		return "DOMAIN-KEYWORD," + value + "," + target, true
	case "geosite":
		// Категории нет в базе — mihomo не разберёт весь конфиг. Проверяем тем же разбором
		// и такую пропускаем (D-126).
		if !validCategory(value) {
			return "", false
		}
		if _, err := rules.ParseRule("GEOSITE", value, target, nil, nil); err != nil {
			return "", false
		}

		return "GEOSITE," + value + "," + target, true
	case "geoip":
		// Без no-resolve: страна чаще нужна для сайтов, а не для голых адресов, и адрес
		// сайта ядро узнаёт, только спросив DNS.
		country := strings.ToUpper(value)
		if len(country) != 2 || !isLetters(country) {
			return "", false
		}

		return "GEOIP," + country + "," + target, true
	}

	return "", false
}

// validKeyword — часть адреса: буквы, цифры, точка, дефис; от двух знаков, иначе совпадёт всё.
func validKeyword(value string) bool {
	if len(value) < 2 {
		return false
	}

	for _, c := range value {
		if !(c == '.' || c == '-' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z') {
			return false
		}
	}

	return true
}

// validCategory — имя категории geosite: `youtube`, `category-ads-all`, `geolocation-!cn`.
func validCategory(value string) bool {
	if value == "" {
		return false
	}

	for _, c := range value {
		if !(c == '-' || c == '_' || c == '!' || c == '@' || c == ':' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z') {
			return false
		}
	}

	return true
}

func isLetters(value string) bool {
	for _, c := range value {
		if c < 'A' || c > 'Z' {
			return false
		}
	}

	return true
}

func validDomain(value string) bool {
	if value == "" || len(value) > 253 || !strings.Contains(value, ".") {
		return false
	}

	for _, c := range value {
		if !(c == '.' || c == '-' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c > 0x7f) {
			return false
		}
	}

	return true
}

func validPackage(value string) bool {
	if value == "" || !strings.Contains(value, ".") {
		return false
	}

	for _, c := range value {
		if !(c == '.' || c == '_' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
			return false
		}
	}

	return true
}
