package tunnel

import (
	"strings"

	"github.com/metacubex/mihomo/tunnel"
)

func QueryMode() string {
	return tunnel.Mode().String()
}

// SetMode переключает режим на лету, как панель mihomo: без перезагрузки конфига,
// которая ради одного поля стоила секунды и перемер всех узлов. Незнакомое имя — false.
func SetMode(name string) bool {
	mode, exist := tunnel.ModeMapping[strings.ToLower(name)]
	if !exist {
		return false
	}

	tunnel.SetMode(mode)

	return true
}
