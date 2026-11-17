package config

import (
	"os"
	P "path"
	"strings"

	"github.com/dlclark/regexp2"
	"github.com/metacubex/mihomo/config"
)

// GroupInfo — группа прокси, прочитанная прямо из файла профиля.
type GroupInfo struct {
	Name    string   `json:"name"`
	Type    string   `json:"type"`
	Proxies []string `json:"proxies"`
	Icon    string   `json:"icon"`
}

// QueryGroups читает группы и их участников из профиля, **ничего не запуская**:
// ни слушателей, ни провайдеров правил, ни туннеля. Нужно, чтобы человек мог выбрать
// узел заранее, при выключенном подключении.
//
// Берём только сырой разбор YAML: `config.ParseRawConfig` вдобавок к разбору создаёт
// провайдеров, а это уже обращения в сеть — при выключенном туннеле их быть не должно.
// Плата за это — участники групп, взятые из `proxy-providers`, здесь не видны:
// в файле профиля их списка нет, он скачивается отдельно.
func QueryGroups(profilePath string) []GroupInfo {
	return readGroups(P.Join(profilePath, "config.yaml"), false)
}

// readGroups is [QueryGroups] for one file; [hidden] keeps the groups marked `hidden: true`.
func readGroups(file string, hidden bool) []GroupInfo {
	data, err := os.ReadFile(file)
	if err != nil {
		return []GroupInfo{}
	}

	raw, err := config.UnmarshalRawConfig(data)
	if err != nil {
		return []GroupInfo{}
	}

	result := make([]GroupInfo, 0, len(raw.ProxyGroup))

	// What `include-all` and `include-all-proxies` add: every top-level node, in the file's order. The built-in
	// template's main group has nothing else, so without them a subscription of links showed only «⚡ AUTO» (D-424).
	all := make([]string, 0, len(raw.Proxy))
	for _, proxy := range raw.Proxy {
		if name, _ := proxy["name"].(string); name != "" {
			all = append(all, name)
		}
	}

	for _, group := range raw.ProxyGroup {
		name, _ := group["name"].(string)
		if name == "" {
			continue
		}

		// Скрытые группы прячем и здесь: при поднятом туннеле ядро поступает так же.
		if skip, ok := group["hidden"].(bool); ok && skip && !hidden {
			continue
		}

		kind, _ := group["type"].(string)

		members := make([]string, 0)

		if list, ok := group["proxies"].([]any); ok {
			for _, item := range list {
				if member, ok := item.(string); ok {
					members = append(members, member)
				}
			}
		}

		// As mihomo's own parser (`adapter/outboundgroup/parser.go`, `groupbase.go`): `filter` picks among the
		// top-level nodes, `exclude-filter` drops from all members, the group's own list included; several patterns
		// are joined by «`». ponytail: `exclude-type` and nodes of `use:` providers are not here — the file does not
		// name a node's type as the core does, nor list a provider's nodes; a subscription loaded in the core is read
		// from the core instead.
		if flag(group["include-all"]) || flag(group["include-all-proxies"]) {
			filter, _ := group["filter"].(string)
			members = append(members, matching(all, filter, true)...)
		}

		if exclude, _ := group["exclude-filter"].(string); exclude != "" {
			members = matching(members, exclude, false)
		}

		icon, _ := group["icon"].(string)

		result = append(result, GroupInfo{Name: name, Type: kind, Proxies: unique(members), Icon: icon})
	}

	return result
}

func flag(value any) bool {
	on, _ := value.(bool)
	return on
}

// matching keeps the [names] some pattern of [patterns] matches (keep), or those none matches (!keep); empty
// [patterns] keep everything. A pattern the regexp engine refuses is skipped: the core refuses such a config anyway.
func matching(names []string, patterns string, keep bool) []string {
	if patterns == "" {
		return names
	}

	var regs []*regexp2.Regexp
	for _, pattern := range strings.Split(patterns, "`") {
		if reg, err := regexp2.Compile(pattern, regexp2.None); err == nil {
			regs = append(regs, reg)
		}
	}

	result := make([]string, 0, len(names))
	for _, name := range names {
		matched := false
		for _, reg := range regs {
			if ok, _ := reg.MatchString(name); ok {
				matched = true
				break
			}
		}
		if matched == keep {
			result = append(result, name)
		}
	}

	return result
}

func unique(names []string) []string {
	seen := make(map[string]struct{}, len(names))
	result := make([]string, 0, len(names))
	for _, name := range names {
		if _, ok := seen[name]; !ok {
			seen[name] = struct{}{}
			result = append(result, name)
		}
	}

	return result
}
