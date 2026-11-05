package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/panicguard"
	"unsafe"

	"cfa/native/app"
	"cfa/native/tunnel"

	mux "github.com/metacubex/sing-mux"
)

//export queryTunnelState
func queryTunnelState() *C.char {
	defer panicguard.Recover("queryTunnelState", failExport)
	mode := tunnel.QueryMode()

	response := &struct {
		Mode string `json:"mode"`
	}{mode}

	return marshalJson(response)
}

//export setTunnelMode
func setTunnelMode(mode C.c_string) C.int {
	defer panicguard.Recover("setTunnelMode", failExport)
	if !tunnel.SetMode(C.GoString(mode)) {
		return 1
	}

	return 0
}

//export queryNow
func queryNow(upload, download *C.uint64_t) {
	defer panicguard.Recover("queryNow", failExport)
	up, down := tunnel.Now()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryTotal
func queryTotal(upload, download *C.uint64_t) {
	defer panicguard.Recover("queryTotal", failExport)
	up, down := tunnel.Total()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryGroupNames
func queryGroupNames(excludeNotSelectable C.int) *C.char {
	defer panicguard.Recover("queryGroupNames", failExport)
	return marshalJson(tunnel.QueryProxyGroupNames(excludeNotSelectable != 0))
}

//export queryGroup
func queryGroup(name C.c_string, sortMode C.c_string) *C.char {
	defer panicguard.Recover("queryGroup", failExport)
	n := C.GoString(name)
	s := C.GoString(sortMode)

	mode := tunnel.Default

	switch s {
	case "Title":
		mode = tunnel.Title
	case "Delay":
		mode = tunnel.Delay
	}

	response := tunnel.QueryProxyGroup(n, mode, app.SubtitlePattern())

	if response == nil {
		return nil
	}

	return marshalJson(response)
}

//export healthCheck
func healthCheck(completable unsafe.Pointer, name C.c_string) {
	handedOff := false
	defer func() {
		if !handedOff {
			C.release_object(completable)
		}
	}()

	defer panicguard.Recover("healthCheck", failCompletable(completable))
	n := C.GoString(name)
	completeAsync(completable, "healthCheck.worker", func() error { return tunnel.HealthCheck(n) })
	handedOff = true
}

//export healthCheckChosen
func healthCheckChosen(completable unsafe.Pointer, name C.c_string) {
	handedOff := false
	defer func() {
		if !handedOff {
			C.release_object(completable)
		}
	}()

	defer panicguard.Recover("healthCheckChosen", failCompletable(completable))
	n := C.GoString(name)
	completeAsync(completable, "healthCheckChosen.worker", func() error { return tunnel.HealthCheckChosen(n) })
	handedOff = true
}

//export queryConnections
func queryConnections() *C.char {
	defer panicguard.Recover("queryConnections", failExport)
	return marshalJson(tunnel.QueryConnections())
}

//export closeAllConnections
func closeAllConnections() {
	defer panicguard.Recover("closeAllConnections", failExport)
	tunnel.CloseAllConnections()
}

// Сессии мультиплекса — не соединения туннеля, closeAllConnections их не видит (D-105).
// Оба вызова не ждут: сессия может держать замок клиента, пока дозванивается.

//export resetMux
func resetMux() {
	defer panicguard.Recover("resetMux", failExport)
	runAsync("resetMux.worker", mux.ResetAll)
}

//export probeMux
func probeMux() {
	defer panicguard.Recover("probeMux", failExport)
	runAsync("probeMux.worker", mux.ProbeAll)
}

//export probeProxy
func probeProxy(name, url C.c_string, timeout C.int) *C.char {
	defer panicguard.Recover("probeProxy", failExport)
	return marshalJson(tunnel.Probe(C.GoString(name), C.GoString(url), int(timeout)))
}

//export healthCheckAll
func healthCheckAll() {
	defer panicguard.Recover("healthCheckAll", failExport)
	tunnel.HealthCheckAll()
}

//export healthCheckAuto
func healthCheckAuto() {
	defer panicguard.Recover("healthCheckAuto", failExport)
	tunnel.HealthCheckAuto()
}

//export patchSelector
func patchSelector(selector, name C.c_string) C.int {
	defer panicguard.Recover("patchSelector", failExport)
	s := C.GoString(selector)
	n := C.GoString(name)

	if tunnel.PatchSelector(s, n) {
		return 1
	}

	return 0
}

//export queryProviders
func queryProviders() *C.char {
	defer panicguard.Recover("queryProviders", failExport)
	return marshalJson(tunnel.QueryProviders())
}

//export updateProvider
func updateProvider(completable unsafe.Pointer, pType C.c_string, name C.c_string) {
	handedOff := false
	defer func() {
		if !handedOff {
			C.release_object(completable)
		}
	}()

	defer panicguard.Recover("updateProvider", failCompletable(completable))
	t, n := C.GoString(pType), C.GoString(name)
	completeAsync(completable, "updateProvider.worker", func() error { return tunnel.UpdateProvider(t, n) })
	handedOff = true
}

//export suspend
func suspend(suspended C.int) {
	defer panicguard.Recover("suspend", failExport)
	tunnel.Suspend(suspended != 0)
}
