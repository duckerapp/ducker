package app

import (
	"net"
	"sync/atomic"
	"syscall"

	"cfa/native/platform"
)

// tunContext is swapped whole at every start and stop of the tunnel while dialing goroutines read it: plain
// variables were a data race.
type tunContext struct {
	markSocket     func(fd int) error
	querySocketUid func(protocol int, source, target string) int
}

var tunCallbacks atomic.Pointer[tunContext]

var tunRunning atomic.Bool

// tunEpoch counts the times the VPN service took the core's sockets over or let them go.
var tunEpoch atomic.Uint64
var serviceProtects atomic.Bool

// TunRunning — поднят ли туннель. Конфиг в ядре бывает загружен и без него: для замеров
// узлов при выключенном VPN (D-63), и тогда его правилам нельзя доверять чужие запросы.
func TunRunning() bool {
	return tunRunning.Load()
}

func MarkSocket(fd int) error {
	return tunCallbacks.Load().markSocket(fd)
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

	// getConnectionOwnerUid is Android 10+; before it /proc/net is still readable (D-482).
	if PlatformVersion() < 29 {
		return platform.QuerySocketUidFromProcFs(source, target)
	}

	return tunCallbacks.Load().querySocketUid(protocol, source.String(), target.String())
}

// ApplyTunContext sets what protects the core's sockets; nil puts back the stopped tunnel's no-op. Whether the
// tunnel is up is SetTunRunning's: the VPN service's protection comes before the interface (F-414).
func ApplyTunContext(markSocket func(fd int) error, querySocketUid func(int, string, string) int) {
	if serviceProtects.Swap(markSocket != nil) != (markSocket != nil) {
		tunEpoch.Add(1)
	}

	if markSocket == nil {
		markSocket = func(fd int) error { return nil }
	}

	if querySocketUid == nil {
		querySocketUid = func(int, string, string) int { return -1 }
	}

	tunCallbacks.Store(&tunContext{markSocket: markSocket, querySocketUid: querySocketUid})
}

// TunEpoch changes when the VPN service takes the core's sockets over or lets them go, swaps aside. A socket opened
// before the VPN comes up is not protected, and once the interface is up its connection breaks: a download that failed
// across a change may have been cut by it rather than refused (D-431).
func TunEpoch() uint64 {
	return tunEpoch.Load()
}

func SetTunRunning(running bool) {
	tunRunning.Store(running)
}

func init() {
	ApplyTunContext(nil, nil)
}
