package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/panicguard"
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

		dns, dials := newThrottle(dnsWindow), newThrottle(dialWindow)
		// Only while a burst is open, so an idle tunnel is not woken.
		var flushAt <-chan time.Time

		send := func(level log.LogLevel, payload string) bool {
			// Многострочное — распечатка настроек на сотню строк — в одну строку.
			if i := strings.IndexByte(payload, '\n'); i >= 0 {
				payload = payload[:i] + " (…)"
			}

			rMsg := &message{
				Level:   level.String(),
				Message: payload,
				Time:    time.Now().UnixNano() / 1000 / 1000,
			}

			return C.logcat_received(remote, marshalJson(rMsg)) == 0
		}

		for {
			select {
			case msg, open := <-sub:
				if !open {
					return
				}
				if !recorded(msg.LogLevel, msg.Payload) {
					continue
				}

				payload, passed := msg.Payload, true
				if key, line, ok := dnsFailure(payload); ok {
					payload, passed = dns.pass(key, line, msg.LogLevel, time.Now())
				} else if key, ok := dialFailure(payload); ok {
					payload, passed = dials.pass(key, payload, msg.LogLevel, time.Now())
				}
				if passed && !send(msg.LogLevel, payload) {
					return
				}
			case <-flushAt:
				flushAt = nil
				now := time.Now()
				for _, b := range append(dns.flush(now), dials.flush(now)...) {
					if !send(b.level, b.line) {
						return
					}
				}
			}

			if flushAt == nil && len(dns.keys)+len(dials.keys) > 0 {
				flushAt = time.After(dialWindow)
			}
		}
	}(remote)
}
