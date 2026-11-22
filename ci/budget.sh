#!/usr/bin/env bash
# Phase 2 budget tests (PHASE2_PLAN P2.8): measures every section 7 budget that can be measured here, compares it
# with ci/budgets.env and writes one report per run: reports/budget/<date>_<time>_<commit>.md, copied to LATEST.md.
# Exit code 1 if a counted value is over its budget (or the build fails); "not measured" is not a failure.
#
#   ci/budget.sh                     build, measure, report (about 2 min on a warm Gradle cache, plus device runs)
#   BUDGET_APK_MB=1 ci/budget.sh     deliberate regression: must exit 1 (any BUDGET_* in ci/budgets.env works)
#
# What is measured where:
#   always             APK size, Web initial load (gzip), Linux package (createDistributable folder)
#   adb device         Android cold start (`am start -W` TotalTime, median) and Home PSS (`dumpsys meminfo`);
#                      the device model goes into the report, anything but a Galaxy A55 5G is marked as such.
#                      The unsigned release APK is installed signed with the local debug key (~/.android); a device
#                      holding a differently signed build needs BUDGET_INSTALL_APK=<signed.apk> or =none (measure
#                      what is installed). Several devices: ANDROID_SERIAL picks one.
#   Linux + display    desktop first frame and RAM at rest (FAI_BUDGET_PROBE hook in the desktop app); under WSL
#                      (WSLg) it is reported but not counted: the budget is for a native Linux desktop.
#   Windows PC (WSL)   installed size, first frame and RAM at rest of the CI Windows image that ci/windows-run.sh
#                      installed (%LOCALAPPDATA%\FAI_Office\ci-build, or WIN_INSTALL_DIR); RAM is the process working
#                      set read with PowerShell Get-Process (no /proc on Windows). No image: "not measured".
# Seeded library (PHASE3_PLAN P3.9): Android and both desktops measure Home with 100 recent files and 2 added
# folders, written by the app itself on an explicit trigger and never over a library of the user's:
#   Android   one launch with `--ez org.faioffice.extra.BUDGET_SEED true` before the warm-up; the app writes the seed
#             into its library only when that is empty (or an earlier seed) and logs `fai_budget seed ...` (logcat
#             FaiFiles). A device whose library holds the user's files is measured as it is, the note says so;
#             BUDGET_ANDROID_CLEAR=1 clears the app's data first (`pm clear`, deletes that library).
#   desktop   FAI_BUDGET_SEED=<folder> on every launch: the app keeps its library in that folder (never the user's
#             app data folder) with real seeded files; build/ci/budget-seed (Linux) and <image>\budget-seed
#             (Windows) are emptied before the warm-up, which writes the seed; the timed launches only read it.
# The report notes say "seeded 100+2" (or what the library held instead).
# Knobs: BUDGET_RUNS (timed runs, default 5), BUDGET_SETTLE_S (seconds at rest before RAM, default 5),
#        BUDGET_REPORT_DIR (default reports/budget), BUDGET_REF_MODEL_RE (reference model, default SM-A556),
#        ADB (adb binary, default from PATH).
source "$(dirname "$0")/lib.sh"
source "$ROOT/ci/windows-lib.sh"

RUNS=${BUDGET_RUNS:-5}
SETTLE=${BUDGET_SETTLE_S:-5}
REPORT_DIR=${BUDGET_REPORT_DIR:-reports/budget}
REF_MODEL_RE=${BUDGET_REF_MODEL_RE:-SM-A556} # Samsung Galaxy A55 5G (SM-A556B/E and regional variants)
PKG=org.faioffice
ACTIVITY=$PKG/org.faioffice.android.MainActivity
ADB=${ADB:-adb}
M="$LOG_DIR/budget-measurements.tsv"
: >"$M"

# row <key> <value or comma-separated runs> <device / environment> <counted 1|0> <note>
row() { printf '%s\t%s\t%s\t%s\t%s\n' "$@" >>"$M"; }
# seed_note "<recent> <folders> <outcome>": the report note for a `fai_budget seed` line (empty: no line seen).
seed_note() {
    local r f o
    read -r r f o <<<"$1"
    case "$o" in
    seeded | already) echo "seeded $r+$f" ;;
    refused) echo "not seeded: the library held $r recent, $f folders of its own" ;;
    notsaved) echo "seed not saved ($r+$f in memory only)" ;;
    *) echo "seed not confirmed (no fai_budget seed line)" ;;
    esac
}
mb() { awk -v b="$1" 'BEGIN { printf "%.3f", b / 1048576 }'; }
median() { tr ' ' '\n' | sort -n | awk '{ a[NR] = $1 } END { print (NR % 2) ? a[(NR + 1) / 2] : (a[NR / 2] + a[NR / 2 + 1]) / 2 }'; }

if grep -qi microsoft /proc/version 2>/dev/null; then
    WSL=1
    HOST="WSL2, kernel $(uname -r)"
else
    WSL=0
    HOST="$(uname -s) $(uname -r)"
    [ -r /etc/os-release ] && HOST="$(. /etc/os-release && echo "${PRETTY_NAME:-$NAME}"), kernel $(uname -r)"
fi

echo "== [1/5] report self-test (deliberate regression on planted values)"
python3 tools/budget/report.py --self-test

echo "== [2/5] build: release APK, desktop distribution, Web distribution"
run_gradle budget :androidApp:assembleRelease :app:createDistributable :app:wasmJsBrowserDistribution

echo "== [3/5] sizes"
row apk_mb "$(mb "$(apk_bytes)")" "build output ($(basename "$(apk_file)"))" 1 ""
row web_gzip_mb "$(mb "$(web_gzip_bytes)")" "build output" 1 ""
if [ "$(uname -s)" = Linux ]; then
    row desktop_pkg_mb "$(mb "$(desktop_bytes)")" "Linux app image, built on $HOST" 1 ""
else
    row desktop_pkg_mb "$(mb "$(desktop_bytes)")" "$(uname -s) app image" 0 "not the Linux package"
fi

echo "== [4/5] Android on the reference phone (adb)"
android_measure() {
    local reason="" serial model ram where apk bt i out total wait state pss seed seeded times=() psses=()
    if ! command -v "$ADB" >/dev/null; then
        reason="no device (adb not found)"
    else
        serial=${ANDROID_SERIAL:-$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}
        [ -n "$serial" ] || reason="no device"
    fi
    if [ -n "$reason" ]; then
        echo "   $reason: Android rows not measured"
        row android_cold_start_ms "" "–" 1 "$reason"
        row android_home_pss_mb "" "–" 1 "$reason"
        return 0
    fi
    a() { "$ADB" -s "$serial" "$@" | tr -d '\r'; }
    model=$(a shell getprop ro.product.model)
    ram=$(a shell cat /proc/meminfo 2>/dev/null | awk '/^MemTotal:/ { printf ", %.1f GB RAM", $2 / 1048576 }')
    where="$model, Android $(a shell getprop ro.build.version.release)$ram"
    [[ $model =~ $REF_MODEL_RE ]] || where="$where (not the reference device)"
    echo "   device $serial: $where"

    apk=${BUDGET_INSTALL_APK:-}
    if [ -z "$apk" ]; then
        bt=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)
        if [ -z "$bt" ] || [ ! -f "$HOME/.android/debug.keystore" ]; then
            reason="APK not installed (no build-tools or debug key to sign it; set BUDGET_INSTALL_APK)"
        else
            echo "   signing a copy of the unsigned release APK with the local debug key"
            "$bt/zipalign" -f -P 16 4 "$(apk_file)" "$LOG_DIR/budget-aligned.apk" 2>/dev/null ||
                "$bt/zipalign" -f -p 4 "$(apk_file)" "$LOG_DIR/budget-aligned.apk"
            "$bt/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
                --key-pass pass:android --ks-key-alias androiddebugkey \
                --out "$LOG_DIR/budget-signed.apk" "$LOG_DIR/budget-aligned.apk"
            apk="$LOG_DIR/budget-signed.apk"
        fi
    fi
    if [ -z "$reason" ] && [ "$apk" != none ]; then
        echo "   installing $apk"
        a install -r "$apk" >/dev/null ||
            reason="install failed (a differently signed build on the device? BUDGET_INSTALL_APK=none or uninstall)"
    fi
    if [ -z "$reason" ] && ! a shell pm path "$PKG" | grep -q package:; then reason="$PKG not installed"; fi
    if [ -n "$reason" ]; then
        echo "!! $reason"
        row android_cold_start_ms "" "$where" 1 "$reason"
        row android_home_pss_mb "" "$where" 1 "$reason"
        return 0
    fi

    if [ "${BUDGET_ANDROID_CLEAR:-0}" = 1 ]; then
        echo "   BUDGET_ANDROID_CLEAR=1: clearing the app's data (library included)"
        a shell pm clear "$PKG" >/dev/null
    fi
    echo "   seeding the library (100 recent files, 2 folders) with one launch"
    a shell am force-stop "$PKG"
    a logcat -c
    a shell am start -W -n "$ACTIVITY" --ez org.faioffice.extra.BUDGET_SEED true >/dev/null
    seed=""
    for i in $(seq 1 15); do
        seed=$(a logcat -d -s FaiFiles:I | sed -n 's/.*fai_budget seed //p' | tail -1)
        [ -n "$seed" ] && break
        sleep 1
    done
    seeded=$(seed_note "$seed")
    echo "   seed: ${seed:-no answer} -> $seeded"
    echo "   warm-up launch after install (not counted)"
    a shell am force-stop "$PKG"
    a shell am start -W -n "$ACTIVITY" >/dev/null
    sleep 2
    for i in $(seq 1 "$RUNS"); do
        a shell am force-stop "$PKG"
        sleep 1
        out=$(a shell am start -W -n "$ACTIVITY")
        total=$(sed -n 's/^TotalTime: *//p' <<<"$out")
        wait=$(sed -n 's/^WaitTime: *//p' <<<"$out")
        state=$(sed -n 's/^LaunchState: *//p' <<<"$out")
        sleep "$SETTLE"
        pss=$(a shell dumpsys meminfo "$PKG" | awk '$1 == "TOTAL" && $2 ~ /^[0-9]+$/ { print $2; exit }
            $1 == "TOTAL" && $2 == "PSS:" { print $3; exit }')
        printf '   run %d/%d: TotalTime %s ms, WaitTime %s ms, %s; Home PSS after %s s: %s KB\n' "$i" "$RUNS" \
            "${total:-?}" "${wait:-?}" "${state:-launch state n/a}" "$SETTLE" "${pss:-?}"
        [ -n "$total" ] && times+=("$total")
        [ -n "$pss" ] && psses+=("$(awk -v k="$pss" 'BEGIN { printf "%.2f", k / 1024 }')")
    done
    a shell am force-stop "$PKG"
    row android_cold_start_ms "$(
        IFS=,
        echo "${times[*]}"
    )" "$where" 1 "${#times[@]} cold launches after force-stop, warm-up excluded; $seeded"
    row android_home_pss_mb "$(
        IFS=,
        echo "${psses[*]}"
    )" "$where" 1 "Home after ${SETTLE} s; $seeded"
}
android_measure

echo "== [5/5] desktop first frame and RAM at rest (Linux, Windows)"
desktop_measure() {
    local bin="$DESKTOP_DIST/fai-office/bin/fai-office" reason="" where counted=1 note="" i out ff pss cds seeded
    local seed_dir="$LOG_DIR/budget-seed"
    local frames=() psses=()
    if [ "$(uname -s)" != Linux ]; then
        reason="not a Linux machine"
    elif [ -z "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]; then
        reason="no display"
    elif [ ! -x "$bin" ]; then
        reason="no desktop distribution at $bin"
    fi
    if [ "$WSL" = 1 ]; then
        where="WSLg ($HOST)"
        counted=0
        note="WSLg, the budget is for a native Linux desktop"
    else
        where="native Linux: $HOST${XDG_SESSION_TYPE:+, $XDG_SESSION_TYPE}"
    fi
    if [ -n "$reason" ]; then
        echo "   $reason: desktop rows not measured"
        row desktop_cold_start_ms "" "$where" "$counted" "$reason"
        row desktop_ram_mb "" "$where" "$counted" "$reason"
        return 0
    fi
    # First start of a fresh build dumps the AppCDS archive (CdsArchive.kt) that every later start maps; like a
    # user's first start after installing, it is not counted.
    echo "   warm-up launch (not counted: first start dumps the AppCDS archive, writes the seed in $seed_dir)"
    rm -rf "$seed_dir"
    out=$(FAI_BUDGET_SEED="$seed_dir" FAI_BUDGET_PROBE=1 timeout 120 "$bin" 2>&1 || true)
    seeded=$(seed_note "$(sed -n 's/^fai_budget seed //p' <<<"$out" | tail -1)")
    echo "   seed: $seeded"
    cds=$(stat -c%s "$(dirname "$bin")/../lib/app/resources/fai.jsa" 2>/dev/null || echo 0)
    echo "   AppCDS archive: $(mb "$cds") MB (cache written by the first start, not in the package size)"
    for i in $(seq 1 "$RUNS"); do
        out=$(FAI_BUDGET_SEED="$seed_dir" FAI_BUDGET_PROBE=$SETTLE timeout $((SETTLE + 60)) "$bin" 2>&1 || true)
        ff=$(sed -n 's/^fai_budget first_frame_ms //p' <<<"$out")
        pss=$(sed -n 's/^fai_budget pss_kb //p' <<<"$out")
        printf '   run %d/%d: first frame %s ms, PSS after %s s: %s KB\n' "$i" "$RUNS" "${ff:-?}" "$SETTLE" "${pss:-?}"
        [ -n "$ff" ] && frames+=("$ff")
        [ -n "$pss" ] && psses+=("$(awk -v k="$pss" 'BEGIN { printf "%.2f", k / 1024 }')")
    done
    [ ${#frames[@]} -gt 0 ] || note="${note:+$note; }the app printed no probe line"
    row desktop_cold_start_ms "$(
        IFS=,
        echo "${frames[*]}"
    )" "$where" "$counted" "${note:-${#frames[@]} launches, warm-up excluded}; AppCDS archive $(mb "$cds") MB; $seeded"
    row desktop_ram_mb "$(
        IFS=,
        echo "${psses[*]}"
    )" "$where" "$counted" "${note:-at rest after ${SETTLE} s}; $seeded"
}
desktop_measure

windows_measure() {
    local inst="" exe reason="" where i ff ws priv seed seeded frames=() wss=() privs=() key cds
    if ! win_reachable; then
        reason="no Windows PC reachable (WSL interop)"
    elif ! inst=$(win_install_dir); then
        reason="%LOCALAPPDATA% not readable from Windows"
    elif [ ! -f "$inst/$WIN_APP/$WIN_APP.exe" ]; then
        reason="no Windows image (ci/windows-run.sh installs the CI build)"
    elif [ -n "$(win_running_from "$inst")" ]; then
        reason="FAI Office is already running from the CI build folder (close it)"
    fi
    if [ -n "$reason" ]; then
        echo "   Windows: $reason: Windows rows not measured"
        for key in windows_pkg_mb windows_cold_start_ms windows_ram_mb; do row "$key" "" "–" 1 "$reason"; done
        return 0
    fi
    exe="$inst/$WIN_APP/$WIN_APP.exe"
    where="$(win_describe), native; CI image $(cat "$inst/BUILD.txt" 2>/dev/null || echo "of unknown build")"
    echo "   Windows: $where"
    # The AppCDS archive a start of this copy may already have written is a cache, not part of the package.
    cds=$(stat -c%s "$inst/$WIN_APP/app/resources/fai.jsa" 2>/dev/null || echo 0)
    row windows_pkg_mb "$(mb $(($(du -sb "$inst/$WIN_APP" | cut -f1) - cds)))" "$where" 1 ""
    echo "   warm-up launch (not counted: first start after install, antivirus scan, AppCDS archive dump)"
    rm -rf "$inst/budget-seed"
    read -r ff ws priv seed <<<"$(win_probe "$exe" "$SETTLE" "$inst/budget-seed")"
    seeded=$(seed_note "${seed//\?/}")
    echo "   seed in $inst/budget-seed: $seeded"
    for i in $(seq 1 "$RUNS"); do
        read -r ff ws priv seed <<<"$(win_probe "$exe" "$SETTLE" "$inst/budget-seed")"
        printf '   run %d/%d: first frame %s ms, working set after %s s: %s bytes, private bytes %s\n' "$i" "$RUNS" \
            "$ff" "$SETTLE" "$ws" "$priv"
        [ "$ff" = "?" ] || frames+=("$ff")
        [ "$ws" = "?" ] || wss+=("$(mb "$ws")")
        [ "$priv" = "?" ] || privs+=("$(mb "$priv")")
    done
    row windows_cold_start_ms "$(
        IFS=,
        echo "${frames[*]}"
    )" "$where" 1 "${#frames[@]} launches, warm-up excluded; AppCDS archive $(mb "$(
        stat -c%s "$inst/$WIN_APP/app/resources/fai.jsa" 2>/dev/null || echo 0
    )") MB; $seeded"
    row windows_ram_mb "$(
        IFS=,
        echo "${wss[*]}"
    )" "$where" 1 "at rest after ${SETTLE} s; private bytes median $(
        [ ${#privs[@]} -gt 0 ] && printf '%s\n' "${privs[@]}" | median | xargs printf '%.1f' || echo "?"
    ) MB; $seeded"
}
windows_measure

sha=$(git rev-parse --short HEAD)
git diff --quiet HEAD -- . ":(exclude)$REPORT_DIR" || sha="$sha-dirty"
report="$REPORT_DIR/$(date +%Y-%m-%d_%H%M)_$sha.md"
echo "== report: $report (and $REPORT_DIR/LATEST.md)"
set +e
python3 tools/budget/report.py --in "$M" --out "$report" --latest "$REPORT_DIR/LATEST.md" \
    --meta "Date=$(date '+%Y-%m-%d %H:%M %Z')" --meta "Commit=$sha ($(git rev-parse --abbrev-ref HEAD))" \
    --meta "Build host=$HOST" --meta "Timed runs=$RUNS per metric, median compared; settle time $SETTLE s"
status=$?
set -e
if [ "$status" -ne 0 ]; then
    echo "!! over budget (MASTER_SPEC section 7): see $report"
    exit 1
fi
echo "== budget passed (rows marked not measured still need their device)"
