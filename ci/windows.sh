#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
# not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Windows x64 desktop build: the GitHub Actions `windows` job runs it under Git Bash (.github/workflows/ci.yml).
# jpackage does not cross-build, so this needs Windows; on a Linux machine use ci/desktop.sh.
#   1. app image (createDistributable: jlink'ed JVM + app) and MSI installer (packageMsi; WiX is fetched by the
#      Compose plugin as a build tool and does not enter the package) with its "Open with" entries (P3.5)
#   2. desktop tests on Windows, screenshots into app/build/bakis (P2.5 screens, both themes and sizes); the core
#      file tests run on NTFS here, including a Turkish file name on a path longer than 260 characters (P3.5)
#   3. installed size of the app image against BUDGET_WINDOWS_PKG_MB (ci/budgets.env; WINDOWS_LIMIT_KB overrides)
# The CI job uploads the app image (fai-office-windows), the MSI (fai-office-windows-msi) and the shots
# (windows-shots); ci/windows-run.sh downloads the image onto the owner's PC.
source "$(dirname "$0")/lib.sh"
case "$(uname -s)" in
MINGW* | MSYS* | CYGWIN*) ;;
*)
    echo "!! ci/windows.sh builds the Windows image and needs Windows (jpackage does not cross-build)"
    exit 1
    ;;
esac

echo "== [1/3] build: Windows app image, MSI installer"
run_gradle windows :app:createDistributable :app:packageMsi
exe="$DESKTOP_DIST/fai-office/fai-office.exe"
[ -f "$exe" ] || { echo "!! no launcher at $exe"; exit 1; }
for msi in app/build/compose/binaries/main/msi/*.msi; do
    [ -f "$msi" ] || { echo "!! no MSI in app/build/compose/binaries/main/msi"; exit 1; }
    printf '== MSI installer: %s, %d KB download\n' "$(basename "$msi")" $(($(wc -c <"$msi") / 1024))
    # "Open with" entries (P3.5, app/msi/open-with.wxs): the registry keys sit in the MSI string pool as plain text.
    entries=$(grep -ac 'OpenWithProgids' "$msi" || true)
    [ "${entries:-0}" -gt 0 ] || { echo "!! no Open with entries in $(basename "$msi")"; exit 1; }
    echo "== MSI Open with entries present"
done

echo "== [2/3] desktop tests on Windows (screenshots: app/build/bakis)"
run_gradle windows-test :app:desktopTest :core:desktopTest
echo "== $(find app/build/bakis -name '*.png' | wc -l) screenshots in app/build/bakis"

echo "== [3/3] installed size"
check_size "Windows app image" "$(desktop_bytes)" $((${WINDOWS_LIMIT_KB:-$((BUDGET_WINDOWS_PKG_MB * 1024))} * 1024))
