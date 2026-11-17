package main

import (
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/log"
)

func TestDialFailureKey(t *testing.T) {
	const unreachable = "[TCP] dial Основной профиль (match Match/) 172.19.0.1:37680(com.tencent.mm) --> 1.2.3.4:8080 error: de1.example:443 connect error: dial tcp4 5.6.7.8:443: connect: network is unreachable"
	cases := []struct {
		payload, key string
	}{
		{unreachable, "[TCP] Основной профиль|de1.example:443|network is unreachable"},
		// Другое приложение и сайт — тот же ключ.
		{"[TCP] dial Основной профиль (match Match/) 172.19.0.1:51800(com.microsoft.appmanager) --> mobile.pipe.aria.microsoft.com:443 error: de1.example:443 connect error: dial tcp4 5.6.7.8:443: connect: network is unreachable",
			"[TCP] Основной профиль|de1.example:443|network is unreachable"},
		{"[TCP] dial Основной профиль (match Match/) 172.19.0.1:1(x) --> a.com:443 error: es.example:443 connect error: dial tcp4 9.9.9.9:443: i/o timeout",
			"[TCP] Основной профиль|es.example:443|i/o timeout"},
		// DIRECT: в ошибке адрес сайта, сервера нет.
		{"[TCP] dial Без VPN (match OR/(x)) 172.19.0.1:5 --> 1.1.1.1:5228 error: dial tcp4 1.1.1.1:5228: i/o timeout",
			"[TCP] Без VPN||i/o timeout"},
		// Без правила.
		{"[UDP] dial PROXY 172.19.0.1:5(app) --> 1.1.1.1:443 error: es.example:443 connect error: EOF",
			"[UDP] PROXY|es.example:443|EOF"},
	}
	for _, c := range cases {
		if key, ok := dialFailure(c.payload); !ok || key != c.key {
			t.Errorf("%q: got %q %v, want %q", c.payload, key, ok, c.key)
		}
	}
	for _, other := range []string{"[TCP] 🇩🇪 [3] Frankfurt: no answer on 1 of 152 connection(s), the last to x:443", "mux: session alive", "[TCP] dial without arrow"} {
		if _, ok := dialFailure(other); ok {
			t.Errorf("%q taken for a dial failure", other)
		}
	}
}

func TestThrottleBurst(t *testing.T) {
	th := newThrottle(time.Minute, 1)
	start := time.Date(2026, 11, 17, 12, 8, 39, 0, time.Local)
	passed := 0
	// 36 минут без сети, отказ каждые 2 секунды: 1080 строк.
	for i := 0; i < 1080; i++ {
		if _, ok := th.pass("k", "line", log.WARNING, start.Add(time.Duration(i)*2*time.Second)); ok {
			passed++
		}
	}
	if passed != 36 {
		t.Fatalf("passed %d lines, want one a minute: 36", passed)
	}
	last := start.Add(1079 * 2 * time.Second)
	if lines := th.flush(last.Add(30 * time.Second)); len(lines) != 0 {
		t.Fatalf("flushed inside the window: %v", lines)
	}
	lines := th.flush(last.Add(time.Minute))
	if len(lines) != 1 || !strings.HasSuffix(lines[0].line, "(+29 since 12:43:39, the last at 12:44:37)") ||
		lines[0].level != log.WARNING {
		t.Fatalf("closing line: %+v", lines)
	}
	if len(th.keys) != 0 {
		t.Fatalf("keys left after the burst: %d", len(th.keys))
	}
	// Одиночная строка закрывается без строки.
	th.pass("k", "line", log.WARNING, last.Add(2*time.Minute))
	if lines := th.flush(last.Add(4 * time.Minute)); len(lines) != 0 || len(th.keys) != 0 {
		t.Fatalf("a single line: %v, keys %d", lines, len(th.keys))
	}
}

func TestThrottleCountsOnNextWindow(t *testing.T) {
	th := newThrottle(5*time.Minute, 1)
	at := time.Date(2026, 11, 17, 4, 0, 0, 0, time.Local)
	th.pass("k", "a", log.DEBUG, at)
	th.pass("k", "b", log.DEBUG, at.Add(time.Minute))
	th.pass("k", "c", log.DEBUG, at.Add(2*time.Minute))
	line, ok := th.pass("k", "d", log.DEBUG, at.Add(5*time.Minute))
	if !ok || line != "d (+2 since 04:00:00)" {
		t.Fatalf("got %q %v", line, ok)
	}
}

func TestThrottleLimit(t *testing.T) {
	th := newThrottle(time.Minute, checkLimit)
	at := time.Date(2026, 11, 17, 12, 8, 39, 0, time.Local)
	passed := 0
	// A long subscription loses the network: 8 000 nodes stop answering within 20 s.
	for i := 0; i < 8000; i++ {
		if _, ok := th.pass("check", "[Check] node stopped answering", log.WARNING, at.Add(time.Duration(i)*2500*time.Microsecond)); ok {
			passed++
		}
	}
	if passed != checkLimit {
		t.Fatalf("passed %d, want %d", passed, checkLimit)
	}
	lines := th.flush(at.Add(2 * time.Minute))
	if len(lines) != 1 || !strings.Contains(lines[0].line, "(+7970 since 12:08:39") {
		t.Fatalf("closing line: %+v", lines)
	}
	if !checkChange("[Check] 🇪🇸  Barcelona answers again, 180 ms") || checkChange("Recheck `⚡ AUTO`") {
		t.Fatal("checkChange")
	}
}
