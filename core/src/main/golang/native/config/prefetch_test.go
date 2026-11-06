package config

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync"
	"testing"

	"github.com/metacubex/mihomo/common/utils"
)

func TestFetchMissingProviders(t *testing.T) {
	var mu sync.Mutex
	requests := map[string]int{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		requests[r.URL.Path]++
		mu.Unlock()
		switch r.URL.Path {
		case "/proxies":
			_, _ = w.Write([]byte("proxies:\n  - {name: QA_P, type: direct}\n"))
		case "/gone":
			http.NotFound(w, r)
		default:
			_, _ = w.Write([]byte("payload:\n  - example.com\n"))
		}
	}))
	defer server.Close()
	count := func(path string) int {
		mu.Lock()
		defer mu.Unlock()
		return requests[path]
	}

	dir := t.TempDir()
	profile := fmt.Sprintf(`proxies:
  - {name: QA_DIRECT, type: direct}
proxy-providers:
  QA_PROXIES: {type: http, url: %[1]s/proxies, path: proxies/qa.yaml}
rule-providers:
  QA_VIA_NODE: {type: http, behavior: domain, url: %[1]s/via-node, proxy: QA_DIRECT}
  QA_PLAIN: {type: http, behavior: domain, url: %[1]s/plain, path: rules/plain.yaml}
  QA_PRESENT: {type: http, behavior: domain, url: %[1]s/present, path: rules/present.yaml}
  QA_GONE: {type: http, behavior: domain, url: %[1]s/gone, path: rules/gone.yaml}
rules:
  - MATCH,DIRECT
`, server.URL)
	_ = os.WriteFile(filepath.Join(dir, "config.yaml"), []byte(profile), 0600)
	present := filepath.Join(dir, "providers", "rules", "present.yaml")
	_ = os.MkdirAll(filepath.Dir(present), 0700)
	_ = os.WriteFile(present, []byte("old"), 0600)

	left, err := FetchMissingProviders(dir)
	if err != nil {
		t.Fatal(err)
	}
	if left != 1 {
		t.Fatalf("still missing %d, want 1 (the 404)", left)
	}
	if count("/present") != 0 {
		t.Fatal("a file on disk was downloaded again")
	}
	if data, _ := os.ReadFile(present); string(data) != "old" {
		t.Fatal("a file on disk was changed")
	}
	// The rule set through a node has no path: the core names it by its link, as patchProviders does.
	viaNode := filepath.Join(dir, "providers", "rules", utils.MakeHash([]byte(server.URL+"/via-node")).String())
	for _, f := range []string{viaNode, "proxies/qa.yaml", "rules/plain.yaml"} {
		if !filepath.IsAbs(f) {
			f = filepath.Join(dir, "providers", f)
		}
		if _, err := os.Stat(f); err != nil {
			t.Errorf("not fetched: %s", f)
		}
	}
	if _, err := os.Stat(filepath.Join(dir, "providers", "rules", "gone.yaml")); err == nil {
		t.Error("a failed download left a file")
	}

	if left, _ := FetchMissingProviders(dir); left != 1 || count("/plain") != 1 {
		t.Fatal("a second pass downloaded what is on disk")
	}
}
