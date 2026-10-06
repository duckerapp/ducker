//go:build pgo

package main

import (
	"cfa/native/panicguard"
	"net/http"
	_ "net/http/pprof"

	"github.com/metacubex/mihomo/log"
)

// Снятие профиля для PGO (D-79).
//
// Собирается только с тегом `pgo` (`./gradlew ... -Ppgo`), в обычную сборку не попадает.
// Отдельный тег, а не готовый `debug`: тот включает в sing проверку утечек буферов,
// и ядро падает через несколько секунд работы (F-48), — профиля не снять.
//
// Слушаем только петлю: порт наружу не торчит, снимать профиль с компьютера через
// `adb forward tcp:8899 tcp:8899`.
func init() {
	go func() {
		defer panicguard.Recover("pprof.server", nil)
		log.Warnln("pprof (сборка для PGO) слушает 127.0.0.1:8899")

		_ = http.ListenAndServe("127.0.0.1:8899", nil)
	}()
}
