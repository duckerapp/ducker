package config

import (
	"os"
	P "path"

	"github.com/metacubex/mihomo/common/yaml"
)

// ProfileNetwork — то, что клиент обязан прочитать из профиля сам, потому что ядро
// на Android этим не распоряжается: интерфейс создаёт `VpnService`, а не ядро.
type ProfileNetwork struct {
	// `tun.exclude-package` — приложения, которые должны ходить мимо туннеля.
	ExcludePackage []string `json:"excludePackage"`
	// `tun.include-package` — если список не пуст, в туннель попадают только они.
	IncludePackage []string `json:"includePackage"`
	// `tun.route-exclude-address` — сети, которые не должны попадать в туннель.
	RouteExcludeAddress []string `json:"routeExcludeAddress"`
	// `tun.stack` — `system`, `gvisor` или `mixed`. Пусто — значения в профиле нет.
	Stack string `json:"stack"`
	// `tun.congestion-controller` — алгоритм TCP у стека `mips`. Пусто — в профиле нет.
	CongestionController string `json:"congestionController"`
	// `dns.enable: false`, написанное в профиле **явно**, а не просто отсутствие раздела.
	DnsDisabled bool `json:"dnsDisabled"`
	// `mode` — режим, заданный провайдером. Пусто — не задан, то есть правила.
	Mode string `json:"mode"`
}

func rawDocument(profilePath string) map[string]any {
	data, err := os.ReadFile(P.Join(profilePath, "config.yaml"))
	if err != nil {
		return nil
	}

	var doc map[string]any

	if err := yaml.Unmarshal(data, &doc); err != nil {
		return nil
	}

	return doc
}

func section(doc map[string]any, key string) map[string]any {
	value, _ := doc[key].(map[string]any)

	return value
}

func stringList(value any) []string {
	items, ok := value.([]any)
	if !ok {
		return nil
	}

	result := make([]string, 0, len(items))

	for _, item := range items {
		if text, ok := item.(string); ok && text != "" {
			result = append(result, text)
		}
	}

	return result
}

// dnsExplicitlyDisabled отличает «провайдер запретил свой DNS» от «раздела dns нет».
//
// В разобранном `RawConfig` это одно и то же значение `false`, поэтому смотреть
// приходится в сам файл. Разница принципиальная: во втором случае подставить умолчания
// необходимо, иначе перехваченные TUN'ом запросы некому обслуживать, а в первом это
// прямое переписывание профиля (инвариант 1).
func dnsExplicitlyDisabled(doc map[string]any) bool {
	dns := section(doc, "dns")
	if dns == nil {
		return false
	}

	enable, declared := dns["enable"].(bool)

	return declared && !enable
}

// QueryNetwork читает из профиля то, что применяет уже сама Kotlin-сторона.
// Как и QueryGroups, только разбирает YAML и ничего не запускает.
func QueryNetwork(profilePath string) ProfileNetwork {
	doc := rawDocument(profilePath)
	if doc == nil {
		return ProfileNetwork{
			ExcludePackage:      []string{},
			IncludePackage:      []string{},
			RouteExcludeAddress: []string{},
		}
	}

	tun := section(doc, "tun")

	result := ProfileNetwork{DnsDisabled: dnsExplicitlyDisabled(doc)}

	result.Mode, _ = doc["mode"].(string)

	if tun != nil {
		result.ExcludePackage = stringList(tun["exclude-package"])
		result.IncludePackage = stringList(tun["include-package"])
		result.RouteExcludeAddress = stringList(tun["route-exclude-address"])
		result.Stack, _ = tun["stack"].(string)
		result.CongestionController, _ = tun["congestion-controller"].(string)
	}

	if result.ExcludePackage == nil {
		result.ExcludePackage = []string{}
	}

	if result.IncludePackage == nil {
		result.IncludePackage = []string{}
	}

	if result.RouteExcludeAddress == nil {
		result.RouteExcludeAddress = []string{}
	}

	return result
}
