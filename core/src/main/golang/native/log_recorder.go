package main

import (
	"fmt"
	"strings"
	"time"

	"github.com/metacubex/mihomo/log"
)

// recorded — что пишет самописец: ошибки и предупреждения всегда; info — без строки о
// каждом соединении (их сотни, и в них адреса сайтов) и без перечней при загрузке
// конфига — распечатки DNS построчно и списка провайдеров, около сотни строк на каждый
// подъём туннеля; из отладочных — только мультиплекс и перепроверка узлов (D-105, D-110).
func recorded(level log.LogLevel, payload string) bool {
	switch level {
	case log.ERROR, log.WARNING:
		return true
	case log.INFO:
		connection := (strings.HasPrefix(payload, "[TCP] ") || strings.HasPrefix(payload, "[UDP] ")) &&
			strings.Contains(payload, " match ")
		listing := strings.HasPrefix(payload, " ") ||
			strings.HasPrefix(payload, "Start initial provider") ||
			strings.HasPrefix(payload, "Start initial compatible provider") ||
			strings.HasPrefix(payload, "Geodata Loader mode") ||
			strings.HasPrefix(payload, "Geosite Matcher implementation")
		return !connection && !listing
	case log.DEBUG:
		_, _, dns := dnsFailure(payload)
		return strings.HasPrefix(payload, "mux:") || strings.HasPrefix(payload, "Recheck") || dns
	}
	return false
}

// dnsFailure — отказ DNS (F-163): какой сервер не ответил и почему. Имя сайта в самописец
// не пишем: у отказа отдельного сервера его нет (строка из своего mihomo, `dns/util.go`),
// а из общего отказа запроса его вырезаем.
func dnsFailure(payload string) (key, line string, ok bool) {
	const failed = " failed: "
	if rest, found := strings.CutPrefix(payload, "[DNS] "); found {
		if server, _, found := strings.Cut(rest, failed); found && !strings.ContainsAny(server, " \t") {
			return server, payload, true
		}
	}
	if query, found := strings.CutPrefix(payload, "[DNS Server] Exchange "); found {
		if _, reason, found := strings.Cut(query, failed); found {
			return "exchange", "[DNS Server] Exchange failed: " + reason, true
		}
	}
	return "", "", false
}

// dialFailure — неудачное соединение туннеля (`tunnel.go`, logMetadataErr): ключ — прокси, сервер узла
// и причина. Приложение и сайт в ключ не входят: без сети отказывает каждое соединение каждого
// приложения, и 36 минут таких строк стёрли из самописца начало сбоя (F-517).
func dialFailure(payload string) (key string, ok bool) {
	rest, found := strings.CutPrefix(payload, "[TCP] dial ")
	if !found {
		if rest, found = strings.CutPrefix(payload, "[UDP] dial "); !found {
			return "", false
		}
	}
	head, reason, found := strings.Cut(rest, " --> ")
	if !found {
		return "", false
	}
	if _, reason, found = strings.Cut(reason, " error: "); !found {
		return "", false
	}
	proxy, _, matched := strings.Cut(head, " (match ")
	if !matched {
		// Без правила за прокси сразу идёт источник, и в нём нет пробелов.
		if i := strings.LastIndexByte(head, ' '); i >= 0 {
			proxy = head[:i]
		}
	}
	server, _, _ := strings.Cut(reason, " connect error: ")
	if server == reason {
		server = "" // DIRECT: в ошибке адрес сайта, а не узла
	}
	if i := strings.LastIndex(reason, ": "); i >= 0 {
		reason = reason[i+2:]
	}
	return payload[:6] + proxy + "|" + server + "|" + reason, true
}

// dnsWindow — не чаще одной строки на сервер: мёртвый сервер в списке отказывает на каждый
// запрос, и без этого забил бы самописец за минуты.
const dnsWindow = 5 * time.Minute

// dialWindow — короче, чем у DNS: по этим строкам видно, с какой минуты узел перестал отвечать.
const dialWindow = time.Minute

// checkLimit — node check changes a minute, all nodes together (D-527): when the network goes, every node of a long
// subscription stops answering at once, and thousands of them would wipe the recorder as the dial failures did.
const checkLimit = 30

// checkChange — a node's check answer changed (the fork's `adapter.go`, logCheck).
func checkChange(payload string) bool {
	return strings.HasPrefix(payload, "[Check] ")
}

// burst — повторы одного ключа: с какого момента считаем, сколько пропустили, и последний пропущенный.
type burst struct {
	first, last     time.Time
	passed, skipped int
	level           log.LogLevel
	line            string
}

// throttle пропускает первые limit строк ключа за окно, остальные считает. Число называет следующая
// пропущенная строка ключа, а если повторы кончились — закрывающая строка из [throttle.flush]:
// иначе не видно, когда сбой прошёл.
type throttle struct {
	window time.Duration
	limit  int
	keys   map[string]*burst
}

func newThrottle(window time.Duration, limit int) *throttle {
	return &throttle{window: window, limit: limit, keys: map[string]*burst{}}
}

func (t *throttle) pass(key, line string, level log.LogLevel, now time.Time) (string, bool) {
	b := t.keys[key]
	if b != nil && now.Sub(b.first) < t.window {
		if b.passed < t.limit {
			b.passed++
			b.last = now
			return line, true
		}
		b.skipped++
		b.last, b.level, b.line = now, level, line
		return "", false
	}
	if b != nil && b.skipped > 0 {
		line += fmt.Sprintf(" (+%d since %s)", b.skipped, b.first.Format("15:04:05"))
	}
	t.keys[key] = &burst{first: now, last: now, passed: 1}
	return line, true
}

// flush закрывает ключи, у которых окно прошло без повторов; для пропущенных — последняя из них
// с их числом и временем.
func (t *throttle) flush(now time.Time) (lines []burst) {
	for key, b := range t.keys {
		if now.Sub(b.last) < t.window {
			continue
		}
		if b.skipped > 0 {
			b.line += fmt.Sprintf(" (+%d since %s, the last at %s)", b.skipped, b.first.Format("15:04:05"),
				b.last.Format("15:04:05"))
			lines = append(lines, *b)
		}
		delete(t.keys, key)
	}
	return lines
}
