package tunnel

import (
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

func CloseAllConnections() {
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		_ = c.Close()
		return true
	})
}

func closeMatch(filter func(conn C.Connection) bool) {
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		if filter(c) {
			_ = c.Close()
		}
		return true
	})
}

// closeConnByGroup closes the group's connections that do not go through member: the ones a
// new choice in the group leaves on another node. A stored choice applied again after a reload
// is the one they were made with, and they stay (F-285).
func closeConnByGroup(group, member string) {
	closeMatch(func(conn C.Connection) bool {
		return offMember(conn.Chains(), group, member)
	})
}

// offMember reports whether a connection goes through group by a member other than member. A
// chain names the node first and then every group the node was reached through, each right
// after the member it used (`AppendToChains`, `adapter/outbound/base.go`).
func offMember(chain []string, group, member string) bool {
	for i, name := range chain {
		if name == group {
			return i == 0 || chain[i-1] != member
		}
	}

	return false
}
