package main

import (
	"errors"
	"io"
	"sync/atomic"
	"testing"
	"unsafe"

	"cfa/native/app"
)

// Run on a computer file by file, from core/src/foss/golang, with app/dns.go replaced by a stub through -overlay:
// go test -race -tags foss,with_gvisor,cmfa ../../main/golang/native/tun_swap.go ../../main/golang/native/tun_swap_test.go
func TestTunSwapNeverLeavesSocketsUnprotected(t *testing.T) {
	// A live tunnel's protection fails here, so only the stopped tunnel's no-op answers success.
	bridgeMarkSocket = func(unsafe.Pointer, int) bool { return false }
	bridgeQuerySocketUid = func(unsafe.Pointer, int, string, string) int { return -1 }
	bridgeRelease = func(unsafe.Pointer) {}
	tunStart = func(int, string, string, string, string, string) (io.Closer, error) { return io.NopCloser(nil), nil }

	if !swapTun(-1, "system", "", "", "", "", nil) {
		t.Fatal("start failed")
	}

	var unprotected atomic.Int32
	stop := make(chan struct{})
	done := make(chan struct{})
	go func() {
		defer close(done)

		for {
			select {
			case <-stop:
				return
			default:
				if app.MarkSocket(-1) == nil || !app.TunRunning() {
					unprotected.Add(1)
				}
			}
		}
	}()

	for i := 0; i < 1000; i++ {
		swapTun(-1, "system", "", "", "", "", nil)
	}

	close(stop)
	<-done

	if n := unprotected.Load(); n != 0 {
		t.Fatalf("%d answers of a stopped tunnel during swaps", n)
	}

	closeTun()
	if app.TunRunning() || app.MarkSocket(-1) != nil {
		t.Fatal("a stopped tunnel must answer as stopped")
	}

	swapTun(-1, "system", "", "", "", "", nil)
	tunStart = func(int, string, string, string, string, string) (io.Closer, error) { return nil, errors.New("no tun") }
	if swapTun(-1, "system", "", "", "", "", nil) || rTun != nil || app.TunRunning() {
		t.Fatal("a failed start must leave no tunnel and the stopped answers")
	}
}
