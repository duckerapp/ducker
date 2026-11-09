package config

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	U "net/url"
	"os"
	P "path"
	"runtime"
	"slices"
	"strings"
	"unicode/utf8"

	"cfa/native/app"

	metaHttp "github.com/metacubex/http"
	"github.com/metacubex/mihomo/adapter/provider"
	"github.com/metacubex/mihomo/component/dialer"
	clashHttp "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/listener/inner"
	RB "github.com/metacubex/mihomo/rules/bundle"
)

type Status struct {
	Action      string            `json:"action"`
	Args        []string          `json:"args"`
	Progress    int               `json:"progress"`
	MaxProgress int               `json:"max"`
	Headers     map[string]string `json:"headers,omitempty"`
	SubUpload   *int64            `json:"subUpload,omitempty"`
	SubDownload *int64            `json:"subDownload,omitempty"`
	SubTotal    *int64            `json:"subTotal,omitempty"`
	SubExpire   *int64            `json:"subExpire,omitempty"`
	Tries       []tryReport       `json:"tries,omitempty"`
}

type fetchHeader struct {
	SubscriptionUserInfo string
	// All — заголовки ответа, которые нужны интерфейсу: название профиля, объявление,
	// ссылки провайдера, состояние лимита устройств, брендинг. Список закрытый и лежит
	// в одном месте (interestingHeaders), чтобы добавить новый стоило одной строки.
	All map[string]string
}

// Заголовки ответа подписки, которые поднимаются в Kotlin. Кроме перечисленных берутся
// все с префиксом `ducker-` (D-35). Значения могут быть в виде `base64:<…>` — раскодирует
// Kotlin, ядру они нужны как есть.
var interestingHeaders = []string{
	"profile-title",
	"profile-update-interval",
	"profile-web-page-url",
	"support-url",
	"announce",
	"subscription-userinfo",
	"subscription-refill-date",
	"x-hwid-active",
	"x-hwid-max-devices-reached",
	"x-hwid-not-supported",
	"x-hwid-limit",
	// Happ's names for a backup link and a move of the subscription (D-345, F-329).
	"fallback-url",
	"new-url",
	"new-domain",
}

const duckerHeaderPrefix = "ducker-"

// Принимает сырую карту, а не http.Header: ядро отвечает типом из своего форка
// net/http (github.com/metacubex/http), и обе версии Header — это map[string][]string.
func collectHeaders(raw map[string][]string) map[string]string {
	lower := make(map[string]string, len(raw))

	for name, values := range raw {
		if len(values) > 0 {
			name = strings.ToLower(name)
			if value, ok := headerValue(name, values[0]); ok {
				lower[name] = value
			}
		}
	}

	result := map[string]string{}

	for name, value := range lower {
		if passedUp(name) {
			result[name] = value
		}
	}

	return result
}

func passedUp(name string) bool {
	return strings.HasPrefix(name, duckerHeaderPrefix) || slices.Contains(interestingHeaders, name)
}

// A value longer than these counts as not sent: the provider's texts and links fit in a few hundred characters,
// and a huge one would only travel through the app's processes to its screens. The logo may be a `data:` picture.
const (
	headerValueMax = 16 * 1024
	logoValueMax   = 1024 * 1024
)

// headerValue is a response header's value as it goes up, or false when it counts as not sent: empty or too long.
// Bytes that are not UTF-8 are read as Latin-1 when each stands alone between ASCII ones — `é` in «Café», as Node and
// Python servers send a Latin-1 character. Runs of them are left as they are: that is cp1251 or another encoding's
// text, which the app counts as broken (D-387) rather than show it as «Ïðîâàéäåð».
func headerValue(name, value string) (string, bool) {
	limit := headerValueMax
	if name == "ducker-brand-logo" {
		limit = logoValueMax
	}
	if value == "" || len(value) > limit {
		return "", false
	}
	if utf8.ValidString(value) {
		return value, true
	}
	for i := 1; i < len(value); i++ {
		if value[i-1] >= 0x80 && value[i] >= 0x80 {
			return value, true
		}
	}
	runes := make([]rune, len(value))
	for i := 0; i < len(value); i++ {
		runes[i] = rune(value[i])
	}
	return string(runes), true
}

// PasarGuard refuses a device with a bare HTTP 403 and its reason in the JSON body
// (`app/operation/subscription.py:432`–`447`); the other panels say it in `x-hwid-*` headers. Its reasons become those
// headers, so the person sees the device-limit screen rather than «Доступ закрыт».
func deviceRefusal(code int, body io.Reader, header map[string]string) {
	if code != http.StatusForbidden {
		return
	}
	var answer struct {
		Detail string `json:"detail"`
	}
	if json.NewDecoder(io.LimitReader(body, 4096)).Decode(&answer) != nil {
		return
	}
	switch answer.Detail {
	case "Device limit reached":
		header["x-hwid-max-devices-reached"] = "true"
	case "HWID header required":
		header["x-hwid-not-supported"] = "true"
	}
}

// httpStatusError is the server's own answer with a status other than 2xx. Its text starts with
// «HTTP <code>»: the service picks the explanation by it (F-155).
type httpStatusError struct{ code int }

func (e *httpStatusError) Error() string {
	return fmt.Sprintf("HTTP %d %s", e.code, http.StatusText(e.code))
}

// openUrl fetches a subscription by route r (subscription_mirrors.go); providers take only r.direct and send
// their own provider headers (providerHeader).
func openUrl(ctx context.Context, url string, subscription bool, r route, provider http.Header) (io.ReadCloser, fetchHeader, error) {
	// Ядро шлёт свои запросы по правилам загруженного конфига. При выключенном VPN там
	// лежит конфиг для замеров (D-63), и подписка качалась через его узел: мёртвый узел —
	// и новую подписку не добавить («first record does not look like a TLS handshake»).
	// VPN выключен — значит, напрямую, как было до D-63 (D-100).
	var options []clashHttp.Option
	if !app.TunRunning() || r.direct {
		options = append(options, clashHttp.WithDialer(dialer.NewDialer()))
	}

	headers := provider
	if subscription {
		headers = buildRequestHeader(url, "mihomo ducker/"+app.VersionName()+" (android)")
	}
	var response *metaHttp.Response
	var err error
	if subscription {
		direct := r.direct || (r.proxy == "" && !app.TunRunning())
		directDialer := dialer.NewDialer()
		response, err = subscriptionHttpRequest(ctx, url, headers, func(ctx context.Context, network, address string) (net.Conn, error) {
			if direct {
				return directDialer.DialContext(ctx, network, address)
			}
			if r.proxy != "" {
				return dialThroughProxy(ctx, address, r.proxy)
			}
			if conn, err := inner.HandleTcp(inner.GetTunnel(), address, ""); err == nil {
				return conn, nil
			}
			return dialer.DialContext(ctx, network, address)
		})
	} else {
		response, err = clashHttp.HttpRequest(ctx, url, http.MethodGet, headers, nil, options...)
	}

	if err != nil {
		return nil, fetchHeader{}, err
	}

	header := fetchHeader{
		SubscriptionUserInfo: response.Header.Get("subscription-userinfo"),
		All:                  collectHeaders(response.Header),
	}

	// ducker: страница ошибки — не конфиг (F-155). Без проверки тело 503 или 404 уходило
	// в разбор YAML, и человек видел «cannot unmarshal» вместо ответа сервера. Текст
	// начинается с «HTTP <код>»: по нему служба подбирает объяснение на языке приложения.
	// Заголовки отдаём и с ошибкой — ими панель может объяснить отказ (лимит устройств).
	if response.StatusCode < 200 || response.StatusCode > 299 {
		if subscription {
			deviceRefusal(response.StatusCode, response.Body, header.All)
		}
		_ = response.Body.Close()

		return nil, header, &httpStatusError{response.StatusCode}
	}

	return response.Body, header, nil
}

func openContent(url string) (io.ReadCloser, error) {
	return app.OpenContent(url)
}

func fetch(url *U.URL, file string, subscription bool, provider http.Header) (fetchHeader, error) {
	if subscription && (url.Scheme == "http" || url.Scheme == "https") {
		a := fetchSubscriptionAttempt(url.String(), route{}, file, 0)
		return a.header, a.err
	}

	ctx, cancel := context.WithTimeout(context.Background(), fetchTimeout)
	defer cancel()

	var reader io.ReadCloser
	var header fetchHeader
	var err error

	switch url.Scheme {
	case "http", "https":
		reader, header, err = openUrl(ctx, url.String(), subscription, route{}, provider)
	case "content":
		reader, err = openContent(url.String())
	default:
		err = fmt.Errorf("unsupported scheme %s of %s", url.Scheme, url)
	}

	if err != nil {
		if subscription {
			return header, &downloadError{"profile", profileDownloadLimit, err}
		}
		return header, err
	}

	return header, writeDownload(file, reader, subscription)
}

// reportHeaders отправляет наверх заголовки ответа подписки. Вызывается сразу после
// загрузки, до проверки конфига: иначе при отказе панели (лимит устройств) причина
// не доедет до интерфейса и придётся гадать по тексту ошибки (FACTS.md, F-19).
func reportHeaders(header fetchHeader, reportStatus func(string)) {
	if len(header.All) == 0 {
		return
	}

	bytes, _ := json.Marshal(&Status{
		Action:      "SubscriptionHeaders",
		Args:        []string{},
		Progress:    -1,
		MaxProgress: -1,
		Headers:     header.All,
	})

	reportStatus(string(bytes))
}

func reportSubscriptionInfo(header fetchHeader, reportStatus func(string)) {
	// The provider's update hours travel with the other headers (reportHeaders, D-311).
	userinfo := header.SubscriptionUserInfo
	if userinfo == "" {
		return
	}

	status := Status{
		Action:      "SubscriptionInfo",
		Args:        []string{},
		Progress:    -1,
		MaxProgress: -1,
	}

	info := provider.NewSubscriptionInfo(userinfo)
	expire := info.Expire * 1000
	status.SubUpload = &info.Upload
	status.SubDownload = &info.Download
	status.SubTotal = &info.Total
	status.SubExpire = &expire

	bytes, _ := json.Marshal(&status)
	reportStatus(string(bytes))
}

func FetchAndValid(
	path string,
	url string,
	force bool,
	reportStatus func(string),
) error {
	configPath := P.Join(path, "config.yaml")

	if _, err := os.Stat(configPath); os.IsNotExist(err) || force {
		url, err := U.Parse(url)
		if err != nil {
			return err
		}

		var header fetchHeader
		if url.Scheme == "http" || url.Scheme == "https" {
			header, err = fetchSubscription(url.String(), configPath, reportStatus)
		} else {
			reportFetching(url.Host, false, reportStatus)
			header, err = fetch(url, configPath, true, nil)
		}

		// Заголовки — и при отказе сервера (F-19, F-155): причину отказа панель пишет в них.
		reportHeaders(header, reportStatus)

		if err != nil {
			return err
		}

		reportSubscriptionInfo(header, reportStatus)
	}

	defer runtime.GC()

	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return err
	}

	var providerFailure error
	forEachProviders(rawCfg, func(index int, total int, name string, provider map[string]any, prefix string) {
		if providerFailure != nil {
			return
		}
		bytes, _ := json.Marshal(&Status{
			Action:      "FetchProviders",
			Args:        []string{name},
			Progress:    index,
			MaxProgress: total,
		})

		reportStatus(string(bytes))

		u, uok := provider["url"]
		p, pok := provider["path"]

		if !uok || !pok {
			return
		}

		us, uok := u.(string)
		ps, pok := p.(string)

		if !uok || !pok {
			return
		}

		if _, err := os.Stat(ps); err == nil {
			return
		}

		url, err := U.Parse(us)
		if err != nil {
			return
		}

		if prefix == RULES {
			if pib, uok := provider["path-in-bundle"]; uok {
				if pib, uok := pib.(string); uok && pib != "" {
					// actually, we don't need to extract the file here; the core will do it.
					// however, due to historical reasons, CMFA fetches provider content when loading profile,
					// so we maintain consistency with the old behavior.
					if file, err := RB.Open(pib); err == nil {
						if writeDownload(ps, file, false) == nil {
							return
						}
					}
				}
			}
		}

		// ducker: набор правил с `proxy:` заранее не качаем (D-229). Здесь загрузка идёт по одному
		// и мимо этого прокси — напрямую, если VPN выключен, — а конфиг просит через него. Ядро
		// скачает такие наборы само при первой загрузке профиля: все разом и через указанный узел.
		if prefix == RULES {
			if via, _ := provider["proxy"].(string); via != "" && !strings.EqualFold(via, "DIRECT") {
				return
			}
		}

		// ducker: набор не скачался — пропускаем, ядро докачает его при подключении, как до
		// правок аудита: на медленной сети с 26 наборами шаблона обрыв одного — частый случай.
		// Срываем добавление подписки только при превышении размера: такой файл не скачается
		// и потом. Недокачанного файла не остаётся — writeDownload пишет во временный (D-206).
		if _, err = fetch(url, ps, false, providerHeader(provider, rawCfg.GlobalUA)); errors.Is(err, errDownloadTooLarge) {
			providerFailure = err
		}
	})
	if providerFailure != nil {
		return providerFailure
	}

	bytes, _ := json.Marshal(&Status{
		Action:      "Verifying",
		Args:        []string{},
		Progress:    0xffff,
		MaxProgress: 0xffff,
	})

	reportStatus(string(bytes))

	cfg, err := validate(rawCfg)
	if err != nil {
		return err
	}

	destroyProviders(cfg)

	return nil
}
