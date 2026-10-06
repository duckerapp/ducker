package main

import (
	"testing"
	"time"

	"github.com/metacubex/mihomo/common/observable"
	"github.com/metacubex/mihomo/log"
)

func TestStopConcurrentSaturatedLogSubscriptions(t *testing.T) {
	subs := []observable.Subscription[log.Event]{log.Subscribe(), log.Subscribe()}
	for i := 0; i <= cap(subs[0]); i++ {
		log.Debugln("synthetic shared event %d", i)
	}
	stopped := make(chan struct{}, len(subs))
	for _, sub := range subs {
		go func(sub observable.Subscription[log.Event]) {
			stopLogSubscription(sub)
			stopped <- struct{}{}
		}(sub)
	}
	for range subs {
		select {
		case <-stopped:
		case <-time.After(2 * time.Second):
			for _, sub := range subs {
				go func(sub observable.Subscription[log.Event]) {
					for range sub {
					}
				}(sub)
			}
			t.Fatal("concurrent subscriptions blocked one another during unsubscribe")
		}
	}
	checkPublisherAfterUnsubscribe(t)
}

func TestStopIdleLogSubscription(t *testing.T) {
	sub := log.Subscribe()
	stopLogSubscription(sub)
	if _, open := <-sub; open {
		t.Fatal("channel not closed")
	}
}

func TestStopSaturatedLogSubscription(t *testing.T) {
	sub := log.Subscribe()
	// Mihomo receives the 201st event and blocks emitting it to the full
	// 200-event subscriber while holding Observable.mux.
	for i := 0; i <= cap(sub); i++ {
		log.Debugln("synthetic log event %d", i)
	}
	if len(sub) != cap(sub) {
		t.Fatal("subscription was not saturated")
	}
	stopped := make(chan struct{})
	go func() {
		stopLogSubscription(sub)
		close(stopped)
	}()
	select {
	case <-stopped:
	case <-time.After(2 * time.Second):
		// Release the real publisher before failing; do not leave the test
		// process or subsequent tests stuck on mihomo's global log channel.
		go func() {
			for range sub {
			}
		}()
		select {
		case <-stopped:
		case <-time.After(2 * time.Second):
			t.Fatal("test cleanup could not unblock the publisher")
		}
		t.Fatal("unsubscribe deadlocked with a saturated publisher")
	}

	checkPublisherAfterUnsubscribe(t)
}

// A publisher may have received its last old event without dispatching it yet.
// Wait for our ordered sentinel rather than assuming the next event is ours.
func checkPublisherAfterUnsubscribe(t *testing.T) {
	t.Helper()
	next := log.Subscribe()
	defer stopLogSubscription(next)
	log.Debugln("synthetic event after unsubscribe")
	deadline := time.After(2 * time.Second)
	for {
		select {
		case event := <-next:
			if event.Payload == "synthetic event after unsubscribe" {
				return
			}
		case <-deadline:
			t.Fatal("publisher remained blocked")
		}
	}
}
