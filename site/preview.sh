#!/bin/sh
# Build both languages and serve them at http://localhost:8002/ducker/ (another port: preview.sh 8003).
# Unlike `zensical serve`, which builds the English site only, the language switch works here.
# The first run sets up Zensical in site/.venv.
set -e
cd "$(dirname "$0")"
PORT=${1:-8002}
[ -x .venv/bin/zensical ] || { python3 -m venv .venv && .venv/bin/pip install -q -r requirements.txt; }
export DUCKER_SITE_URL="http://localhost:$PORT/ducker/" DUCKER_SITE_URL_RU="http://localhost:$PORT/ducker/ru/"
python3 sources.py
.venv/bin/zensical build --strict
.venv/bin/zensical build --strict -f mkdocs.ru.yml
mkdir -p .preview && ln -sfn ../build .preview/ducker
echo "Open http://localhost:$PORT/ducker/ (Ctrl+C stops)"
exec python3 -m http.server "$PORT" --bind 127.0.0.1 --directory .preview
