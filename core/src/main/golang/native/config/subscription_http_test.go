package config

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/component/ca"
)

// Every test hostname connects to a loopback server. No public host is contacted.
type loopbackSubscriptionDialer struct{}

func (loopbackSubscriptionDialer) DialContext(ctx context.Context, network, address string) (net.Conn, error) {
	host, port, err := net.SplitHostPort(address)
	if err != nil {
		return nil, err
	}
	if host != "provider.invalid" && host != "api.provider.invalid" && host != "other.invalid" && host != "github.com" && host != "cdn.jsdelivr.net" {
		return nil, fmt.Errorf("non-fixture host refused")
	}
	return (&net.Dialer{}).DialContext(ctx, network, net.JoinHostPort("127.0.0.1", port))
}
func (loopbackSubscriptionDialer) ListenPacket(context.Context, string, string, netip.AddrPort) (net.PacketConn, error) {
	return nil, fmt.Errorf("UDP refused")
}

func subscriptionTestServer(t *testing.T, tlsEnabled bool, handler http.HandlerFunc) (*httptest.Server, string) {
	t.Helper()
	s := httptest.NewUnstartedServer(handler)
	if tlsEnabled {
		key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		if err != nil {
			t.Fatal(err)
		}
		cert := &x509.Certificate{SerialNumber: big.NewInt(time.Now().UnixNano()), Subject: pkix.Name{CommonName: "QA subscription provider"}, NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour), KeyUsage: x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}, IsCA: true, BasicConstraintsValid: true, DNSNames: []string{"provider.invalid", "api.provider.invalid", "other.invalid", "github.com", "cdn.jsdelivr.net"}}
		der, err := x509.CreateCertificate(rand.Reader, cert, cert, &key.PublicKey, key)
		if err != nil {
			t.Fatal(err)
		}
		if err := ca.AddCertificate(string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))); err != nil {
			t.Fatal(err)
		}
		s.TLS = &tls.Config{Certificates: []tls.Certificate{{Certificate: [][]byte{der}, PrivateKey: key}}}
		s.StartTLS()
	} else {
		s.Start()
	}
	t.Cleanup(s.Close)
	return s, strings.Replace(s.URL, "127.0.0.1", "provider.invalid", 1)
}

func syntheticSubscriptionHeaders(t *testing.T) {
	t.Helper()
	if err := SetRequestHeaders(`{"User-Agent":"mihomo/1.19.31 ducker/QA","x-hwid":"SYNTHETIC-HWID-1234","x-device-model":"QA MODEL","x-device-os":"QA OS","x-ver-os":"QA VERSION","Authorization":"Bearer QA_SECRET","Cookie":"qa=QA_SECRET"}`); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = SetRequestHeaders("") })
}

func requestTestSubscription(address string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	resp, err := subscriptionHttpRequest(ctx, address, buildRequestHeader(true, address, "mihomo/fallback ducker"), loopbackSubscriptionDialer{}.DialContext)
	if resp != nil {
		_, _ = io.Copy(io.Discard, resp.Body)
		_ = resp.Body.Close()
	}
	return err
}

func assertNoDeviceHeaders(t *testing.T, h http.Header) {
	t.Helper()
	for name := range h {
		if sensitiveSubscriptionHeader(name) {
			t.Errorf("redirect exposed %s", name)
		}
	}
	if h.Get("User-Agent") != "mihomo/1.19.31 ducker/QA" {
		t.Error("panel User-Agent lost")
	}
}

func TestSubscriptionSameOriginRedirectPreservesProviderHeaders(t *testing.T) {
	syntheticSubscriptionHeaders(t)
	seen := make(chan http.Header, 2)
	_, url := subscriptionTestServer(t, true, func(w http.ResponseWriter, r *http.Request) {
		seen <- r.Header.Clone()
		if r.URL.Path == "/sub" {
			http.Redirect(w, r, "/final", 302)
			return
		}
		w.WriteHeader(200)
	})
	if err := requestTestSubscription(url + "/sub"); err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 2; i++ {
		h := <-seen
		if h.Get("x-hwid") != "SYNTHETIC-HWID-1234" || h.Get("x-device-model") != "QA MODEL" || h.Get("User-Agent") != "mihomo/1.19.31 ducker/QA" {
			t.Fatal("HTTPS provider compatibility lost")
		}
	}
}

// D-193: провайдер вправе переадресовать подписку на другой домен или сервер — данные
// устройства идут следом, иначе панель не узнает устройство и откажет по лимиту.
func TestSubscriptionRedirectToAnotherHostKeepsDeviceHeaders(t *testing.T) {
	for _, destination := range []string{"api.provider.invalid", "other.invalid", "github.com"} {
		t.Run(destination, func(t *testing.T) {
			syntheticSubscriptionHeaders(t)
			seen := make(chan http.Header, 1)
			_, target := subscriptionTestServer(t, true, func(w http.ResponseWriter, r *http.Request) { seen <- r.Header.Clone(); w.WriteHeader(200) })
			target = strings.Replace(target, "provider.invalid", destination, 1)
			_, origin := subscriptionTestServer(t, true, func(w http.ResponseWriter, r *http.Request) { http.Redirect(w, r, target+"/target", 302) })
			if err := requestTestSubscription(origin + "/sub"); err != nil {
				t.Fatal(err)
			}
			if h := <-seen; h.Get("x-hwid") != "SYNTHETIC-HWID-1234" || h.Get("x-device-model") != "QA MODEL" || h.Get("User-Agent") != "mihomo/1.19.31 ducker/QA" {
				t.Fatal("device headers lost on the way to another host")
			}
		})
	}
}

// D-193: переход с https на http не запрещён — подписка скачивается, но данные устройства
// после первого адреса http больше не отправляются, даже когда цепочка возвращается на https.
func TestSubscriptionDowngradeIsFollowedWithoutDeviceHeaders(t *testing.T) {
	syntheticSubscriptionHeaders(t)
	seen := make(chan http.Header, 2)
	_, final := subscriptionTestServer(t, true, func(w http.ResponseWriter, r *http.Request) { seen <- r.Header.Clone(); w.WriteHeader(200) })
	_, insecure := subscriptionTestServer(t, false, func(w http.ResponseWriter, r *http.Request) {
		seen <- r.Header.Clone()
		http.Redirect(w, r, final+"/final", 302)
	})
	_, origin := subscriptionTestServer(t, true, func(w http.ResponseWriter, r *http.Request) { http.Redirect(w, r, insecure+"/insecure", 302) })
	if err := requestTestSubscription(origin + "/sub"); err != nil {
		t.Fatalf("downgrade stopped the subscription: %v", err)
	}
	assertNoDeviceHeaders(t, <-seen)
	assertNoDeviceHeaders(t, <-seen)
}

func TestSubscriptionHttpUpgradeDoesNotAddDeviceHeaders(t *testing.T) {
	// Kotlin normally provides only UA on HTTP. The extra synthetic headers test Go's own filter.
	if err := SetRequestHeaders(`{"User-Agent":"mihomo/1.19.31 ducker/QA","x-hwid":"SYNTHETIC-HWID-1234","x-device-model":"QA MODEL","x-device-os":"QA OS","x-ver-os":"QA VERSION"}`); err != nil {
		t.Fatal(err)
	}
	defer SetRequestHeaders("")
	seen := make(chan http.Header, 2)
	_, target := subscriptionTestServer(t, true, func(w http.ResponseWriter, r *http.Request) { seen <- r.Header.Clone(); w.WriteHeader(200) })
	_, origin := subscriptionTestServer(t, false, func(w http.ResponseWriter, r *http.Request) {
		seen <- r.Header.Clone()
		http.Redirect(w, r, target+"/target", 302)
	})
	if err := requestTestSubscription(origin + "/sub"); err != nil {
		t.Fatal(err)
	}
	assertNoDeviceHeaders(t, <-seen)
	assertNoDeviceHeaders(t, <-seen)
}

func TestSubscriptionRedirectLimit(t *testing.T) {
	syntheticSubscriptionHeaders(t)
	_, origin := subscriptionTestServer(t, true, func(w http.ResponseWriter, r *http.Request) { http.Redirect(w, r, "/loop", 302) })
	if err := requestTestSubscription(origin + "/sub"); err == nil {
		t.Fatal("redirect loop not bounded")
	}
}

func TestSubscriptionRedirectStatusCompatibility(t *testing.T) {
	syntheticSubscriptionHeaders(t)
	for _, code := range []int{301, 302, 303, 307, 308} {
		seen := make(chan http.Header, 1)
		_, address := subscriptionTestServer(t, true, func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/sub" {
				http.Redirect(w, r, "/final", code)
				return
			}
			seen <- r.Header.Clone()
			w.WriteHeader(200)
		})
		if err := requestTestSubscription(address + "/sub"); err != nil {
			t.Fatal(err)
		}
		if h := <-seen; h.Get("x-hwid") != "SYNTHETIC-HWID-1234" {
			t.Errorf("%d broke HTTPS provider", code)
		}
	}
}
