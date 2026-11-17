#!/usr/bin/env bash
# WP7: builds the LibreOffice WASM engine (headless: Writer, Calc, Impress) in Docker. Hours; progress every minute.
# Usage: build.sh            (image + configure + make)
#        build.sh configure  (stop after autogen, to check options)
#        build.sh devam      (continue an interrupted make, no autogen)
# make runs in a detached container (fai-lo-wasm-build), so it survives the calling shell.
set -euo pipefail
cd "$(dirname "$0")"
CACHE=$HOME/dev/cache/fai-office/lo-src
SRC=${LO_WASM_SRC:-$CACHE/core-wasm}
TAG=libreoffice-26.8.2.2
mkdir -p out "$CACHE/tarballs" "$CACHE/ccache-wasm"
if [ ! -d "$SRC" ]; then
  echo "[0/3] source: git worktree of $TAG (shares objects with the Android tree)"
  git -C "$CACHE/core" worktree add --detach "$SRC" "$TAG"
fi
cmp -s autogen.input "$SRC/autogen.input" || cp autogen.input "$SRC/autogen.input"  # a newer copy makes make re-run configure
echo "[1/3] docker image"
docker build -q -t fai-lo-wasm --build-arg UID="$(id -u)" . >/dev/null
VOLS=(-v "$SRC":/src -v "$CACHE/tarballs":/tarballs -v "$PWD/out":/out -v "$CACHE/ccache-wasm":/home/builder/.ccache)
running() { [ "$(docker inspect -f '{{.State.Running}}' fai-lo-wasm-build 2>/dev/null)" = true ]; }
progress() {
  local start=$1
  while running; do
    n=$(grep -c '^\[' out/build.log || true)
    echo "$(date +%H:%M) $(( ($(date +%s) - start) / 60 )) min, $n targets, last: $(grep '^\[' out/build.log | tail -1 | cut -c1-80)"
    sleep 60
  done
  code=$(docker inspect -f "{{.State.ExitCode}}" fai-lo-wasm-build 2>/dev/null || echo "?")
  docker rm fai-lo-wasm-build >/dev/null 2>&1 || true
  [ "$code" = 0 ] && echo "make ok in $(( ($(date +%s) - start) / 60 )) min" || { echo "make FAILED ($code), tail:"; tail -30 out/build.log; exit 1; }
}
if running; then echo "build already running, attaching"; progress "$(date +%s)"; exit 0; fi
docker rm fai-lo-wasm-build >/dev/null 2>&1 || true
if [ "${1:-}" != devam ]; then
  echo "[2/3] autogen"
  docker run --rm "${VOLS[@]}" fai-lo-wasm bash -c "./autogen.sh" 2>&1 | tee out/autogen.log | grep -E 'error|unrecognized' || true
  grep -q 'unrecognized options' out/autogen.log && echo "!! unrecognized options, see out/autogen.log"
  [ "${1:-}" = configure ] && exit 0
fi
echo "[3/3] make (log: out/build.log)"
[ -f out/build.log ] && mv out/build.log "out/build.$(date +%H%M).log"
docker run -d --name fai-lo-wasm-build "${VOLS[@]}" fai-lo-wasm bash -c "make -j$(nproc) build > /out/build.log 2>&1" >/dev/null
progress "$(date +%s)"
