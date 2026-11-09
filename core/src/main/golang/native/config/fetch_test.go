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
	header, err := fetch(url, file, true, nil)

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
	if _, err := fetch(url, file, true, nil); err != nil {
		t.Fatalf("200 failed: %v", err)
	}
	if data, _ := os.ReadFile(file); string(data) != "proxies: []" {
		t.Fatalf("200 body not written: %q", data)
	}
}

// Hostile response values: empty and too long count as not sent; a lone Latin-1 byte is read as Latin-1,
// a run of them (cp1251) is left for the app to count as broken (D-387).
func TestCollectHeadersHostileValues(t *testing.T) {
	got := collectHeaders(map[string][]string{
		"Profile-Title":        {"Caf\xe9 M\xfcller"},
		"Announce":             {"\xcf\xf0\xee"},
		"Support-Url":          {""},
		"Profile-Web-Page-Url": {strings.Repeat("a", headerValueMax+1)},
		"Ducker-Brand-Logo":    {"data:image/png;base64," + strings.Repeat("A", 200*1024)},
		"Ducker-Brand-Name":    {strings.Repeat("a", headerValueMax)},
		"Content-Type":         {"text/plain"},
	})
	if got["profile-title"] != "Café Müller" || got["announce"] != "\xcf\xf0\xee" || len(got["ducker-brand-logo"]) == 0 ||
		len(got["ducker-brand-name"]) != headerValueMax || len(got) != 4 {
		t.Fatalf("%q", got)
	}
	if _, ok := collectHeaders(map[string][]string{"Ducker-Brand-Logo": {strings.Repeat("A", logoValueMax+1)}})["ducker-brand-logo"]; ok {
		t.Fatal("a huge logo passed up")
	}
}
