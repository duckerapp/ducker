package config

import (
	"net/http"
	"net/http/httptest"
	U "net/url"
	"os"
	P "path"
	"strings"
	"testing"
)

// Страница ошибки сервера не пишется в файл как конфиг, а заголовки панели доходят (F-155).
func TestFetchRejectsErrorStatus(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/ok" {
			_, _ = w.Write([]byte("proxies: []"))

			return
		}

		w.Header().Set("x-hwid-max-devices-reached", "true")
		w.WriteHeader(http.StatusServiceUnavailable)
		_, _ = w.Write([]byte("down"))
	}))
	defer server.Close()

	file := P.Join(t.TempDir(), "config.yaml")

	url, _ := U.Parse(server.URL + "/sub")
	header, err := fetch(url, file, true)

	if err == nil || !strings.HasPrefix(err.Error(), "DOWNLOAD_FAILED: profile: HTTP 503") {
		t.Fatalf("want HTTP 503 error, got %v", err)
	}
	if header.All["x-hwid-max-devices-reached"] != "true" {
		t.Fatalf("headers lost on error: %v", header.All)
	}
	if _, err := os.Stat(file); !os.IsNotExist(err) {
		t.Fatalf("error page written as config")
	}

	url, _ = U.Parse(server.URL + "/ok")
	if _, err := fetch(url, file, true); err != nil {
		t.Fatalf("200 failed: %v", err)
	}
	if data, _ := os.ReadFile(file); string(data) != "proxies: []" {
		t.Fatalf("200 body not written: %q", data)
	}
}
