package config

import (
	"os"
	P "path"
	"runtime"
	"strings"

	"cfa/native/app"

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/log"
)

func logDns(cfg *config.RawConfig) {
	bytes, err := yaml.Marshal(&cfg.DNS)
	if err != nil {
		log.Warnln("Marshal dns: %s", err.Error())

		return
	}

	log.Infoln("dns:")

	for _, line := range strings.Split(string(bytes), "\n") {
		log.Infoln("  %s", line)
	}
}

func UnmarshalAndPatch(profilePath string) (*config.RawConfig, error) {
	configPath := P.Join(profilePath, "config.yaml")

	configData, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}

	rawConfig, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		return nil, err
	}

	if err := process(rawConfig, profilePath); err != nil {
		return nil, err
	}

	return rawConfig, nil
}

func Parse(rawConfig *config.RawConfig) (*config.Config, error) {
	cfg, err := config.ParseRawConfig(rawConfig)
	if err != nil {
		return nil, err
	}

	return cfg, nil
}

// ValidateConfig разбирает конфиг из каталога так же, как загрузка, но ничего не применяет
// и не качает (D-124): ошибка YAML приходит с номером строки, ошибка содержимого — с именем
// узла или группы. Провайдеры не скачиваются — их проверит загрузка.
func ValidateConfig(path string) error {
	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return err
	}

	_, err = validate(rawCfg)

	return err
}

// validate parses a config that will not be applied. Its fake-ip pool is made in memory: a
// pool on the core's cache file works on the table of the running core, and making one
// deleted that table (F-285).
func validate(rawConfig *config.RawConfig) (*config.Config, error) {
	rawConfig.Profile.StoreFakeIP = false

	return Parse(rawConfig)
}

// keepFakeIP prepares the fake-ip table in the cache file for the pool the coming parse makes
// (F-285). The core writes where allocation stands only in executor.Shutdown, which is never
// called here; a pool that finds no such record deletes the table, and one that finds an old
// record hands out addresses that are taken.
//
// A reload: the place of the running pool is written, the new pool goes on from it, and the
// addresses apps hold keep their names. A start with no core running: a clean table — the
// previous pool went away without saying where it stood.
//
// ponytail: a name asked between this call and the moment the new pool takes over, tens of
// milliseconds, may lose its address to the next new name. mihomo#3283 copies the place at
// the takeover itself; it comes with the next core update.
func keepFakeIP() {
	if resolver.DefaultHostMapper != nil {
		resolver.StoreFakePoolState()

		return
	}

	cache := cachefile.Cache()
	if cache.DB == nil {
		return
	}

	_ = cache.FakeIpStore().FlushFakeIP()
	_ = cache.FakeIpStore6().FlushFakeIP()
}

func Load(path string) error {
	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	logDns(rawCfg)

	keepFakeIP()

	cfg, err := Parse(rawCfg)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	// like hub.Parse()
	hub.ApplyConfig(cfg)

	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)

	runtime.GC()

	return nil
}

func LoadDefault() {
	cfg, err := config.Parse([]byte{})
	if err != nil {
		panic(err.Error())
	}

	hub.ApplyConfig(cfg)
}
