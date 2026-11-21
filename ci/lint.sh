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

echo "== hardcode: no fixed colour and no bare dp / sp in app/src/commonMain outside theme/ (DESIGN.md section 8)"
# Colours, spacing, sizes and text sizes come from FaiTheme and the generated Fai* tokens. Allowed: Color.Transparent
# and Color.Unspecified (no hue), comment lines, and a line carrying "// hardcode-ok: <reason>" (reviewed exception).
HARDCODE_RE='Color\(\s*(0x|[0-9]|red\s*=)|Color\.[A-Z][A-Za-z]*|\bDp\(\s*[0-9]|\b[0-9]+(\.[0-9]+)?f?\.(dp|sp)\b'
hardcode_hits() {
    local f
    for f in "$@"; do
        sed -E 's/Color\.(Transparent|Unspecified)\b//g' "$f" | grep -nE "$HARDCODE_RE" |
            grep -vE '^[0-9]+:\s*(//|\*|/\*)' | grep -vE '// hardcode-ok: \S' | sed "s#^#$f:#" || true
    done
}
# Self-test: five planted violations must be caught, the four allowed lines must pass.
probe="$LOG_DIR/hardcode-selftest.kt"
cat >"$probe" <<'KT'
val a = Color(0xFF112233)
val b = Color.Red
val c = 12.dp
val d = TextStyle(fontSize = 14.sp)
val e = Color.Transparent; val f = 4.dp
val g = Color.Transparent
val h = FaiSpace.s12 * 2
val i = 3.dp // hardcode-ok: planted exception
// 12.dp inside a comment
KT
caught=$(hardcode_hits "$probe" | wc -l)
if [ "$caught" -ne 5 ]; then
    echo "!! hardcode self-test: expected 5 planted violations, caught $caught:"
    hardcode_hits "$probe" | sed 's/^/   /'
    exit 1
fi
echo "   self-test: 5/5 planted violations caught, allowed lines pass"
mapfile -t ui_sources < <(find app/src/commonMain -name '*.kt' ! -path '*/org/faioffice/app/theme/*' | sort)
hits=$(hardcode_hits "${ui_sources[@]}")
if [ -n "$hits" ]; then
    echo "!! fixed colour or size outside the theme (use FaiTheme.colors / FaiSpace / FaiSize / FaiRadius / FaiTheme.type,"
    echo "   or add a token in design/tokens/office.tokens.json; a real exception gets '// hardcode-ok: <reason>'):"
    echo "$hits" | sed 's/^/   /'
    exit 1
fi
echo "== hardcode passed (${#ui_sources[@]} files)"

echo "== tokens: generated Compose tokens match design/tokens/*.json (tools/tokens/gen_compose.py)"
python3 tools/tokens/gen_compose.py --check

echo "== i18n: string and casing gate (ci/i18n.sh)"
bash ci/i18n.sh

run_gradle lint ktlintCheck detekt
