package config

import (
	"encoding/json"
	"net/http"
	U "net/url"
	"strings"
	"sync"
)

func sensitiveSubscriptionHeader(name string) bool {
	name = strings.ToLower(name)
	return strings.HasPrefix(name, "x-hwid") || strings.HasPrefix(name, "x-device") || name == "x-ver-os"
}

// Данные устройства уходят только по https (аудит 2.11). Списка «чужих» хостингов нет: панель
// провайдера может стоять где угодно, за адрес подписки отвечает он (D-192). То же правило до
// сбора данных на стороне Android — SubscriptionDestination.kt.
func deviceHeadersAllowed(address string) bool {
	url, err := U.Parse(address)
	return err == nil && strings.EqualFold(url.Scheme, "https") && url.Hostname() != ""
}

// ducker: заголовки запроса подписки задаёт Kotlin — там известны идентификатор
// устройства, версия приложения и версия ядра, и там же живёт выключатель отправки
// идентификатора (D-36). Отдельный вызов вместо новых параметров `fetchAndValid`:
// сигнатура JNI остаётся прежней, образец — setAgeSecretKey (FACTS.md, F-21).
var (
	requestHeadersMu sync.RWMutex
	requestHeaders   = map[string]string{}
)

// SetRequestHeaders заменяет набор заголовков, добавляемых к запросу подписки.
// Пустой json очищает набор.
func SetRequestHeaders(jsonHeaders string) error {
	headers := map[string]string{}

	if strings.TrimSpace(jsonHeaders) != "" {
		if err := json.Unmarshal([]byte(jsonHeaders), &headers); err != nil {
			return err
		}
	}

	requestHeadersMu.Lock()
	defer requestHeadersMu.Unlock()

	requestHeaders = headers

	return nil
}

// buildRequestHeader собирает заголовки запроса подписки. Провайдеры правил и узлов их не
// получают: они качаются с чужих адресов (jsdelivr, github), и отправлять туда идентификатор
// устройства нельзя — правило 3 в docs/ARCHITECTURE.md; их заголовки — providerHeader.
func buildRequestHeader(address, fallbackUA string) http.Header {
	header := http.Header{}

	requestHeadersMu.RLock()
	defer requestHeadersMu.RUnlock()

	for k, v := range requestHeaders {
		if v = cleanRequestValue(v); v == "" {
			continue
		}
		if !sensitiveSubscriptionHeader(k) || deviceHeadersAllowed(address) {
			header.Set(k, v)
		}
	}

	// Запасной User-Agent на случай, если Kotlin ничего не прислал. Формат важен:
	// панель выбирает формат ответа по User-Agent, и незнакомый получает base64-список
	// ссылок вместо конфига (FACTS.md, F-42; D-38).
	if header.Get("User-Agent") == "" {
		header.Set("User-Agent", fallbackUA)
	}

	return header
}

// cleanRequestValue drops control characters: one of them in a phone's model name made the HTTP client
// refuse the whole request, and the subscription did not download at all.
func cleanRequestValue(value string) string {
	return strings.TrimSpace(strings.Map(func(r rune) rune {
		if r < 0x20 || r == 0x7f {
			return -1
		}
		return r
	}, value))
}

// providerHeader is what the core itself sends for a provider's file (`resource.HTTPVehicle`): the provider's own
// `header:`, and the config's `global-ua` (the core's default `clash.meta/<version>`) unless that header names a
// User-Agent. The first download of a file is the core's later updates of it: the provider decides both (invariant 1).
func providerHeader(provider map[string]any, globalUA string) http.Header {
	header := http.Header{}

	own, _ := provider["header"].(map[string]any)
	for name, values := range own {
		switch v := values.(type) {
		case string:
			header.Add(name, v)
		case []any:
			for _, value := range v {
				if s, ok := value.(string); ok {
					header.Add(name, s)
				}
			}
		}
	}

	if header.Get("User-Agent") == "" && globalUA != "" {
		header.Set("User-Agent", globalUA)
	}

	return header
}
