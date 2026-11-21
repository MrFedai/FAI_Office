#!/usr/bin/env bash
# Web (Kotlin/Wasm) production build and its first-load size, gzip -9, against the budget (BUDGET_WEB_GZIP_MB in
# ci/budgets.env). Counted: every file of the distribution except source maps. WEB_LIMIT_KB overrides the limit (tests).
source "$(dirname "$0")/lib.sh"
run_gradle web :app:wasmJsBrowserDistribution
check_size "web first load (gzip)" "$(web_gzip_bytes)" $((${WEB_LIMIT_KB:-$((BUDGET_WEB_GZIP_MB * 1024))} * 1024))
