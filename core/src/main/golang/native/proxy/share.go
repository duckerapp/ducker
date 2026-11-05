package proxy

import (
	"context"
	"net"
	"net/netip"
	"sync"
	"syscall"

	"cfa/native/app"

	"github.com/metacubex/mihomo/adapter/inbound"
	"github.com/metacubex/mihomo/component/auth"
	C "github.com/metacubex/mihomo/constant"
	authStore "github.com/metacubex/mihomo/listener/auth"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/mixed"
	"github.com/metacubex/mihomo/listener/socks"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// ShareInName marks the connections of «Раздача по Wi-Fi», so «Подключения» can name the device they came from.
const ShareInName = "ducker-share"

// The listener of «Раздача по Wi-Fi» (D-419–D-421): HTTP and SOCKS5 on one address of the phone's Wi-Fi or hotspot,
// UDP over SOCKS5 on the same port, as the core's own mixed-port has. It is ours, not the config's: an AuthStore and
// additions of its own keep the config's `authentication`, `skip-auth-prefixes` and `lan-allowed-ips` off it
// (listener/mixed/mixed.go), and a config reload does not close it.
var share struct {
	sync.Mutex
	tcp *mixed.Listener
	udp *socks.UDPListener
}

// StartShare listens on the phone's address in network ("192.168.1.5/24") at port, 0 for any free one, and returns
// the port. With a user, only with that login and password; mihomo asks none for UDP. What was shared before is
// closed first, connections too: a password just turned on keeps nobody in who came without it.
func StartShare(network string, port int, user, pass string) (int, error) {
	prefix, err := netip.ParsePrefix(network)
	if err != nil {
		return 0, err
	}

	share.Lock()
	defer share.Unlock()

	stopShareLocked()

	store := authStore.Nil
	if user != "" {
		store = authStore.NewAuthStore(auth.NewAuthenticator([]auth.AuthUser{{User: user, Pass: pass}}))
	}

	lc := peers{InboundListenConfig: inbound.NewListenConfig(), network: prefix}
	in := []inbound.Addition{inbound.WithInName(ShareInName), inbound.WithSpecialRules("")}
	listen := netip.AddrPortFrom(prefix.Addr(), uint16(port)).String()

	tcp, err := mixed.NewWithConfig(LC.AuthServer{Enable: true, Listen: listen, AuthStore: store}, lc, tunnel.Tunnel, in...)
	if err != nil {
		return 0, err
	}

	at, err := netip.ParseAddrPort(tcp.Address())
	if err == nil {
		// A SOCKS5 client is told the TCP port and sends its UDP there.
		share.udp, err = socks.NewUDPWithConfig(LC.AuthServer{Enable: true, Listen: at.String()}, lc, tunnel.Tunnel, in...)
	}
	if err != nil {
		_ = tcp.Close()
		return 0, err
	}

	share.tcp = tcp

	return int(at.Port()), nil
}

// StopShare closes the port and what came through it.
func StopShare() {
	share.Lock()
	defer share.Unlock()

	stopShareLocked()
}

func stopShareLocked() {
	if share.tcp != nil {
		_ = share.tcp.Close()
	}
	if share.udp != nil {
		_ = share.udp.Close()
	}
	share.tcp, share.udp = nil, nil

	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		if c.Info().Metadata.InName == ShareInName {
			_ = c.Close()
		}
		return true
	})
}

// peers lets in only the devices of the shared network itself. Not the phone: an app on it kept out of the VPN would
// find the node through the port (D-216). Not anyone routed in from beyond the subnet either.
type peers struct {
	C.InboundListenConfig
	network netip.Prefix
}

func (p peers) allowed(addr net.Addr) bool {
	var ip netip.Addr

	switch a := addr.(type) {
	case *net.TCPAddr:
		ip = a.AddrPort().Addr().Unmap()
	case *net.UDPAddr:
		ip = a.AddrPort().Addr().Unmap()
	}

	return ip.IsValid() && ip != p.network.Addr() && p.network.Contains(ip)
}

func (p peers) Listen(ctx context.Context, network, address string) (net.Listener, error) {
	l, err := p.InboundListenConfig.Listen(ctx, network, address)
	if err != nil {
		return nil, err
	}

	return &peerListener{Listener: l, peers: p}, nil
}

func (p peers) ListenPacket(ctx context.Context, network, address string) (net.PacketConn, error) {
	c, err := p.InboundListenConfig.ListenPacket(ctx, network, address)
	if err != nil {
		return nil, err
	}

	// Replies from the UDP port go back over the shared network, not into the tunnel: with private networks routed
	// through the VPN they came back to the core and went out from another port, which a SOCKS5 client drops.
	// TCP needs nothing: Android marks an accepted connection with the network it came in on.
	if sc, ok := c.(syscall.Conn); ok {
		if raw, err := sc.SyscallConn(); err == nil {
			_ = raw.Control(func(fd uintptr) { _ = app.MarkSocket(int(fd)) })
		}
	}

	return &peerPacketConn{PacketConn: c, peers: p}, nil
}

type peerListener struct {
	net.Listener
	peers peers
}

func (l *peerListener) Accept() (net.Conn, error) {
	for {
		c, err := l.Listener.Accept()
		if err != nil || l.peers.allowed(c.RemoteAddr()) {
			return c, err
		}

		_ = c.Close()
	}
}

// peerPacketConn hides the enhanced reads of the socket underneath, so mihomo reads through ReadFrom and its filter.
type peerPacketConn struct {
	net.PacketConn
	peers peers
}

func (c *peerPacketConn) ReadFrom(b []byte) (int, net.Addr, error) {
	for {
		n, addr, err := c.PacketConn.ReadFrom(b)
		if err != nil || c.peers.allowed(addr) {
			return n, addr, err
		}
	}
}
