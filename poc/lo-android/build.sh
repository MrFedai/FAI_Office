#!/usr/bin/env bash
# WP6: builds the LibreOffice Android aarch64 engine in Docker. Long (hours); progress goes to out/build.log
# and the console prints one line per finished build target count.
# Usage: build.sh            (image + configure + make)
#        build.sh configure  (stop after autogen, to check options)
#        build.sh devam      (continue an interrupted make, no autogen)
# make runs in a detached container (fai-lo-build), so it survives the calling shell; re-running build.sh
# while it runs only re-attaches the progress display.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${LO_SRC:-$HOME/dev/cache/fai-office/lo-src/core}
TAR=$HOME/dev/cache/fai-office/lo-src/tarballs
SDK=${ANDROID_HOME:-$HOME/dev/android-sdk}
NDK=$(ls -d "$SDK"/ndk/2[7-9]* | sort -V | tail -1)
mkdir -p out "$TAR"
cp autogen.input "$SRC/autogen.input"
# The Android block of configure.ac forces CoinMP (CPL-1.0, not allowed by section 11) on; turn it off.
python3 - "$SRC/configure.ac" <<'PY'
import re, sys
p = sys.argv[1]; s = open(p).read()
a = s.index('_os=Android'); b = s.rindex('enable_coinmp=', 0, a)
if s[b:b + 18] == 'enable_coinmp=yes\n':
    open(p, 'w').write(s[:b] + 'enable_coinmp=no  # FAI_Office: CoinMP is CPL-1.0\n' + s[b + 18:])
    print('patched configure.ac: Android CoinMP off')
PY
echo "[1/3] docker image"
docker build -q -t fai-lo-android --build-arg UID="$(id -u)" . >/dev/null
VOLS=(-v "$SRC":/src -v "$TAR":/tarballs -v "$SDK":/sdk:ro -v "$NDK":/ndk:ro -v "$PWD/out":/out
      -v "$HOME/dev/cache/fai-office/lo-src/ccache":/home/builder/.ccache)
run() { docker run --rm "${VOLS[@]}" fai-lo-android bash -c "$1"; }
running() { [ "$(docker inspect -f '{{.State.Running}}' fai-lo-build 2>/dev/null)" = true ]; }
progress() {
  local start=$1
  while running; do
    n=$(grep -c '^\[' out/build.log || true)
    echo "$(date +%H:%M) $(( ($(date +%s) - start) / 60 )) min, $n targets, last: $(grep '^\[' out/build.log | tail -1 | cut -c1-80)"
    sleep 60
  done
  code=$(docker inspect -f '{{.State.ExitCode}}' fai-lo-build)
  docker rm fai-lo-build >/dev/null
  [ "$code" = 0 ] && echo "make ok in $(( ($(date +%s) - start) / 60 )) min" || { echo "make FAILED ($code), tail:"; tail -30 out/build.log; exit 1; }
}
if running; then echo "build already running, attaching"; progress "$(date +%s)"; exit 0; fi
docker rm fai-lo-build >/dev/null 2>&1 || true
if [ "${1:-}" != devam ]; then
echo "[2/3] autogen (NDK $(basename "$NDK"))"
run "./autogen.sh" 2>&1 | tee out/autogen.log | grep -E 'WARNING|error|unrecognized|^Build ' || true
grep -q 'unrecognized options' out/autogen.log && echo "!! unrecognized options, see out/autogen.log"
[ "${1:-}" = configure ] && exit 0
fi
echo "[3/3] make (log: out/build.log)"
[ -f out/build.log ] && mv out/build.log "out/build.$(date +%H%M).log"
docker run -d --name fai-lo-build "${VOLS[@]}" fai-lo-android \
  bash -c "make -j$(nproc) build > /out/build.log 2>&1" >/dev/null
progress "$(date +%s)"
