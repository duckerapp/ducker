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

// From the hand-over before the first establish() to the stop no answer is the no-op's, «tunnel up» only from
// startTun, and every callback is released once: after a start, after a start whose establish() failed (no startTun)
// and after a hand-over made while a tunnel runs (F-414).
func TestProtectEarlyUntilStop(t *testing.T) {
	released := map[unsafe.Pointer]int{}
	var handed []unsafe.Pointer
	callback := func() unsafe.Pointer {
		p := unsafe.Pointer(new(int))
		handed = append(handed, p)
		return p
	}
	bridgeMarkSocket = func(unsafe.Pointer, int) bool { return false }
	bridgeQuerySocketUid = func(unsafe.Pointer, int, string, string) int { return -1 }
	bridgeRelease = func(p unsafe.Pointer) { released[p]++ } // called under rTunLock only
	tunStart = func(int, string, string, string, string, string) (io.Closer, error) { return io.NopCloser(nil), nil }

	// Odd while a callback must answer: a reader counts a no-op answer only if no change fell inside its call.
	var phase atomic.Int64
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
				before := phase.Load()
				answer := app.MarkSocket(-1)
				if before%2 == 1 && phase.Load() == before && answer == nil {
					unprotected.Add(1)
				}
			}
		}
	}()

	for i := 0; i < 500; i++ {
		protectEarly(callback())
		phase.Add(1)
		if app.TunRunning() || app.MarkSocket(-1) == nil {
			t.Fatal("after the hand-over: tunnel up before startTun, or the no-op")
		}
		if i%5 == 0 { // establish() failed: no startTun, the stop clears the hand-over
			phase.Add(1)
			closeTun()
			if rTun != nil || app.MarkSocket(-1) != nil {
				t.Fatal("the stop left the hand-over")
			}
			continue
		}
		if i%7 == 0 { // the core failed to start on the fd: no tunnel, the no-op
			phase.Add(1)
			start := tunStart
			tunStart = func(int, string, string, string, string, string) (io.Closer, error) { return nil, errors.New("no tun") }
			if swapTun(-1, "system", "", "", "", "", callback()) || rTun != nil || app.TunRunning() {
				t.Fatal("a failed start must leave no tunnel")
			}
			tunStart = start
			continue
		}
		swapTun(-1, "system", "", "", "", "", callback())
		if !app.TunRunning() {
			t.Fatal("tunnel down after startTun")
		}
		protectEarly(callback()) // a tunnel runs: nothing changes
		swapTun(-1, "system", "", "", "", "", callback())
		phase.Add(1)
		closeTun()
	}

	close(stop)
	<-done

	if n := unprotected.Load(); n != 0 {
		t.Fatalf("%d answers of the no-op between the hand-over and the stop", n)
	}
	if app.TunRunning() || app.MarkSocket(-1) != nil || rTun != nil {
		t.Fatal("a stop must leave the no-op and no tunnel")
	}
	for _, p := range handed {
		if released[p] != 1 {
			t.Fatalf("a callback released %d times", released[p])
		}
	}
}
