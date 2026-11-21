#!/usr/bin/env bash
# Android release APK (arm64-v8a, minified, unsigned) and its size against the base download budget
# (BUDGET_APK_MB in ci/budgets.env; ANDROID_LIMIT_KB overrides it); the merged manifest asks for no storage
# permission (P3.4: files only through the Storage Access Framework).
source "$(dirname "$0")/lib.sh"

STORAGE_PERMISSIONS='android\.permission\.(READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|READ_MEDIA_[A-Z_]+)'

# storage_hits <manifest...>: every storage permission named in the given manifests, as "<file>: <permission>".
storage_hits() {
    local m
    for m in "$@"; do
        grep -oE "$STORAGE_PERMISSIONS" "$m" | sort -u | sed "s#^#$m: #" || true
    done
}

# Self-test: a planted manifest with three storage permissions must be caught, a clean one must pass.
probe_dir="$LOG_DIR/manifest-selftest"
mkdir -p "$probe_dir"
cat >"$probe_dir/bad.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission-sdk-23 android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" android:maxSdkVersion="36" />
</manifest>
XML
cat >"$probe_dir/good.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
</manifest>
XML
if [ "$(storage_hits "$probe_dir/bad.xml" | wc -l)" -ne 3 ] || [ -n "$(storage_hits "$probe_dir/good.xml")" ]; then
    echo "!! manifest permission check self-test failed"
    exit 1
fi

run_gradle android :androidApp:assembleRelease
check_size "android base APK" "$(apk_bytes)" $((${ANDROID_LIMIT_KB:-$((BUDGET_APK_MB * 1024))} * 1024))

echo "== manifest: no storage permission in the merged release manifest"
mapfile -t manifests < <(find androidApp/build/intermediates -name AndroidManifest.xml -path '*release*' \
    \( -path '*merged_manifest*' -o -path '*packaged_manifests*' \) 2>/dev/null)
if [ ${#manifests[@]} -eq 0 ]; then
    echo "!! merged release manifest not found under androidApp/build/intermediates"
    exit 1
fi
hits=$(storage_hits "${manifests[@]}")
if [ -n "$hits" ]; then
    echo "!! storage permission in the merged manifest (P3.4 allows none; find the library that adds it, or add"
    echo "   tools:node=\"remove\" for it in androidApp/src/main/AndroidManifest.xml):"
    echo "$hits" | sed 's/^/   /'
    exit 1
fi
echo "== manifest passed (${#manifests[@]} merged manifest(s) checked)"
