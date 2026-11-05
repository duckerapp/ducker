package tunnel

import (
	"sync"

	"cfa/native/app"

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
}

// The owner of a connection the core did not look up: asked of Android once per connection, the answer kept while the
// connection lives. The core finds an app only for a rule that names one (find-process-mode strict).
var (
	ownersLock sync.Mutex
	owners     = map[string]int{}
)

// QueryConnections lists the core's open connections, in no particular order.
func QueryConnections() []Connection {
	list := []Connection{}

	ownersLock.Lock()
	defer ownersLock.Unlock()

	alive := make(map[string]int, len(owners))

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

		return true
	})

	owners = alive

	return list
}
