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
#                      the device model goes into the report, anything but a Galaxy A17 5G is marked as such.
#                      The unsigned release APK is installed signed with the local debug key (~/.android); a device
#                      holding a differently signed build needs BUDGET_INSTALL_APK=<signed.apk> or =none (measure
#                      what is installed). Several devices: ANDROID_SERIAL picks one.
#   Linux + display    desktop first frame and RAM at rest (FAI_BUDGET_PROBE hook in the desktop app); under WSL
#                      (WSLg) it is reported but not counted: the budget is for a native Linux desktop.
# Knobs: BUDGET_RUNS (timed runs, default 5), BUDGET_SETTLE_S (seconds at rest before RAM, default 5),
#        BUDGET_REPORT_DIR (default reports/budget), BUDGET_REF_MODEL_RE (reference model, default SM-A176),
#        ADB (adb binary, default from PATH).
source "$(dirname "$0")/lib.sh"

RUNS=${BUDGET_RUNS:-5}
SETTLE=${BUDGET_SETTLE_S:-5}
REPORT_DIR=${BUDGET_REPORT_DIR:-reports/budget}
REF_MODEL_RE=${BUDGET_REF_MODEL_RE:-SM-A176} # Samsung Galaxy A17 5G (SM-A176B and its regional variants)
PKG=org.faioffice
ACTIVITY=$PKG/org.faioffice.android.MainActivity
ADB=${ADB:-adb}
M="$LOG_DIR/budget-measurements.tsv"
: >"$M"

# row <key> <value or comma-separated runs> <device / environment> <counted 1|0> <note>
row() { printf '%s\t%s\t%s\t%s\t%s\n' "$@" >>"$M"; }
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
    local reason="" serial model where apk bt i out total wait state pss times=() psses=()
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
    where="$model, Android $(a shell getprop ro.build.version.release)"
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
    )" "$where" 1 "${#times[@]} cold launches after force-stop, warm-up excluded"
    row android_home_pss_mb "$(
        IFS=,
        echo "${psses[*]}"
    )" "$where" 1 "Home after ${SETTLE} s"
}
android_measure

echo "== [5/5] desktop first frame and RAM at rest"
desktop_measure() {
    local bin="$DESKTOP_DIST/fai-office/bin/fai-office" reason="" where counted=1 note="" i out ff pss
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
    for i in $(seq 1 "$RUNS"); do
        out=$(FAI_BUDGET_PROBE=$SETTLE timeout $((SETTLE + 60)) "$bin" 2>&1 || true)
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
    )" "$where" "$counted" "${note:-${#frames[@]} launches}"
    row desktop_ram_mb "$(
        IFS=,
        echo "${psses[*]}"
    )" "$where" "$counted" "${note:-at rest after ${SETTLE} s}"
}
desktop_measure

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
