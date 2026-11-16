#!/usr/bin/env bash
# Render the agreed release-page layout without building, changing versions or publishing.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=${1:?version required}
OUT=${2:?output directory with both release APKs required}
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
changes() {
  awk -v h="## $VERSION" 'index($0, h) == 1 && (length($0) == length(h) || substr($0, length(h) + 1, 1) == " ") {f = 1; next}
    /^## / {f = 0} f' "$1" | awk '/<!--/ {c = 1} !c; /-->/ {c = 0}' | sed '/./,$!d' |
    # A release body shows every line break, so an item's wrapped lines are joined; nested items stay.
    awk '/^ +[^ ]/ && !/^ +- / && n {sub(/^ +/, ""); line[n] = line[n] " " $0; next} {line[++n] = $0}
      END {for (i = 1; i <= n; i++) print line[i]}'
}
# Описание выпуска для страницы на GitHub: кнопки скачивания, «что нового» из обоих списков изменений.
# Picture URLs are absolute: a release body has no repository to resolve relative paths against.
# The buttons come from docs/images/release/ on main (notes/brand/build-release-buttons.py makes them).
REPO=https://github.com/duckerapp/ducker
IMG=https://raw.githubusercontent.com/duckerapp/ducker/v$VERSION/docs/images/release
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
    "If one file does not install, take the other. Android 8 or later."
  echo
  echo "[How to install]($SITE/users/install/) · [Installing on a TV]($SITE/users/tv/) · [Documentation]($SITE/)"
  echo
  echo "Telegram: [channel](https://t.me/ducker_news) for releases and news ·" \
    "[chat](https://t.me/ducker_chat) for questions and discussion"
  echo
  echo "[![Downloads](https://img.shields.io/github/downloads/duckerapp/ducker/v$VERSION/total?style=flat-square&label=downloads&labelColor=000&color=2b2b2b)]($REPO/releases/tag/v$VERSION)"
  echo
  echo "Plain links: [arm64-v8a]($DL/ducker-$VERSION-arm64-v8a.apk) · [armeabi-v7a]($DL/ducker-$VERSION-armeabi-v7a.apk) · [GitLab mirror](https://gitlab.com/duckerx/ducker/-/packages)"
  echo
  echo "Beta is optional in Settings. Stable promotion uses these same files; turning beta off does not downgrade the app."
  echo
  echo "## What's new"
  echo
  changes CHANGELOG.md
  echo
  # GitHub puts a spoiler's first line right under its title; a <br> gives it a line of room.
  echo "<details><summary>По-русски</summary>"
  echo "<br>"
  echo
  echo "**Какой файл скачать.** Телефон — большая кнопка, \`arm64-v8a\`. Старый телефон — \`armeabi-v7a\`." \
    "Телевизор или приставка: \`armeabi-v7a\` ставится почти на любой телевизор, современному телевизору или" \
    "приставке нужен \`arm64-v8a\`. Если файл не установился, возьмите другой. Нужен Android 8 или новее."
  echo
  echo "[Как установить]($SITE/ru/users/install/) · [Установка на телевизор]($SITE/ru/users/tv/) · [Документация]($SITE/ru/)"
  echo
  echo "Telegram: [канал](https://t.me/ducker_news) — выпуски и новости ·" \
    "[чат](https://t.me/ducker_chat) — вопросы и обсуждение"
  echo
  echo "Прямые ссылки: [arm64-v8a]($DL/ducker-$VERSION-arm64-v8a.apk) · [armeabi-v7a]($DL/ducker-$VERSION-armeabi-v7a.apk) · [зеркало GitLab](https://gitlab.com/duckerx/ducker/-/packages)"
  echo
  echo "Бета-версии включаются в настройках. В stable переходят те же файлы; выключение beta не откатывает установленную версию."
  echo
  echo "### Что нового"
  echo
  changes docs/CHANGELOG.ru.md
  echo
  echo "</details>"
  echo
  echo "<details><summary>Verify the files</summary>"
  echo "<br>"
  echo
  # The report's address is the file's SHA-256; the report exists once the file is uploaded there.
  ( cd "$OUT" && sha256sum ./*.apk | sed 's|^\([0-9a-f]*\)  \./\(.*\)$|- `\2`: [VirusTotal](https://www.virustotal.com/gui/file/\1)|' )
  echo
  echo "VirusTotal scan results can change and are not a guarantee of safety. The report must exist for each file before publication."
  echo
  echo "SHA-256 of both files: \`SHA256SUMS\` below."
  echo
  # Read the APK's exact bytes: a preview may be rendered on another day or machine.
  echo "Bundled country and site databases from MetaCubeX/meta-rules-dat, SHA-256:"
  echo
    python3 - "$OUT/ducker-$VERSION-arm64-v8a.apk" <<'PYHASH'
import hashlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    for name in ('geoip.metadb', 'geosite.dat'):
        print(f"- `{name}`: `{hashlib.sha256(z.read('assets/' + name)).hexdigest()}`")
PYHASH
  echo
  echo "</details>"
} > "$OUT/release-notes.md"
