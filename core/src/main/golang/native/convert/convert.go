// Package convert превращает набор ссылок протоколов в готовый конфиг mihomo,
// подставляя узлы во встроенный шаблон приложения.
//
// Нужен для D-19: если по ссылке подписки пришёл не конфиг, а список ссылок вида
// `vless://`, `vmess://` и подобных, клиент не отказывает, а берёт свой шаблон и
// вставляет в него полученные узлы.
//
// Пакет намеренно не зависит от Android и cgo: так его можно проверить обычным
// `go test` на компьютере, а не только сборкой под телефон.
package convert

import (
	"errors"
	"fmt"
	"strings"

	"github.com/metacubex/mihomo/common/convert"
	"github.com/metacubex/mihomo/common/yaml"
)

// ErrNotConvertible означает, что в содержимом не нашлось ни одной ссылки протокола.
var ErrNotConvertible = errors.New("содержимое не похоже на список ссылок протоколов")

// Convert разбирает ссылки протоколов и отдаёт описания узлов.
//
// ConvertsV2Ray сам распознаёт как обычный текст со ссылками, так и подписку,
// закодированную в base64 (FACTS.md, F-23).
func Convert(content string) ([]map[string]any, error) {
	proxies, err := convert.ConvertsV2Ray([]byte(strings.TrimSpace(content)))
	if err != nil {
		return nil, err
	}

	if len(proxies) == 0 {
		return nil, ErrNotConvertible
	}

	nameProxies(proxies)

	return proxies, nil
}

// nameProxies даёт имя узлам, у которых его нет, и разводит одинаковые.
//
// Имя ядро берёт из якоря ссылки (`#Франкфурт`), но якорь необязателен: ссылка без него
// остаётся валидной. Узел без имени — это пустая строка в списке выбора и, что хуже,
// невозможность его выбрать: группы адресуют участников по имени.
func nameProxies(proxies []map[string]any) {
	used := map[string]bool{}

	for _, proxy := range proxies {
		name, _ := proxy["name"].(string)
		name = strings.TrimSpace(name)

		if name == "" {
			server, _ := proxy["server"].(string)
			name = server

			if port, ok := proxy["port"]; ok && server != "" {
				name = fmt.Sprintf("%s:%v", server, port)
			}

			if name == "" {
				name = "proxy"
			}
		}

		unique := name
		for i := 2; used[unique]; i++ {
			unique = fmt.Sprintf("%s (%d)", name, i)
		}

		used[unique] = true
		proxy["name"] = unique
	}
}

// withDirectHost ставит первым правилом «домен подписки — мимо туннеля».
//
// Зачем: при поднятом туннеле запрос подписки идёт по правилам профиля, то есть
// обычно через узел (FACTS.md, F-53). Если узлы умерли или подписка кончилась,
// обновиться становится нельзя — ровно тогда, когда это нужно. Здесь мы правим
// свой шаблон, а не конфиг провайдера, так что инвариант 1 не нарушается.
func withDirectHost(rules any, host string) []any {
	result := []any{"DOMAIN," + host + ",DIRECT"}

	if existing, ok := rules.([]any); ok {
		result = append(result, existing...)
	}

	return result
}

// ApplyTemplate вставляет узлы в шаблон и отдаёт готовый конфиг.
//
// directHost — хост ссылки подписки. Если задан, в начало правил добавляется
// отправка этого домена напрямую (см. withDirectHost).
//
// Узлы дописываются к тем, что уже есть в шаблоне: в шаблоне могут быть служебные
// записи вроде `DIRECT`, на которые ссылаются группы. Остальное содержимое шаблона
// не трогается — группы наполняются нативными ключами ядра (`include-all-proxies`),
// см. D-29.
func ApplyTemplate(content, template, directHost string) (string, error) {
	proxies, err := Convert(content)
	if err != nil {
		return "", err
	}

	var document map[string]any
	if err := yaml.Unmarshal([]byte(template), &document); err != nil {
		return "", err
	}

	if document == nil {
		document = map[string]any{}
	}

	merged := make([]any, 0, len(proxies)+4)

	if existing, ok := document["proxies"].([]any); ok {
		merged = append(merged, existing...)
	}

	for _, proxy := range proxies {
		merged = append(merged, proxy)
	}

	document["proxies"] = merged

	if directHost != "" {
		document["rules"] = withDirectHost(document["rules"], directHost)
	}

	out, err := yaml.Marshal(document)
	if err != nil {
		return "", err
	}

	return string(out), nil
}
