package platform

import (
	"net"
	"os"
	"testing"
)

// Android 8–9 find a connection's app this way (D-482); the host's /proc/net has the same layout.
func TestQuerySocketUidFromProcFs(t *testing.T) {
	for _, network := range []string{"tcp4", "tcp6"} {
		host := "127.0.0.1"
		if network == "tcp6" {
			host = "::1"
		}

		listener, err := net.Listen(network, net.JoinHostPort(host, "0"))
		if err != nil {
			t.Logf("%s: %v", network, err)
			continue
		}

		conn, err := net.Dial(network, listener.Addr().String())
		if err != nil {
			t.Fatal(err)
		}

		if uid := QuerySocketUidFromProcFs(conn.LocalAddr(), conn.RemoteAddr()); uid != os.Getuid() {
			t.Errorf("%s: uid %d, want %d", network, uid, os.Getuid())
		}

		_ = conn.Close()
		_ = listener.Close()
	}
}
