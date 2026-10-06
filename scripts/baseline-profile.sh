#!/usr/bin/env bash
# Пересобирает baseline profile на одном эмуляторе.
#
#   ./scripts/baseline-profile.sh emulator-5556
#
# Штатная задача `generateMetaReleaseBaselineProfile` ставит сборку на все подключённые
# устройства сразу и стирает на них данные приложения (F-86). Поэтому тест запускаем сами,
# только на названном эмуляторе, а плагину оставляем слияние. Телефоны можно не отсоединять.
#
# На эмуляторе данные ducker тоже стираются: сохрани снимок до запуска и верни после
# (docs/DEV-SETUP.md, «Baseline profile»).
set -euo pipefail

cd "$(dirname "$0")/.."

SERIAL=${1:-}
TEST=com.github.kr328.clash.baselineprofile
MEDIA=/sdcard/Android/media/$TEST
INPUT=baselineprofile/build/intermediates/baselineprofiles/meta/nonMinifiedRelease
# Обе задачи работают с устройствами: первая ставит и запускает, вторая разбирает её вывод.
SKIP=(-x :baselineprofile:connectedMetaNonMinifiedReleaseAndroidTest -x :baselineprofile:collectMetaNonMinifiedReleaseBaselineProfile)

case "$SERIAL" in
  emulator-*) ;;
  *) echo "Укажи эмулятор: ./scripts/baseline-profile.sh emulator-5556 (список — adb devices)"; exit 1 ;;
esac

emu() { adb -s "$SERIAL" "$@"; }

if ./gradlew :app:generateMetaReleaseBaselineProfile "${SKIP[@]}" --dry-run | grep -iE "connected|install|collect"; then
  echo "В плане сборки остались задачи, которые ходят на устройства. Останавливаюсь."
  exit 1
fi

echo "Собираю приложение и тест…"
./gradlew -q :app:assembleMetaNonMinifiedRelease :baselineprofile:assembleMetaNonMinifiedRelease

ABI=$(emu shell getprop ro.product.cpu.abi | tr -d '\r')
emu install -r "$(ls -t app/build/outputs/apk/meta/nonMinifiedRelease/*-"$ABI"-nonMinifiedRelease.apk | head -1)"
emu install -r baselineprofile/build/outputs/apk/meta/nonMinifiedRelease/baselineprofile-meta-nonMinifiedRelease.apk
emu shell rm -rf "$MEDIA"

echo "Снимаю профиль (несколько проходов, минут пять)…"
if ! emu shell am instrument -w -e class "$TEST.StartupProfile" -e androidx.benchmark.enabledRules baselineprofile \
    "$TEST/androidx.test.runner.AndroidJUnitRunner" | tee /dev/stderr | grep -q "^OK ("; then
  echo "Сценарий не прошёл: в сообщении выше — чего он не нашёл и что было на экране."
  exit 1
fi

rm -rf baselineprofile/build/intermediates/baselineprofiles
mkdir -p "$INPUT"
emu pull "$MEDIA/$(emu shell ls "$MEDIA" | tr -d '\r' | grep -E -- '-startup-prof-.+\.txt$' | tail -1)" "$INPUT/"

./gradlew :app:generateMetaReleaseBaselineProfile "${SKIP[@]}"

emu uninstall "$TEST" >/dev/null
echo "Готово: $(wc -l < app/src/metaRelease/generated/baselineProfiles/baseline-prof.txt) правил. Проверь git diff --stat и собери релиз."
