package tunnel

import (
	"testing"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outbound"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/adapter/provider"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
)

func node(name string) C.Proxy {
	return adapter.NewProxy(outbound.NewDirectWithOption(outbound.DirectOption{Name: name}))
}

func selector(t *testing.T, name, url, selected string, proxies ...C.Proxy) *outboundgroup.Selector {
	t.Helper()
	pd, err := provider.NewCompatibleProvider(name, proxies, provider.NewHealthCheck(proxies, url, 0, 0, false, nil))
	if err != nil {
		t.Fatal(err)
	}
	g, err := outboundgroup.NewSelector(outboundgroup.GroupCommonOption{Name: name}, outboundgroup.SelectorOption{DefaultSelected: selected}, nil, []P.ProxyProvider{pd})
	if err != nil {
		t.Fatal(err)
	}
	return g
}

func TestChosenNodeFollowsGroupsToTheProvidersAddress(t *testing.T) {
	inner := selector(t, "AUTO", "https://inner.test/204", "c", node("a"), node("c"))
	main := selector(t, "Main", "https://main.test/204", "AUTO", node("b"), adapter.NewProxy(inner))

	got, url := chosenNode(main, 0)
	if got == nil || got.Name() != "c" || url != "https://inner.test/204" {
		t.Fatalf("got %v at %q", got, url)
	}

	direct := selector(t, "Plain", "https://main.test/204", "b", node("a"), node("b"))
	if got, url := chosenNode(direct, 0); got == nil || got.Name() != "b" || url != "https://main.test/204" {
		t.Fatalf("got %v at %q", got, url)
	}

	// A provider without an address, as GLOBAL's: the address the screen reads, the default for a new node.
	global := selector(t, "GLOBAL", "", "a", node("a"))
	if got, url := chosenNode(global, 0); got == nil || url != C.DefaultTestURL {
		t.Fatalf("got %v at %q", got, url)
	}
}
