// Package panicguard protects only the goroutine in which it is used. Goroutines
// created inside mihomo still have to protect themselves; a caller cannot do it.
package panicguard

import (
	"fmt"
	"os"
	"runtime/debug"
)

// Error deliberately contains no panic payload: that may include a profile key.
type Error struct{ Operation string }

func (e *Error) Error() string { return "core panic in " + e.Operation }

// Recover must be deferred directly at the boundary, before any owned resources
// are acquired. Resource cleanup defers then run before the error is delivered.
func Recover(operation string, failed func(error)) {
	if value := recover(); value != nil {
		err := &Error{Operation: operation}
		// The original payload and stack stay private until scrubbed at export.
		_, _ = fmt.Fprintf(os.Stderr, "[APP] %s: %.4096v\n%s\n", err, value, debug.Stack())
		if failed != nil {
			failed(err)
		}
	}
}

func Run(operation string, work func() error) (err error) {
	defer Recover(operation, func(failure error) { err = failure })
	return work()
}

// Complete keeps the callback alive until success/failure has been delivered.
// The caller owns starting a goroutine; recovery must run inside that goroutine.
func Complete(operation string, work func() error, complete func(error), release func()) {
	defer release()
	defer Recover(operation, complete)
	complete(work())
}
