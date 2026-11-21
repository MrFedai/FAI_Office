#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
# not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Reaching the owner's Windows PC from WSL (interop: cmd.exe, powershell.exe). Sourced by ci/windows-run.sh, which
# installs the CI Windows image, and ci/budget.sh step 5, which measures it. The image lives in
# %LOCALAPPDATA%\FAI_Office\ci-build: fai-office\fai-office.exe plus BUILD.txt (CI run, commit, branch).

WIN_APP=fai-office

# True under WSL with Windows interop.
win_reachable() {
    grep -qi microsoft /proc/version 2>/dev/null && command -v cmd.exe >/dev/null &&
        command -v powershell.exe >/dev/null && command -v wslpath >/dev/null
}
# Windows tools run from C:\ (cmd.exe warns about a \\wsl.localhost current directory); output without CR. The exit
# status is always 0 (callers read the output): PowerShell exits 1 after any non-terminating error.
win_cmd() { (cd /mnt/c && cmd.exe /d /c "$@" 2>/dev/null | tr -d '\r') || true; }
win_ps() { (cd /mnt/c && powershell.exe -NoProfile -NonInteractive -Command "$1" 2>/dev/null | tr -d '\r') || true; }
# WIN_INSTALL_DIR (WSL path, e.g. /mnt/d/fai-ci) puts the image elsewhere.
win_install_dir() {
    local local_app_data
    [ -z "${WIN_INSTALL_DIR:-}" ] || { echo "$WIN_INSTALL_DIR"; return 0; }
    local_app_data=$(win_cmd "echo %LOCALAPPDATA%")
    [ -n "$local_app_data" ] && [ "$local_app_data" != "%LOCALAPPDATA%" ] || return 1
    echo "$(wslpath -u "$local_app_data")/FAI_Office/ci-build"
}
# "Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM"
win_describe() { # shellcheck disable=SC2016  # PowerShell variables, not shell ones
    win_ps '$o = Get-CimInstance Win32_OperatingSystem; $c = Get-CimInstance Win32_ComputerSystem;
        "{0} {1}, {2:N1} GB RAM" -f $o.Caption, $o.Version, ($c.TotalPhysicalMemory / 1GB)'
}
# PIDs of fai-office.exe processes started from <dir> (WSL path), one per line.
win_running_from() {
    [ -d "$1" ] || return 0
    win_ps "Get-Process $WIN_APP -ErrorAction SilentlyContinue |
        Where-Object { \$_.Path -like '$(wslpath -w "$1")*' } | ForEach-Object { \$_.Id }"
}

# win_probe <exe (WSL path)> <settle s>
# One launch with the FAI_BUDGET_PROBE hook (app/src/desktopMain/.../BudgetProbe.kt). Prints
# "<first frame ms> <working set bytes> <private bytes>" ("?" for a missing value). Memory is read from outside
# with Get-Process once the app reports it is at rest: WorkingSet64 (resident pages including shared ones, like
# Linux RSS, not PSS) and PrivateMemorySize64 (committed private memory). The app is then ended with taskkill.
win_probe() {
    local exe=$1 settle=$2 dir out line pid="" ff="" mem="" launcher t=0
    dir=$(dirname "$exe")
    out="$dir/probe.txt"
    rm -f "$out"
    (cd "$dir" && FAI_BUDGET_PROBE=$settle FAI_BUDGET_OUT="$(wslpath -w "$out")" \
        WSLENV="${WSLENV:+$WSLENV:}FAI_BUDGET_PROBE:FAI_BUDGET_OUT" "./$(basename "$exe")" >/dev/null 2>&1) &
    launcher=$!
    while [ $t -lt $((settle + 90)) ]; do
        sleep 1
        t=$((t + 1))
        [ -f "$out" ] || continue
        line=$(tr -d '\r' <"$out")
        ff=$(sed -n 's/^fai_budget first_frame_ms //p' <<<"$line")
        pid=$(sed -n 's/^fai_budget at_rest pid //p' <<<"$line")
        [ -z "$pid" ] || break
    done
    if [ -n "$pid" ]; then
        mem=$(win_ps "\$p = Get-Process -Id $pid -ErrorAction SilentlyContinue; if (\$p) {
            '{0} {1}' -f \$p.WorkingSet64, \$p.PrivateMemorySize64 }")
        win_cmd "taskkill /PID $pid /F" >/dev/null || true
    else
        for pid in $(win_running_from "$dir"); do win_cmd "taskkill /PID $pid /F" >/dev/null || true; done
    fi
    wait "$launcher" 2>/dev/null || true
    rm -f "$out"
    echo "${ff:-?} ${mem:-? ?}"
}
