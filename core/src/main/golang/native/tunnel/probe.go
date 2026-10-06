package tunnel

import (
	"context"
	"time"

	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/tunnel"
)

// ProbeResult — один замер узла. Либо задержка, либо причина неудачи; пустая причина
// означает, что замер прошёл.
type ProbeResult struct {
	Delay uint16 `json:"delay"`
	Error string `json:"error"`
}

const (
	defaultProbeURL     = "https://cp.cloudflare.com/generate_204"
	defaultProbeTimeout = 5000
)

// Probe замеряет **один** узел, а не группу, как HealthCheck.
//
// Зачем отдельно: под шейпингом оператора узел жив, но бесполезен, и единственный замер
// «60 мс» об этом ничего не говорит. Нужна серия замеров по конкретному узлу, из которой
// видно разброс и долю неответов, — её собирает уже Kotlin-сторона (D-57).
//
// Вызов блокирующий: ждать всё равно приходится, а возвращать результат через completable
// сложнее без всякой выгоды.
func Probe(name, url string, timeoutMs int) ProbeResult {
	proxy := tunnel.Proxies()[name]
	if proxy == nil {
		return ProbeResult{Error: "proxy not found: " + name}
	}

	if url == "" {
		url = defaultProbeURL
	}

	if timeoutMs <= 0 {
		timeoutMs = defaultProbeTimeout
	}

	// 204 — то, что отдаёт страница проверки связи; другой код значит, что в ответ
	// вмешались, и замер честнее считать неудачным.
	expected, err := utils.NewUnsignedRanges[uint16]("204")
	if err != nil {
		return ProbeResult{Error: err.Error()}
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()

	delay, err := proxy.URLTest(ctx, url, expected)
	if err != nil {
		return ProbeResult{Error: err.Error()}
	}

	return ProbeResult{Delay: delay}
}
