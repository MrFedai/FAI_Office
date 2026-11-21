#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
# not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Runs the CI Windows build on this PC, from WSL (P2.10). Finds the newest CI run of `dev` that has the
# fai-office-windows artifact (the `windows` job of .github/workflows/ci.yml), downloads it with gh, installs it into
# %LOCALAPPDATA%\FAI_Office\ci-build (replacing the previous CI build there; nothing else is touched) and starts it.
# An already installed run is not downloaded again. ci/budget.sh step 5 measures this copy.
#
#   ci/windows-run.sh                        newest Windows build of dev
#   BRANCH=task/7-x ci/windows-run.sh        newest Windows build of another branch (e.g. after workflow_dispatch)
#   RUN_ID=44932462742 ci/windows-run.sh     that CI run
#   FAI_RUN_START=0 ci/windows-run.sh        install only, do not start
#   WIN_INSTALL_DIR=/mnt/d/fai-ci ...         another install folder (WSL path; ci/budget.sh reads the same variable)
source "$(dirname "$0")/lib.sh"
source "$ROOT/ci/windows-lib.sh"
ARTIFACT=fai-office-windows
BRANCH=${BRANCH:-dev}
die() {
    echo "!! $*"
    exit 1
}

echo "== [1/4] checks: WSL interop, gh"
win_reachable || die "needs WSL with Windows interop (cmd.exe, powershell.exe)"
command -v gh >/dev/null || die "gh (GitHub CLI) not found"
gh auth status >/dev/null 2>&1 || die "gh is not logged in: gh auth login"
inst=$(win_install_dir) || die "could not read %LOCALAPPDATA% from Windows"
echo "   Windows: $(win_describe)"
echo "   install folder: $(wslpath -w "$(dirname "$inst")")\\$(basename "$inst")"

echo "== [2/4] CI run with the $ARTIFACT artifact"
if [ -n "${RUN_ID:-}" ]; then
    runs=$(gh run view "$RUN_ID" --json databaseId,headSha,headBranch,createdAt,conclusion \
        --jq '"\(.databaseId) \(.headSha[0:7]) \(.headBranch) \(.createdAt) \(.conclusion)"')
else
    runs=$(gh run list --workflow ci.yml --branch "$BRANCH" --limit 10 \
        --json databaseId,headSha,headBranch,createdAt,status,conclusion \
        --jq '.[] | select(.status == "completed") |
            "\(.databaseId) \(.headSha[0:7]) \(.headBranch) \(.createdAt) \(.conclusion)"')
fi
[ -n "$runs" ] || die "no completed CI run${RUN_ID:+ $RUN_ID} on ${BRANCH} (push it, or start the CI workflow)"
run="" art=""
while read -r id sha branch created conclusion; do
    printf '   run %s (%s, %s, %s, %s): ' "$id" "$sha" "$branch" "$created" "$conclusion"
    art=$(gh api "repos/{owner}/{repo}/actions/runs/$id/artifacts" \
        --jq ".artifacts[] | select(.name == \"$ARTIFACT\" and (.expired | not)) | \"\(.id) \(.size_in_bytes)\"" 2>/dev/null) || true
    if [ -n "$art" ]; then
        echo "has $ARTIFACT"
        run="$id $sha $branch $created"
        break
    fi
    echo "no $ARTIFACT (Windows job failed, not run, or expired)"
done <<<"$runs"
[ -n "$run" ] || die "none of these runs has a Windows build"
read -r id sha branch created <<<"$run"
read -r art_id art_bytes <<<"$art"
build_line="run=$id commit=$sha branch=$branch built=$created"

echo "== [3/4] install"
running=$(win_running_from "$inst")
[ -z "$running" ] || die "FAI Office is running from $(wslpath -w "$inst") (PID ${running//$'\n'/ }): close it first"
if [ -f "$inst/$WIN_APP/$WIN_APP.exe" ] && [ "$(cat "$inst/BUILD.txt" 2>/dev/null)" = "$build_line" ]; then
    echo "   run $id is already installed, no download"
else
    tmp="$LOG_DIR/windows-run"
    rm -rf "$tmp"
    mkdir -p "$tmp"
    zip="$tmp/$ARTIFACT.zip"
    echo "   downloading $ARTIFACT of run $id ($((art_bytes / 1048576)) MB zipped)"
    gh api "repos/{owner}/{repo}/actions/artifacts/$art_id/zip" >"$zip" 2>"$tmp/gh.err" &
    dl=$!
    t0=$SECONDS
    while kill -0 "$dl" 2>/dev/null; do
        sleep 2
        got=$(stat -c%s "$zip" 2>/dev/null || echo 0)
        el=$((SECONDS - t0))
        [ "$el" -gt 0 ] || el=1
        rate=$((got / el))
        eta=$((rate > 0 ? (art_bytes - got) / rate : 0))
        [ "$eta" -ge 0 ] || eta=0
        printf '   %3d%%  %d / %d MB  %d KB/s  ETA %s\n' $((art_bytes > 0 ? 100 * got / art_bytes : 0)) \
            $((got / 1048576)) $((art_bytes / 1048576)) $((rate / 1024)) "$(mmss "$eta")"
    done
    new="$inst.new"
    rm -rf "$new"
    mkdir -p "$new"
    if wait "$dl" && unzip -tqq "$zip" >/dev/null 2>&1; then
        total=$(unzip -Z1 "$zip" | grep -vc '/$')
        echo "   extracting $total files into $(wslpath -w "$new")"
        unzip -o "$zip" -d "$new" | awk -v t="$total" '/inflating:|extracting:/ { n++; p = int(100 * n / t)
            if (p >= last + 10) { printf "   %3d%%  %d / %d files\n", p, n, t; last = p; fflush() } }'
    else
        echo "   direct download failed ($(head -1 "$tmp/gh.err")); gh run download instead (no progress line)"
        gh run download "$id" -n "$ARTIFACT" -D "$new" || die "download failed"
    fi
    [ -f "$new/$WIN_APP/$WIN_APP.exe" ] || die "the artifact has no $WIN_APP/$WIN_APP.exe"
    echo "$build_line" >"$new/BUILD.txt"
    if [ -d "$inst" ]; then
        echo "   replacing the previous CI build: $(cat "$inst/BUILD.txt" 2>/dev/null || echo unknown)"
        rm -rf "$inst"
    fi
    mv "$new" "$inst"
    rm -rf "$tmp"
fi
printf '   installed: %s (%d MB on disk)\n' "$(wslpath -w "$inst/$WIN_APP/$WIN_APP.exe")" \
    $(($(du -sb "$inst/$WIN_APP" | cut -f1) / 1048576))

echo "== [4/4] start"
if [ "${FAI_RUN_START:-1}" = 0 ]; then
    echo "   FAI_RUN_START=0: not started"
else
    (cd "$inst/$WIN_APP" && cmd.exe /d /c start "" "$WIN_APP.exe" 2>/dev/null)
    echo "   started (window opens on Windows). Measure: ci/budget.sh (step 5 uses this copy)"
fi
