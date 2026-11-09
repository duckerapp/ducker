package config

import (
	"bufio"
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	U "net/url"
	"os"
	"slices"
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
// request headers (F-21). When every address fails without the server answering, they are tried the
// other way round as a last resort (Q-81): each directly, if the usual way is the VPN's rules, and the
// first one through the subscription's own node.

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

	body := bufio.NewReaderSize(reader, bodyHeadSize)
	if looksLikeWebPage(body) {
		_ = reader.Close()
		a.webPage = true
		a.err = &downloadError{"profile", profileDownloadLimit, errWebPage}
		return a
	}

	// Read before the body is written: Peek's bytes are the reader's own buffer.
	head, _ := body.Peek(bodyHeadSize)
	whole := len(head) < bodyHeadSize
	lines := bodyHeaders(head, whole)

	a.err = writeDownload(file, struct {
		io.Reader
		io.Closer
	}{body, reader}, true)

	if a.err == nil && !whole {
		for name, value := range bodyTailHeaders(file) {
			if _, ok := lines[name]; !ok {
				lines[name] = value
			}
		}
	}

	// A real header wins over the same line.
	if a.err == nil {
		for name, value := range lines {
			if _, sent := a.header.All[name]; !sent {
				a.header.All[name] = value
			}
		}
		if a.header.SubscriptionUserInfo == "" {
			a.header.SubscriptionUserInfo = a.header.All["subscription-userinfo"]
		}
	}

	return a
}

// bodyHeadSize is how much of a subscription's beginning is read for its `#name: value` lines.
const bodyHeadSize = 64 * 1024

// bodyHeaderName: every header passed up may also come as a `#name: value` line, as Happ reads them (F-329): a
// provider on static hosting cannot send headers, and PasarGuard can put all of its own at the start or the end of
// the body (`header_placement`). Not `x-hwid-*`: a device refusal is the panel's answer, not a line in a file.
func bodyHeaderName(name string) bool {
	return passedUp(name) && !strings.HasPrefix(name, "x-hwid-")
}

// bodyHeaders reads those lines from head, the body's beginning, up to the first line that is not a
// `#` one; whole — head is the entire body, and its last lines are read the same way from the end. A
// base64 body is decoded first, as the core decodes a list of links.
func bodyHeaders(head []byte, whole bool) map[string]string {
	text := bytes.TrimPrefix(head, []byte("\xef\xbb\xbf"))
	if decoded, ok := decodeBase64Head(text, whole); ok {
		text = decoded
	}

	lines := strings.Split(string(text), "\n")
	// The last line of a cut head may be cut too.
	if !whole {
		lines = lines[:len(lines)-1]
	}

	result := map[string]string{}
	headerLines(lines, result)
	if whole {
		slices.Reverse(lines)
		headerLines(lines, result)
	}

	return result
}

// bodyTailHeaders reads the lines at the end of a body longer than its head, from the file it was written to.
// ponytail: plain text only — the end of a long base64 body is not decoded; PasarGuard sends ducker YAML (R1).
func bodyTailHeaders(file string) map[string]string {
	result := map[string]string{}
	f, err := os.Open(file)
	if err != nil {
		return result
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		return result
	}
	start := info.Size() - bodyHeadSize
	if start < 0 {
		start = 0
	}
	tail := make([]byte, info.Size()-start)
	if _, err := f.ReadAt(tail, start); err != nil {
		return result
	}
	lines := strings.Split(string(tail), "\n")
	// The first line of a cut tail may be cut too.
	if start > 0 {
		lines = lines[1:]
	}
	slices.Reverse(lines)
	headerLines(lines, result)

	return result
}

// headerLines takes `#name: value` lines up to the first that is not one; the first value of a name is kept.
func headerLines(lines []string, result map[string]string) {
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if !strings.HasPrefix(line, "#") {
			return
		}
		name, raw, ok := strings.Cut(line[1:], ":")
		name = strings.ToLower(strings.TrimSpace(name))
		if _, seen := result[name]; !ok || seen || !bodyHeaderName(name) {
			continue
		}
		if value, ok := headerValue(name, strings.TrimSpace(raw)); ok {
			result[name] = value
		}
	}
}

// decodeBase64Head decodes a base64 body; a cut one up to its last whole group of four characters.
// YAML, JSON and plain lines are not base64: `:`, `{` and `#` are outside its alphabet.
func decodeBase64Head(text []byte, whole bool) ([]byte, bool) {
	compact := bytes.Join(bytes.Fields(text), nil)
	if !whole {
		compact = compact[:len(compact)/4*4]
	}

	decoded, err := base64.RawStdEncoding.DecodeString(strings.TrimRight(string(compact), "="))

	return decoded, err == nil
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

	var tried []subscriptionAttempt
	// Every try and every address left untried go to the service, whatever the end (Q-82).
	defer func() { reportTries(addresses, tried, report) }()

	epoch := app.TunEpoch()
	header, err := tryAddresses(addresses, source, file, report, &tried)
	// The VPN came up or went down meanwhile and no server answered with a status: the download was likely cut by
	// that, and the ways to the server are others now — the whole plan once more, its tries added to the list (D-431).
	if err != nil && app.TunEpoch() != epoch && !anyStatus(tried) {
		log.Warnln("[Subscription] the VPN came up or went down during the download; trying again")
		header, err = tryAddresses(addresses, source, file, report, &tried)
	}

	return header, err
}

// tryAddresses runs the plan once: every address the usual way, then the last resort.
func tryAddresses(addresses []string, source, file string, report func(string), tried *[]subscriptionAttempt) (fetchHeader, error) {
	resort := lastResortRoutes(addresses[0])

	type step struct {
		address string
		route   route
	}
	steps := make([]step, 0, 2*len(addresses)+len(resort))
	for _, address := range addresses {
		steps = append(steps, step{address, route{}})
	}
	for _, r := range resort {
		if !r.direct {
			steps = append(steps, step{addresses[0], r})
			continue
		}
		// Every address directly: with the node dead, the usual way took the mirrors through it too.
		for _, address := range addresses {
			steps = append(steps, step{address, r})
		}
	}

	for i, s := range steps {
		// The server answered with a status at one of its addresses: another way to the same
		// server would hear the same, so the last resort is skipped.
		if i >= len(addresses) && anyStatus(*tried) {
			break
		}

		timeout := subscriptionAnswerTimeout
		if i == len(steps)-1 {
			timeout = 0
		}

		reportFetching(hostOf(s.address), s.address != addresses[0], report)

		a := fetchSubscriptionAttempt(s.address, s.route, file, timeout)
		*tried = append(*tried, a)

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

	best := mostTelling(*tried, source)

	return best.header, withBackups(best.err, len(addresses)-1)
}

// lastResortRoutes are the other ways to reach address when every address failed the usual way
// (Q-81): directly, if the usual way is the VPN's rules, and through the subscription's own node if a
// profile is loaded in the core — always with the VPN on, with it off while the app is open (D-63).
// The direct route is taken for every address, the node's for the first one only.
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

// reportFetching names the host about to be asked. backup — another address of the subscription than the
// first one, which has failed: the sheets say «Пробую запасной адрес…» by the second argument. The last
// resort's try of the first address is not one.
func reportFetching(host string, backup bool, report func(string)) {
	args := []string{host}
	if backup {
		args = append(args, "backup")
	}

	bytes, _ := json.Marshal(&Status{
		Action:      "FetchConfiguration",
		Args:        args,
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
