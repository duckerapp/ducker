//go:debug panicnil=0

package tunnel

import (
	"errors"
	"testing"
	"time"
)

func TestCheckBatchSuccessAndOrdinaryError(t *testing.T) {
	if err := checkBatch("empty", nil); err != nil {
		t.Fatal(err)
	}
	want := errors.New("check failed")
	if err := checkBatch("error", []func() error{func() error { return nil }, func() error { return want }}); err != want {
		t.Fatal(err)
	}
}

func TestCheckBatchPanicWaitsForOtherWorkers(t *testing.T) {
	started, release, result := make(chan struct{}), make(chan struct{}), make(chan error, 1)
	go func() {
		result <- checkBatch("group", []func() error{
			func() error { panic("synthetic provider failure") },
			func() error { close(started); <-release; return nil },
		})
	}()
	<-started
	select {
	case <-result:
		t.Fatal("returned while a check still writes measurements")
	default:
	}
	close(release)
	select {
	case err := <-result:
		if err == nil || err.Error() != "core panic in group" {
			t.Fatal(err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("panic leaked a WaitGroup count")
	}
}
