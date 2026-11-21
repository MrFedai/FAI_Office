# Budget report

* Date: 2026-11-21 21:39 +03
* Commit: d99bdae (dev)
* Build host: WSL2, kernel 6.18.33.2-microsoft-standard-WSL2
* Timed runs: 5 per metric, median compared; settle time 5 s
* Result: **FAIL** (6 pass, 1 over budget, 3 not measured, not counted or without a number)
* Budgets: MASTER_SPEC.md section 7 (numbers in ci/budgets.env); MB = 1024 × 1024 bytes

| Metric | Value | Budget | Device / environment | Result |
|---|---|---|---|---|
| Android base download | 1.61 MB | ≤ 30.00 MB | build output (androidApp-release-unsigned.apk) | pass |
| Web initial load | 4.08 MB | ≤ 5.00 MB | build output | pass |
| Linux package, installed | 127.23 MB | ≤ 150.00 MB | Linux app image, built on WSL2, kernel 6.18.33.2-microsoft-standard-WSL2 | pass |
| Android cold start to first frame | 279 ms | ≤ 1500 ms | SM-A556E, Android 17, 7.3 GB RAM | pass |
| Android RAM on Home | 50.24 MB | ≤ 150.00 MB | SM-A556E, Android 17, 7.3 GB RAM | pass |
| Linux cold start to first frame | 1716 ms | ≤ 1500 ms | WSLg (WSL2, kernel 6.18.33.2-microsoft-standard-WSL2) | not counted: WSLg, the budget is for a native Linux desktop |
| Linux desktop RAM at rest | 234.36 MB | awaiting owner approval | WSLg (WSL2, kernel 6.18.33.2-microsoft-standard-WSL2) | not counted: WSLg, the budget is for a native Linux desktop |
| Windows package, installed | 110.34 MB | ≤ 150.00 MB | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44935547173 commit=d99bdae branch=dev built=2026-11-21T18:26:30Z | pass |
| Windows cold start to first frame | 1600 ms | ≤ 1500 ms | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44935547173 commit=d99bdae branch=dev built=2026-11-21T18:26:30Z | FAIL |
| Windows RAM at rest | 245.62 MB | awaiting owner approval | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44935547173 commit=d99bdae branch=dev built=2026-11-21T18:26:30Z | awaiting owner approval |

How each value is measured:

* Android base download: release APK, arm64-v8a, minified, unsigned.
* Web initial load: distribution without source maps, gzip -9.
* Linux package, installed: createDistributable app image (jlink'ed JVM + app), du -sb; the folder a package installs.
* Android cold start to first frame: `am start -W` TotalTime after force-stop, median; 5 cold launches after force-stop, warm-up excluded; runs: 317 ms, 269 ms, 279 ms, 273 ms, 292 ms.
* Android RAM on Home: `dumpsys meminfo` TOTAL PSS (graphics included), median; Home after 5 s; runs: 33.29 MB, 50.16 MB, 51.08 MB, 50.24 MB, 50.31 MB.
* Linux cold start to first frame: process start to second frame (FAI_BUDGET_PROBE), median; WSLg, the budget is for a native Linux desktop; runs: 2328 ms, 1364 ms, 1308 ms, 1716 ms, 1865 ms.
* Linux desktop RAM at rest: process PSS after the settle time (FAI_BUDGET_PROBE), median; WSLg, the budget is for a native Linux desktop; runs: 224.77 MB, 234.36 MB, 230.48 MB, 246.23 MB, 245.02 MB.
* Windows package, installed: CI app image (createDistributable on windows-latest) as installed on the PC, du -sb.
* Windows cold start to first frame: process start to second frame (FAI_BUDGET_PROBE), median; 5 launches, warm-up excluded; runs: 1537 ms, 1600 ms, 1650 ms, 1768 ms, 1592 ms.
* Windows RAM at rest: process working set (Get-Process WorkingSet64: resident pages, shared ones in full, unlike Linux PSS) after the settle time (FAI_BUDGET_PROBE), median; at rest after 5 s; private bytes median 404.9 MB; runs: 244.73 MB, 246.42 MB, 245.92 MB, 245.62 MB, 244.78 MB.
