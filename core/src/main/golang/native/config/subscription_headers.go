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

// buildRequestHeader собирает заголовки запроса. Заголовки устройства добавляются
// ТОЛЬКО к запросу подписки: через ту же функцию качаются провайдеры правил с чужих
// адресов (jsdelivr, github), и отправлять туда идентификатор устройства нельзя —
// правило 3 в docs/ARCHITECTURE.md.
//
// The User-Agent goes with every download, rule sets too: a provider's own list of nodes is a panel
// link as well, and the panel picks its format by the User-Agent (D-439).
func buildRequestHeader(subscription bool, address, fallbackUA string) http.Header {
	header := http.Header{}

	requestHeadersMu.RLock()
	defer requestHeadersMu.RUnlock()

	for k, v := range requestHeaders {
		if v = cleanRequestValue(v); v == "" {
			continue
		}
		if strings.EqualFold(k, "User-Agent") ||
			subscription && (!sensitiveSubscriptionHeader(k) || deviceHeadersAllowed(address)) {
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
