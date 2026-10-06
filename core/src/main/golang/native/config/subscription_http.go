package config

import (
	"context"
	"fmt"
	"net"
	"runtime"
	"time"

	"github.com/metacubex/http"
	"github.com/metacubex/mihomo/component/ca"
)

type subscriptionDialFunc func(context.Context, string, string) (net.Conn, error)

// Keep the pinned mihomo HTTP transport/TLS settings, but own the redirect policy.
// Upstream HttpRequest does not expose Client.CheckRedirect through its options.
func subscriptionHttpRequest(ctx context.Context, address string, headers map[string][]string, dial subscriptionDialFunc) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, address, nil)
	if err != nil {
		return nil, err
	}
	for name, values := range headers {
		for _, value := range values {
			req.Header.Add(name, value)
		}
	}
	if user := req.URL.User; user != nil {
		password, _ := user.Password()
		req.SetBasicAuth(user.Username(), password)
	}
	tlsConfig, err := ca.GetTLSConfig(ca.Option{})
	if err != nil {
		return nil, err
	}
	transport := &http.Transport{
		DisableKeepAlives:     runtime.GOOS == "android",
		MaxIdleConns:          100,
		IdleConnTimeout:       30 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: time.Second,
		DialContext:           dial,
		TLSClientConfig:       tlsConfig,
	}
	client := &http.Client{Transport: transport, CheckRedirect: checkSubscriptionRedirect}
	defer client.CloseIdleConnections()
	return client.Do(req)
}

// Подписка идёт за переадресацией, куда бы та ни вела — на другой домен, сервер или с https
// на http: за адрес отвечает провайдер, и скачаться она должна как раньше (D-193). Данные
// устройства идут следом, пока вся цепочка шла по https; после первого адреса http — больше
// нет, даже если дальше снова https: такую цепочку может подменить чужая сеть. Клиент на
// каждом шаге заново копирует заголовки первого запроса, поэтому смотрим цепочку целиком.
// Пароль из ссылки и cookie при смене домена снимает сам клиент, как у штатного ядра.
func checkSubscriptionRedirect(req *http.Request, via []*http.Request) error {
	if len(via) >= 10 {
		return fmt.Errorf("subscription stopped after 10 redirects")
	}
	secure := deviceHeadersAllowed(req.URL.String())
	for _, hop := range via {
		secure = secure && deviceHeadersAllowed(hop.URL.String())
	}
	if !secure {
		for name := range req.Header {
			if sensitiveSubscriptionHeader(name) {
				req.Header.Del(name)
			}
		}
	}
	return nil
}
