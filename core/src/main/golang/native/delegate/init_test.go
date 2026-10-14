package delegate

import (
	"testing"

	"github.com/metacubex/mihomo/component/profile/cachefile"
)

// A write of the core's cache file does not wait for others to batch with (D-303).
func TestCacheFileWritesAtOnce(t *testing.T) {
	Init(t.TempDir(), "test", "test", 29)

	db := cachefile.Cache().DB
	if db == nil {
		t.Fatal("the core's cache file did not open")
	}
	if db.MaxBatchDelay != 0 {
		t.Fatalf("MaxBatchDelay is %s, expected 0", db.MaxBatchDelay)
	}
}
