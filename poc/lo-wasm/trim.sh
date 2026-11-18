#!/usr/bin/env bash
# PoC WP7 part 3: repackages the engine's file-system image (soffice.data) without the dialog definitions
# (soffice.cfg/**/*.ui except the three the engine reads headless), gallery, wizards and splash images, and with only the product fonts
# (Liberation, Carlito, Caladea) plus OpenSymbol. No C++ rebuild: soffice.js reads the image through its separate
# metadata file, so soffice.js / soffice.wasm from out/web (run.sh, relinked with test/include.js) are reused.
# Then measures the result like run.sh. Throwaway (MASTER_SPEC section 15).
# Usage: trim.sh [out dir name=web-trim]
#        TRIM='fonts ui extra' (default: all three) picks what is removed, for bisecting.
set -euo pipefail
cd "$(dirname "$0")"
CACHE=$HOME/dev/cache/fai-office/lo-src
SRC=${LO_WASM_SRC:-$CACHE/core-wasm}
NAME=${1:-web-trim}
TRIM=${TRIM:-fonts ui extra}
LIST=$SRC/workdir/CustomTarget/static/emscripten_fs_image/soffice.data.filelist
[ -f out/web/soffice.js ] || { echo "out/web missing: run run.sh first"; exit 1; }

echo "[1/3] filter the file list ($LIST)"
mkdir -p "out/$NAME"
python3 - "$LIST" "out/$NAME/filelist" "$SRC" "$TRIM" <<'EOF'
import os, re, sys
src_list, out_list, root, trim = sys.argv[1:]
trim = trim.split()
keep_font = re.compile(r'/fonts/.*/(Liberation|Carlito|Caladea|opens___)[^/]*$')
# The only dialog definitions the engine opens while loading Writer, Calc and Impress documents headless (traced
# with an FS.open hook, see RESULTS.md WP7 part 3).
keep_ui = re.compile(r'/soffice\.cfg/(svt/ui/(scrollbars|tabbuttons)|modules/simpress/ui/tabviewbar)\.ui$')
drop = [re.compile(p) for p in ([r'/soffice\.cfg/.*\.ui$'] if 'ui' in trim else []) +
        ([r'/share/gallery/', r'/share/config/wizard/', r'/program/intro[^/]*\.png$'] if 'extra' in trim else [])]
kept, removed, kb, rb = [], [], 0, 0
for e in open(src_list).read().split():
    dst = e.partition('@')[2] or e
    size = os.path.getsize(os.path.join(root, e.partition('@')[0]))
    out = (('fonts' in trim and '/fonts/' in dst and not keep_font.search(dst) and not dst.endswith('.conf'))
           or (any(d.search(dst) for d in drop) and not keep_ui.search(dst)))
    (removed if out else kept).append(e)
    if out: rb += size
    else: kb += size
open(out_list, 'w').write(' '.join(kept))
print(f'    kept {len(kept)} files {kb/1048576:.1f} MB, removed {len(removed)} files {rb/1048576:.1f} MB')
EOF

echo "[2/3] file_packager (Docker image fai-lo-wasm)"
docker run --rm -v "$SRC":/src -v "$PWD/out":/out fai-lo-wasm bash -c \
  "python3 /emsdk/upstream/emscripten/tools/file_packager.py /out/$NAME/soffice.data \
     --preload \$(cat /out/$NAME/filelist) --js-output=/out/$NAME/soffice.data.js --separate-metadata >/dev/null"
rm -f "out/$NAME/soffice.data.js" "out/$NAME/filelist"   # the loader code is already linked into soffice.js
cp out/web/soffice.js out/web/soffice.wasm out/web/favicon.ico "out/$NAME/"
mkdir -p "out/$NAME/docs" && cp out/web/docs/* "out/$NAME/docs/"
cp test/index.html test/fai_test.js "out/$NAME/"
ls -la "out/$NAME" | awk 'NR>1 && $5>0 {printf "    %8.1f MB  %s\n", $5/1048576, $9}'

echo "[3/3] measure in headless Chromium"
node measure.mjs "out/$NAME" 600
