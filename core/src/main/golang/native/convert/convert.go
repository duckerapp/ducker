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
	"bytes"
	"errors"
	"fmt"
	"sort"
	"strings"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/convert"
	// The library itself, not mihomo's `common/yaml`: that one has no node tree.
	"go.yaml.in/yaml/v3"
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

// ConvertConfig достаёт узлы из конфига mihomo, который ядро не приняло.
//
// Случай один: провайдер ошибся в конфиге — в правиле, в группе, — а человек добавляет
// подписку впервые, и рабочего конфига у него ещё нет. Раздел `proxies` в таком конфиге
// обычно цел, и встроенный шаблон с этими узлами лучше отказа. Пускать ли сюда, решает
// служба: у подписки, которая уже работает на конфиге провайдера, остаётся он.
//
// Узел, который ядро собрать не может, пропускается — так же, как ядро пропускает битый
// узел в провайдере: одна опечатка в шифре не должна стоить человеку остальных узлов.
//
// Узлы из `proxy-providers` не берём: это ссылки на другие файлы, а не сами узлы.
func ConvertConfig(content string) ([]map[string]any, error) {
	var document struct {
		Proxies []map[string]any `yaml:"proxies"`
	}

	if err := yaml.Unmarshal([]byte(content), &document); err != nil {
		return nil, err
	}

	proxies := make([]map[string]any, 0, len(document.Proxies))

	for _, proxy := range document.Proxies {
		name, _ := proxy["name"].(string)
		kind, _ := proxy["type"].(string)

		if strings.TrimSpace(name) == "" || strings.TrimSpace(kind) == "" {
			continue
		}

		built, err := adapter.ParseProxy(proxy)
		if err != nil {
			continue
		}

		_ = built.Close()

		proxies = append(proxies, proxy)
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
func withDirectHost(rules *yaml.Node, host string) {
	rule := &yaml.Node{}
	rule.SetString("DOMAIN," + host + ",DIRECT")

	rules.Content = append([]*yaml.Node{rule}, rules.Content...)
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
//
// Второе значение — откуда узлы: true, если это были не ссылки, а конфиг mihomo
// (см. ConvertConfig). Об отказе всегда говорит ошибка разбора ссылок: она про то, что
// прислал провайдер, а не про нашу вторую попытку.
func ApplyTemplate(content, template, directHost string) (string, bool, error) {
	fromConfig := false

	proxies, err := Convert(content)
	if err != nil {
		fromFile, configErr := ConvertConfig(content)
		if configErr != nil {
			return "", false, err
		}

		proxies, fromConfig = fromFile, true
	}

	// The template is edited as a tree of nodes, not as a map: a map comes back sorted by
	// key, and a person opening their config found `allow-lan`, `dns`, … instead of the
	// template's order (D-288). Anchors and their references stay as written.
	var document yaml.Node
	if err := yaml.Unmarshal([]byte(template), &document); err != nil {
		return "", false, err
	}

	// An empty template is no document at all.
	if document.Kind == 0 {
		document = yaml.Node{Kind: yaml.DocumentNode, Content: []*yaml.Node{{Kind: yaml.MappingNode}}}
	}

	root := document.Content[0]
	if root.Kind != yaml.MappingNode {
		return "", false, errors.New("шаблон не является набором ключей")
	}

	tidy(root)

	list := sequence(root, "proxies")

	for _, proxy := range proxies {
		node, err := proxyNode(proxy)
		if err != nil {
			return "", false, err
		}

		list.Content = append(list.Content, node)
	}

	if directHost != "" {
		withDirectHost(sequence(root, "rules"), directHost)
	}

	var out bytes.Buffer

	encoder := yaml.NewEncoder(&out)
	// As the template is written.
	encoder.SetIndent(2)

	if err := encoder.Encode(&document); err != nil {
		return "", false, err
	}

	if err := encoder.Close(); err != nil {
		return "", false, err
	}

	return out.String(), fromConfig, nil
}

// sequence returns the list under a top-level key. A key the template lacks is added at
// the end; a value that is not a list becomes an empty one.
func sequence(root *yaml.Node, key string) *yaml.Node {
	for i := 0; i+1 < len(root.Content); i += 2 {
		if root.Content[i].Value != key {
			continue
		}

		value := root.Content[i+1]
		if value.Kind != yaml.SequenceNode {
			*value = yaml.Node{Kind: yaml.SequenceNode}
		}

		// `proxies: []` would otherwise put every node on that one line.
		value.Style &^= yaml.FlowStyle

		return value
	}

	name, value := &yaml.Node{}, &yaml.Node{Kind: yaml.SequenceNode}
	name.SetString(key)

	root.Content = append(root.Content, name, value)

	return value
}

// tidy removes what has no place in a person's config: the template's comments, written for
// whoever maintains it, and the tag the encoder would put before every merge — `!!merge <<:`.
func tidy(node *yaml.Node) {
	node.HeadComment, node.LineComment, node.FootComment = "", "", ""

	if node.Tag == "!!merge" {
		node.Tag = ""
	}

	for _, child := range node.Content {
		tidy(child)
	}
}

// proxyNode writes a node with the keys a person looks for first. The converter hands it
// over as a map, which would come out sorted by key: `alpn` at the top, `name` in the middle.
func proxyNode(proxy map[string]any) (*yaml.Node, error) {
	node := &yaml.Node{}
	if err := node.Encode(proxy); err != nil {
		return nil, err
	}

	pairs := make([][2]*yaml.Node, 0, len(node.Content)/2)
	for i := 0; i+1 < len(node.Content); i += 2 {
		pairs = append(pairs, [2]*yaml.Node{node.Content[i], node.Content[i+1]})
	}

	sort.SliceStable(pairs, func(a, b int) bool {
		return proxyKeyRank(pairs[a][0].Value) < proxyKeyRank(pairs[b][0].Value)
	})

	node.Content = node.Content[:0]
	for _, pair := range pairs {
		node.Content = append(node.Content, pair[0], pair[1])
	}

	return node, nil
}

func proxyKeyRank(key string) int {
	switch key {
	case "name":
		return 0
	case "type":
		return 1
	case "server":
		return 2
	case "port":
		return 3
	}

	return 4
}
