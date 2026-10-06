//go:build debug
// +build debug

package main

import (
	"cfa/native/panicguard"
	"net/http"
	_ "net/http/pprof"

	"github.com/metacubex/mihomo/log"
)

func init() {
	go func() {
		defer panicguard.Recover("pprof.server", nil)
		log.Debugln("pprof service listen at: 0.0.0.0:8888")

		_ = http.ListenAndServe("0.0.0.0:8888", nil)
	}()
}
