package main

//#include "bridge.h"
import "C"

import (
	"unsafe"

	"cfa/native/panicguard"
	"github.com/metacubex/mihomo/log"
)

func failExport(err error) {
	log.Errorln("[APP] %s", err)
	C.raise_exception(C.CString(err.Error()))
}

func failCompletable(callback unsafe.Pointer) func(error) {
	return func(err error) {
		log.Errorln("[APP] %s", err)
		C.complete(callback, C.CString(err.Error()))
	}
}

func failFetch(callback unsafe.Pointer) func(error) {
	return func(err error) {
		log.Errorln("[APP] %s", err)
		C.fetch_complete(callback, C.CString(err.Error()))
	}
}

func completeAsync(callback unsafe.Pointer, operation string, work func() error) {
	go panicguard.Complete(operation, work, func(err error) {
		if _, recovered := err.(*panicguard.Error); recovered {
			log.Errorln("[APP] %s", err)
		}
		C.complete(callback, marshalString(err))
	}, func() { C.release_object(callback) })
}

func runAsync(operation string, work func()) {
	go func() {
		defer panicguard.Recover(operation, func(err error) { log.Errorln("[APP] %s", err) })
		work()
	}()
}
