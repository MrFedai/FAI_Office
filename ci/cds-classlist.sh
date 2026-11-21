#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
# not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Writes the AppCDS class list app/cds/common/fai.classlist (P2.8b, risk R7): the classes the desktop app loads up
# to its first frames and a second at rest. The installed app dumps its CDS archive from this list on its first start
# (app/src/desktopMain/.../CdsArchive.kt). Run it after a change that loads many new classes at start (new library,
# new start screen); a stale list only makes the start slower again, it never breaks it.
#   1. Linux: builds the desktop distribution and records one start under -XX:DumpLoadedClassList (needs a display;
#      WSLg works)
#   2. Windows (from WSL, if ci/windows-run.sh installed a CI image): the same for the Windows image, so the
#      Windows-only AWT / Skiko classes are in the list too
#   3. merges the lists into the committed one (ids and comments removed, duplicates dropped); FRESH=1 starts from an
#      empty list instead of keeping the committed lines (drops classes the app no longer loads)
source "$(dirname "$0")/lib.sh"
source "$ROOT/ci/windows-lib.sh"
OUT=app/cds/common/fai.classlist
work="$LOG_DIR/cds"
rm -rf "$work"
mkdir -p "$work"
# Only classes of the built-in loaders, without the per-run ids (merged lists would carry clashing ids).
normalize() { tr -d '\r' <"$1" | grep -v '^#' | sed -E 's/ id: [0-9]+$//' | grep -v ' source: ' || true; }

echo "== [1/3] Linux: build, one recorded start"
[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] || { echo "!! no display (DISPLAY / WAYLAND_DISPLAY)"; exit 1; }
run_gradle cds :app:createDistributable
(cd "$work" && _JAVA_OPTIONS="-XX:DumpLoadedClassList=linux.classlist" FAI_BUDGET_PROBE=1 \
    timeout 120 "$ROOT/$DESKTOP_DIST/fai-office/bin/fai-office" >/dev/null 2>&1) || true
[ -s "$work/linux.classlist" ] || { echo "!! the app wrote no class list"; exit 1; }
normalize "$work/linux.classlist" >"$work/linux.norm"
echo "   Linux: $(wc -l <"$work/linux.norm") classes"

echo "== [2/3] Windows CI image (WSL)"
: >"$work/windows.norm"
inst=""
if ! win_reachable; then
    echo "   no Windows PC reachable (WSL interop): skipped"
elif ! inst=$(win_install_dir) || [ ! -f "$inst/$WIN_APP/$WIN_APP.exe" ]; then
    echo "   no Windows image (ci/windows-run.sh installs the CI build): skipped"
elif [ -n "$(win_running_from "$inst")" ]; then
    echo "   FAI Office is running from the CI build folder (close it): skipped"
else
    rm -f "$inst/$WIN_APP/windows.classlist"
    # Relative path: the launcher runs in its own folder (win_probe), so no Windows path inside _JAVA_OPTIONS.
    _JAVA_OPTIONS="-XX:DumpLoadedClassList=windows.classlist" WSLENV="${WSLENV:+$WSLENV:}_JAVA_OPTIONS" \
        win_probe "$inst/$WIN_APP/$WIN_APP.exe" 1 >/dev/null
    if [ -s "$inst/$WIN_APP/windows.classlist" ]; then
        normalize "$inst/$WIN_APP/windows.classlist" >"$work/windows.norm"
        echo "   Windows ($(cat "$inst/BUILD.txt" 2>/dev/null || echo "unknown build")): $(wc -l <"$work/windows.norm") classes"
    else
        echo "   the Windows app wrote no class list: skipped"
    fi
    rm -f "$inst/$WIN_APP/windows.classlist"
fi

echo "== [3/3] merge into $OUT"
before=0
[ -f "$OUT" ] && before=$(wc -l <"$OUT")
if [ "${FRESH:-0}" = 1 ] || [ ! -f "$OUT" ]; then : >"$work/old.norm"; else cp "$OUT" "$work/old.norm"; fi
cat "$work/old.norm" "$work/linux.norm" "$work/windows.norm" | awk 'NF && !seen[$0]++' >"$work/merged"
mkdir -p "$(dirname "$OUT")"
mv "$work/merged" "$OUT"
echo "== $OUT: $before -> $(wc -l <"$OUT") lines${FRESH:+ (FRESH=$FRESH)}; commit it with the change that needed it"
