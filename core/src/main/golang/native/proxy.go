package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/panicguard"
	"cfa/native/proxy"
)

//export startHttp
func startHttp(listenAt C.c_string) *C.char {
	defer panicguard.Recover("startHttp", failExport)
	l := C.GoString(listenAt)

	listen, err := proxy.Start(l)
	if err != nil {
		return nil
	}

	return C.CString(listen)
}

//export stopHttp
func stopHttp() {
	defer panicguard.Recover("stopHttp", failExport)
	proxy.Stop()
}
