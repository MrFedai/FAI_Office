#!/usr/bin/env bash
# Android release APK (arm64-v8a, minified, unsigned) and its size against the base download budget
# (BUDGET_APK_MB in ci/budgets.env; ANDROID_LIMIT_KB overrides it).
source "$(dirname "$0")/lib.sh"
run_gradle android :androidApp:assembleRelease
check_size "android base APK" "$(apk_bytes)" $((${ANDROID_LIMIT_KB:-$((BUDGET_APK_MB * 1024))} * 1024))
