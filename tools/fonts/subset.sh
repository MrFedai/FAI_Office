#!/usr/bin/env bash
# Subsets IBM Plex Sans (400/500/600/700) and IBM Plex Mono (400/500) into the Compose resources, with the OFL
# texts beside them. Coverage: Basic Latin, Latin-1, Latin Extended-A (Turkish İ ı Ş ş Ğ ğ), common punctuation,
# currency (€ ₺), arrows and math signs. Document text uses the document's own fonts (MASTER_SPEC section 6),
# so the UI fonts stay small.
#
# Usage: bash tools/fonts/subset.sh [SOURCE_DIR]
#   SOURCE_DIR  folder with the full IBMPlexSans-*.ttf / IBMPlexMono-*.ttf and OFL-IBMPlex{Sans,Mono}.txt
#               (default: ~/PROJECTS/FAI_Downloader/assets/fonts)
# Tool: fonttools (MIT) in ~/dev/venv-fonttools, created on first run.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC=${1:-$HOME/PROJECTS/FAI_Downloader/assets/fonts}
OUT="$ROOT/app/src/commonMain/composeResources/font"
VENV="$HOME/dev/venv-fonttools"

if [ ! -x "$VENV/bin/pyftsubset" ]; then
    echo "== fonttools: creating $VENV"
    python3 -m venv "$VENV"
    "$VENV/bin/pip" install --quiet fonttools
fi

UNICODES="U+0000-007E,U+00A0-00FF,U+0100-017F,U+0307,U+02C6,U+02DA,U+02DC,U+2010-2027,U+2030,U+2032-2033,\
U+2039-203A,U+2044,U+20AC,U+20BA,U+2116,U+2122,U+2190-2195,U+2212,U+2215,U+2248,U+2260,U+2264-2265,U+25CF,U+2713"
FEATURES="kern,liga,calt,ccmp,locl,mark,mkmk,tnum,lnum,case"

# source file : output name
FONTS=(
    "IBMPlexSans-Regular.ttf:ibm_plex_sans_regular.ttf"
    "IBMPlexSans-Medium.ttf:ibm_plex_sans_medium.ttf"
    "IBMPlexSans-SemiBold.ttf:ibm_plex_sans_semibold.ttf"
    "IBMPlexSans-Bold.ttf:ibm_plex_sans_bold.ttf"
    "IBMPlexMono-Regular.ttf:ibm_plex_mono_regular.ttf"
    "IBMPlexMono-Medium.ttf:ibm_plex_mono_medium.ttf"
)

mkdir -p "$OUT"
echo "== subset ${#FONTS[@]} fonts from $SRC into ${OUT#"$ROOT"/}"
n=0 before=0 after=0
for pair in "${FONTS[@]}"; do
    src="$SRC/${pair%%:*}" dst="$OUT/${pair##*:}"
    n=$((n + 1))
    "$VENV/bin/pyftsubset" "$src" --output-file="$dst" --unicodes="$UNICODES" --layout-features="$FEATURES" \
        --no-hinting --name-IDs='0,1,2,3,4,5,6,13,14' --name-languages='*'
    s=$(stat -c %s "$src") d=$(stat -c %s "$dst")
    before=$((before + s)) after=$((after + d))
    printf '  [%d/%d] %-28s %5d KB -> %4d KB\n' "$n" "${#FONTS[@]}" "${pair##*:}" $((s / 1024)) $((d / 1024))
done
cp "$SRC/OFL-IBMPlexSans.txt" "$SRC/OFL-IBMPlexMono.txt" "$OUT/"
echo "== total: $((before / 1024)) KB -> $((after / 1024)) KB (uncompressed); OFL texts copied"
echo "   gzip: $(cat "$OUT"/*.ttf | gzip -9 | wc -c | awk '{printf "%d KB", $1 / 1024}')"
