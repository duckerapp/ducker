package main

import (
	"cfa/native/panicguard"
	"testing"
)

func TestMarshalPanicsBecomeOperationErrors(t *testing.T) {
	for name, work := range map[string]func() error{
		"marshalJson":   func() error { marshalJson(make(chan int)); return nil },
		"marshalString": func() error { marshalString(42); return nil },
	} {
		t.Run(name, func(t *testing.T) {
			err := panicguard.Run(name, work)
			if err == nil || err.Error() != "core panic in "+name {
				t.Fatalf("unexpected result: %v", err)
			}
		})
	}
}
