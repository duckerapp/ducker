package convert

import (
	"errors"
	"fmt"
	"strings"

	"github.com/metacubex/mihomo/component/age"
	"go.yaml.in/yaml/v3"
)

// The core's own proxies and its GLOBAL group: a subscription name equal to one is refused.
var reservedNames = []string{"DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL"}

var (
	errEncrypted = errors.New("подписка зашифрована (AGE): с шаблоном её узлы легли бы в конфиг открытым текстом")
	errNoSources = errors.New("в подписке нет ни узлов, ни провайдеров узлов")
)

// sourceGroup is what the dependency closure needs from a subscription's group.
type sourceGroup struct {
	Name                string   `yaml:"name"`
	Proxies             []string `yaml:"proxies"`
	IncludeAll          bool     `yaml:"include-all"`
	IncludeAllProxies   bool     `yaml:"include-all-proxies"`
	IncludeAllProviders bool     `yaml:"include-all-providers"`
}

// ApplyTemplateWithSources builds a config in template mode (D-493): everything from the
// template, the connections from the subscription. Links give their nodes; a config gives its
// buildable `proxies`, every `proxy-providers` entry as written and the groups its dialer
// chains go through, placed after the template's groups so the first select group is still
// the template's (user rules send «через VPN» there). Its own groups and rules are dropped.
//
// directHost works as in ApplyTemplate. An error is a reason to show a person; the caller
// keeps the working profile.
func ApplyTemplateWithSources(content, template, directHost string) (string, error) {
	// The core decrypts AGE in memory only; the generated config would keep the nodes in the clear.
	if strings.HasPrefix(content, age.FileHeader) {
		return "", errEncrypted
	}

	proxies, linksErr := Convert(content)

	var providers, groups []*yaml.Node
	var fingerprint *yaml.Node

	if linksErr != nil {
		var source struct {
			Proxies     []map[string]any `yaml:"proxies"`
			Providers   yaml.Node        `yaml:"proxy-providers"`
			Groups      yaml.Node        `yaml:"proxy-groups"`
			Fingerprint yaml.Node        `yaml:"global-client-fingerprint"`
		}

		if err := yaml.Unmarshal([]byte(content), &source); err != nil {
			// Neither links nor a config: what the provider sent is described by the links' error.
			return "", linksErr
		}

		proxies = buildable(source.Proxies)
		nameProxies(proxies)

		if source.Providers.Kind == yaml.MappingNode {
			providers = source.Providers.Content
		}

		if len(proxies) == 0 && len(providers) == 0 {
			return "", errNoSources
		}

		var err error
		if groups, err = chainGroups(proxies, providers, source.Groups.Content); err != nil {
			return "", err
		}

		if source.Fingerprint.Kind == yaml.ScalarNode && source.Fingerprint.Value != "" {
			fingerprint = &source.Fingerprint
		}
	}

	document, root, err := templateDocument(template)
	if err != nil {
		return "", err
	}

	if err := checkNames(root, proxies, providers, groups); err != nil {
		return "", err
	}

	list := sequence(root, "proxies")
	for _, proxy := range proxies {
		node, err := proxyNode(proxy)
		if err != nil {
			return "", err
		}

		list.Content = append(list.Content, node)
	}

	if len(providers) > 0 {
		own := child(root, "proxy-providers", yaml.MappingNode)
		own.Content = append(own.Content, inline(providers)...)
	}

	if len(groups) > 0 {
		own := sequence(root, "proxy-groups")
		own.Content = append(own.Content, inline(groups)...)
	}

	if fingerprint != nil && !hasKey(root, "global-client-fingerprint") {
		key := &yaml.Node{}
		key.SetString("global-client-fingerprint")
		root.Content = append(root.Content, key, fingerprint)
	}

	if directHost != "" {
		withDirectHost(sequence(root, "rules"), directHost)
	}

	tidy(root)

	return encode(document)
}

// chainGroups returns the subscription's groups that its nodes and providers dial through
// (`dialer-proxy`, a provider's `override.dialer-proxy` and `proxy`), followed through those
// groups' members, in the subscription's order. `use` needs no following: every provider is
// carried anyway.
func chainGroups(proxies []map[string]any, providers, groups []*yaml.Node) ([]*yaml.Node, error) {
	var wanted []string

	for _, proxy := range proxies {
		if name, _ := proxy["dialer-proxy"].(string); name != "" {
			wanted = append(wanted, name)
		}
	}

	for i := 1; i < len(providers); i += 2 {
		var provider struct {
			Proxy    string `yaml:"proxy"`
			Override struct {
				DialerProxy string `yaml:"dialer-proxy"`
			} `yaml:"override"`
		}

		// A provider the core cannot read is the core's to refuse, with its own reason.
		_ = providers[i].Decode(&provider)

		wanted = append(wanted, provider.Proxy, provider.Override.DialerProxy)
	}

	byName := map[string]sourceGroup{}
	for _, node := range groups {
		var group sourceGroup
		if node.Decode(&group) == nil && group.Name != "" {
			byName[group.Name] = group
		}
	}

	carried := map[string]bool{}

	for len(wanted) > 0 {
		name := wanted[0]
		wanted = wanted[1:]

		group, ok := byName[name]
		if !ok || carried[name] {
			continue
		}

		// Inside the template such a group would also take the template's service exits.
		if group.IncludeAll || group.IncludeAllProxies || group.IncludeAllProviders {
			return nil, fmt.Errorf("через группу «%s» из подписки идёт цепочка узлов, но эта группа собирает все узлы подряд: в шаблоне она взяла бы и выход мимо VPN", name)
		}

		carried[name] = true
		wanted = append(wanted, group.Proxies...)
	}

	var out []*yaml.Node
	for _, node := range groups {
		var group sourceGroup
		if node.Decode(&group) == nil && carried[group.Name] {
			out = append(out, node)
		}
	}

	return out, nil
}

// checkNames refuses a subscription name the template or the core already uses. The core
// refuses such a config too, but its message does not tell a person what to do.
func checkNames(root *yaml.Node, proxies []map[string]any, providers, groups []*yaml.Node) error {
	var template struct {
		Proxies   []struct{ Name string } `yaml:"proxies"`
		Groups    []struct{ Name string } `yaml:"proxy-groups"`
		Providers map[string]any          `yaml:"proxy-providers"`
	}

	if err := root.Decode(&template); err != nil {
		return err
	}

	taken := map[string]bool{}
	for _, name := range reservedNames {
		taken[name] = true
	}
	for _, proxy := range template.Proxies {
		taken[proxy.Name] = true
	}
	for _, group := range template.Groups {
		taken[group.Name] = true
	}

	var names []string
	for _, proxy := range proxies {
		names = append(names, proxy["name"].(string))
	}
	for _, node := range groups {
		var group sourceGroup
		_ = node.Decode(&group)
		names = append(names, group.Name)
	}

	for _, name := range names {
		if taken[name] {
			return fmt.Errorf("в подписке есть узел или группа «%s», а это имя уже занято в шаблоне", name)
		}
	}

	for i := 0; i < len(providers); i += 2 {
		if _, ok := template.Providers[providers[i].Value]; ok {
			return fmt.Errorf("в подписке есть провайдер узлов «%s», а это имя уже занято в шаблоне", providers[i].Value)
		}
	}

	return nil
}

// inline replaces aliases with what they name and drops anchors: the subscription's anchors
// are not in the template's document. The copies share children, which the encoder writes twice.
func inline(nodes []*yaml.Node) []*yaml.Node {
	for _, node := range nodes {
		if node.Kind == yaml.AliasNode {
			*node = *node.Alias
		}

		node.Anchor = ""
		inline(node.Content)
	}

	return nodes
}

func hasKey(root *yaml.Node, key string) bool {
	for i := 0; i+1 < len(root.Content); i += 2 {
		if root.Content[i].Value == key {
			return true
		}
	}

	return false
}
