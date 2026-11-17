#!/usr/bin/env bash
# PoC WP7: after build.sh, relinks soffice with the PoC's pre-js (test/include.js), assembles a web dir from the
# emscripten installation + test page + corpus documents, and measures it in headless Chromium (measure.mjs).
# Usage: run.sh
set -euo pipefail
cd "$(dirname "$0")"
CACHE=$HOME/dev/cache/fai-office/lo-src
SRC=${LO_WASM_SRC:-$CACHE/core-wasm}
VOLS=(-v "$SRC":/src -v "$CACHE/tarballs":/tarballs -v "$PWD/out":/out -v "$PWD/test":/fai:ro
      -v "$CACHE/ccache-wasm":/home/builder/.ccache)

echo "[1/3] relink soffice with test/include.js (EMSCRIPTEN_EXTRA_SOFFICE_PRE_JS) and refresh the installation"
start=$(date +%s)
docker run --rm "${VOLS[@]}" fai-lo-wasm bash -c \
  "make -j$(nproc) build EMSCRIPTEN_EXTRA_SOFFICE_PRE_JS=/fai/include.js > /out/relink.log 2>&1" \
  || { echo "relink failed:"; tail -20 out/relink.log; exit 1; }
echo "    done in $(( $(date +%s) - start )) s"

echo "[2/3] assemble out/web"
INST=$SRC/workdir/installation/LibreOffice/emscripten
rm -rf out/web && mkdir -p out/web/docs
cp -r "$INST"/. out/web/
cp test/index.html test/fai_test.js out/web/
cp ../corpus/out/{typical-20p.docx,typical.xlsx,typical.pptx} out/web/docs/
ls -la out/web | awk 'NR>1 {printf "    %8.1f MB  %s\n", $5/1048576, $9}'

echo "[3/3] measure in headless Chromium"
node measure.mjs out/web 900
