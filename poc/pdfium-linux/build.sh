#!/usr/bin/env bash
# Builds the PoC-1 renderer against the pinned prebuilt PDFium. Output: out/render
set -euo pipefail
cd "$(dirname "$0")"
P=${PDFIUM:-$HOME/dev/cache/fai-office/pdfium-8111/linux-x64}
mkdir -p out
cc -O2 -Wall -o out/render render.c -I"$P/include" -L"$P/lib" -lpdfium -Wl,-rpath,"$P/lib"
echo "built out/render (PDFium: $P)"
