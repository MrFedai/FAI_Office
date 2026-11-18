#!/usr/bin/env bash
# Android release APK (arm64-v8a, minified, unsigned) and its size against the 30 MB base download budget.
source "$(dirname "$0")/lib.sh"
run_gradle android :androidApp:assembleRelease
apk=$(ls androidApp/build/outputs/apk/release/*.apk | head -1)
check_size "android base APK" "$(stat -c%s "$apk")" $((${ANDROID_LIMIT_KB:-30720} * 1024))
