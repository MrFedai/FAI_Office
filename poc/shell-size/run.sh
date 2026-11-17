#!/usr/bin/env bash
# Shell size PoC: builds the same home screen with Flutter and Compose Multiplatform for Linux and Web and prints
# package size, Linux start time / RSS (5 runs) and the Web first-load transfer (gzip -9, what the browser fetches).
# Usage: run.sh   (needs a display for the Linux runs; WSLg works)
set -euo pipefail
cd "$(dirname "$0")"
source "$HOME/dev/env.sh"
GRADLE=${GRADLE:-$(ls -d "$GRADLE_USER_HOME"/wrapper/dists/gradle-9.3.1-all/*/gradle-9.3.1/bin/gradle | head -1)}
mkdir -p out

echo "[1/4] Flutter: build Linux, Web (JS) and Web (Wasm), resources served locally (no Google CDN)"
(cd flutter_home && flutter build linux --release >/dev/null &&
  flutter build web --release --no-web-resources-cdn -o build/web-local >/dev/null &&
  flutter build web --release --wasm --no-web-resources-cdn -o build/web-wasm-local >/dev/null)

echo "[2/4] Compose Multiplatform: build Linux (JVM app image) and Web (Kotlin/Wasm)"
(cd compose_home && "$GRADLE" -q --console=plain createReleaseDistributable wasmJsBrowserDistribution >/dev/null 2>&1)

linux() { # $1 label, $2 dir, $3 binary
  echo "$1 package $(du -sm "$2" | cut -f1) MB, tar.gz -9 $(tar -C "$2" -cf - . | gzip -9 | wc -c | awk '{printf "%.1f", $1/1048576}') MB"
  for i in 1 2 3 4 5; do printf '  run %d: %s\n' $i "$(POC_EXIT=1 timeout 30 "$3" 2>&1 | grep -E 'first_frame|rss_mb' | tr '\n' ' ')"; done
}
echo "[3/4] Linux"
linux flutter-linux flutter_home/build/linux/x64/release/bundle flutter_home/build/linux/x64/release/bundle/flutter_home
linux compose-linux compose_home/build/compose/binaries/main-release/app/compose-home \
  compose_home/build/compose/binaries/main-release/app/compose-home/bin/compose-home

echo "[4/4] Web first load (local server, headless Chromium)"
node webmeasure.mjs flutter_home/build/web-local flutter-web-js 4000 | head -1
node webmeasure.mjs flutter_home/build/web-wasm-local flutter-web-wasm 4000 | head -1
node webmeasure.mjs compose_home/build/dist/wasmJs/productionExecutable compose-web-wasm 4000 | head -1
echo "screenshots in out/"
