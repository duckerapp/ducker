package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/panicguard"
	"runtime"
	"unsafe"

	"cfa/native/config"

	"github.com/metacubex/mihomo/log"
)

type remoteValidCallback struct {
	callback unsafe.Pointer
}

func (r *remoteValidCallback) reportStatus(json string) {
	C.fetch_report(r.callback, marshalString(json))
}

type ageKeyPair struct {
	SecretKey string `json:"secretKey"`
	PublicKey string `json:"publicKey"`
}

//export fetchAndValid
func fetchAndValid(callback unsafe.Pointer, path, url C.c_string, force C.int) {
	handedOff := false
	defer func() {
		if !handedOff {
			C.release_object(callback)
		}
	}()

	defer panicguard.Recover("fetchAndValid", failFetch(callback))
	p, u := C.GoString(path), C.GoString(url)
	go panicguard.Complete("fetchAndValid.worker", func() error {
		defer runtime.GC()
		cb := &remoteValidCallback{callback: callback}
		return config.FetchAndValid(p, u, force != 0, cb.reportStatus)
	}, func(err error) {
		if _, recovered := err.(*panicguard.Error); recovered {
			log.Errorln("[APP] %s", err)
		}
		C.fetch_complete(callback, marshalString(err))
	}, func() { C.release_object(callback) })
	handedOff = true
}

//export load
func load(completable unsafe.Pointer, path C.c_string) {
	handedOff := false
	defer func() {
		if !handedOff {
			C.release_object(completable)
		}
	}()

	defer panicguard.Recover("load", failCompletable(completable))
	p := C.GoString(path)
	completeAsync(completable, "load.worker", func() error {
		defer runtime.GC()
		return config.Load(p)
	})
	handedOff = true
}

//export readOverride
func readOverride(slot C.int) *C.char {
	defer panicguard.Recover("readOverride", failExport)
	return C.CString(config.ReadOverride(config.OverrideSlot(slot)))
}

//export writeOverride
func writeOverride(slot C.int, content C.c_string) {
	defer panicguard.Recover("writeOverride", failExport)
	c := C.GoString(content)

	config.WriteOverride(config.OverrideSlot(slot), c)
}

//export clearOverride
func clearOverride(slot C.int) {
	defer panicguard.Recover("clearOverride", failExport)
	config.ClearOverride(config.OverrideSlot(slot))
}

// setSubscriptionHeaders задаёт заголовки, которые добавляются к запросу подписки:
// идентификатор устройства, сведения о нём и User-Agent. Собираются в Kotlin (D-30),
// принимаются в виде JSON-объекта. Пустая строка очищает набор.
//
//export setSubscriptionHeaders
func setSubscriptionHeaders(headers C.c_string) {
	defer panicguard.Recover("setSubscriptionHeaders", failExport)
	if headers == nil {
		_ = config.SetRequestHeaders("")
		return
	}

	if err := config.SetRequestHeaders(C.GoString(headers)); err != nil {
		log.Warnln("Set subscription headers: %s", err.Error())
	}
}

//export validateConfig
func validateConfig(path C.c_string) *C.char {
	defer panicguard.Recover("validateConfig", failExport)
	if err := config.ValidateConfig(C.GoString(path)); err != nil {
		return C.CString(err.Error())
	}

	return C.CString("")
}

//export setUserRules
func setUserRules(data C.c_string) C.int {
	defer panicguard.Recover("setUserRules", failExport)
	if err := config.SetUserRules(C.GoString(data)); err != nil {
		log.Warnln("[APP] User rules: %s", err.Error())

		return 1
	}

	return 0
}

//export setAgeSecretKey
func setAgeSecretKey(key C.c_string) {
	defer panicguard.Recover("setAgeSecretKey", failExport)
	if key == nil {
		config.SetGlobalSecretKeys()
		return
	}

	k := C.GoString(key)
	config.SetGlobalSecretKeys(k)
}

//export genX25519KeyPair
func genX25519KeyPair() *C.char {
	defer panicguard.Recover("genX25519KeyPair", failExport)
	secretKey, publicKey, err := config.GenX25519KeyPair()
	if err != nil {
		return nil
	}

	return marshalJson(ageKeyPair{SecretKey: secretKey, PublicKey: publicKey})
}

//export genHybridKeyPair
func genHybridKeyPair() *C.char {
	defer panicguard.Recover("genHybridKeyPair", failExport)
	secretKey, publicKey, err := config.GenHybridKeyPair()
	if err != nil {
		return nil
	}

	return marshalJson(ageKeyPair{SecretKey: secretKey, PublicKey: publicKey})
}

//export veritySecretKeys
func veritySecretKeys(secretKeys C.c_string) C.int {
	defer panicguard.Recover("veritySecretKeys", failExport)
	if config.VeritySecretKeys(C.GoString(secretKeys)) != nil {
		return 0
	}

	return 1
}

//export toPublicKeys
func toPublicKeys(secretKeys C.c_string) *C.char {
	defer panicguard.Recover("toPublicKeys", failExport)
	publicKeys, err := config.ToPublicKeys(C.GoString(secretKeys))
	if err != nil {
		return nil
	}

	return marshalJson(publicKeys)
}

//export verityPublicKeys
func verityPublicKeys(publicKeys C.c_string) C.int {
	defer panicguard.Recover("verityPublicKeys", failExport)
	if config.VerityPublicKeys(C.GoString(publicKeys)) != nil {
		return 0
	}

	return 1
}

//export queryProfileNetwork
func queryProfileNetwork(path C.c_string) *C.char {
	defer panicguard.Recover("queryProfileNetwork", failExport)
	return marshalJson(config.QueryNetwork(C.GoString(path)))
}

//export queryProfileGroups
func queryProfileGroups(path C.c_string) *C.char {
	defer panicguard.Recover("queryProfileGroups", failExport)
	return marshalJson(config.QueryGroups(C.GoString(path)))
}
