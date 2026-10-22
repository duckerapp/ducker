package config

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	U "net/url"
	"strings"
	"sync"
	"time"

	"cfa/native/app"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/listener/inner"
	"github.com/metacubex/mihomo/log"
	T "github.com/metacubex/mihomo/tunnel"
)

// Subscription mirrors (D-345, stage 21 of PLAN.md). A subscription is tried at several addresses
// in turn: the one that answered last time, its own link, the provider's mirrors. Kotlin builds the
// order from the subscription's provider.json and sets it before each download, the way it sets the
// request headers (F-21). When every address fails without the server answering, the first one is
// tried the other way round as a last resort (Q-81): directly, if the usual way is the VPN's rules,
// and through the subscription's own node.

const fetchTimeout = 60 * time.Second

// subscriptionAnswerTimeout bounds the wait for an address's response headers while another try is
// still left: a blocked domain often swallows the connection without a word. The last try waits the
// whole fetchTimeout, so a slow but working panel is not cut off. Happ waits 9 s (F-329).
var subscriptionAnswerTimeout = 10 * time.Second

var errWebPage = errors.New("a web page instead of a subscription")

type mirrorPlan struct {
	Source string   `json:"source"`
	Order  []string `json:"order"`
}

var (
	mirrorPlanMu sync.RWMutex
	currentPlan  mirrorPlan
)

// SetSubscriptionMirrors sets the addresses to try, in order, for the subscription whose own link is
// `source`. An empty string clears them.
func SetSubscriptionMirrors(jsonPlan string) error {
	plan := mirrorPlan{}

	if strings.TrimSpace(jsonPlan) != "" {
		if err := json.Unmarshal([]byte(jsonPlan), &plan); err != nil {
			return err
		}
	}

	mirrorPlanMu.Lock()
	defer mirrorPlanMu.Unlock()

	currentPlan = plan

	return nil
}

// subscriptionAddresses is the order set for source, ending with source itself if the order lacks it.
// An order set for another link is not used: its mirrors belong to another subscription, and a mirror
// given as a bare host would carry this one's token there.
func subscriptionAddresses(source string) []string {
	mirrorPlanMu.RLock()
	defer mirrorPlanMu.RUnlock()

	var addresses []string
	seen := map[string]bool{}
	add := func(address string) {
		if address != "" && !seen[address] {
			seen[address] = true
			addresses = append(addresses, address)
		}
	}

	if currentPlan.Source == source {
		for _, address := range currentPlan.Order {
			add(address)
		}
	}
	add(source)

	return addresses
}

// route is how an address is reached. The zero value is the usual way: directly with the VPN off, by
// the profile's rules with it on (D-100).
type route struct {
	direct bool
	// proxy names the group of the subscription's own node.
	proxy string
}

// name is what the service stores and shows: "", "direct" or "proxy".
func (r route) name() string {
	switch {
	case r.direct:
		return "direct"
	case r.proxy != "":
		return "proxy"
	}
	return ""
}

// dialThroughProxy opens a connection through the named proxy or group of the loaded profile.
// A variable so that tests can stand in for the core's tunnel.
var dialThroughProxy = func(ctx context.Context, address, proxy string) (net.Conn, error) {
	return inner.HandleTcp(inner.GetTunnel(), address, proxy)
}

type subscriptionAttempt struct {
	address string
	route   route
	header  fetchHeader
	err     error
	// status is the server's own answer other than 2xx; webPage — it answered with HTML; waited —
	// how long it was given when it did not answer in time.
	status  int
	webPage bool
	waited  time.Duration
}

// tryReport is one try as the person sees it in the subscription's list of addresses (Q-82).
// Outcome: ok, connection, timeout (Code — the seconds waited), status (Code — the HTTP status),
// webpage, failed, untried.
type tryReport struct {
	Address string `json:"address"`
	Route   string `json:"route"`
	Outcome string `json:"outcome"`
	Code    int    `json:"code,omitempty"`
}

func (a subscriptionAttempt) report() tryReport {
	t := tryReport{Address: a.address, Route: a.route.name()}
	switch {
	case a.err == nil:
		t.Outcome = "ok"
	case a.status != 0:
		t.Outcome, t.Code = "status", a.status
	case a.webPage:
		t.Outcome = "webpage"
	case a.waited != 0:
		t.Outcome, t.Code = "timeout", int((a.waited+time.Second-1)/time.Second)
	case errors.Is(a.err, errDownloadTooLarge):
		t.Outcome = "failed"
	default:
		t.Outcome = "connection"
	}
	return t
}

// fetchSubscriptionAttempt downloads address into file by route r. A non-zero answerTimeout bounds the
// wait for the response headers; the body then has the rest of fetchTimeout.
func fetchSubscriptionAttempt(address string, r route, file string, answerTimeout time.Duration) subscriptionAttempt {
	a := subscriptionAttempt{address: address, route: r}

	ctx, cancel := context.WithTimeout(context.Background(), fetchTimeout)
	defer cancel()

	answered := func() bool { return true }
	if answerTimeout > 0 {
		answered = time.AfterFunc(answerTimeout, cancel).Stop
	}

	reader, header, err := openUrl(ctx, address, true, r)
	if !answered() {
		if reader != nil {
			_ = reader.Close()
		}
		a.waited = answerTimeout
		a.err = &downloadError{"profile", profileDownloadLimit, fmt.Errorf("no answer in %s", answerTimeout)}
		return a
	}

	a.header = header

	if err != nil {
		var status *httpStatusError
		if errors.As(err, &status) {
			a.status = status.code
		} else if errors.Is(err, context.DeadlineExceeded) {
			a.waited = fetchTimeout
		}
		a.err = &downloadError{"profile", profileDownloadLimit, err}
		return a
	}

	body := bufio.NewReader(reader)
	if looksLikeWebPage(body) {
		_ = reader.Close()
		a.webPage = true
		a.err = &downloadError{"profile", profileDownloadLimit, errWebPage}
		return a
	}

	a.err = writeDownload(file, struct {
		io.Reader
		io.Closer
	}{body, reader}, true)

	return a
}

// looksLikeWebPage: the body starts as HTML — a block page of the network, a captive portal, a CDN
// challenge, or a link to a site page instead of the subscription. A subscription never does: YAML,
// JSON, base64 and protocol links all start otherwise.
func looksLikeWebPage(body *bufio.Reader) bool {
	head, _ := body.Peek(512)
	head = bytes.TrimPrefix(head, []byte("\xef\xbb\xbf"))
	head = bytes.TrimLeft(head, " \t\r\n")

	return len(head) > 0 && head[0] == '<'
}

// fetchSubscription downloads the subscription `source` into file, trying its addresses in order.
func fetchSubscription(source, file string, report func(string)) (fetchHeader, error) {
	addresses := subscriptionAddresses(source)
	resort := lastResortRoutes(addresses[0])

	type step struct {
		address string
		route   route
	}
	steps := make([]step, 0, len(addresses)+len(resort))
	for _, address := range addresses {
		steps = append(steps, step{address, route{}})
	}
	for _, r := range resort {
		steps = append(steps, step{addresses[0], r})
	}

	var tried []subscriptionAttempt
	// Every try and every address left untried go to the service, whatever the end (Q-82).
	defer func() { reportTries(addresses, tried, report) }()

	for i, s := range steps {
		// The server answered with a status at one of its addresses: another way to the same
		// server would hear the same, so the last resort is skipped.
		if i >= len(addresses) && anyStatus(tried) {
			break
		}

		timeout := subscriptionAnswerTimeout
		if i == len(steps)-1 {
			timeout = 0
		}

		reportFetching(hostOf(s.address), report)

		a := fetchSubscriptionAttempt(s.address, s.route, file, timeout)
		tried = append(tried, a)

		if a.err == nil {
			reportSource(a, report)
			return a.header, nil
		}

		// About the account or the file itself: every address would say the same.
		if finalAnswer(a) {
			return a.header, a.err
		}

		if i < len(steps)-1 {
			log.Warnln("[Subscription] %s%s: %s; trying the next address", hostOf(s.address), routeLabel(s.route), failureReason(a.err))
		}
	}

	best := mostTelling(tried, source)

	return best.header, withBackups(best.err, len(addresses)-1)
}

// lastResortRoutes are the other ways to reach address when every address failed the usual way
// (Q-81): directly, if the usual way is the VPN's rules, and through the subscription's own node if a
// profile is loaded in the core — always with the VPN on, with it off while the app is open (D-63).
// A variable so that tests can choose the routes.
var lastResortRoutes = func(address string) []route {
	var routes []route

	if app.TunRunning() {
		routes = append(routes, route{direct: true})
	}
	if proxy := subscriptionProxy(address); proxy != "" {
		routes = append(routes, route{proxy: proxy})
	}

	return routes
}

// subscriptionProxy names the group the loaded profile sends unknown traffic to: GLOBAL in Global
// mode, the target of the final MATCH rule otherwise. Empty when no profile is loaded, or when the
// group leads to DIRECT or a reject right now: then the node route would be the direct one again.
func subscriptionProxy(address string) string {
	name := ""

	switch T.Mode() {
	case T.Global:
		name = "GLOBAL"
	case T.Rule:
		rules := T.Rules()
		for i := len(rules) - 1; i >= 0; i-- {
			if rules[i].RuleType() == C.MATCH {
				name = rules[i].Adapter()
				break
			}
		}
	}

	proxy, ok := T.Proxies()[name]
	if !ok {
		return ""
	}

	metadata := &C.Metadata{NetWork: C.TCP, Type: C.INNER}
	if u, err := U.Parse(address); err == nil {
		port := u.Port()
		if port == "" {
			port = map[string]string{"http": "80", "https": "443"}[u.Scheme]
		}
		_ = metadata.SetRemoteAddress(net.JoinHostPort(u.Hostname(), port))
	}

	// A group's choice may be another group; a cycle is cut by the bound.
	for i := 0; i < 16; i++ {
		next := proxy.Unwrap(metadata, false)
		if next == nil {
			break
		}
		proxy = next
	}

	switch proxy.Type() {
	case C.Direct, C.Reject, C.RejectDrop, C.Pass, C.Compatible, C.Dns:
		return ""
	}

	return name
}

func anyStatus(tried []subscriptionAttempt) bool {
	for _, a := range tried {
		if a.status != 0 {
			return true
		}
	}
	return false
}

// finalAnswer: the panel's device-limit answer, or a file over the size limit.
func finalAnswer(a subscriptionAttempt) bool {
	for _, name := range []string{"x-hwid-max-devices-reached", "x-hwid-not-supported"} {
		if strings.EqualFold(strings.TrimSpace(a.header.All[name]), "true") {
			return true
		}
	}

	return errors.Is(a.err, errDownloadTooLarge)
}

// mostTelling picks the failure to show: the server's status says more than a web page, a web page more
// than silence, and the subscription's own link the usual way more than a mirror or a last resort.
func mostTelling(tried []subscriptionAttempt, source string) subscriptionAttempt {
	score := func(a subscriptionAttempt) int {
		s := 0
		switch {
		case a.status != 0:
			s = 4
		case a.webPage:
			s = 2
		}
		if a.address == source && a.route == (route{}) {
			s++
		}
		return s
	}

	best := tried[0]
	for _, a := range tried[1:] {
		if score(a) > score(best) {
			best = a
		}
	}

	return best
}

// backupsError is the failure shown, marked with how many mirrors were tried besides. The service
// reads the count from the text (Failure.kt).
type backupsError struct {
	err error
	n   int
}

func (e *backupsError) Error() string { return fmt.Sprintf("%s (+%d backups)", e.err, e.n) }
func (e *backupsError) Unwrap() error { return e.err }

func withBackups(err error, n int) error {
	if n <= 0 {
		return err
	}
	return &backupsError{err, n}
}

func reportFetching(host string, report func(string)) {
	bytes, _ := json.Marshal(&Status{
		Action:      "FetchConfiguration",
		Args:        []string{host},
		Progress:    -1,
		MaxProgress: -1,
	})

	report(string(bytes))
}

// reportTries lists the tries in their order, then the addresses never reached. It comes before the
// headers.
func reportTries(addresses []string, tried []subscriptionAttempt, report func(string)) {
	reached := map[string]bool{}
	reports := make([]tryReport, 0, len(addresses)+len(tried))
	for _, a := range tried {
		reached[a.address] = true
		reports = append(reports, a.report())
	}
	for _, address := range addresses {
		if !reached[address] {
			reports = append(reports, tryReport{Address: address, Outcome: "untried"})
		}
	}

	bytes, _ := json.Marshal(&Status{
		Action:      "SubscriptionTries",
		Args:        []string{},
		Progress:    -1,
		MaxProgress: -1,
		Tries:       reports,
	})

	report(string(bytes))
}

// reportSource tells the service which address answered and how; it comes before the headers.
func reportSource(a subscriptionAttempt, report func(string)) {
	bytes, _ := json.Marshal(&Status{
		Action:      "SubscriptionSource",
		Args:        []string{a.address, a.route.name()},
		Progress:    -1,
		MaxProgress: -1,
	})

	report(string(bytes))
}

func hostOf(address string) string {
	if u, err := U.Parse(address); err == nil {
		return u.Host
	}
	return ""
}

func routeLabel(r route) string {
	if name := r.name(); name != "" {
		return " (" + name + ")"
	}
	return ""
}

// failureReason is the error without the address: the link's path carries the person's token.
func failureReason(err error) string {
	var u *U.Error
	if errors.As(err, &u) {
		return u.Err.Error()
	}

	var d *downloadError
	if errors.As(err, &d) {
		return d.cause.Error()
	}

	return err.Error()
}
