package main

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

// The JNI side of the tunnel, set in tun.go. This file has no cgo, so the swap below can be tested on a computer,
// where bridge.h does not build.
var (
	bridgeMarkSocket     func(callback unsafe.Pointer, fd int) bool
	bridgeQuerySocketUid func(callback unsafe.Pointer, protocol int, source, target string) int
	bridgeRelease        func(callback unsafe.Pointer)
	tunStart             = tun.Start
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

	if !bridgeMarkSocket(t.callback, fd) {
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

	return bridgeQuerySocketUid(t.callback, protocol, source, target)
}

// close leaves the callbacks in app set: a closed tunnel's answer «tun callback closed» fails a dial, while the
// no-op of a stopped tunnel answers success without protecting, and a socket dialed then goes into the interface.
func (t *remoteTun) close() {
	_ = t.limit.Acquire(context.TODO(), 4)
	defer t.limit.Release(4)

	if t.closed {
		return
	}
	t.closed = true
	defer bridgeRelease(t.callback)

	if t.closer != nil {
		_ = t.closer.Close()
	}

}

// protectEarly hands the core the VPN service's protect callback before its first establish(): the interface is up
// from establish(), the tunnel's own callbacks come with startTun, and a core socket dialed in between went into the
// tunnel and back into the core (F-414). Android lets the prepared VPN app protect with no interface yet (F-416). Kept
// as a tunnel with nothing to close, so startTun swaps from it as from any tunnel and stopTun releases it, a start
// whose establish() failed included; «tunnel up» waits for startTun. A no-op if a tunnel or a hand-over is in place.
func protectEarly(callback unsafe.Pointer) {
	rTunLock.Lock()
	defer rTunLock.Unlock()

	if rTun != nil {
		bridgeRelease(callback)
		return
	}

	rTun = &remoteTun{callback: callback, limit: semaphore.NewWeighted(4)}
	app.ApplyTunContext(rTun.markSocket, rTun.querySocketUid)
}

// swapTun replaces the running tunnel or the early protection, if any, with a tunnel on fd. The callbacks go straight
// from the old ones to the new; the no-op is set only when no tunnel is left.
func swapTun(fd int, stack, congestion, gateway, portal, dns string, callback unsafe.Pointer) bool {
	rTunLock.Lock()
	defer rTunLock.Unlock()

	remote := &remoteTun{callback: callback, limit: semaphore.NewWeighted(4)}
	handedOff := false
	defer func() {
		if !handedOff {
			remote.close()
			tun.CloseIfOpen(fd)
			// After the fd: until it closes, the interface is up and takes unprotected sockets.
			app.ApplyTunContext(nil, nil)
			app.SetTunRunning(false)
		}
	}()

	if rTun != nil {
		rTun.close()
		rTun = nil
	}

	app.ApplyTunContext(remote.markSocket, remote.querySocketUid)

	closer, err := tunStart(fd, stack, congestion, gateway, portal, dns)
	if err != nil {
		return false
	}

	remote.closer = closer
	app.SetTunRunning(true)

	rTun = remote
	handedOff = true

	return true
}

func closeTun() {
	rTunLock.Lock()
	defer rTunLock.Unlock()

	if rTun != nil {
		rTun.close()
		rTun = nil
		app.ApplyTunContext(nil, nil)
		app.SetTunRunning(false)
	}
}
