package tunnel

import (
	"cfa/native/panicguard"
	"context"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

func HealthCheck(name string) error {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)

		return nil
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Request health check for `%s`: invalid type %s", name, p.Type().String())

		return nil
	}

	if name == "GLOBAL" {
		return healthCheckGlobal(g)
	}

	checks := make([]func() error, 0, len(g.Providers()))
	for _, pr := range g.Providers() {
		pr := pr
		checks = append(checks, func() error { pr.HealthCheck(); return nil })
	}
	return checkBatch("healthCheck.provider", checks)
}

// HealthCheckChosen checks only the node a group is using now — Home's own check after a start (Q-102).
//
// A group's check asks its nodes in the order of the list, ten at a time, so the node in use gets its
// number when its turn comes: near the end of a long list (F-394).
func HealthCheckChosen(name string) error {
	p := tunnel.Proxies()[name]
	if p == nil {
		return nil
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		return nil
	}

	node, url := chosenNode(g, 0)
	if node == nil {
		log.Warnln("Request health check of the node `%s` uses: not found", name)

		return nil
	}

	// ponytail: the default timeout and any answer, as for GLOBAL; a group with its own `timeout` or
	// `expected-status` is judged by them only in its full check. Upgrade: read them from the fork's HealthCheck.
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// A network timeout is an ordinary measurement failure.
	_, _ = node.URLTest(ctx, url, nil)
	return nil
}

// chosenNode follows a group's choice through groups that choose a group, as Home shows it, to a node,
// and gives the address its provider checks it at: the screen reads a node's delay at the first address
// it has a history for (`testURLOf`), and a check at another one would mix the numbers.
func chosenNode(g outboundgroup.ProxyGroup, depth int) (C.Proxy, string) {
	if depth >= maxChosenDepth {
		return nil, ""
	}

	now := g.Now()
	for _, pr := range g.Providers() {
		for _, px := range pr.Proxies() {
			if px.Name() != now {
				continue
			}

			if inner, ok := px.Adapter().(outboundgroup.ProxyGroup); ok {
				return chosenNode(inner, depth+1)
			}

			// The provider of GLOBAL, which the core builds itself, has no address (see below).
			url := pr.HealthCheckURL()
			if url == "" {
				url = testURLOf(px)
			}

			return px, url
		}
	}

	return nil, ""
}

// Deeper than this a group choosing a group is a mistake of the config, as on Home.
const maxChosenDepth = 4

// healthCheckGlobal меряет участников GLOBAL по одному, без встроенных выходов (D-143).
//
// Проверкой провайдера GLOBAL не померить: у группы, которую ядро собирает само, пустой
// проверочный адрес, и ядро её пропускает (`execute`, `adapter/provider/healthcheck.go`), а
// первыми в ней стоят DIRECT и REJECT (F-153). REJECT всегда «нет ответа», DIRECT уходит
// мимо узлов, и проверочный адрес напрямую может не открыться до конца срока. Экран их в
// GLOBAL не показывает, меряем только то, что видно, и по тому адресу, по которому экран
// берёт задержку.
func healthCheckGlobal(g outboundgroup.ProxyGroup) error {
	checks := make([]func() error, 0, len(g.Proxies()))
	limit := make(chan struct{}, 10) // как у ядра: не больше десяти замеров разом

	for _, px := range g.Proxies() {
		if isBuiltin(px.Type()) {
			continue
		}

		px := px
		checks = append(checks, func() error {

			limit <- struct{}{}
			defer func() { <-limit }()

			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()

			// A network timeout is an ordinary measurement failure, as before.
			_, _ = px.URLTest(ctx, testURLOf(px), nil)
			return nil
		})
	}

	return checkBatch("healthCheck.proxy", checks)
}

// isBuiltin — встроенные выходы ядра: не узлы и не группы (`constant/adapters.go`).
func isBuiltin(t C.AdapterType) bool {
	switch t {
	case C.Direct, C.Reject, C.RejectDrop, C.Compatible, C.Pass, C.PassRule, C.Dns:
		return true
	}

	return false
}

// HealthCheckAuto перемеряет группы с автовыбором — url-test, fallback, load-balance.
//
// Ядро проверяет их само сразу при загрузке конфига, а конфиг грузится одновременно с
// подъёмом туннеля. Успел раньше — проверка ушла, пока свои сокеты ядро ещё не умело
// выводить мимо туннеля, и все узлы записаны мёртвыми. Автовыбор тогда держит первый
// попавшийся узел до следующей проверки — до пяти минут (D-110).
//
// Просто позвать проверку мало: пока идёт прежняя, новый запрос присоединяется к ней и
// получает её провал, а ещё секунду после неё ядро отдаёт тот же результат из кэша
// (`singledo`, `adapter/provider/healthcheck.go`). Поэтому — дождаться идущей, переждать
// кэш и мерить заново.
//
// Перебираем все прокси ядра, а не список групп: группы с автовыбором у провайдеров
// обычно скрыты (`hidden: true`), и список для экрана их не содержит.
func HealthCheckAuto() {
	for name, p := range tunnel.Proxies() {
		switch p.Type() {
		case C.URLTest, C.Fallback, C.LoadBalance:
			go func(group string) {
				defer panicguard.Recover("healthCheckAuto.worker", logCheckFailure)
				if err := HealthCheck(group); err != nil {
					logCheckFailure(err)
				}
				time.Sleep(recheckAfter)
				log.Debugln("Recheck `%s` after tunnel start", group)
				if err := HealthCheck(group); err != nil {
					logCheckFailure(err)
				}
			}(name)
		}
	}
}

// Сколько ядро отдаёт прошлый результат проверки группы из кэша — секунду — и запас.
const recheckAfter = 1100 * time.Millisecond

func HealthCheckAll() {
	for _, g := range QueryProxyGroupNames(false) {
		go func(group string) {
			defer panicguard.Recover("healthCheckAll.worker", logCheckFailure)
			if err := HealthCheck(group); err != nil {
				logCheckFailure(err)
			}
		}(g)
	}
}

func logCheckFailure(err error) { log.Errorln("[APP] %s", err) }
