#!/usr/bin/env bash
# PoC-2: serves index.html with the pinned PDFium WASM and a corpus PDF, measures it in headless Chromium.
# Usage: run.sh [corpus pdf name=long-100p.pdf]   (gzip transfer size is printed separately)
set -euo pipefail
cd "$(dirname "$0")"
W=${PDFIUM_WASM:-$HOME/dev/cache/fai-office/pdfium-8111/wasm/lib}
PDF=${1:-long-100p.pdf}
mkdir -p out/corpus
cp index.html out/
cp "$W/pdfium.js" "$W/pdfium.wasm" out/
cp "../corpus/out/$PDF" out/corpus/
echo "pdfium.wasm raw $(( $(stat -c%s out/pdfium.wasm) / 1024 )) KB, gzip -9 $(( $(gzip -9c out/pdfium.wasm | wc -c) / 1024 )) KB, brotli n/a"
echo "pdfium.js   raw $(( $(stat -c%s out/pdfium.js) / 1024 )) KB, gzip -9 $(( $(gzip -9c out/pdfium.js | wc -c) / 1024 )) KB"
PORT=$((8300 + RANDOM % 500))
python3 -m http.server "$PORT" -d out -b 127.0.0.1 >/dev/null 2>&1 &
SRV=$!
trap 'kill $SRV' EXIT
sleep 0.5
for i in 1 2 3; do
  echo "--- run $i"
  node measure.mjs "http://127.0.0.1:$PORT/?pdf=$PDF"
done
