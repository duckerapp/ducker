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

# The version's entry of a changelog as markdown, without its HTML comments; the release page shows it.
changes() {
  awk -v h="## $VERSION" 'index($0, h) == 1 && (length($0) == length(h) || substr($0, length(h) + 1, 1) == " ") {f = 1; next}
    /^## / {f = 0} f' "$1" | awk '/<!--/ {c = 1} !c; /-->/ {c = 0}' | sed '/./,$!d' |
    # A release body shows every line break, so an item's wrapped lines are joined; nested items stay.
    awk '/^ +[^ ]/ && !/^ +- / && n {sub(/^ +/, ""); line[n] = line[n] " " $0; next} {line[++n] = $0}
      END {for (i = 1; i <= n; i++) print line[i]}'
}
for log in CHANGELOG.md docs/CHANGELOG.ru.md; do
  if [ -z "$(changes "$log")" ]; then
    echo "В $log нет записи «## $VERSION» — её покажут страница выпуска и «Что нового» в приложении."
    exit 1
  fi
done

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

# Описание выпуска для страницы на GitHub: кнопки скачивания, «что нового» из обоих списков изменений.
# Picture URLs are absolute: a release body has no repository to resolve relative paths against.
# The buttons come from docs/images/release/ on main (notes/brand/build-release-buttons.py makes them).
REPO=https://github.com/duckerapp/ducker
IMG=$REPO/raw/main/docs/images/release
DL=$REPO/releases/download/v$VERSION
SITE=https://duckerapp.github.io/ducker
button() { # file, image, width, alt
  echo "<a href=\"$DL/$1\"><picture><source media=\"(prefers-color-scheme: dark)\" srcset=\"$IMG/$2-dark.svg\"><img src=\"$IMG/$2-light.svg\" width=\"$3\" alt=\"$4\"></picture></a>"
}
{
  echo "<p>"
  button "ducker-$VERSION-arm64-v8a.apk" android 360 "Download for Android: arm64-v8a"
  button "ducker-$VERSION-armeabi-v7a.apk" android-v7a 360 "Older phones and TVs: armeabi-v7a"
  echo "</p>"
  echo
  echo "**On a TV or a box:** \`armeabi-v7a\` installs on almost any TV; a current TV or box needs \`arm64-v8a\`." \
    "If one file does not install, take the other. Android 10 or later."
  echo
  echo "[How to install]($SITE/users/install/) · [Installing on a TV]($SITE/users/tv/) · [Documentation]($SITE/)"
  echo
  echo "Telegram: [channel](https://t.me/ducker_mihomo) for releases and news ·" \
    "[chat](https://t.me/ducker_community) for questions and discussion"
  echo
  echo "[![Downloads](https://img.shields.io/github/downloads/duckerapp/ducker/v$VERSION/total?style=flat-square&label=downloads&labelColor=000&color=2b2b2b)]($REPO/releases/tag/v$VERSION)"
  echo
  echo "## What's new"
  echo
  changes CHANGELOG.md
  echo
  echo "<details><summary>По-русски</summary>"
  echo
  echo "**Какой файл скачать.** Телефон — большая кнопка, \`arm64-v8a\`. Старый телефон — \`armeabi-v7a\`." \
    "Телевизор или приставка: \`armeabi-v7a\` ставится почти на любой телевизор, современному телевизору или" \
    "приставке нужен \`arm64-v8a\`. Если файл не установился, возьмите другой. Нужен Android 10 или новее."
  echo
  echo "[Как установить]($SITE/ru/users/install/) · [Установка на телевизор]($SITE/ru/users/tv/) · [Документация]($SITE/ru/)"
  echo
  echo "Telegram: [канал](https://t.me/ducker_mihomo) — выпуски и новости ·" \
    "[чат](https://t.me/ducker_community) — вопросы и обсуждение"
  echo
  echo "### Что нового"
  echo
  changes docs/CHANGELOG.ru.md
  echo
  echo "</details>"
  echo
  echo "<details><summary>Verify the files</summary>"
  echo
  # The report's address is the file's SHA-256; the report exists once the file is uploaded there.
  ( cd "$OUT" && sha256sum ./*.apk | sed 's|^\([0-9a-f]*\)  \./\(.*\)$|- `\2`: [VirusTotal](https://www.virustotal.com/gui/file/\1)|' )
  echo
  echo "SHA-256 of both files: \`SHA256SUMS\` below."
  echo
  # meta-rules-dat publishes only a daily `latest`, so the hashes say which databases this file has;
  # placed in app/src/main/assets/, they are not downloaded again and a rebuild matches.
  echo "Country and site databases, MetaCubeX/meta-rules-dat \`latest\` of $(date +%d.%m.%Y), SHA-256:"
  echo
  ( cd app/src/main/assets && sha256sum geoip.metadb geosite.dat | sed 's/^\([0-9a-f]*\)  \(.*\)$/- `\2`: `\1`/' )
  echo
  echo "</details>"
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
