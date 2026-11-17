#!/usr/bin/env bash
# PoC-4: builds the LibreOfficeKit renderer and runs it on the seed corpus, cold and warm.
# Usage: run.sh   (needs the unpacked LibreOffice and LOK headers from poc/README.md "Pinned inputs")
set -euo pipefail
cd "$(dirname "$0")"
LO=${LO_PROGRAM:-$(ls -d "$HOME"/dev/cache/fai-office/lo-*/root/opt/libreoffice*/program | head -1)}
INC=${LOK_INCLUDE:-$(ls -d "$HOME"/dev/cache/fai-office/lok-headers-* | head -1)}
C=../corpus/out
mkdir -p out
c++ -O2 -Wall -o out/render render.cpp -I"$INC" -ldl
echo "engine: $LO"
echo "libmergedlo.so $(( $(stat -c%s "$LO/libmergedlo.so") / 1048576 )) MB; program dir $(du -sm "$LO" | cut -f1) MB; install $(du -sm "$LO/.." | cut -f1) MB"
# First run creates the user profile; it is not measured.
rm -rf out/profile
echo "--- profile warm-up (not measured)"
out/render "$LO" out "$C/typical-20p.odt" >/dev/null 2>&1 || true
for order in "typical-20p.docx typical.xlsx typical.pptx typical-20p.docx" \
             "typical.xlsx typical.pptx typical-20p.docx" \
             "typical.pptx typical-20p.docx typical.xlsx"; do
  echo "--- run: $order"
  files=(); for f in $order; do files+=("$C/$f"); done
  out/render "$LO" out "${files[@]}" 2>/dev/null
done
