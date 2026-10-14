package delegate

import (
	"errors"
	"fmt"
	"strings"
	"syscall"

	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/log"

	"cfa/native/app"
	"cfa/native/platform"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/constant"
)

var errBlocked = errors.New("blocked")

func Init(home, versionName, gitVersion string, platformVersion int) {
	log.Infoln("Init core, home: %s, versionName: %s, gitVersion: %s, platformVersion: %d", home, versionName, gitVersion, platformVersion)
	constant.SetHomeDir(home)
	// Each write of the core's cache file waits 10 ms for others to batch with. A new fake-ip name
	// makes two under the pool's lock, with nobody to batch with: 26 ms a name on a phone instead
	// of 2, and every answer and connection waits behind it (F-288, D-303). Set before any write.
	if db := cachefile.Cache().DB; db != nil {
		db.MaxBatchDelay = 0
	}
	// gitVersion = ${CURRENT_BRANCH}_${COMMIT_HASH}_${COMPILE_TIME}
	if versions := strings.Split(gitVersion, "_"); len(versions) == 3 {
		constant.Version = fmt.Sprintf("%s-%s-CMFA-%s", strings.ToLower(versions[0]), versions[1], strings.ToLower(versionName))
		constant.BuildTime = versions[2]
	} else {
		constant.Version = gitVersion
	}
	constant.Version = strings.ToLower(constant.Version)
	app.ApplyVersionName(versionName)
	app.ApplyPlatformVersion(platformVersion)

	process.DefaultPackageNameResolver = func(metadata *constant.Metadata) (string, error) {
		src, dst := metadata.RawSrcAddr, metadata.RawDstAddr

		if src == nil || dst == nil {
			return "", process.ErrInvalidNetwork
		}

		uid := app.QuerySocketUid(metadata.RawSrcAddr, metadata.RawDstAddr)
		pkg := app.QueryAppByUid(uid)

		log.Debugln("[PKG] %s --> %s by %d[%s]", metadata.SourceAddress(), metadata.RemoteAddress(), uid, pkg)

		return pkg, nil
	}

	dialer.DefaultSocketHook = func(network, address string, conn syscall.RawConn) error {
		if platform.ShouldBlockConnection() {
			return errBlocked
		}

		var protectErr error
		if err := conn.Control(func(fd uintptr) {
			protectErr = app.MarkSocket(int(fd))
		}); err != nil {
			return err
		}
		return protectErr
	}
}
