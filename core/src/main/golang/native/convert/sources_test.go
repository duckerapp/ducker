package convert

import (
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	// Defines what config.Parse links to (go:linkname).
	_ "github.com/metacubex/mihomo/hub/executor"
	"go.yaml.in/yaml/v3"
)

// Template mode over the built-in template, each result read by the pinned core: what matters
// is which nodes end up in the template's groups, not how the YAML looks.

const (
	mainGroup = "Основной профиль"
	autoGroup = "⚡ AUTO"
)

func builtInTemplate(t *testing.T) string {
	t.Helper()

	source, err := os.ReadFile("../../../../../../app/src/main/assets/ducker/template.yaml")
	if err != nil {
		t.Fatal(err)
	}

	return string(source)
}

func fixture(t *testing.T, name string) string {
	t.Helper()

	content, err := os.ReadFile("testdata/" + name)
	if err != nil {
		t.Fatal(err)
	}

	return string(content)
}

// parseCore reads a config as the core does and loads its proxy providers.
func parseCore(t *testing.T, out string) *config.Config {
	t.Helper()

	C.SetHomeDir(t.TempDir())

	cfg, err := config.Parse([]byte(out))
	if err != nil {
		t.Fatalf("the core refuses the result: %v", err)
	}

	for name, provider := range cfg.Providers {
		if err := provider.Initial(); err != nil {
			t.Fatalf("provider %s: %v", name, err)
		}
	}

	return cfg
}

// members lists a group's candidates as `name(Type)`.
func members(t *testing.T, cfg *config.Config, name string) []string {
	t.Helper()

	proxy, ok := cfg.Proxies[name]
	if !ok {
		t.Fatalf("no group %s", name)
	}

	group, ok := proxy.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		t.Fatalf("%s is not a group", name)
	}

	var out []string
	for _, member := range group.Proxies() {
		out = append(out, member.Name()+"("+member.Type().String()+")")
	}

	return out
}

func withTemplate(t *testing.T, content string) (string, *config.Config) {
	t.Helper()

	out, err := ApplyTemplateWithSources(content, builtInTemplate(t), "sub.example.net")
	if err != nil {
		t.Fatalf("template mode refused: %v", err)
	}

	return out, parseCore(t, out)
}

func ss(names ...string) []string {
	out := make([]string, len(names))
	for i, name := range names {
		out[i] = name + "(Shadowsocks)"
	}

	return out
}

func expectGroups(t *testing.T, cfg *config.Config, nodes []string) {
	t.Helper()

	if got := members(t, cfg, autoGroup); !reflect.DeepEqual(got, nodes) {
		t.Fatalf("%s: %v, want %v", autoGroup, got, nodes)
	}

	want := append([]string{autoGroup + "(Fallback)"}, nodes...)
	if got := members(t, cfg, mainGroup); !reflect.DeepEqual(got, want) {
		t.Fatalf("%s: %v, want %v", mainGroup, got, want)
	}
}

// C1: links, base64 links and a provider's full config give their nodes and nothing else of theirs.
func TestSourcesNodesOnly(t *testing.T) {
	for file, nodes := range map[string][]string{
		"links.txt":         ss("Link One", "Link Two"),
		"links-base64.txt":  ss("Link One", "Link Two"),
		"full-proxies.yaml": ss("🇩🇪 Alpha", "🇫🇮 Beta"),
		// Its invalid rule is not carried, so the config is fine.
		"broken-rules.yaml": ss("Gamma"),
	} {
		t.Run(file, func(t *testing.T) {
			out, cfg := withTemplate(t, fixture(t, file))

			expectGroups(t, cfg, nodes)

			for _, foreign := range []string{"Provider choice", "Choice", "example.org", "mixed-port"} {
				if strings.Contains(out, foreign) {
					t.Fatalf("%q of the subscription is in the result", foreign)
				}
			}

			if cfg.Rules[0].Payload() != "sub.example.net" {
				t.Fatalf("first rule: %v", cfg.Rules[0])
			}
		})
	}
}

// C7: a config made after ducker's template brings the template's service exits (`DIRECTv4`) as nodes; only its
// servers are carried, and the name stays the template's own direct exit.
func TestSourcesServiceNodes(t *testing.T) {
	out, cfg := withTemplate(t, fixture(t, "service-nodes.yaml"))

	expectGroups(t, cfg, ss("🇪🇪 Delta"))

	// The core refuses a second definition of a name, so this one is the template's.
	if proxy, ok := cfg.Proxies["DIRECTv4"]; !ok || proxy.Type() != C.Direct {
		t.Fatalf("DIRECTv4: %v", proxy)
	}

	for _, foreign := range []string{"Блок", "dns-out"} {
		if strings.Contains(out, foreign) {
			t.Fatalf("%q of the subscription is in the result", foreign)
		}
	}
}

// C2: a plain node, an inline provider and an HTTP provider reach both groups; the HTTP
// provider is fetched with its own header and keeps every key as written.
func TestSourcesProviders(t *testing.T) {
	payload := fixture(t, "provider-a.yaml")

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Fixture-Token") != "provider-a-only" || r.Header.Get("x-hwid") != "" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}

		_, _ = w.Write([]byte(payload))
	}))
	defer server.Close()

	source := strings.ReplaceAll(fixture(t, "providers-mixed.yaml"), "http://127.0.0.1:18766", server.URL)

	out, cfg := withTemplate(t, source)

	// Providers follow the plain nodes in name order (the core sorts them): http-src, inline-src.
	expectGroups(t, cfg, ss("Static", "A · keep A1", "A · keep A2", "Inline One"))

	var before, after struct {
		Providers map[string]map[string]any `yaml:"proxy-providers"`
	}
	if err := yaml.Unmarshal([]byte(source), &before); err != nil {
		t.Fatal(err)
	}
	if err := yaml.Unmarshal([]byte(out), &after); err != nil {
		t.Fatal(err)
	}

	if !reflect.DeepEqual(before.Providers, after.Providers) {
		t.Fatalf("providers changed:\n%v\n%v", before.Providers, after.Providers)
	}
}

// C3: a dialer chain brings its helper group, after the template's groups; the helper's
// member stays an ordinary choice (Q-134).
func TestSourcesChain(t *testing.T) {
	out, cfg := withTemplate(t, fixture(t, "chain.yaml"))

	expectGroups(t, cfg, ss("Entry", "Exit", "Chained One"))

	var document struct {
		Groups []struct {
			Name string `yaml:"name"`
			Type string `yaml:"type"`
		} `yaml:"proxy-groups"`
	}
	if err := yaml.Unmarshal([]byte(out), &document); err != nil {
		t.Fatal(err)
	}

	last := document.Groups[len(document.Groups)-1]
	if last.Name != "Relay-in" {
		t.Fatalf("last group: %s", last.Name)
	}

	for _, group := range document.Groups {
		if group.Name == "Provider choice" {
			t.Fatal("a group no chain goes through was carried")
		}

		// «через VPN» in user rules goes to the first select group.
		if group.Type == "select" {
			if group.Name != mainGroup {
				t.Fatalf("first select group: %s", group.Name)
			}

			break
		}
	}

	if got := members(t, cfg, "Relay-in"); !reflect.DeepEqual(got, ss("Entry")) {
		t.Fatalf("Relay-in: %v", got)
	}
}

// C4: what cannot be carried is refused with a reason, before the core sees it.
func TestSourcesRefused(t *testing.T) {
	includeAllChain := strings.Replace(fixture(t, "chain.yaml"),
		"{name: Relay-in, type: select, proxies: [Entry]}",
		"{name: Relay-in, type: select, include-all: true}", 1)
	if includeAllChain == fixture(t, "chain.yaml") {
		t.Fatal("chain.yaml changed: the include-all case no longer applies")
	}

	for name, content := range map[string]string{
		"a node named like a template group": fixture(t, "collision.yaml"),
		"a node named like a core proxy":     "proxies:\n  - {name: DIRECT, type: socks5, server: 192.0.2.1, port: 1080}\n",
		"a chain group taking every node":    includeAllChain,
		"AGE":                                "-----BEGIN AGE ENCRYPTED FILE-----\nYWdlLWVuY3J5cHRpb24ub3JnL3Yx\n-----END AGE ENCRYPTED FILE-----\n",
		"nothing to carry":                   "mode: rule\nrules:\n  - MATCH,DIRECT\n",
		"only broken nodes":                  "proxies:\n  - {name: Broken, type: ss, server: 192.0.2.1, port: 1, cipher: no-such-cipher, password: x}\n",
		"an error page":                      "<html><body><h1>502 Bad Gateway</h1></body></html>",
		"empty":                              "",
	} {
		if _, err := ApplyTemplateWithSources(content, builtInTemplate(t), ""); err == nil {
			t.Errorf("%s: accepted", name)
		} else {
			t.Logf("%s: %v", name, err)
		}
	}

	_, err := ApplyTemplateWithSources(fixture(t, "collision.yaml"), builtInTemplate(t), "")
	if err == nil || !strings.Contains(err.Error(), mainGroup) {
		t.Fatalf("the reason does not name the taken name: %v", err)
	}
}

// C5: a provider of direct nodes leaves the groups on REJECT, never on a direct exit.
func TestSourcesDirectProvider(t *testing.T) {
	_, cfg := withTemplate(t, fixture(t, "direct-provider.yaml"))

	if got := members(t, cfg, autoGroup); !reflect.DeepEqual(got, []string{"REJECT(Reject)"}) {
		t.Fatalf("%s: %v", autoGroup, got)
	}

	if got := members(t, cfg, mainGroup); !reflect.DeepEqual(got, []string{autoGroup + "(Fallback)"}) {
		t.Fatalf("%s: %v", mainGroup, got)
	}
}

// C6: today's link fallback reads the same way through the core: link nodes only, no direct exit.
func TestLinkFallbackGroups(t *testing.T) {
	out, _, err := ApplyTemplate(fixture(t, "links.txt"), builtInTemplate(t), "sub.example.net")
	if err != nil {
		t.Fatal(err)
	}

	expectGroups(t, parseCore(t, out), ss("Link One", "Link Two"))
}
