#!/usr/bin/env bash
# Сборка релиза ducker.
#
#   ./scripts/release.sh 0.2.0
#
# Что делает: поднимает номер версии, собирает подписанные apk, проверяет подпись,
# прогоняет дымовой тест на подключённом телефоне и складывает всё в отдельную папку
# вместе с контрольными суммами и заготовкой описания релиза.
#
# Что НЕ делает: не коммитит, не ставит тег, ничего никуда не выкладывает. Последний
# шаг остаётся за человеком — он и решает, выпускать ли то, что получилось.
set -u

cd "$(dirname "$0")/.."

VERSION=${1:-}
OUT_ROOT=${OUT_ROOT:-$HOME/Документы/ducker-release}

if [ -z "$VERSION" ]; then
  echo "Укажи версию: ./scripts/release.sh 0.2.0"
  exit 1
fi

case "$VERSION" in
  [0-9]*.[0-9]*.[0-9]*) ;;
  *) echo "Версия должна быть вида 0.2.0, а не '$VERSION'"; exit 1 ;;
esac

if [ -n "$(git status --porcelain)" ]; then
  echo "В дереве есть несохранённые правки. Сначала закоммить их — иначе непонятно,"
  echo "что именно собрано. git status:"
  git status --short
  exit 1
fi

if git rev-parse "v$VERSION" >/dev/null 2>&1; then
  echo "Тег v$VERSION уже есть. Возьми следующий номер."
  exit 1
fi

# The APK ships the licences of every Go module in the core; a dependency change needs a new list.
python3 scripts/core-licenses.py --check || exit 1

OLD_VERSION=$(grep -m1 'versionName = ' build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
OLD_CODE=$(grep -m1 'versionCode = ' build.gradle.kts | sed 's/[^0-9]//g')
NEW_CODE=$((OLD_CODE + 1))

echo "Версия: $OLD_VERSION (код $OLD_CODE) → $VERSION (код $NEW_CODE)"

# Профиль из APK снимают отдельно (D-231). Не обязательно к каждому выпуску — но после обновления
# Compose или переделки экранов он нужен свежий.
PROFILE=app/src/metaRelease/generated/baselineProfiles/baseline-prof.txt
echo "Базовый профиль снят $(git log -1 --format=%cs -- "$PROFILE"), коммитов с правками приложения после него:" \
  "$(git rev-list --count "$(git log -1 --format=%H -- "$PROFILE")..HEAD" -- app/src/main)." \
  "Пересобрать — ./scripts/baseline-profile.sh"
echo

sed -i "s/versionName = \"$OLD_VERSION\"/versionName = \"$VERSION\"/" build.gradle.kts
sed -i "s/versionCode = $OLD_CODE/versionCode = $NEW_CODE/" build.gradle.kts

# The build downloads the geo databases only when they are missing, so without this every release
# shipped them as old as the last `clean` on this computer.
rm -f app/src/main/assets/geoip.metadb app/src/main/assets/geosite.dat

echo "Собираю (это надолго)…"
if ! ./gradlew :app:assembleMetaRelease -q >/tmp/ducker-release-build.log 2>&1; then
  echo "Сборка упала, смотри /tmp/ducker-release-build.log. Версию возвращаю обратно."
  git checkout build.gradle.kts
  tail -20 /tmp/ducker-release-build.log
  exit 1
fi
echo "Собралось."

OUT="$OUT_ROOT/v$VERSION"
mkdir -p "$OUT"
rm -f "$OUT"/*.apk "$OUT"/SHA256SUMS "$OUT"/release-notes.md

SIGNER=$(find "$HOME/Android/Sdk/build-tools" -name apksigner 2>/dev/null | sort | tail -1)
BAD=0

for abi in arm64-v8a armeabi-v7a; do
  SRC="app/build/outputs/apk/meta/release/ducker-$VERSION-meta-$abi-release.apk"
  if [ ! -f "$SRC" ]; then
    echo "Нет файла $SRC"
    BAD=1
    continue
  fi

  if [ -n "$SIGNER" ] && ! "$SIGNER" verify --print-certs "$SRC" 2>/dev/null | grep -q "CN=ducker"; then
    echo "ВНИМАНИЕ: $abi подписан не нашим ключом. Проверь signing.properties."
    BAD=1
  fi

  cp "$SRC" "$OUT/ducker-$VERSION-$abi.apk"
done

[ "$BAD" -eq 0 ] || { echo "Собранное не годится для раздачи. Версию возвращаю."; git checkout build.gradle.kts; exit 1; }

( cd "$OUT" && sha256sum ./*.apk > SHA256SUMS )

# Заготовка описания: коммиты с прошлого тега, без служебных.
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)
{
  echo "# ducker $VERSION"
  echo
  if [ -n "$LAST_TAG" ]; then
    echo "Изменения с $LAST_TAG:"
    echo
    git log --pretty='- %s' "$LAST_TAG..HEAD" | grep -v '^- docs:' || true
  else
    echo "Первый релиз."
  fi
  echo
  echo "## Какой файл скачивать"
  echo
  echo "- \`ducker-$VERSION-arm64-v8a.apk\` — любой телефон свежее 2017 года;"
  echo "- \`ducker-$VERSION-armeabi-v7a.apk\` — старый телефон, если первый не ставится."
  echo
  echo "Минимальная версия Android — 10."
} > "$OUT/release-notes.md"

echo
echo "Готово. Файлы в $OUT:"
ls -la "$OUT" | tail -n +4 | awk '{printf "  %s  %.1f МБ\n", $9, $5/1048576}'

echo
if adb get-state >/dev/null 2>&1; then
  echo "Прогоняю дымовой тест на подключённом телефоне…"
  ./scripts/smoke.sh --no-build
  RESULT=$?
else
  echo "Телефон не подключён — дымовой тест пропущен. Раздавать непроверенное нельзя:"
  echo "подключи телефон и запусти ./scripts/smoke.sh --no-build"
  RESULT=1
fi

echo
echo "Осталось сделать руками:"
echo "  git commit -am \"release: $VERSION\""
echo "  git tag v$VERSION"
echo
echo "Описание релиза лежит в $OUT/release-notes.md — поправь и используй."
exit "$RESULT"
