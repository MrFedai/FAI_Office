#!/usr/bin/env bash
# PoC WP6: LibreOfficeKit on the phone, engine in its own process. Builds the harness from the LibreOffice
# Android build (lo-android/), installs it, and measures cold opens of DOCX / XLSX / PPTX, engine memory, and
# that ending the engine process gives the memory back.
# Usage: run.sh            build + install + measure (needs one device in `adb devices`)
#        run.sh build      only build and print the APK size
# Output: out/<device>-lok-<time>.txt, plus the last first-screen PNG of each run in out/
set -euo pipefail
cd "$(dirname "$0")"
source "$HOME/dev/env.sh"
PKG=org.faioffice.poc.lok
LO=${LO_SRC:-$HOME/dev/cache/fai-office/lo-src/core}
CORPUS=../corpus/out
DOCS=(typical-20p.docx typical.xlsx typical.pptx)
GRADLE=${GRADLE:-$(ls -d "$GRADLE_USER_HOME"/wrapper/dists/gradle-9.3.1-all/*/gradle-9.3.1/bin/gradle | head -1)}
M=app/src/main
mkdir -p out

echo "[1/6] copy engine from the LibreOffice Android build"
rm -rf $M/jniLibs $M/assets $M/java/org/libreoffice
mkdir -p $M/jniLibs/arm64-v8a $M/assets $M/java/org/libreoffice/kit
cp "$LO"/android/jniLibs/arm64-v8a/*.so $M/jniLibs/arm64-v8a/
cp -r "$LO"/android/source/assets/. "$LO"/android/source/assets_strippedUI/. $M/assets/
cp "$LO"/android/Bootstrap/src/org/libreoffice/kit/*.java $M/java/org/libreoffice/kit/
# Metric-compatible open fonts the product bundles (section 6), into LibreOffice's user font dir.
FONTS=$(ls -d "$HOME"/dev/cache/fai-office/lo-*/root/opt/libreoffice*/share/fonts/truetype | head -1)
cp "$FONTS"/{Liberation,Carlito,Caladea}*.ttf $M/assets/unpack/user/fonts/
# LibreOffice's fonts.conf points the fontconfig cache at its sample package name; use ours.
sed -i "s|/data/data/org.example.libreoffice/fontconfig|/data/data/$PKG/cache/fontconfig|" $M/assets/unpack/etc/fonts/fonts.conf
# LibreOfficeKit.init() takes an Activity; the engine runs in a Service, so accept any Context.
sed -i -e 's/init(Activity activity)/init(android.content.Context activity)/' \
       -e 's/activity.getApplication()/activity.getApplicationContext()/g' $M/java/org/libreoffice/kit/LibreOfficeKit.java
echo "    $(du -sm $M/jniLibs | cut -f1) MB libraries, $(du -sm $M/assets | cut -f1) MB assets (fonts $(du -sm $M/assets/unpack/user/fonts | cut -f1) MB)"
echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "[2/6] build release APK"
"$GRADLE" -q --console=plain assembleRelease 2>&1 | grep -vE '^w: |warning:|^\s*$' | tail -20 || true
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "build failed"; exit 1; }
echo "    APK $(( $(stat -c%s "$APK") / 1048576 )) MB"
[ "${1:-}" = build ] && exit 0

[ "$(adb devices | grep -c 'device$')" = 1 ] || { echo "need exactly one device in 'adb devices'"; exit 1; }
MODEL=$(adb shell getprop ro.product.model | tr -d '\r' | tr ' ' '_')
OUT=out/$MODEL-lok-$(date +%Y%m%d-%H%M).txt
# PSS in MB of one process name: "<total> <graphics>"
pss() { adb shell dumpsys meminfo "$1" 2>/dev/null | tr -d '\r' | awk '/Graphics:/ {g=$2} /TOTAL PSS:/ {t=$3} END {printf "%d %d", t/1024, g/1024}'; }
waitlog() { local s=$(date +%s); until adb logcat -d -s POC | grep -q "$1"; do
  [ $(( $(date +%s) - s )) -gt "$2" ] && { echo "    timeout waiting for $1"; adb logcat -d -s POC AndroidRuntime libc DEBUG | tail -25; return 1; }
  printf '\r    %3d s' $(( $(date +%s) - s )); sleep 1; done; printf '\r'; }
F=/sdcard/Android/data/$PKG/files
{
  echo "device      $(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "android     $(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "apk_mb      $(( $(stat -c%s "$APK") / 1048576 ))"
} | tee "$OUT"

echo "[3/6] install + push documents"
adb install -r "$APK" >/dev/null
adb shell pm clear $PKG >/dev/null   # fresh data dir, so the first run really unpacks
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 1   # creates the files dir
for d in "${DOCS[@]}"; do adb push "$CORPUS/$d" $F/ >/dev/null; done

run() { # $1 = label, rest = document order
  local label=$1; shift
  adb shell am force-stop $PKG; sleep 1
  adb shell rm -f $F/results.txt
  printf '%s\n' "$@" | adb shell "cat > $F/auto"
  adb logcat -c
  adb shell am start -n $PKG/.MainActivity >/dev/null
  waitlog PHASE_SAMPLE 240 || return 1
  read -r et eg <<<"$(pss $PKG:engine)"; read -r ut ug <<<"$(pss $PKG)"
  local first; first=$(adb logcat -d -s POC | awk '/engine_process_start_to_service_ms/ {print $NF}' | tail -1 | tr -d '\r')
  waitlog PHASE_UNBOUND 30 || return 1
  sleep 1
  local alive; alive=$(adb shell pidof $PKG:engine | tr -d '\r' || true)
  read -r ut2 ug2 <<<"$(pss $PKG)"
  waitlog POC_DONE 30 || return 1
  {
    echo "--- $label: $*"
    echo "engine_process_start_to_service_ms $first"
    adb shell cat $F/results.txt | tr -d '\r'; echo
    echo "pss_engine_with_doc_open_mb $et (graphics $eg, other $((et - eg)))"
    echo "pss_ui_mb $ut (graphics $ug)"
    echo "engine_process_after_unbind ${alive:-gone}"
    echo "pss_ui_after_unbind_mb $ut2"
  } | tee -a "$OUT"
  adb pull "$F/${!#}.png" "out/$label-${!#}.png" >/dev/null 2>&1 || true
}

echo "[4/6] first run after install (unpacks assets; reported, not a budget number)"
run first-run "${DOCS[@]}"
echo "[5/6] cold runs, each type first once"
run cold-docx typical-20p.docx typical.xlsx typical.pptx typical-20p.docx
run cold-xlsx typical.xlsx typical.pptx typical-20p.docx
run cold-pptx typical.pptx typical-20p.docx typical.xlsx
echo "[6/6] done"
adb shell rm -f $F/auto
echo "saved $OUT"
