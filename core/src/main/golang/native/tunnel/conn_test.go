package tunnel

import "testing"

// Which connections a choice in a group closes: those left on another member. A choice applied
// again after a reload is the one they were made with, and nothing is closed (F-285).
func TestOffMember(t *testing.T) {
	cases := []struct {
		name          string
		chain         []string
		group, member string
		want          bool
	}{
		{"made through the chosen node", []string{"Paris", "Main"}, "Main", "Paris", false},
		{"made through another node", []string{"Oslo", "Main"}, "Main", "Paris", true},
		{"made through the chosen automatic group", []string{"Oslo", "Auto", "Main"}, "Main", "Auto", false},
		{"made through an automatic group, a node is chosen now", []string{"Oslo", "Auto", "Main"}, "Main", "Oslo", true},
		{"came to the group from an outer one", []string{"Paris", "Main", "Video"}, "Main", "Paris", false},
		{"came from an outer one through another node", []string{"Oslo", "Main", "Video"}, "Main", "Paris", true},
		{"the choice is made in the outer group", []string{"Paris", "Main", "Video"}, "Video", "Main", false},
		{"does not go through the group", []string{"DIRECT", "Bypass"}, "Main", "Paris", false},
		{"a node named like the chosen one, in another group", []string{"Paris", "Bypass"}, "Main", "Paris", false},
	}

	for _, c := range cases {
		if got := offMember(c.chain, c.group, c.member); got != c.want {
			t.Errorf("%s: chain %v, %s -> %s: %v, expected %v", c.name, c.chain, c.group, c.member, got, c.want)
		}
	}
}
