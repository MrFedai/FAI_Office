#!/usr/bin/env bash
# Desktop distribution (jlink'ed JVM + app) for the current OS and its installed size against the Linux package
# budget (150 MB, light core).
source "$(dirname "$0")/lib.sh"
run_gradle desktop :app:createDistributable
check_size "desktop distribution" "$(du -sb app/build/compose/binaries/main/app | cut -f1)" \
    $((${DESKTOP_LIMIT_KB:-153600} * 1024))
