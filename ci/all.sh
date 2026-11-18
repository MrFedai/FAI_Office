#!/usr/bin/env bash
# Every CI step in order; runs all of them and prints a summary. Exit code is non-zero if any step failed.
# Usage: ci/all.sh            all steps
#        ci/all.sh lint web   only the named steps
cd "$(dirname "$0")"
steps=("$@")
[ ${#steps[@]} -gt 0 ] || steps=(lint test licenses android desktop web)
declare -A result took
failed=0
start=$SECONDS
for i in "${!steps[@]}"; do
    s=${steps[$i]}
    echo
    echo "######## [$((i + 1))/${#steps[@]}] $s"
    t=$SECONDS
    if bash "./$s.sh"; then result[$s]=pass; else result[$s]=FAIL; failed=1; fi
    took[$s]=$((SECONDS - t))
done
echo
echo "######## summary ($(((SECONDS - start) / 60)) min $(((SECONDS - start) % 60)) s)"
for s in "${steps[@]}"; do printf '  %-9s %-4s %4d s\n' "$s" "${result[$s]}" "${took[$s]}"; done
exit $failed
