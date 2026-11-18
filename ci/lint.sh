#!/usr/bin/env bash
# Lint: MPL-2.0 header on every Kotlin source, ktlint (formatting), detekt (code smells).
source "$(dirname "$0")/lib.sh"

echo "== header: MPL-2.0 notice on every Kotlin source file"
missing=$(find app core androidApp -path '*/build' -prune -o -name '*.kt' -print |
    while read -r f; do head -2 "$f" | grep -q 'Mozilla Public License, v. 2.0' || echo "$f"; done)
if [ -n "$missing" ]; then
    echo "!! missing MPL-2.0 header (copy it from core/src/commonMain/kotlin/org/faioffice/core/AppInfo.kt):"
    echo "$missing" | sed 's/^/   /'
    exit 1
fi
echo "== header passed"

echo "== tokens: generated Compose tokens match design/tokens/*.json (tools/tokens/gen_compose.py)"
python3 tools/tokens/gen_compose.py --check

run_gradle lint ktlintCheck detekt
