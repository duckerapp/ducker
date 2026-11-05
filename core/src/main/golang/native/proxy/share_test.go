package proxy

import (
	"bufio"
	"encoding/base64"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"testing"
	"time"
)

// dial connects to the shared port from the address from, as a device of the network would.
func dial(t *testing.T, from string, port int) net.Conn {
	t.Helper()

	d := net.Dialer{LocalAddr: &net.TCPAddr{IP: net.ParseIP(from)}, Timeout: time.Second}
	c, err := d.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		t.Fatal(err)
	}
	_ = c.SetDeadline(time.Now().Add(2 * time.Second))
	t.Cleanup(func() { _ = c.Close() })

	return c
}

// connect asks for a tunnel to example.com and returns the proxy's status code, 0 when it hung up instead.
func connect(t *testing.T, c net.Conn, user, pass string) int {
	t.Helper()

	request := "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n"
	if user != "" {
		request += "Proxy-Authorization: Basic " + base64.StdEncoding.EncodeToString([]byte(user+":"+pass)) + "\r\n"
	}
	if _, err := io.WriteString(c, request+"\r\n"); err != nil {
		return 0
	}

	response, err := http.ReadResponse(bufio.NewReader(c), nil)
	if err != nil {
		return 0
	}

	return response.StatusCode
}

func start(t *testing.T, network, user, pass string) int {
	t.Helper()

	port, err := StartShare(network, 0, user, pass)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(StopShare)

	return port
}

func TestSharePassword(t *testing.T) {
	port := start(t, "127.0.0.1/8", "ducker", "secret")

	if code := connect(t, dial(t, "127.0.0.2", port), "", ""); code != http.StatusProxyAuthRequired {
		t.Fatalf("no password: %d, want 407", code)
	}
	if code := connect(t, dial(t, "127.0.0.2", port), "ducker", "wrong"); code != http.StatusForbidden {
		t.Fatalf("a wrong password: %d, want 403", code)
	}
	if code := connect(t, dial(t, "127.0.0.2", port), "ducker", "secret"); code != http.StatusOK {
		t.Fatalf("the password: %d, want 200", code)
	}
}

func TestShareWithoutPassword(t *testing.T) {
	port := start(t, "127.0.0.1/8", "", "")

	if code := connect(t, dial(t, "127.0.0.2", port), "", ""); code != http.StatusOK {
		t.Fatalf("HTTP: %d, want 200", code)
	}

	// SOCKS5 offering no authentication at all is taken.
	c := dial(t, "127.0.0.2", port)
	reply := make([]byte, 2)
	if _, err := c.Write([]byte{5, 1, 0}); err != nil {
		t.Fatal(err)
	}
	if _, err := io.ReadFull(c, reply); err != nil || reply[1] != 0 {
		t.Fatalf("SOCKS5: %v %v, want no authentication", reply, err)
	}
}

func TestShareRefusesThePhoneAndOutsiders(t *testing.T) {
	port := start(t, "127.0.0.1/8", "", "")

	// The phone itself: the source is the shared address.
	if code := connect(t, dial(t, "127.0.0.1", port), "", ""); code != 0 {
		t.Fatalf("the phone itself: %d, want refused", code)
	}

	StopShare()
	port = start(t, "127.0.0.1/32", "", "")

	// Outside the shared network.
	if code := connect(t, dial(t, "127.0.0.2", port), "", ""); code != 0 {
		t.Fatalf("outside the network: %d, want refused", code)
	}
}

func TestShareUDPPeers(t *testing.T) {
	p := peers{network: netip.MustParsePrefix("192.168.43.1/24")}

	for addr, want := range map[net.Addr]bool{
		&net.UDPAddr{IP: net.ParseIP("192.168.43.20"), Port: 5000}: true,
		&net.UDPAddr{IP: net.ParseIP("192.168.43.1"), Port: 5000}:  false,
		&net.UDPAddr{IP: net.ParseIP("10.0.0.5"), Port: 5000}:      false,
		&net.TCPAddr{IP: net.ParseIP("::ffff:192.168.43.20")}:     true,
	} {
		if got := p.allowed(addr); got != want {
			t.Errorf("%v: %v, want %v", addr, got, want)
		}
	}
}
