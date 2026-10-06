package main

import (
	"testing"

	"github.com/metacubex/mihomo/log"
)

func TestLogcatForwarded(t *testing.T) {
	conn := "[TCP] 172.19.0.1:40000 --> example.com:443 match Match using PROXY[node]"
	cases := []struct {
		level, profile log.LogLevel
		payload        string
		want           bool
	}{
		{log.INFO, log.INFO, conn, false}, // главное: соединения на уровне info в logcat не идут
		{log.WARNING, log.INFO, "warning", true},
		{log.ERROR, log.INFO, "error", true},
		{log.WARNING, log.ERROR, "warning", false}, // уровень профиля действует по-прежнему
		{log.ERROR, log.SILENT, "error", false},
		{log.INFO, log.DEBUG, conn, true}, // «Подробный журнал» — всё, как раньше
		{log.DEBUG, log.DEBUG, "debug", true},
		{log.INFO, log.ERROR, "[APP] Logcat level: error", true},
	}
	for _, c := range cases {
		if got := logcatForwarded(c.level, c.profile, c.payload); got != c.want {
			t.Errorf("level %s, profile %s, %q: got %v, want %v", c.level, c.profile, c.payload, got, c.want)
		}
	}
}
