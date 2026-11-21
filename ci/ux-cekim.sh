#!/usr/bin/env bash
# Screenshot runner for the shared screen package (claude-ortak ux.sh, [ux].cek in .devops.toml).
# Runs the Compose Desktop shot tests and copies <theme>_<size>_<screen>.png into $UX_OUT (set by ux.sh).
# Without UX_OUT the frames stay in app/build/bakis.
source "$(dirname "$0")/lib.sh"
shots=app/build/bakis
rm -rf "$shots"
run_gradle ux-cekim :app:desktopTest --tests '*ShellShotTest*' --tests '*TokenShotTest*' --rerun
count=$(find "$shots" -name '*.png' | wc -l)
[ "$count" -gt 0 ] || { echo "!! no shots in $shots"; exit 1; }
if [ -n "${UX_OUT:-}" ]; then
    mkdir -p "$UX_OUT"
    cp "$shots"/*.png "$UX_OUT"/
    echo "== $count shots -> $UX_OUT"
else
    echo "== $count shots in $shots"
fi
