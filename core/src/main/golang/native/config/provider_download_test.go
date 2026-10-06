package config

import (
	"compress/gzip"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestFetchProviderBodyFailure(t *testing.T) {
	for _, kind := range []string{"proxy", "rule"} {
		for _, mode := range []string{"overflow", "broken"} {
			t.Run(kind+"/"+mode, func(t *testing.T) {
				server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					if mode == "overflow" {
						w.Header().Set("Content-Encoding", "gzip")
						gz := gzip.NewWriter(w)
						_, _ = io.Copy(gz, &downloadTestReader{left: providerDownloadLimit + 1})
						_ = gz.Close()
					} else {
						w.Header().Set("Content-Length", "1000")
						_, _ = w.Write([]byte("short"))
					}
				}))
				defer server.Close()
				dir := t.TempDir()
				profile := "proxies:\n  - {name: QA_DIRECT, type: direct}\nrules:\n  - MATCH,DIRECT\n"
				if kind == "proxy" {
					profile += fmt.Sprintf("proxy-providers:\n  QA:\n    type: http\n    url: %s\n    path: proxies/qa.yaml\n", server.URL)
				} else {
					profile += fmt.Sprintf("rule-providers:\n  QA:\n    type: http\n    behavior: domain\n    url: %s\n    path: rules/qa.yaml\n", server.URL)
				}
				_ = os.WriteFile(filepath.Join(dir, "config.yaml"), []byte(profile), 0600)
				err := FetchAndValid(dir, "", false, func(string) {})
				if mode == "broken" {
					// Обрыв загрузки набора подписку не срывает: ядро докачает его при подключении (D-206).
					if err != nil {
						t.Fatalf("broken provider failed the import: %v", err)
					}
				} else if err == nil || !strings.HasPrefix(err.Error(), "DOWNLOAD_TOO_LARGE: provider:") {
					t.Fatalf("wrong failure: %v", err)
				}
				data, _ := os.ReadFile(filepath.Join(dir, "config.yaml"))
				if string(data) != profile {
					t.Fatal("config changed")
				}
				_ = filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
					if err != nil {
						t.Error(err)
						return err
					}
					if !info.IsDir() && path != filepath.Join(dir, "config.yaml") {
						t.Errorf("failed provider left file %s", info.Name())
					}
					return nil
				})
			})
		}
	}
}

func TestFetchOrdinaryProviderAndCache(t *testing.T) {
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		if r.Header.Get("X-Hwid") != "" {
			t.Error("subscription headers sent to provider")
		}
		_, _ = w.Write([]byte("proxies:\n  - {name: QA_DIRECT, type: direct}\n"))
	}))
	defer server.Close()
	dir := t.TempDir()
	profile := fmt.Sprintf("proxy-providers:\n  QA:\n    type: http\n    url: %s\n    path: proxies/qa.yaml\nproxy-groups:\n  - {name: QA_GROUP, type: select, use: [QA]}\nrules:\n  - MATCH,QA_GROUP\n", server.URL)
	_ = os.WriteFile(filepath.Join(dir, "config.yaml"), []byte(profile), 0600)
	_ = SetRequestHeaders(`{"x-hwid":"SYNTHETIC-HWID-ONLY","User-Agent":"mihomo/QA ducker/QA"}`)
	defer SetRequestHeaders("")
	for i := 0; i < 2; i++ {
		if err := FetchAndValid(dir, "", false, func(string) {}); err != nil {
			t.Fatal(err)
		}
	}
	if requests != 1 {
		t.Fatalf("cached provider fetched again: %d", requests)
	}
	data, err := os.ReadFile(filepath.Join(dir, "providers/proxies/qa.yaml"))
	if err != nil || !strings.Contains(string(data), "QA_DIRECT") {
		t.Fatalf("provider missing: %v", err)
	}
}

// Набор правил с `proxy:` заранее не качается: ядро скачает его само через указанный узел (D-229).
func TestRuleProviderWithProxyIsNotPrefetched(t *testing.T) {
	for via, want := range map[string]int{"": 1, "DIRECT": 1, "QA_DIRECT": 0} {
		t.Run("proxy="+via, func(t *testing.T) {
			requests := 0
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				requests++
				_, _ = w.Write([]byte("payload:\n  - example.com\n"))
			}))
			defer server.Close()
			dir := t.TempDir()
			profile := fmt.Sprintf("proxies:\n  - {name: QA_DIRECT, type: direct}\nrule-providers:\n  QA:\n    type: http\n    behavior: domain\n    url: %s\n    path: rules/qa.yaml\n", server.URL)
			if via != "" {
				profile += "    proxy: " + via + "\n"
			}
			profile += "rules:\n  - RULE-SET,QA,QA_DIRECT\n  - MATCH,DIRECT\n"
			_ = os.WriteFile(filepath.Join(dir, "config.yaml"), []byte(profile), 0600)
			if err := FetchAndValid(dir, "", false, func(string) {}); err != nil {
				t.Fatal(err)
			}
			if requests != want {
				t.Fatalf("requests: %d, want %d", requests, want)
			}
		})
	}
}
