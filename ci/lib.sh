#!/usr/bin/env bash
# Shared helpers for ci/*.sh: environment, Gradle runs with a progress line per task, size checks.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"
if [ -f "$HOME/dev/env.sh" ]; then source "$HOME/dev/env.sh"; fi # local machine; GitHub Actions brings its own
LOG_DIR="$ROOT/build/ci"
mkdir -p "$LOG_DIR"
source "$ROOT/ci/budgets.env" # MASTER_SPEC section 7 numbers, shared by the size gates and ci/budget.sh

mmss() { printf '%d:%02d' $(($1 / 60)) $(($1 % 60)); }

# run_gradle <label> <gradle args...>
# Prints "[done/total pct] task  elapsed  ETA" for every task; full output in build/ci/<label>.log.
run_gradle() {
    local label=$1
    shift
    local log="$LOG_DIR/$label.log" total n=0 start=$SECONDS line t eta
    # tr: on Windows (Git Bash, ci/windows.sh) Gradle ends its lines with CR LF.
    total=$(./gradlew "$@" --dry-run -q --console=plain 2>/dev/null | tr -d '\r' | grep -c ' SKIPPED$' || true)
    [ "$total" -gt 0 ] || total=1
    echo "== $label: $total Gradle tasks (log: build/ci/$label.log)"
    set +e
    ./gradlew "$@" --console=plain 2>&1 | tee "$log" | while IFS= read -r line; do
        line=${line%$'\r'}
        case $line in
        "> Task "*)
            n=$((n + 1))
            t=$((SECONDS - start))
            eta=$((n > 0 ? t * (total - n) / n : 0))
            [ $eta -ge 0 ] || eta=0
            printf '  [%d/%d %3d%%] %-60s %s  ETA %s\n' "$n" "$total" $((100 * n / total)) "${line#> Task }" \
                "$(mmss $t)" "$(mmss $eta)"
            ;;
        esac
    done
    local status=${PIPESTATUS[0]}
    set -e
    if [ "$status" -ne 0 ]; then
        echo "!! $label failed after $(mmss $((SECONDS - start))); from the log:"
        sed -n '/What went wrong/,/^\* Try/p' "$log" | head -40
        grep -E '^e: |FAILED|Lint error|\.kt:[0-9]+:[0-9]+' "$log" | head -40
        return "$status"
    fi
    echo "== $label passed in $(mmss $((SECONDS - start)))"
}

# Sizes of the built artifacts, in bytes; the size gates (android, desktop, web) and ci/budget.sh measure the same way.
APK_DIR=androidApp/build/outputs/apk/release
DESKTOP_DIST=app/build/compose/binaries/main/app
WEB_DIST=app/build/dist/wasmJs/productionExecutable
apk_file() { ls "$APK_DIR"/*.apk | head -1; }
apk_bytes() { stat -c%s "$(apk_file)"; }
# Distribution folder of createDistributable (jlink'ed JVM + app): what a package installs.
desktop_bytes() { du -sb "$DESKTOP_DIST" | cut -f1; }
# Web first load: every file of the distribution except source maps, each gzip -9.
web_gzip_bytes() {
    local total=0 f
    while IFS= read -r -d '' f; do
        total=$((total + $(gzip -9c "$f" | wc -c)))
    done < <(find "$WEB_DIST" -type f ! -name '*.map' -print0)
    echo "$total"
}

# check_size <label> <bytes> <limit-bytes>
check_size() {
    local label=$1 size=$2 limit=$3
    printf '== %s: %d KB (limit %d KB)\n' "$label" $((size / 1024)) $((limit / 1024))
    if [ "$size" -gt "$limit" ]; then
        echo "!! $label is over its budget (MASTER_SPEC section 7)"
        return 1
    fi
}
