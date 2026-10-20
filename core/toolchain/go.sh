#!/usr/bin/env bash
# The Go the core is built with, the same on every computer and in CI (D-330): the official
# release of the version below, checked against its SHA-256, with the patches of this directory.
# Called like `go`. The first run prepares it in ~/.cache/ducker; later runs only start it.
set -euo pipefail

VERSION=1.27.3

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)
    PLATFORM=linux-amd64 SUM=sha256sum
    SHA256=65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40 ;;
  Darwin-x86_64)
    PLATFORM=darwin-amd64 SUM="shasum -a 256"
    SHA256=b6f8e931e4ab497fcde6da8ae4499a58f9d258e55c8a80824a516eae5950a750 ;;
  Darwin-arm64)
    PLATFORM=darwin-arm64 SUM="shasum -a 256"
    SHA256=dd3908cf20959d798c1e06ebe5b72213f0505ce01373b40ce3110975a6cd99c2 ;;
  *)
    echo "go.sh: no pinned Go for $(uname -s)-$(uname -m)" >&2
    exit 1 ;;
esac

HERE=$(cd "$(dirname "$0")" && pwd)
PATCHES=("$HERE"/*.patch)
# The patches are part of the name: a changed patch gets a fresh copy instead of one patched twice.
STAMP=$(cat "${PATCHES[@]}" | $SUM | cut -c1-12)
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/ducker
GOROOT=$CACHE/go$VERSION-$PLATFORM-$STAMP

# ponytail: an older version stays in the cache after an update (about 250 MB each); delete
# ~/.cache/ducker by hand, or prune here if updates become frequent.
if [ ! -x "$GOROOT/bin/go" ]; then
  mkdir -p "$CACHE"
  TMP=$(mktemp -d "$CACHE/.go.XXXXXX")
  trap 'rm -rf "$TMP"' EXIT
  echo "go.sh: downloading Go $VERSION for $PLATFORM" >&2
  curl -fsSL --retry 3 -o "$TMP/go.tar.gz" "https://go.dev/dl/go$VERSION.$PLATFORM.tar.gz"
  echo "$SHA256  $TMP/go.tar.gz" | $SUM -c --status \
    || { echo "go.sh: go$VERSION.$PLATFORM.tar.gz does not match its SHA-256" >&2; exit 1; }
  tar -xzf "$TMP/go.tar.gz" -C "$TMP"
  rm "$TMP/go.tar.gz"
  for p in "${PATCHES[@]}"; do patch -s -t -N -p1 -d "$TMP/go" < "$p"; done
  # Several builds may prepare it at once (one task per ABI, an IDE): only one of them can
  # create the link, and the others drop their copy. Without -n a second `ln` would put its link
  # inside the first one's Go.
  ln -sn "$TMP/go" "$GOROOT" 2>/dev/null || rm -rf "$TMP"
  trap - EXIT
fi

export GOROOT GOTOOLCHAIN=local
exec "$GOROOT/bin/go" "$@"
