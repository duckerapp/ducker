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
set -euo pipefail
: "${ANDROID_SERIAL:?Select the one agreed physical test device with ANDROID_SERIAL}"

cd "$(dirname "$0")/.."

VERSION=${1:-}
OUT_ROOT=${OUT_ROOT:-$HOME/Документы/ducker-release}

if [ -z "$VERSION" ]; then
  echo "Укажи версию: ./scripts/release.sh 0.2.0"
  exit 1
fi

[[ "$VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
  echo "Version must be three nonnegative integers" >&2; exit 1;
}
OUT="$OUT_ROOT/v$VERSION"
[ ! -e "$OUT" ] || { echo "Output already exists: $OUT. Preserve it; choose a new version." >&2; exit 1; }
python3 -c 'import sys; sys.path.insert(0, "scripts"); from release_artifacts import tool, expected_certificate; tool("apksigner"); tool("aapt2"); expected_certificate()'

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
  if grep -Ei "^## $VERSION([[:space:]]|$).*(draft|черновик)" "$log" >/dev/null; then
    echo "Date and finalize $log before building $VERSION" >&2; exit 1
  fi
  if [ -z "$(changes "$log")" ]; then
    echo "В $log нет записи «## $VERSION» — её покажут страница выпуска и «Что нового» в приложении."
    exit 1
  fi
done

# The APK ships the licences of every Go module in the core; a dependency change needs a new list.
python3 scripts/release_artifacts.py inputs "$VERSION"
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

VERSION_BACKUP=$(mktemp)
cp build.gradle.kts "$VERSION_BACKUP"
trap 'rm -f "$VERSION_BACKUP"' EXIT
# Post-build failures retain the version and artifacts for diagnosis; no automatic publication.
sed -i "s/versionName = \"$OLD_VERSION\"/versionName = \"$VERSION\"/" build.gradle.kts
sed -i "s/versionCode = $OLD_CODE/versionCode = $NEW_CODE/" build.gradle.kts

# The build downloads the geo databases only when they are missing, so without this every release
# shipped them as old as the last `clean` on this computer.
rm -f app/src/main/assets/geoip.metadb app/src/main/assets/geosite.dat

echo "Собираю (это надолго)…"
if ! ./gradlew :app:assembleMetaRelease -q >/tmp/ducker-release-build.log 2>&1; then
  echo "Сборка упала, смотри /tmp/ducker-release-build.log. Версию возвращаю обратно."
  cp "$VERSION_BACKUP" build.gradle.kts
  tail -20 /tmp/ducker-release-build.log
  exit 1
fi
echo "Собралось."

OUT="$OUT_ROOT/v$VERSION"
mkdir -p "$OUT"


for abi in arm64-v8a armeabi-v7a; do
  SRC="app/build/outputs/apk/meta/release/ducker-$VERSION-meta-$abi-release.apk"
  python3 scripts/release_artifacts.py apk "$VERSION" --abi "$abi" --file "$SRC"
  cp "$SRC" "$OUT/ducker-$VERSION-$abi.apk"
done
( cd "$OUT" && sha256sum ./*.apk > SHA256SUMS )

./scripts/release-notes.sh "$VERSION" "$OUT"

echo
echo "Готово. Файлы в $OUT:"
ls -la "$OUT" | tail -n +4 | awk '{printf "  %s  %.1f МБ\n", $9, $5/1048576}'

echo
if adb get-state >/dev/null 2>&1; then
  echo "Прогоняю дымовой тест на подключённом телефоне…"
  RESULT=0
  APK="$OUT/ducker-$VERSION-arm64-v8a.apk" ./scripts/smoke.sh --no-build || RESULT=$?
else
  echo "Телефон не подключён — дымовой тест пропущен. Раздавать непроверенное нельзя:"
  echo "подключи телефон и запусти ./scripts/smoke.sh --no-build"
  RESULT=1
fi

if [ "$RESULT" -eq 0 ]; then
  python3 scripts/release_artifacts.py seal "$VERSION" --out "$OUT" --smoke-passed
else
  echo 'Smoke did not pass: artifacts and version retained, publication is blocked.' >&2
fi

echo
echo "Осталось сделать руками:"
echo "  git add -- build.gradle.kts && git commit -m \"release: $VERSION\""
echo "  scripts/publish.sh $VERSION   # only after publication authorization; creates the tag"
echo
echo "Описание релиза лежит в $OUT/release-notes.md — проверь до публикации; после seal файлы неизменяемы."
exit "$RESULT"
