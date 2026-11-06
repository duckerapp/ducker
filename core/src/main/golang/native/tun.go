package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/panicguard"
	"unsafe"
)

func init() {
	bridgeMarkSocket = func(callback unsafe.Pointer, fd int) bool {
		return C.mark_socket(callback, C.int(fd)) != 0
	}
	bridgeQuerySocketUid = func(callback unsafe.Pointer, protocol int, source, target string) int {
		return int(C.query_socket_uid(callback, C.int(protocol), C.CString(source), C.CString(target)))
	}
	bridgeRelease = func(callback unsafe.Pointer) {
		C.release_object(callback)
	}
}

//export startTun
func startTun(fd C.int, stack, congestion, gateway, portal, dns C.c_string, callback unsafe.Pointer) C.int {
	defer panicguard.Recover("startTun", failExport)

	if !swapTun(int(fd), C.GoString(stack), C.GoString(congestion), C.GoString(gateway), C.GoString(portal), C.GoString(dns), callback) {
		return 1
	}

	return 0
}

//export stopTun
func stopTun() {
	defer panicguard.Recover("stopTun", failExport)

	closeTun()
}
