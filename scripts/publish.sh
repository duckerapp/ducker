#!/usr/bin/env bash
# Выкладка выпуска ducker: файлы на GitHub и на зеркало, запись о выпуске в update.json.
#
#   ./scripts/publish.sh 0.5.0            выложить в бету
#   ./scripts/publish.sh 0.5.0 --stable   перевести тот же выпуск всем
#
# Перед этим: ./scripts/release.sh 0.5.0, записи в CHANGELOG.md и docs/CHANGELOG.ru.md и
# git commit -am "release: 0.5.0". Тег ставит этот скрипт.
#
# Приложение узнаёт о новой версии из update.json в корне репозитория: в нём запись на канал,
# `beta` и `stable`. Каждый выпуск сначала идёт в бету — выпуск GitHub с пометкой pre-release.
# Через несколько дней без жалоб тот же файл переводится всем: пометка снимается, запись
# копируется в `stable`. Неудачную бету не переводят, а чинят следующим номером.
# Отозвать выпуск — git revert коммита с update.json и отправить на оба адреса.
#
# Файлы берутся из папки выпуска, куда их сложил release.sh; «что нового» — из записей о
# версии в двух списках изменений. Ключ подписи скрипту не нужен.
#
# Проверить обновление без выкладки (нужны только файлы и записи в списках изменений):
#   APK_BASES=http://127.0.0.1:8765/ ./scripts/publish.sh 0.5.0 --local
# пишет update.json рядом с файлами выпуска и больше ничего не делает: ни тегов, ни коммитов,
# ни отправки. Раздать папку — python3 -m http.server 8765 --bind 127.0.0.1, телефону её
# показать — adb reverse tcp:8765 tcp:8765, сборку собрать с
# -Pducker.update.urls=http://127.0.0.1:8765/update.json.
set -eu

cd "$(dirname "$0")/.."

VERSION=${1:-}
STABLE=0
LOCAL=0
for arg in "${@:2}"; do
  case "$arg" in
    --stable) STABLE=1 ;;
    --local) LOCAL=1 ;;
    *) echo "Не знаю, что такое '$arg'. Бывает --stable и --local."; exit 1 ;;
  esac
done

case "$VERSION" in
  [0-9]*.[0-9]*.[0-9]*) ;;
  *) echo "Укажи версию: ./scripts/publish.sh 0.5.0 [--stable]"; exit 1 ;;
esac

OUT=${OUT_ROOT:-$HOME/Документы/ducker-release}/v$VERSION
GITLAB_PROJECT='duckerx%2Fducker'
GITLAB_FILES="https://gitlab.com/api/v4/projects/$GITLAB_PROJECT/packages/generic/ducker/$VERSION"
# Откуда приложение качает файл, по порядку: не ответил первый — берёт со второго.
APK_BASES=${APK_BASES:-"https://github.com/duckerapp/ducker/releases/download/v$VERSION/ $GITLAB_FILES/"}

NAME=$(grep -m1 'versionName = ' build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
CODE=$(grep -m1 'versionCode = ' build.gradle.kts | sed 's/[^0-9]//g')
MIN_SDK=$(grep -m1 'minSdk = ' build.gradle.kts | sed 's/[^0-9]//g')

if [ "$NAME" != "$VERSION" ]; then
  echo "В build.gradle.kts версия $NAME, а выкладывается $VERSION. Выкладывать надо с коммита выпуска."
  exit 1
fi

if ! ls "$OUT"/ducker-"$VERSION"-*.apk >/dev/null 2>&1; then
  echo "В $OUT нет файлов выпуска. Сначала ./scripts/release.sh $VERSION"
  exit 1
fi

if [ "$LOCAL" -eq 1 ]; then
  FEED=$OUT/update.json
  NEW=$FEED
else
  FEED=update.json
  # Сначала в сторону: в репозиторий запись попадёт, когда файлы выпуска уже выложены.
  NEW=$(mktemp)

  if [ -n "$(git status --porcelain)" ]; then
    echo "В дереве есть несохранённые правки — сначала закоммить выпуск. git status:"
    git status --short
    exit 1
  fi

  if [ "$(git branch --show-current)" != main ]; then
    echo "Выкладывают с ветки main, а сейчас $(git branch --show-current)."
    exit 1
  fi

  for remote in origin gitlab; do
    if ! git remote get-url "$remote" >/dev/null 2>&1; then
      echo "Нет адреса '$remote'. Зеркало: git remote add gitlab git@gitlab-duckerx:duckerx/ducker.git"
      exit 1
    fi
  done

  if [ -z "${GITLAB_TOKEN:-}" ]; then
    echo "Нужен GITLAB_TOKEN — deploy token проекта с правом write_package_registry: без него файлы на зеркало не положить."
    exit 1
  fi
fi

# Запись о выпуске. Отдельным шагом до всякой выкладки: нет записи в списке изменений или
# переводим всем не тот выпуск, что в бете, — остановимся, ничего не отправив.
python3 - "$VERSION" "$CODE" "$MIN_SDK" "$STABLE" "$OUT" "$FEED" "$NEW" $APK_BASES <<'PY'
import hashlib, json, pathlib, re, sys

version, code, min_sdk, stable, out, source, target, *bases = sys.argv[1:]
source = pathlib.Path(source)


def notes(path):
    """Запись о версии из списка изменений — простым текстом, пункт в строку."""
    lines = pathlib.Path(path).read_text().splitlines()
    start = next((i for i, line in enumerate(lines) if re.match(rf"## {re.escape(version)}(\s|$)", line)), None)

    if start is None:
        sys.exit(f"В {path} нет записи о версии {version} — её покажет панель «Что нового».")

    items = []

    for line in lines[start + 1:]:
        if line.startswith("## "):
            break
        if line.startswith("- "):
            items.append(line[2:].strip())
        elif line.strip() and items:
            items[-1] += " " + line.strip()

    text = "\n".join("• " + item for item in items)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)

    return re.sub(r"[`*]", "", text)


feed = json.loads(source.read_text()) if source.exists() else {"mirrors": [], "stable": None, "beta": None}

if stable == "1":
    if not feed["beta"] or feed["beta"]["version"] != version:
        sys.exit(f"В бете сейчас не {version}: всем переводят только выпуск, который в ней побывал.")

    feed["stable"] = feed["beta"]
else:
    apk = {}

    for file in sorted(pathlib.Path(out).glob(f"ducker-{version}-*.apk")):
        abi = file.name[len(f"ducker-{version}-"):-len(".apk")]

        apk[abi] = {
            "sha256": hashlib.sha256(file.read_bytes()).hexdigest(),
            "size": file.stat().st_size,
            "urls": [base + file.name for base in bases],
        }

    feed["beta"] = {
        "version": version,
        "code": int(code),
        "minSdk": int(min_sdk),
        "notes": {"en": notes("CHANGELOG.md"), "ru": notes("docs/CHANGELOG.ru.md")},
        "apk": apk,
    }

pathlib.Path(target).write_text(json.dumps(feed, ensure_ascii=False, indent=2) + "\n")
PY

if [ "$LOCAL" -eq 1 ]; then
  echo "Записано: $FEED"
  exit 0
fi

if cmp -s "$NEW" update.json; then
  echo "update.json не изменился: $VERSION уже выложен."
  exit 1
fi

if [ "$STABLE" -eq 1 ]; then
  gh release edit "v$VERSION" --prerelease=false --latest
  MESSAGE="release: $VERSION for everyone"
else
  # Файлы — раньше записи: приложение не должно узнать о версии, пока скачивать нечего.
  git rev-parse "v$VERSION" >/dev/null 2>&1 || git tag "v$VERSION"

  for remote in origin gitlab; do
    git push "$remote" main "v$VERSION"
  done

  if gh release view "v$VERSION" >/dev/null 2>&1; then
    gh release upload "v$VERSION" "$OUT"/*.apk "$OUT"/SHA256SUMS --clobber
  else
    gh release create "v$VERSION" "$OUT"/*.apk "$OUT"/SHA256SUMS \
      --prerelease --title "ducker $VERSION" --notes-file "$OUT/release-notes.md"
  fi

  for file in "$OUT"/*.apk "$OUT"/SHA256SUMS; do
    curl --fail --silent --show-error --header "DEPLOY-TOKEN: $GITLAB_TOKEN" \
      --upload-file "$file" "$GITLAB_FILES/$(basename "$file")" >/dev/null
  done

  MESSAGE="release: $VERSION to beta"
fi

mv "$NEW" update.json
git add update.json
git commit -q -m "$MESSAGE"

for remote in origin gitlab; do
  git push "$remote" main
done

echo "Готово: $VERSION — $([ "$STABLE" -eq 1 ] && echo "всем" || echo "в бете")."
