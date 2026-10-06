package tun

import (
	"encoding/json"
	"io"
	"net"
	"net/netip"
	"strings"

	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// Алгоритмы TCP, которые знает стек `mips` (`tun.congestion-controller`).
// ponytail: список повторяет реестр mipstack; появится там новый алгоритм — дописать сюда,
// до тех пор он заменяется умолчанием с предупреждением.
var congestionControllers = map[string]bool{"cubic": true, "reno": true, "bbr": true, "bbr3": true}

// congestionController пропускает в ядро только известное значение: на незнакомом стек `mips`
// отказывается собирать туннель целиком, а с умолчанием (cubic) он работает всегда.
func congestionController(value string) string {
	value = strings.ToLower(strings.TrimSpace(value))
	if value == "" || congestionControllers[value] {
		return value
	}

	log.Warnln("TUN: unknown congestion-controller %q, using the default", value)

	return ""
}

func Start(fd int, stack, congestion, gateway, portal, dns string) (io.Closer, error) {
	log.Debugln("TUN: fd = %d, stack = %s, congestion = %s, gateway = %s, portal = %s, dns = %s", fd, stack, congestion, gateway, portal, dns)

	tunStack, ok := C.StackTypeMapping[strings.ToLower(stack)]
	if !ok {
		tunStack = C.TunSystem
	}

	var prefix4 []netip.Prefix
	var prefix6 []netip.Prefix
	for _, gatewayStr := range strings.Split(gateway, ",") { // "172.19.0.1/30" or "172.19.0.1/30,fdfe:dcba:9876::1/126"
		gatewayStr = strings.TrimSpace(gatewayStr)
		if len(gatewayStr) == 0 {
			continue
		}
		prefix, err := netip.ParsePrefix(gatewayStr)
		if err != nil {
			log.Errorln("TUN: %v", err)
			return nil, err
		}

		if prefix.Addr().Is4() {
			prefix4 = append(prefix4, prefix)
		} else {
			prefix6 = append(prefix6, prefix)
		}
	}

	var dnsHijack []string
	for _, dnsStr := range strings.Split(dns, ",") { // "172.19.0.2" or "0.0.0.0"
		dnsStr = strings.TrimSpace(dnsStr)
		if len(dnsStr) == 0 {
			continue
		}
		dnsHijack = append(dnsHijack, net.JoinHostPort(dnsStr, "53"))
	}

	options := LC.Tun{
		Enable:              true,
		Device:              sing_tun.InterfaceName,
		Stack:               tunStack,
		DNSHijack:           dnsHijack,
		AutoRoute:           false, // had set route in TunService.kt
		AutoDetectInterface: false, // implements by VpnService::protect
		Inet4Address:        prefix4,
		Inet6Address:        prefix6,
		MTU:                 9000, // private const val TUN_MTU = 9000 in TunService.kt
		FileDescriptor:      fd,

		CongestionController: congestionController(congestion), // читает только стек mips
	}

	tunOptions, _ := json.Marshal(options)
	log.Debugln(string(tunOptions))

	listener, err := sing_tun.New(options, tunnel.Tunnel)
	if err != nil {
		log.Errorln("TUN: %v", err)
		return nil, err
	}

	return listener, nil
}
