package tunnel

import (
	"sync"

	"cfa/native/panicguard"
)

// Each check has its own recovery boundary. Recovering in the waiting parent
// would neither catch a worker panic nor release its WaitGroup count.
func checkBatch(operation string, checks []func() error) error {
	var wg sync.WaitGroup
	failures := make(chan error, len(checks))
	for _, check := range checks {
		wg.Add(1)
		go func(work func() error) {
			defer wg.Done()
			if err := panicguard.Run(operation, work); err != nil {
				failures <- err
			}
		}(check)
	}
	wg.Wait()
	close(failures)
	for err := range failures {
		return err
	}
	return nil
}
