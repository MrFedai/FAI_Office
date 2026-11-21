#!/usr/bin/env bash
# Desktop distribution (jlink'ed JVM + app) for the current OS and its installed size against the Linux package
# budget (BUDGET_LINUX_PKG_MB in ci/budgets.env, light core; DESKTOP_LIMIT_KB overrides it).
source "$(dirname "$0")/lib.sh"
run_gradle desktop :app:createDistributable
check_size "desktop distribution" "$(desktop_bytes)" $((${DESKTOP_LIMIT_KB:-$((BUDGET_LINUX_PKG_MB * 1024))} * 1024))
