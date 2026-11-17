#!/usr/bin/env bash
# PoC WP8: builds the Jetpack Compose + PDFium app, installs it on the connected phone and measures it with the
# same scenario as ../flutter-pdf/run.sh.
# Usage: run.sh            build + install + measure (needs one device in `adb devices`)
#        run.sh build      only build the release APK and print its size
# Output: out/<device>-compose-<time>.txt
set -euo pipefail
cd "$(dirname "$0")"
source "$HOME/dev/env.sh"
PKG=org.faioffice.poc.compose
PDFIUM_DIR=${PDFIUM_DIR:-$HOME/dev/cache/fai-office/pdfium-8111/android-arm64}
export PDFIUM_DIR
PDF=${PDF:-long-100p.pdf}
CORPUS=../corpus/out
GRADLE=${GRADLE:-$(ls -d "$GRADLE_USER_HOME"/wrapper/dists/gradle-9.3.1-all/*/gradle-9.3.1/bin/gradle | head -1)}
mkdir -p out app/src/main/jniLibs/arm64-v8a
cp "$PDFIUM_DIR/lib/libpdfium.so" app/src/main/jniLibs/arm64-v8a/
echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "[1/5] build release APK (arm64)"
"$GRADLE" -q --console=plain assembleRelease 2>&1 | grep -vE '^w: |warning:' | tail -20 || true
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "build failed"; exit 1; }
echo "    APK $(( $(stat -c%s "$APK") / 1024 )) KB"
unzip -lv "$APK" | awk 'NR>3 && ($8 ~ /^lib\// || $8 ~ /\.dex$/) {printf "    %-45s %6d KB raw %6d KB packed\n", $8, $1/1024, $3/1024}'
[ "${1:-}" = build ] && exit 0

[ "$(adb devices | grep -c 'device$')" = 1 ] || { echo "need exactly one device in 'adb devices'"; exit 1; }
[ -f "$CORPUS/$PDF" ] || { echo "missing $CORPUS/$PDF, run ../corpus/gen.py"; exit 1; }
MODEL=$(adb shell getprop ro.product.model | tr -d '\r' | tr ' ' '_')
OUT=out/$MODEL-compose-$(date +%Y%m%d-%H%M).txt
pss() { adb shell dumpsys meminfo $PKG | tr -d '\r' | awk '/Graphics:/ {g=$2} /TOTAL PSS:/ {t=$3} END {printf "%d %d", t/1024, g/1024}'; }
{
  echo "device      $(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "android     $(adb shell getprop ro.build.version.release | tr -d '\r') (sdk $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "display     $(adb shell wm size | tr -d '\r' | awk '{print $NF}')"
  echo "shell       Jetpack Compose (BOM 2026.06.01, Compose 1.11.4)"
  echo "apk_kb      $(( $(stat -c%s "$APK") / 1024 ))"
} | tee "$OUT"

# SurfaceFlinger TimeStats for the app's busiest layer: "<total> <janky> <dropped> <layer>" (same metric for every shell).
sfstats() { adb shell dumpsys SurfaceFlinger --timestats -dump | tr -d '\r' | awk -v p="$PKG" '
  /^layerName = / {name = substr($0, 14); keep = index(name, p) && name !~ /^Splash/}
  keep && /^totalFrames = / {t[name] += $3}
  keep && /^droppedFrames = / {d[name] += $3}
  keep && /^jankyFrames = / {j[name] += $3}
  END {for (n in t) if (t[n] > best) {best = t[n]; b = n}; printf "%d %d %d %s", t[b], j[b], d[b], b}'; }

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
  ff=$(adb logcat -d -s POC | awk '/first_frame_ms_from_process_start/ {print $NF}' | tail -1 | tr -d '\r')
  read -r tot gfx <<<"$(pss)"
  echo "cold_start_$i am_TotalTime ${t} ms, first_frame ${ff} ms, home_pss ${tot} MB (graphics ${gfx}, other $((tot - gfx)))" | tee -a "$OUT"
done

echo "[4/5] open $PDF and scroll through it (auto mode)"
adb shell am force-stop $PKG; sleep 1
echo "$PDF" | adb shell "cat > $F/auto"
adb logcat -c
adb shell dumpsys SurfaceFlinger --timestats -enable -clear >/dev/null
adb shell am start -n $PKG/.MainActivity >/dev/null
peak=0; peakg=0; start=$(date +%s)
until adb logcat -d -s POC | grep -q POC_DONE; do
  read -r tot gfx <<<"$(pss)"
  [ "${tot:-0}" -gt "$peak" ] && { peak=$tot; peakg=$gfx; }
  printf '\r    %3d s, peak PSS %d MB ' $(( $(date +%s) - start )) "$peak"
  [ $(( $(date +%s) - start )) -gt 180 ] && { echo; echo "timeout"; adb logcat -d -s POC AndroidRuntime | tail -20; break; }
  sleep 2
done
echo
read -r sft sfj sfd sfl <<<"$(sfstats)"
adb shell dumpsys SurfaceFlinger --timestats -disable >/dev/null
echo "surfaceflinger_frames $sft, janky $sfj ($(awk -v a=$sfj -v b=$sft 'BEGIN {printf "%.2f", b ? 100*a/b : 0}') %), dropped $sfd, layer $sfl" | tee -a "$OUT"
echo "pss_peak_viewing_mb $peak (graphics $peakg, other $((peak - peakg)))" | tee -a "$OUT"

echo "[5/5] collect"
adb shell cat $F/results.txt | tr -d '\r' | tee -a "$OUT"
adb shell rm -f $F/auto
echo "saved $OUT"
