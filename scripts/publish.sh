#!/usr/bin/env bash
# Publish immutable beta files; --stable promotes those exact bytes. --local makes only a local feed.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=${1:-}
[[ "$VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
  echo 'Usage: scripts/publish.sh 1.0.0 [--stable] [--local]' >&2; exit 1;
}
STABLE=0 LOCAL=0
for arg in "${@:2}"; do
  case "$arg" in --stable) STABLE=1;; --local) LOCAL=1;; *) echo "Unknown option: $arg" >&2; exit 1;; esac
done
OUT=${OUT_ROOT:-$HOME/Документы/ducker-release}/v$VERSION
TAG=v$VERSION REPO=duckerapp/ducker
GITLAB_FILES="https://gitlab.com/api/v4/projects/duckerx%2Fducker/packages/generic/ducker/$VERSION"
read -r -a BASES <<< "${APK_BASES:-https://github.com/$REPO/releases/download/$TAG/ $GITLAB_FILES/}"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
has_asset() {
  python3 - "$TMP/release.json" "$1" <<'PY'
import json, sys
sys.exit(0 if any(a['name']==sys.argv[2] for a in json.load(open(sys.argv[1]))['assets']) else 1)
PY
}
ARGS=()
if [ "$STABLE" -eq 1 ] && [ "$LOCAL" -eq 0 ]; then
  git rev-parse --verify "refs/tags/$TAG^{commit}" >/dev/null
  ARGS+=(--revision "$TAG")
fi
FEED=update.json
if [ "$LOCAL" -eq 1 ]; then FEED=$OUT/update.json; ARGS+=(--fixture); fi
if [ "$STABLE" -eq 1 ]; then ARGS+=(--stable); fi
python3 scripts/release_artifacts.py feed "$VERSION" --out "$OUT" --source "$FEED" \
  --target "$TMP/update.json" --bases "${BASES[@]}" "${ARGS[@]}"
if [ "$LOCAL" -eq 1 ]; then
  cp "$TMP/update.json" "$FEED"
  echo "Local feed: $FEED"
  exit 0
fi
python3 - "$OUT/release-manifest.json" <<'PY'
import json, sys
if json.load(open(sys.argv[1])).get('smokePassed') is not True:
    sys.exit('The exact release APK needs a successful agreed device smoke before publication')
PY
[ -z "$(git status --porcelain)" ] || { echo 'Commit source changes before publication' >&2; exit 1; }
[ "$(git branch --show-current)" = main ] || { echo 'Publish from main' >&2; exit 1; }
[ "$(git config user.name)" = ducker ] && \
  [ "$(git config user.email)" = '345288940+duckwright@users.noreply.github.com' ] && \
  [ "$(git config commit.gpgsign)" = true ] || { echo 'Project identity and signed commits required' >&2; exit 1; }
export GH_CONFIG_DIR=${GH_CONFIG_DIR:-$HOME/.config/gh-ducker}
[ "$(gh api user --jq .login)" = duckwright ] || { echo 'Use the project GitHub account' >&2; exit 1; }
for remote in origin gitlab; do
  address=$(git remote get-url "$remote")
  case "$remote:$address" in
    origin:git@github-duckerapp:duckerapp/ducker.git|gitlab:git@gitlab-duckerx:duckerx/ducker.git) ;;
    *) echo "Unexpected $remote URL; inspect repository identity" >&2; exit 1;;
  esac
done
if [ "$STABLE" -eq 0 ]; then
  [ -n "${GITLAB_TOKEN:-}" ] || { echo 'GITLAB_TOKEN deploy token is required' >&2; exit 1; }
fi
if git rev-parse --verify "refs/tags/$TAG" >/dev/null 2>&1; then
  python3 scripts/release_artifacts.py verify "$VERSION" --out "$OUT" --revision "$TAG"
elif [ "$STABLE" -eq 0 ]; then
  git tag "$TAG"
fi
for remote in origin gitlab; do
  remote_tag=$(git ls-remote "$remote" "refs/tags/$TAG" "refs/tags/$TAG^{}")
  if [ -n "$remote_tag" ]; then
    actual=$(printf '%s\n' "$remote_tag" | tail -1 | cut -f1)
    [ "$actual" = "$(git rev-parse "$TAG^{commit}")" ] || { echo "$remote tag differs" >&2; exit 1; }
  fi
  git push "$remote" main "$TAG"
done
if [ "$STABLE" -eq 0 ]; then
  if ! gh release view "$TAG" -R "$REPO" --json assets,isPrerelease,isDraft,body > "$TMP/release.json"; then
    gh release create "$TAG" -R "$REPO" --draft --prerelease --verify-tag \
      --title "ducker $VERSION" --notes-file "$OUT/release-notes.md"
    gh release view "$TAG" -R "$REPO" --json assets,isPrerelease,isDraft,body > "$TMP/release.json"
  fi
  python3 - "$TMP/release.json" "$OUT/release-notes.md" <<'PY'
import json, pathlib, sys
r=json.load(open(sys.argv[1]))
if not r['isPrerelease'] and not r['isDraft']:
    sys.exit('Already stable on GitHub; resume with --stable')
if r['body'].replace('\r\n', '\n').strip() != pathlib.Path(sys.argv[2]).read_text().strip():
    sys.exit('GitHub release notes differ from the sealed candidate')
PY
  for name in "ducker-$VERSION-arm64-v8a.apk" "ducker-$VERSION-armeabi-v7a.apk" SHA256SUMS release-manifest.json; do
    if has_asset "$name"; then
      gh release download "$TAG" -R "$REPO" --pattern "$name" --dir "$TMP"
      cmp "$OUT/$name" "$TMP/$name" || { echo "GitHub artifact differs: $name" >&2; exit 1; }
    else
      gh release upload "$TAG" -R "$REPO" "$OUT/$name"
    fi
    # Verify the public download route; registry redirects must not carry a deploy token.
    status=$(curl --location --proto '=https' --proto-redir '=https' --silent --show-error \
      --output "$TMP/gitlab-file" --write-out '%{http_code}' "$GITLAB_FILES/$name")
    case "$status" in
      200) cmp "$OUT/$name" "$TMP/gitlab-file" || { echo "GitLab artifact differs: $name" >&2; exit 1; };;
      404) curl --fail --silent --show-error --header "DEPLOY-TOKEN: $GITLAB_TOKEN" \
        --upload-file "$OUT/$name" "$GITLAB_FILES/$name" >/dev/null;;
      *) echo "GitLab artifact check failed: HTTP $status" >&2; exit 1;;
    esac
  done
  # Unversioned copies give provider pages a fixed releases/latest/download/ link. GitHub only:
  # the GitLab registry has no "latest" route, and OTA and checksums use the versioned names.
  for abi in arm64-v8a armeabi-v7a; do
    name=ducker-$abi.apk
    if has_asset "$name"; then
      gh release download "$TAG" -R "$REPO" --pattern "$name" --dir "$TMP"
      cmp "$OUT/ducker-$VERSION-$abi.apk" "$TMP/$name" || { echo "GitHub artifact differs: $name" >&2; exit 1; }
    else
      cp "$OUT/ducker-$VERSION-$abi.apk" "$TMP/$name"
      gh release upload "$TAG" -R "$REPO" "$TMP/$name"
    fi
  done
  gh release edit "$TAG" -R "$REPO" --draft=false --prerelease --latest=false
  MESSAGE="release: $VERSION to beta"
else
  gh release view "$TAG" -R "$REPO" --json assets,isPrerelease,isDraft,body > "$TMP/release.json"
  python3 - "$TMP/release.json" "$OUT/release-notes.md" <<'PY'
import json, pathlib, sys
body=json.load(open(sys.argv[1]))['body'].replace('\r\n', '\n').strip()
if body != pathlib.Path(sys.argv[2]).read_text().strip():
    sys.exit('GitHub release notes differ from the sealed candidate')
PY
  # Promotion checks existing files without uploading or replacing anything.
  for name in "ducker-$VERSION-arm64-v8a.apk" "ducker-$VERSION-armeabi-v7a.apk" SHA256SUMS release-manifest.json; do
    gh release download "$TAG" -R "$REPO" --pattern "$name" --dir "$TMP"
    cmp "$OUT/$name" "$TMP/$name" || { echo "GitHub artifact differs: $name" >&2; exit 1; }
    curl --location --proto '=https' --proto-redir '=https' --fail --silent --show-error \
      --output "$TMP/gitlab-file" "$GITLAB_FILES/$name"
    cmp "$OUT/$name" "$TMP/gitlab-file" || { echo "GitLab artifact differs: $name" >&2; exit 1; }
  done
  for abi in arm64-v8a armeabi-v7a; do
    gh release download "$TAG" -R "$REPO" --pattern "ducker-$abi.apk" --dir "$TMP"
    cmp "$OUT/ducker-$VERSION-$abi.apk" "$TMP/ducker-$abi.apk" || { echo "GitHub artifact differs: ducker-$abi.apk" >&2; exit 1; }
  done
  gh release edit "$TAG" -R "$REPO" --prerelease=false --latest
  MESSAGE="release: $VERSION for everyone"
fi
if ! cmp -s "$TMP/update.json" update.json; then
  cp "$TMP/update.json" update.json
  git add -- update.json
  git commit -m "$MESSAGE"
fi
# Retry both pushes even when the local feed already has the desired state.
for remote in origin gitlab; do git push "$remote" main "$TAG"; done
echo "Published $VERSION ($([ "$STABLE" -eq 1 ] && echo stable || echo beta)); verify anonymous downloads and OTA."
