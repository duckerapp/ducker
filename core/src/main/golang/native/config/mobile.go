package config

import (
	"os"
	P "path"
	"strings"
)

// QueryMobilePreset is the provider's list for «Мобильная сеть» (D-356, D-534): the group the config names in
// `ducker: {mobile-group: …}`, else the one [headerGroup] (`ducker-mobile-group`) names. The list is its members that
// are members of the main group too, in the provider's order — Ducker switches the node of the main group. None are,
// and the group itself is a member: the group alone, which then picks its node itself. Empty — no preset.
//
// The group is looked up with `hidden: true` included, and in the provider's own download (`source.yaml`) when a
// configuration template built `config.yaml` (D-493): the template's main group holds the provider's nodes.
func QueryMobilePreset(profilePath, headerGroup string) []string {
	source := P.Join(profilePath, "source.yaml")
	if _, err := os.Stat(source); err != nil {
		source = P.Join(profilePath, "config.yaml")
	}

	name := ""
	if doc := rawFile(source); doc != nil {
		name, _ = section(doc, "ducker")["mobile-group"].(string)
	}

	name = strings.TrimSpace(name)
	if name == "" {
		name = strings.TrimSpace(headerGroup)
	}

	result := []string{}
	if name == "" {
		return result
	}

	var members []string
	for _, group := range readGroups(source, true) {
		if group.Name == name {
			members = group.Proxies
			break
		}
	}

	var main []string
	for _, group := range QueryGroups(profilePath) {
		if strings.EqualFold(group.Type, "select") {
			main = group.Proxies
			break
		}
	}

	inMain := make(map[string]struct{}, len(main))
	for _, member := range main {
		inMain[member] = struct{}{}
	}

	for _, member := range members {
		if _, ok := inMain[member]; ok {
			result = append(result, member)
		}
	}

	if _, ok := inMain[name]; ok && len(result) == 0 {
		result = append(result, name)
	}

	return result
}
