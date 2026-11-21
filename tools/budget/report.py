#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
# not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
"""Budget report for ci/budget.sh: compares measurements with MASTER_SPEC section 7 and writes one Markdown report.

Input: a TSV file, one line per metric: key, value, environment, counted (1/0), note.
  value    one number, or several comma-separated runs (the median is compared); empty = not measured
  counted  0 = measured where it does not count for the budget (for example under WSLg)
  note     why it was not measured / not counted, or how it was measured
Limits come from the BUDGET_* environment variables (ci/budgets.env). An empty limit means the budget has no number
yet (awaiting owner approval). Exit code 1 if any counted metric is over its limit, 0 otherwise: a metric that was
not measured is not a failure.

  report.py --in m.tsv --out reports/budget/x.md --latest reports/budget/LATEST.md --meta "Commit=abc" ...
  report.py --self-test     compare() and the report against planted values (deliberate regression)
"""

import argparse
import os
import statistics
import sys
import tempfile

# key: (label, unit, limit variable, what is measured)
METRICS = {
    "apk_mb": ("Android base download", "MB", "BUDGET_APK_MB", "release APK, arm64-v8a, minified, unsigned"),
    "android_cold_start_ms": (
        "Android cold start to first frame",
        "ms",
        "BUDGET_COLD_START_MS",
        "`am start -W` TotalTime after force-stop, median",
    ),
    "android_home_pss_mb": (
        "Android RAM on Home",
        "MB",
        "BUDGET_HOME_PSS_MB",
        "`dumpsys meminfo` TOTAL PSS (graphics included), median",
    ),
    "desktop_cold_start_ms": (
        "Linux cold start to first frame",
        "ms",
        "BUDGET_COLD_START_MS",
        "process start to second frame (FAI_BUDGET_PROBE), median",
    ),
    "desktop_pkg_mb": (
        "Linux package, installed",
        "MB",
        "BUDGET_LINUX_PKG_MB",
        "createDistributable app image (jlink'ed JVM + app), du -sb; the folder a package installs",
    ),
    "desktop_ram_mb": (
        "Desktop RAM at rest",
        "MB",
        "BUDGET_DESKTOP_RAM_MB",
        "process PSS after the settle time (FAI_BUDGET_PROBE), median",
    ),
    "web_gzip_mb": ("Web initial load", "MB", "BUDGET_WEB_GZIP_MB", "distribution without source maps, gzip -9"),
}

PASS, FAIL = "pass", "FAIL"


def compare(value, limit, counted=True, note=""):
    """Status of one metric: pass / FAIL / not measured / not counted / awaiting owner approval."""
    if value is None:
        return f"not measured: {note}" if note else "not measured"
    if not counted:
        return f"not counted: {note}" if note else "not counted"
    if limit is None:
        return "awaiting owner approval"
    return PASS if value <= limit else FAIL


def parse_value(text):
    """(compared value, runs) from '12.3' or '339,298,310'; (None, []) when empty."""
    runs = [float(x) for x in text.split(",") if x.strip()]
    if not runs:
        return None, []
    return statistics.median(runs), runs


def parse_limit(text):
    return float(text) if text and text.strip() else None


def fmt(number, unit):
    if number is None:
        return "–"
    return f"{number:.0f} {unit}" if unit == "ms" else f"{number:.2f} {unit}"


def build_rows(lines, env):
    rows = []
    for line in lines:
        if not line.strip():
            continue
        key, value, where, counted, note = (line.rstrip("\n").split("\t") + [""] * 5)[:5]
        label, unit, limit_var, method = METRICS[key]
        number, runs = parse_value(value)
        limit = parse_limit(env.get(limit_var, ""))
        status = compare(number, limit, counted != "0", note)
        rows.append(
            {
                "label": label,
                "value": fmt(number, unit),
                "limit": f"≤ {fmt(limit, unit)}" if limit is not None else "awaiting owner approval",
                "where": where or "–",
                "status": status,
                "method": method,
                "runs": ", ".join(fmt(r, unit) for r in runs) if len(runs) > 1 else "",
                "note": note if number is not None else "",
            }
        )
    return rows


def render(rows, meta):
    failed = sum(r["status"] == FAIL for r in rows)
    passed = sum(r["status"] == PASS for r in rows)
    out = ["# Budget report", ""]
    out += [f"* {k}: {v}" for k, v in meta]
    out += [
        f"* Result: **{'FAIL' if failed else 'pass'}** ({passed} pass, {failed} over budget, "
        f"{len(rows) - passed - failed} not measured, not counted or without a number)",
        "* Budgets: MASTER_SPEC.md section 7 (numbers in ci/budgets.env); MB = 1024 × 1024 bytes",
        "",
        "| Metric | Value | Budget | Device / environment | Result |",
        "|---|---|---|---|---|",
    ]
    out += [f"| {r['label']} | {r['value']} | {r['limit']} | {r['where']} | {r['status']} |" for r in rows]
    out += ["", "How each value is measured:", ""]
    for r in rows:
        extra = "".join(f"; {x}" for x in (r["note"], f"runs: {r['runs']}" if r["runs"] else "") if x)
        out.append(f"* {r['label']}: {r['method']}{extra}.")
    return "\n".join(out) + "\n", failed


def self_test():
    """Planted values: an over-budget value must fail, missing / uncounted / unnumbered ones must not."""
    cases = [
        ((31.0, 30.0), FAIL),
        ((29.9, 30.0), PASS),
        ((30.0, 30.0), PASS),
        ((None, 30.0, True, "no device"), "not measured: no device"),
        ((240.0, 150.0, False, "WSLg"), "not counted: WSLg"),
        ((240.0, None), "awaiting owner approval"),
    ]
    errors = [f"compare{args} = {compare(*args)!r}, expected {want!r}" for args, want in cases if compare(*args) != want]
    if parse_value("339,298,310") != (310.0, [339.0, 298.0, 310.0]):
        errors.append("median of runs")
    env = {"BUDGET_APK_MB": "1", "BUDGET_COLD_START_MS": "1500", "BUDGET_DESKTOP_RAM_MB": ""}
    lines = [
        "apk_mb\t8.2\thost\t1\t",
        "android_cold_start_ms\t\t–\t1\tno device",
        "desktop_ram_mb\t230\tWSLg\t0\tWSLg, not counted",
    ]
    _, failed = render(build_rows(lines, env), [])
    if failed != 1:
        errors.append(f"deliberate regression (APK 8.2 MB, limit 1 MB): {failed} failures, expected 1")
    with tempfile.TemporaryDirectory() as tmp:
        tsv = os.path.join(tmp, "m.tsv")
        with open(tsv, "w") as f:
            f.write("\n".join(lines) + "\n")
        code = main(["--in", tsv, "--out", os.path.join(tmp, "r.md")], env, quiet=True)
        if code != 1:
            errors.append(f"exit code on regression: {code}, expected 1")
        env["BUDGET_APK_MB"] = "30"
        code = main(["--in", tsv, "--out", os.path.join(tmp, "r.md")], env, quiet=True)
        if code != 0:
            errors.append(f"exit code inside budget with unmeasured rows: {code}, expected 0")
    if errors:
        print("!! budget self-test failed:\n   " + "\n   ".join(errors))
        return 1
    print("   budget self-test: over-budget fails, not measured / not counted / no number pass through")
    return 0


def main(argv, env, quiet=False):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--in", dest="inp")
    p.add_argument("--out")
    p.add_argument("--latest")
    p.add_argument("--meta", action="append", default=[], help="Key=Value line in the report header")
    p.add_argument("--self-test", action="store_true")
    a = p.parse_args(argv)
    if a.self_test:
        return self_test()
    if not (a.inp and a.out):
        p.error("--in and --out are required")
    with open(a.inp) as f:
        rows = build_rows(f.readlines(), env)
    text, failed = render(rows, [m.split("=", 1) for m in a.meta])
    for path in filter(None, (a.out, a.latest)):
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with open(path, "w") as f:
            f.write(text)
    if not quiet:
        width = max(len(r["label"]) for r in rows)
        for r in rows:
            print(f"   {r['label']:<{width}}  {r['value']:>10}  {r['limit']:<26} {r['status']}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], os.environ))
