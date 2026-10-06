package main

import (
	"github.com/metacubex/mihomo/common/observable"
	"github.com/metacubex/mihomo/log"
)

func stopLogSubscription(sub observable.Subscription[log.Event]) {
	// Observable.process holds its mutex while emitting into the bounded buffer.
	// Once JNI consumption stops, drain until UnSubscribe can take that mutex and
	// close the channel. This goroutine never calls Java or retains its callback.
	drained := make(chan struct{})
	go func() {
		defer close(drained)
		for range sub {
		}
	}()
	log.UnSubscribe(sub)
	<-drained
}
