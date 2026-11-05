package tunnel

import (
	"sync"
	"time"

	"cfa/native/app"
	"cfa/native/panicguard"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// Connection is one row of «Подключения» (stage 15): who opened it, where to, by which rule and node, how much went.
type Connection struct {
	ID          string   `json:"id"`
	Host        string   `json:"host"`
	Address     string   `json:"address"`
	Port        uint16   `json:"port"`
	Network     string   `json:"network"`
	Uid         int      `json:"uid"`
	Process     string   `json:"process"`
	Inner       bool     `json:"inner"`
	Rule        string   `json:"rule"`
	RulePayload string   `json:"rulePayload"`
	Chain       []string `json:"chain"`
	Upload      int64    `json:"upload"`
	Download    int64    `json:"download"`
	Start       int64    `json:"start"`
	Closed      bool     `json:"closed"`
}

// The owner of a connection the core did not look up: asked of Android once per connection, the answer kept while the
// connection lives. The core finds an app only for a rule that names one (find-process-mode strict).
//
// A watch goes on looking once a second for a while after the page last asked, and keeps what closed: the person opens
// an app to see where it connects and comes back, and its short connections were gone by then — the app does not ask
// while it is hidden. Android names a socket's owner only while it is open, so the watch asks then, as the page does.
var (
	lock     sync.Mutex
	owners   = map[string]int{}
	open     = map[string]Connection{}
	closed   []Connection
	until    time.Time
	watching bool
)

const (
	watchFor   = 5 * time.Minute
	keepClosed = 300
)

// QueryConnections lists the core's open connections, in no particular order, then those closed since the last call.
func QueryConnections() []Connection {
	lock.Lock()
	defer lock.Unlock()

	list := append(look(), closed...)
	closed = nil
	until = time.Now().Add(watchFor)

	if !watching {
		watching = true
		go watch()
	}

	return list
}

func watch() {
	defer panicguard.Recover("watchConnections", nil)

	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()

	for range ticker.C {
		if !watchOnce() {
			return
		}
	}
}

// watchOnce looks once, or forgets it all when the page has not asked for watchFor.
func watchOnce() bool {
	lock.Lock()
	defer lock.Unlock()

	if time.Now().After(until) {
		watching = false
		owners, open, closed = map[string]int{}, map[string]Connection{}, nil
		return false
	}

	look()
	return true
}

// look lists the open connections and moves those gone since the last look to closed. Called under lock.
func look() []Connection {
	list := []Connection{}
	alive := make(map[string]int, len(owners))
	now := make(map[string]Connection, len(open))

	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		info := c.Info()
		meta := info.Metadata
		id := c.ID()

		uid := int(meta.Uid)
		if uid == 0 && meta.Process == "" && meta.Type != C.INNER {
			known, ok := owners[id]
			if !ok && meta.RawSrcAddr != nil && meta.RawDstAddr != nil {
				known = app.QuerySocketUid(meta.RawSrcAddr, meta.RawDstAddr)
			}
			alive[id] = known
			uid = known
		}

		chain := []string(info.Chain)
		if chain == nil {
			chain = []string{}
		}

		address := ""
		if meta.DstIP.IsValid() {
			address = meta.DstIP.String()
		}

		list = append(list, Connection{
			ID:          id,
			Host:        meta.Host,
			Address:     address,
			Port:        meta.DstPort,
			Network:     meta.NetWork.String(),
			Uid:         uid,
			Process:     meta.Process,
			Inner:       meta.Type == C.INNER,
			Rule:        info.Rule,
			RulePayload: info.RulePayload,
			Chain:       chain,
			Upload:      info.UploadTotal.Load(),
			Download:    info.DownloadTotal.Load(),
			Start:       info.Start.UnixMilli(),
		})
		now[id] = list[len(list)-1]

		return true
	})

	owners = alive

	for id, c := range open {
		if _, ok := now[id]; !ok {
			c.Closed = true
			closed = append(closed, c)
		}
	}
	if len(closed) > keepClosed {
		closed = closed[len(closed)-keepClosed:]
	}
	open = now

	return list
}
