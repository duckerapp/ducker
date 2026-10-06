package tun

import "testing"

func TestCongestionController(t *testing.T) {
	for in, want := range map[string]string{
		"":       "",
		"cubic":  "cubic",
		"reno":   "reno",
		"bbr":    "bbr",
		" BBR3 ": "bbr3",
		"xyz":    "", // незнакомое значение не должно дойти до ядра: стек mips на нём не соберётся
	} {
		if got := congestionController(in); got != want {
			t.Errorf("congestionController(%q) = %q, нужно %q", in, got, want)
		}
	}
}
