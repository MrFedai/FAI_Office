# Budget report

* Date: 2026-11-22 10:00 +03
* Commit: f1ee523 (dev)
* Build host: WSL2, kernel 6.18.33.2-microsoft-standard-WSL2
* Timed runs: 5 per metric, median compared; settle time 5 s
* Result: **pass** (8 pass, 0 over budget, 2 not measured, not counted or without a number)
* Budgets: MASTER_SPEC.md section 7 (numbers in ci/budgets.env); MB = 1024 × 1024 bytes

| Metric | Value | Budget | Device / environment | Result |
|---|---|---|---|---|
| Android base download | 1.86 MB | ≤ 30.00 MB | build output (androidApp-release-unsigned.apk) | pass |
| Web initial load | 4.17 MB | ≤ 5.00 MB | build output | pass |
| Linux package, installed | 128.47 MB | ≤ 150.00 MB | Linux app image, built on WSL2, kernel 6.18.33.2-microsoft-standard-WSL2 | pass |
| Android cold start to first frame | 318 ms | ≤ 1500 ms | SM-A556E, Android 17, 7.3 GB RAM | pass |
| Android RAM on Home | 54.40 MB | ≤ 150.00 MB | SM-A556E, Android 17, 7.3 GB RAM | pass |
| Linux cold start to first frame | 1241 ms | ≤ 1500 ms | WSLg (WSL2, kernel 6.18.33.2-microsoft-standard-WSL2) | not counted: WSLg, the budget is for a native Linux desktop; AppCDS archive 41.266 MB; seeded 100+2 |
| Linux desktop RAM at rest | 223.56 MB | ≤ 300.00 MB | WSLg (WSL2, kernel 6.18.33.2-microsoft-standard-WSL2) | not counted: WSLg, the budget is for a native Linux desktop; seeded 100+2 |
| Windows package, installed | 111.58 MB | ≤ 150.00 MB | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44995489350 commit=f1ee523 branch=dev built=2026-11-22T06:49:59Z | pass |
| Windows cold start to first frame | 1318 ms | ≤ 1500 ms | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44995489350 commit=f1ee523 branch=dev built=2026-11-22T06:49:59Z | pass |
| Windows RAM at rest | 244.96 MB | ≤ 300.00 MB | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44995489350 commit=f1ee523 branch=dev built=2026-11-22T06:49:59Z | pass |

How each value is measured:

* Android base download: release APK, arm64-v8a, minified, unsigned.
* Web initial load: distribution without source maps, gzip -9.
* Linux package, installed: createDistributable app image (jlink'ed JVM + app), du -sb; the folder a package installs.
* Android cold start to first frame: `am start -W` TotalTime after force-stop, median; 5 cold launches after force-stop, warm-up excluded; seeded 100+2; runs: 356 ms, 316 ms, 318 ms, 333 ms, 306 ms.
* Android RAM on Home: `dumpsys meminfo` TOTAL PSS (graphics included), median; Home after 5 s; seeded 100+2; runs: 54.43 MB, 54.45 MB, 54.28 MB, 54.40 MB, 53.32 MB.
* Linux cold start to first frame: process start to second frame (FAI_BUDGET_PROBE), median; WSLg, the budget is for a native Linux desktop; AppCDS archive 41.266 MB; seeded 100+2; runs: 1230 ms, 1247 ms, 1224 ms, 1266 ms, 1241 ms.
* Linux desktop RAM at rest: process PSS after the settle time (FAI_BUDGET_PROBE), median; WSLg, the budget is for a native Linux desktop; seeded 100+2; runs: 224.03 MB, 220.68 MB, 223.76 MB, 222.49 MB, 223.56 MB.
* Windows package, installed: CI app image (createDistributable on windows-latest) as installed on the PC, du -sb.
* Windows cold start to first frame: process start to second frame (FAI_BUDGET_PROBE), median; 5 launches, warm-up excluded; AppCDS archive 38.188 MB; seeded 100+2; runs: 1522 ms, 1318 ms, 1414 ms, 1146 ms, 1131 ms.
* Windows RAM at rest: process working set (Get-Process WorkingSet64: resident pages, shared ones in full, unlike Linux PSS) after the settle time (FAI_BUDGET_PROBE), median; at rest after 5 s; private bytes median 889.8 MB; seeded 100+2; runs: 244.94 MB, 244.96 MB, 245.39 MB, 244.52 MB, 245.43 MB.
