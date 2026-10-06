//go:debug panicnil=0

package panicguard

import (
	"errors"
	"os"
	"strings"
	"sync"
	"testing"
)

func TestRunPreservesSuccessAndError(t *testing.T) {
	if err := Run("ok", func() error { return nil }); err != nil {
		t.Fatal(err)
	}
	want := errors.New("ordinary error")
	if err := Run("error", func() error { return want }); err != want {
		t.Fatal(err)
	}
}

func TestPanicPayloadStaysInPrivateStderr(t *testing.T) {
	file, err := os.CreateTemp(t.TempDir(), "stderr")
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	before := os.Stderr
	os.Stderr = file
	defer func() { os.Stderr = before }()
	failure := Run("marshalJson", func() error { panic("synthetic-profile-key") })
	var recovered *Error
	if !errors.As(failure, &recovered) {
		t.Fatalf("missing panic error: %v", failure)
	}
	if strings.Contains(failure.Error(), "synthetic-profile-key") {
		t.Fatal("payload leaked to caller")
	}
	data, err := os.ReadFile(file.Name())
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(data), "synthetic-profile-key") || !strings.Contains(string(data), "panic_test.go") {
		t.Fatal("private diagnostic lacks payload or stack")
	}
}

func TestNilPanicIsAnError(t *testing.T) {
	if err := Run("nil", func() error { panic(nil) }); err == nil {
		t.Fatal("nil panic became success")
	}
}

func TestWorkersRecoverIndependentlyAndRunCleanup(t *testing.T) {
	var wg sync.WaitGroup
	for i := 0; i < 16; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			cleaned := false
			err := Run("worker", func() error {
				defer func() { cleaned = true }()
				panic("synthetic worker failure")
			})
			if err == nil || !cleaned {
				t.Error("worker panic lost or cleanup skipped")
			}
		}()
	}
	wg.Wait()
}

func TestCompletionPrecedesReleaseOnSuccessErrorAndPanic(t *testing.T) {
	for _, mode := range []string{"success", "error", "panic"} {
		t.Run(mode, func(t *testing.T) {
			events := []string{}
			var failure error
			Complete("async", func() error {
				events = append(events, "work")
				if mode == "panic" {
					panic("synthetic async failure")
				}
				if mode == "error" {
					return errors.New("ordinary failure")
				}
				return nil
			}, func(err error) {
				events = append(events, "complete")
				failure = err
			}, func() { events = append(events, "release") })
			if strings.Join(events, ",") != "work,complete,release" {
				t.Fatal(events)
			}
			if (failure == nil) != (mode == "success") {
				t.Fatal(failure)
			}
		})
	}
}
