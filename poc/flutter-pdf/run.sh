#!/usr/bin/env bash
# PoC WP5: builds the Flutter + PDFium app, installs it on the connected phone and measures it.
# Usage: run.sh                     build + install + measure (needs one device in `adb devices`)
#        run.sh build               only build the release APK and print its size
#        RENDERER=skia run.sh       Impeller off (Skia). ImpellerBackend=opengles is ignored by Flutter 3.47 on
#                                   Vulkan devices (measured 2026-11-18), so it is not offered.
# Output: out/<device>-<renderer>-<time>.txt (device info, cold starts, app results, memory)
set -euo pipefail
cd "$(dirname "$0")"
source "$HOME/dev/env.sh"
PKG=org.faioffice.poc.pdf_poc
PDFIUM=${PDFIUM:-$HOME/dev/cache/fai-office/pdfium-8111/android-arm64/lib/libpdfium.so}
PDF=${PDF:-long-100p.pdf}
RENDERER=${RENDERER:-vulkan}
CORPUS=../corpus/out
MANIFEST=android/app/src/main/AndroidManifest.xml
mkdir -p out android/app/src/main/jniLibs/arm64-v8a
cp "$PDFIUM" android/app/src/main/jniLibs/arm64-v8a/

# Renderer choice goes into the manifest for this build only.
cp "$MANIFEST" out/AndroidManifest.xml.orig
trap 'cp out/AndroidManifest.xml.orig "$MANIFEST"' EXIT
case $RENDERER in
  vulkan) meta='' ;;
  skia) meta='<meta-data android:name="io.flutter.embedding.android.EnableImpeller" android:value="false" />' ;;
  *) echo "RENDERER must be vulkan or skia"; exit 2 ;;
esac
[ -n "$meta" ] && sed -i "s|^ *<!-- RENDERER:.*-->|        $meta|" "$MANIFEST"

echo "[1/5] build release APK (arm64, renderer $RENDERER)"
flutter build apk --release --split-per-abi --target-platform android-arm64 2>&1 | grep -E 'Built|rror' || true
APK=build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
[ -f "$APK" ] || { echo "build failed"; exit 1; }
echo "    APK $(( $(stat -c%s "$APK") / 1024 )) KB"
unzip -lv "$APK" | awk 'NR>3 && $8 ~ /^lib\// {printf "    %-45s %6d KB raw %6d KB packed\n", $8, $1/1024, $3/1024}'
[ "${1:-}" = build ] && exit 0

[ "$(adb devices | grep -c 'device$')" = 1 ] || { echo "need exactly one device in 'adb devices'"; exit 1; }
[ -f "$CORPUS/$PDF" ] || { echo "missing $CORPUS/$PDF, run ../corpus/gen.py"; exit 1; }
MODEL=$(adb shell getprop ro.product.model | tr -d '\r' | tr ' ' '_')
OUT=out/$MODEL-$RENDERER-$(date +%Y%m%d-%H%M).txt
# PSS of the app in MB: "<total> <graphics>" from dumpsys App Summary.
pss() { adb shell dumpsys meminfo $PKG | tr -d '\r' | awk '/Graphics:/ {g=$2} /TOTAL PSS:/ {t=$3} END {printf "%d %d", t/1024, g/1024}'; }
{
  echo "device      $(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "android     $(adb shell getprop ro.build.version.release | tr -d '\r') (sdk $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "soc         $(adb shell getprop ro.soc.manufacturer | tr -d '\r') $(adb shell getprop ro.soc.model | tr -d '\r')"
  echo "abi         $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "ram         $(adb shell cat /proc/meminfo | awk '/MemTotal/ {printf "%.1f GB", $2/1048576}')"
  echo "display     $(adb shell wm size | tr -d '\r' | awk '{print $NF}')"
  echo "renderer    $RENDERER"
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
  ff=$(adb logcat -d -s POC | awk '/first_flutter_frame_ms_from_process_start/ {print $NF}' | tail -1 | tr -d '\r')
  read -r tot gfx <<<"$(pss)"
  echo "cold_start_$i am_TotalTime ${t} ms, first_flutter_frame ${ff} ms, home_pss ${tot} MB (graphics ${gfx}, other $((tot - gfx)))" | tee -a "$OUT"
done

echo "[4/5] open $PDF and scroll through it (auto mode)"
adb shell am force-stop $PKG; sleep 1
echo "$PDF" | adb shell "cat > $F/auto"
adb logcat -c
adb shell am start -n $PKG/.MainActivity >/dev/null
peak=0; peakg=0; start=$(date +%s)
until adb logcat -d -s flutter | grep -q POC_DONE; do
  read -r tot gfx <<<"$(pss)"
  [ "${tot:-0}" -gt "$peak" ] && { peak=$tot; peakg=$gfx; }
  printf '\r    %3d s, peak PSS %d MB ' $(( $(date +%s) - start )) "$peak"
  [ $(( $(date +%s) - start )) -gt 180 ] && { echo; echo "timeout"; break; }
  sleep 2
done
echo
echo "pss_peak_viewing_mb $peak (graphics $peakg, other $((peak - peakg)))" | tee -a "$OUT"

echo "[5/5] collect"
adb shell cat $F/results.txt | tr -d '\r' | tee -a "$OUT"
adb shell rm -f $F/auto
echo "saved $OUT"
