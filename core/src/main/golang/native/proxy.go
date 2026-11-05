package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/panicguard"
	"cfa/native/proxy"

	"github.com/metacubex/mihomo/log"
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

// startShare opens «Раздача по Wi-Fi» on the phone's address in network ("192.168.1.5/24") and returns the port, 0
// when it could not.
//
//export startShare
func startShare(network C.c_string, port C.int, user C.c_string, pass C.c_string) C.int {
	defer panicguard.Recover("startShare", failExport)

	at, err := proxy.StartShare(C.GoString(network), int(port), C.GoString(user), C.GoString(pass))
	if err != nil {
		log.Warnln("Sharing over Wi-Fi did not start: %s", err)
		return 0
	}

	return C.int(at)
}

//export stopShare
func stopShare() {
	defer panicguard.Recover("stopShare", failExport)
	proxy.StopShare()
}
