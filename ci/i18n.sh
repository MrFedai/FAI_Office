#!/usr/bin/env bash
# i18n gate: (1) no user-visible string literals in app/src/*Main, (2) values and values-tr have the same keys,
# (3) bare case conversion only in core/.../text/Casing.kt. Prints file:line for each violation, exits 1 on any.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"
fail=0

echo "== i18n: no user-visible string literals in app/src/*Main (strings live in composeResources)"
srcs=$(find app/src -path '*/build' -prune -o -path 'app/src/*Main/*' -name '*.kt' ! -name 'FaiTokens.kt' -print)
hits=$(echo "$srcs" | xargs -r grep -nE '\b(Text|BasicText|BasicTextField|TextField)\(\s*"|contentDescription\s*=\s*"|\b(text|label|title|placeholder)\s*=\s*"' || true)
if [ -n "$hits" ]; then
    echo "!! string literal shown to the user (use stringResource):"
    echo "$hits" | sed 's/^/   /'
    fail=1
fi

echo "== i18n: values and values-tr have the same keys"
res=app/src/commonMain/composeResources
keys() { grep -o '<string name="[^"]*"' "$1" | sed 's/.*name="//;s/"$//' | sort; }
diffk=$(diff <(keys $res/values/strings.xml) <(keys $res/values-tr/strings.xml) || true)
if [ -n "$diffk" ]; then
    echo "!! key sets differ (< only in values, > only in values-tr):"
    echo "$diffk" | sed 's/^/   /'
    fail=1
fi

echo "== i18n: bare uppercase()/lowercase()/capitalize only in Casing.kt"
bare=$(find app/src core/src -path '*/build' -prune -o -path '*/src/*Main/*' -name '*.kt' ! -path '*/core/text/Casing.kt' -print |
    xargs -r grep -nE '\.(uppercase|lowercase|capitalize|decapitalize|toUpperCase|toLowerCase|titlecase)\(\)' || true)
if [ -n "$bare" ]; then
    echo "!! bare case conversion (use uppercase(language) / lowercase(language) from org.faioffice.core.text):"
    echo "$bare" | sed 's/^/   /'
    fail=1
fi

[ "$fail" -eq 0 ] && echo "== i18n passed"
exit "$fail"
