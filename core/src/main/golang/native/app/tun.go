package app

import (
	"net"
	"sync/atomic"
	"syscall"
)

var markSocketImpl func(fd int) error
var querySocketUidImpl func(protocol int, source, target string) int

var tunRunning atomic.Bool

// TunRunning — поднят ли туннель. Конфиг в ядре бывает загружен и без него: для замеров
// узлов при выключенном VPN (D-63), и тогда его правилам нельзя доверять чужие запросы.
func TunRunning() bool {
	return tunRunning.Load()
}

func MarkSocket(fd int) error {
	return markSocketImpl(fd)
}

func QuerySocketUid(source, target net.Addr) int {
	var protocol int

	switch source.Network() {
	case "udp", "udp4", "udp6":
		protocol = syscall.IPPROTO_UDP
	case "tcp", "tcp4", "tcp6":
		protocol = syscall.IPPROTO_TCP
	default:
		return -1
	}

	return querySocketUidImpl(protocol, source.String(), target.String())
}

func ApplyTunContext(markSocket func(fd int) error, querySocketUid func(int, string, string) int) {
	tunRunning.Store(markSocket != nil)

	if markSocket == nil {
		markSocket = func(fd int) error { return nil }
	}

	if querySocketUid == nil {
		querySocketUid = func(int, string, string) int { return -1 }
	}

	markSocketImpl = markSocket
	querySocketUidImpl = querySocketUid
}

func init() {
	ApplyTunContext(nil, nil)
}
