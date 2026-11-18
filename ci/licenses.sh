#!/usr/bin/env bash
# License gate: every runtime dependency of the Android, desktop and Web builds (MASTER_SPEC section 11).
source "$(dirname "$0")/lib.sh"
if ! run_gradle licenses checkLicenses --no-configuration-cache; then
    grep -E '^checkLicenses:|^  .*: (FORBIDDEN|LGPL|unknown license|no license)' "$LOG_DIR/licenses.log" || true
    exit 1
fi
grep '^checkLicenses:' "$LOG_DIR/licenses.log" || true
