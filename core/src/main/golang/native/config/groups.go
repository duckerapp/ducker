package config

import (
	"os"
	P "path"

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
	data, err := os.ReadFile(P.Join(profilePath, "config.yaml"))
	if err != nil {
		return []GroupInfo{}
	}

	raw, err := config.UnmarshalRawConfig(data)
	if err != nil {
		return []GroupInfo{}
	}

	result := make([]GroupInfo, 0, len(raw.ProxyGroup))

	for _, group := range raw.ProxyGroup {
		name, _ := group["name"].(string)
		if name == "" {
			continue
		}

		// Скрытые группы прячем и здесь: при поднятом туннеле ядро поступает так же.
		if hidden, ok := group["hidden"].(bool); ok && hidden {
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

		icon, _ := group["icon"].(string)

		result = append(result, GroupInfo{Name: name, Type: kind, Proxies: members, Icon: icon})
	}

	return result
}
