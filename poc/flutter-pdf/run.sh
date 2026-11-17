#!/usr/bin/env bash
# PoC WP5: builds the Flutter + PDFium app, installs it on the connected phone and measures it.
# Usage: run.sh            build + install + measure (needs one device in `adb devices`)
#        run.sh build      only build the release APK and print its size
# Output: out/<device>-<time>.txt (device info, cold starts, app results, PSS samples)
set -euo pipefail
cd "$(dirname "$0")"
source "$HOME/dev/env.sh"
PKG=org.faioffice.poc.pdf_poc
PDFIUM=${PDFIUM:-$HOME/dev/cache/fai-office/pdfium-8111/android-arm64/lib/libpdfium.so}
PDF=${PDF:-long-100p.pdf}
CORPUS=../corpus/out
mkdir -p out android/app/src/main/jniLibs/arm64-v8a
cp "$PDFIUM" android/app/src/main/jniLibs/arm64-v8a/

echo "[1/5] build release APK (arm64)"
flutter build apk --release --split-per-abi --target-platform android-arm64 2>&1 | grep -E 'Built|rror' || true
APK=build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
[ -f "$APK" ] || { echo "build failed"; exit 1; }
echo "    APK $(( $(stat -c%s "$APK") / 1024 )) KB"
unzip -lv "$APK" | awk 'NR>3 && $8 ~ /^lib\// {printf "    %-45s %6d KB raw %6d KB packed\n", $8, $1/1024, $3/1024}'
[ "${1:-}" = build ] && exit 0

[ "$(adb devices | grep -c 'device$')" = 1 ] || { echo "need exactly one device in 'adb devices'"; exit 1; }
[ -f "$CORPUS/$PDF" ] || { echo "missing $CORPUS/$PDF, run ../corpus/gen.py"; exit 1; }
MODEL=$(adb shell getprop ro.product.model | tr -d '\r' | tr ' ' '_')
OUT=out/$MODEL-$(date +%Y%m%d-%H%M).txt
{
  echo "device      $(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "android     $(adb shell getprop ro.build.version.release | tr -d '\r') (sdk $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "soc         $(adb shell getprop ro.soc.manufacturer | tr -d '\r') $(adb shell getprop ro.soc.model | tr -d '\r')"
  echo "abi         $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "ram         $(adb shell cat /proc/meminfo | awk '/MemTotal/ {printf "%.1f GB", $2/1048576}')"
  echo "apk_kb      $(( $(stat -c%s "$APK") / 1024 ))"
} | tee "$OUT"

echo "[2/5] install"
adb install -r "$APK" >/dev/null
adb shell am start -W -n $PKG/.MainActivity >/dev/null   # creates the files dir
sleep 2
F=/sdcard/Android/data/$PKG/files
adb shell rm -f $F/auto $F/results.txt
adb push "$CORPUS/$PDF" $F/ >/dev/null

echo "[3/5] cold start x5 (home screen, no document)"
for i in 1 2 3 4 5; do
  adb shell am force-stop $PKG; sleep 1
  adb logcat -c
  t=$(adb shell am start -W -n $PKG/.MainActivity | awk '/TotalTime/ {print $2}' | tr -d '\r')
  sleep 3
  ff=$(adb logcat -d -s flutter | awk '/first_frame_ms_from_process_start/ {print $NF}' | tail -1 | tr -d '\r')
  pss=$(adb shell dumpsys meminfo $PKG | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}' | tr -d '\r')
  echo "cold_start_$i am_TotalTime ${t} ms, flutter_first_frame ${ff} ms, home_pss $(( ${pss:-0} / 1024 )) MB" | tee -a "$OUT"
done

echo "[4/5] open $PDF and scroll through it (auto mode)"
adb shell am force-stop $PKG; sleep 1
echo "$PDF" | adb shell "cat > $F/auto"
adb logcat -c
adb shell am start -n $PKG/.MainActivity >/dev/null
peak=0; start=$(date +%s)
until adb logcat -d -s flutter | grep -q POC_DONE; do
  pss=$(adb shell dumpsys meminfo $PKG | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}' | tr -d '\r')
  [ "${pss:-0}" -gt "$peak" ] && peak=$pss
  printf '\r    %3d s, peak PSS %d MB ' $(( $(date +%s) - start )) $(( peak / 1024 ))
  [ $(( $(date +%s) - start )) -gt 180 ] && { echo; echo "timeout"; break; }
  sleep 2
done
echo
echo "pss_peak_viewing_mb $(( peak / 1024 ))" | tee -a "$OUT"

echo "[5/5] collect"
adb shell cat $F/results.txt | tr -d '\r' | tee -a "$OUT"
adb shell rm -f $F/auto
echo "saved $OUT"
