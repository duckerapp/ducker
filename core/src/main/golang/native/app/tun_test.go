package app

import (
	"net"
	"sync"
	"testing"
)

// Dialing goroutines read the callbacks while the tunnel starts and stops: under -race the plain variables failed this.
func TestTunCallbacksSwapWhileRead(t *testing.T) {
	stop := make(chan struct{})
	var wg sync.WaitGroup

	wg.Add(1)
	go func() {
		defer wg.Done()

		addr := &net.TCPAddr{IP: net.IPv4(172, 19, 0, 1), Port: 443}
		for {
			select {
			case <-stop:
				return
			default:
				_ = MarkSocket(-1)
				_ = QuerySocketUid(addr, addr)
			}
		}
	}()

	for i := 0; i < 1000; i++ {
		ApplyTunContext(func(int) error { return nil }, func(int, string, string) int { return 1000 })
		ApplyTunContext(nil, nil)
	}

	close(stop)
	wg.Wait()

	if QuerySocketUid(&net.TCPAddr{}, &net.TCPAddr{}) != -1 {
		t.Fatal("a stopped tunnel must answer as stopped")
	}
}
