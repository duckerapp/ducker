package main

import (
	"strings"

	"github.com/metacubex/mihomo/log"
)

// logcatForwarded — что из журнала ядра идёт в системный журнал Android (аудит, perf 2.3).
//
// Только предупреждения и ошибки не ниже уровня профиля. Раньше шло всё не ниже уровня
// профиля, а в конфигах панелей обычно стоит info: на нём mihomo пишет строку о каждом
// соединении с адресом сайта — вызов через JNI на каждое соединение и история посещений в
// logcat. Остальное получают подписчики: экран «Журнал» и самописец.
//
// С «Подробным журналом» (уровень debug) идёт всё, как раньше (D-111): его включают, чтобы
// разобраться, и читают в том числе кабелем. Служебные строки «[APP]» идут всегда.
func logcatForwarded(level, profile log.LogLevel, payload string) bool {
	if strings.HasPrefix(payload, "[APP]") {
		return true
	}
	if level < profile {
		return false
	}
	return profile == log.DEBUG || level >= log.WARNING
}
