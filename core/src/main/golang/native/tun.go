package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/panicguard"
	"context"
	"errors"
	"io"
	"sync"
	"unsafe"

	"golang.org/x/sync/semaphore"

	"cfa/native/app"
	"cfa/native/tun"
)

var rTunLock sync.Mutex
var rTun *remoteTun

type remoteTun struct {
	closer   io.Closer
	callback unsafe.Pointer

	closed bool
	limit  *semaphore.Weighted
}

func (t *remoteTun) markSocket(fd int) (err error) {
	defer panicguard.Recover("markSocket", func(failure error) { err = failure })
	_ = t.limit.Acquire(context.Background(), 1)
	defer t.limit.Release(1)

	if t.closed {
		return errors.New("tun callback closed")
	}

	if C.mark_socket(t.callback, C.int(fd)) == 0 {
		return errors.New("socket protection failed")
	}
	return nil
}

func (t *remoteTun) querySocketUid(protocol int, source, target string) (uid int) {
	defer panicguard.Recover("querySocketUid", func(error) { uid = -1 })
	_ = t.limit.Acquire(context.Background(), 1)
	defer t.limit.Release(1)

	if t.closed {
		return -1
	}

	return int(C.query_socket_uid(t.callback, C.int(protocol), C.CString(source), C.CString(target)))
}

func (t *remoteTun) close() {
	_ = t.limit.Acquire(context.TODO(), 4)
	defer t.limit.Release(4)

	if t.closed {
		return
	}
	t.closed = true
	defer C.release_object(t.callback)
	defer app.ApplyTunContext(nil, nil)

	if t.closer != nil {
		_ = t.closer.Close()
	}

}

//export startTun
func startTun(fd C.int, stack, congestion, gateway, portal, dns C.c_string, callback unsafe.Pointer) C.int {
	defer panicguard.Recover("startTun", failExport)
	f := int(fd)
	remote := &remoteTun{callback: callback, limit: semaphore.NewWeighted(4)}
	handedOff := false
	defer func() {
		if !handedOff {
			remote.close()
			tun.CloseIfOpen(f)
		}
	}()
	rTunLock.Lock()
	defer rTunLock.Unlock()

	if rTun != nil {
		rTun.close()
		rTun = nil
	}

	s := C.GoString(stack)
	c := C.GoString(congestion)
	g := C.GoString(gateway)
	p := C.GoString(portal)
	d := C.GoString(dns)

	app.ApplyTunContext(remote.markSocket, remote.querySocketUid)

	closer, err := tun.Start(f, s, c, g, p, d)
	if err != nil {
		return 1
	}

	remote.closer = closer

	rTun = remote
	handedOff = true

	return 0
}

//export stopTun
func stopTun() {
	defer panicguard.Recover("stopTun", failExport)
	rTunLock.Lock()
	defer rTunLock.Unlock()

	if rTun != nil {
		rTun.close()
		rTun = nil
	}
}
