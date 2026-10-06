package config

import (
	"os"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"sync"
	"testing"

	C "github.com/metacubex/mihomo/constant"
)

func TestPersistOverrideRoundTrip(t *testing.T) {
	oldHome := C.Path.HomeDir()
	C.SetHomeDir(t.TempDir())
	t.Cleanup(func() { C.SetHomeDir(oldHome) })
	ClearOverride(OverrideSlotPersist)
	if got := ReadOverride(OverrideSlotPersist); got != "{}" {
		t.Fatalf("default: %q", got)
	}
	for i := 0; i < 100; i++ {
		WriteOverride(OverrideSlotPersist, `{"mode":"global"}`)
		if got := ReadOverride(OverrideSlotPersist); got != `{"mode":"global"}` {
			t.Fatalf("roundtrip: %q", got)
		}
		WriteOverride(OverrideSlotPersist, `{}`)
		if got := ReadOverride(OverrideSlotPersist); got != `{}` {
			t.Fatalf("shorter write: %q", got)
		}
	}
	info, err := os.Stat(overridePersistPath())
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0600 {
		t.Fatalf("permissions: %v", info.Mode())
	}
	ClearOverride(OverrideSlotPersist)
	if got := ReadOverride(OverrideSlotPersist); got != "{}" {
		t.Fatalf("clear: %q", got)
	}
}

func TestSessionOverrideConcurrentAccess(t *testing.T) {
	ClearOverride(OverrideSlotSession)
	t.Cleanup(func() { ClearOverride(OverrideSlotSession) })
	var wg sync.WaitGroup
	for i := 0; i < 16; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 200; j++ {
				WriteOverride(OverrideSlotSession, `{"mode":"global"}`)
				got := ReadOverride(OverrideSlotSession)
				if got != "{}" && got != `{"mode":"global"}` {
					t.Errorf("invalid snapshot: %q", got)
				}
				ClearOverride(OverrideSlotSession)
			}
		}()
	}
	wg.Wait()
}

func TestPersistOverrideDoesNotLeaveDescriptorsOpen(t *testing.T) {
	if runtime.GOOS != "linux" {
		t.Skip("/proc/self/fd is required")
	}
	oldHome := C.Path.HomeDir()
	C.SetHomeDir(t.TempDir())
	t.Cleanup(func() { C.SetHomeDir(oldHome) })
	previousGC := debug.SetGCPercent(-1)
	defer debug.SetGCPercent(previousGC)
	for i := 0; i < 50; i++ {
		WriteOverride(OverrideSlotPersist, `{}`)
		_ = ReadOverride(OverrideSlotPersist)
	}
	fds, err := os.ReadDir("/proc/self/fd")
	if err != nil {
		t.Fatal(err)
	}
	for _, fd := range fds {
		target, err := os.Readlink(filepath.Join("/proc/self/fd", fd.Name()))
		if err == nil && target == overridePersistPath() {
			t.Fatalf("override descriptor left open: %s", fd.Name())
		}
	}
}
