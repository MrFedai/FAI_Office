#!/usr/bin/env bash
# WP6: builds the LibreOffice Android aarch64 engine in Docker. Long (hours); progress goes to out/build.log
# and the console prints one line per finished build target count.
# Usage: build.sh            (image + configure + make)
#        build.sh configure  (stop after autogen, to check options)
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
run() { docker run --rm -v "$SRC":/src -v "$TAR":/tarballs -v "$SDK":/sdk:ro -v "$NDK":/ndk:ro \
          -v "$HOME/dev/cache/fai-office/lo-src/ccache":/home/builder/.ccache fai-lo-android bash -c "$1"; }
echo "[2/3] autogen (NDK $(basename "$NDK"))"
run "./autogen.sh" 2>&1 | tee out/autogen.log | grep -E 'WARNING|error|unrecognized|^Build ' || true
grep -q 'unrecognized options' out/autogen.log && echo "!! unrecognized options, see out/autogen.log"
[ "${1:-}" = configure ] && exit 0
echo "[3/3] make (log: out/build.log)"
start=$(date +%s)
run "make -j$(nproc) build-nocheck" > out/build.log 2>&1 &
pid=$!
while kill -0 $pid 2>/dev/null; do
  sleep 60
  n=$(grep -c '^\[' out/build.log || true)
  echo "$(date +%H:%M) $(( ($(date +%s) - start) / 60 )) min, $n targets, last: $(grep '^\[' out/build.log | tail -1 | cut -c1-80)"
done
wait $pid && echo "make ok in $(( ($(date +%s) - start) / 60 )) min" || { echo "make FAILED, tail:"; tail -30 out/build.log; exit 1; }
