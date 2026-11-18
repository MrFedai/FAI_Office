#!/usr/bin/env bash
# Web (Kotlin/Wasm) production build and its first-load size, gzip -9, against the 5 MB budget.
# Counted: every file of the distribution except source maps. WEB_LIMIT_KB overrides the limit (tests).
source "$(dirname "$0")/lib.sh"
run_gradle web :app:wasmJsBrowserDistribution
dist=app/build/dist/wasmJs/productionExecutable
total=0
while IFS= read -r -d '' f; do
    total=$((total + $(gzip -9c "$f" | wc -c)))
done < <(find "$dist" -type f ! -name '*.map' -print0)
check_size "web first load (gzip)" "$total" $((${WEB_LIMIT_KB:-5120} * 1024))
