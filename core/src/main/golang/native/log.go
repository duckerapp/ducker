package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/panicguard"
	"fmt"
	"strings"
	"time"
	"unsafe"

	"github.com/metacubex/mihomo/log"
)

type message struct {
	Level   string `json:"level"`
	Message string `json:"message"`
	Time    int64  `json:"time"`
}

func init() {
	go func() {
		defer panicguard.Recover("logcat.forward", nil)
		sub := log.Subscribe()
		defer stopLogSubscription(sub)

		for msg := range sub {
			if !logcatForwarded(msg.LogLevel, log.Level(), msg.Payload) {
				continue
			}

			cPayload := C.CString(msg.Payload)

			switch msg.LogLevel {
			case log.INFO:
				C.log_info(cPayload)
			case log.ERROR:
				C.log_error(cPayload)
			case log.WARNING:
				C.log_warn(cPayload)
			case log.DEBUG:
				C.log_debug(cPayload)
			case log.SILENT:
				C.log_verbose(cPayload)
			}
		}
	}()
}

//export subscribeLogcat
func subscribeLogcat(remote unsafe.Pointer) {
	defer panicguard.Recover("subscribeLogcat", failExport)
	go func(remote unsafe.Pointer) {
		defer C.release_object(remote)
		defer panicguard.Recover("log.subscription", nil)
		sub := log.Subscribe()
		defer stopLogSubscription(sub)

		for msg := range sub {
			if msg.LogLevel < log.Level() && !strings.HasPrefix(msg.Payload, "[APP]") {
				continue
			}

			rMsg := &message{
				Level:   msg.LogLevel.String(),
				Message: msg.Payload,
				Time:    time.Now().UnixNano() / 1000 / 1000,
			}

			if C.logcat_received(remote, marshalJson(rMsg)) != 0 {
				break
			}
		}
	}(remote)

	log.Infoln("[APP] Logcat level: %s", log.Level().String())
}

// setLogLevel поднимает или опускает уровень журнала у работающего ядра.
//
// Зачем отдельно от оверрайда: оверрайд применяется при следующей загрузке конфига, то есть
// человеку пришлось бы переподключаться, чтобы увидеть события. Переключатель «подробный
// журнал» должен срабатывать сразу — иначе он выглядит сломанным.
//
//export setLogLevel
func setLogLevel(level C.c_string) C.int {
	defer panicguard.Recover("setLogLevel", failExport)
	l, exist := log.LogLevelMapping[strings.ToLower(C.GoString(level))]
	if !exist {
		return 1
	}

	log.SetLevel(l)
	log.Infoln("[APP] Logcat level: %s", l.String())

	return 0
}

// subscribeRecorder — поток событий для самописца службы (D-111). Уровень журнала профиля
// здесь не действует: провайдеры обычно ставят «только ошибки», и тогда в самописце не
// было бы ни предупреждений, ни закрытий мёртвых сессий мультиплекса. Лишнее отсекаем тут
// же, чтобы оно не шло через JNI.
//
//export subscribeRecorder
func subscribeRecorder(remote unsafe.Pointer) {
	defer panicguard.Recover("subscribeRecorder", failExport)
	go func(remote unsafe.Pointer) {
		defer C.release_object(remote)
		defer panicguard.Recover("log.subscription", nil)
		sub := log.Subscribe()
		defer stopLogSubscription(sub)

		dns := dnsThrottle{}

		for msg := range sub {
			if !recorded(msg.LogLevel, msg.Payload) {
				continue
			}

			payload := msg.Payload
			if key, line, ok := dnsFailure(payload); ok {
				if payload, ok = dns.pass(key, line, time.Now()); !ok {
					continue
				}
			}

			// Многострочное — распечатка настроек на сотню строк — в одну строку.
			if i := strings.IndexByte(payload, '\n'); i >= 0 {
				payload = payload[:i] + " (…)"
			}

			rMsg := &message{
				Level:   msg.LogLevel.String(),
				Message: payload,
				Time:    time.Now().UnixNano() / 1000 / 1000,
			}

			if C.logcat_received(remote, marshalJson(rMsg)) != 0 {
				break
			}
		}
	}(remote)
}

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

// dnsWindow — не чаще одной строки на сервер: мёртвый сервер в списке отказывает на каждый
// запрос, и без этого забил бы самописец за минуты.
const dnsWindow = 5 * time.Minute

type dnsWindowState struct {
	at      time.Time
	skipped int
}

// dnsThrottle пропускает первый отказ сервера за окно, остальные считает и называет число
// в следующей строке. ponytail: если отказы прекратились до конца окна, их число теряется;
// нужно точнее — сбрасывать счётчики по таймеру.
type dnsThrottle map[string]*dnsWindowState

func (t dnsThrottle) pass(key, line string, now time.Time) (string, bool) {
	s := t[key]
	if s == nil {
		s = &dnsWindowState{}
		t[key] = s
	} else if now.Sub(s.at) < dnsWindow {
		s.skipped++
		return "", false
	}
	if s.skipped > 0 {
		line += fmt.Sprintf(" (+%d since %s)", s.skipped, s.at.Format("15:04:05"))
	}
	s.at, s.skipped = now, 0
	return line, true
}
