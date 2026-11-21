# Budget report

* Date: 2026-11-21 22:11 +03
* Commit: a4e97e1 (dev)
* Build host: WSL2, kernel 6.18.33.2-microsoft-standard-WSL2
* Timed runs: 5 per metric, median compared; settle time 5 s
* Result: **pass** (8 pass, 0 over budget, 2 not measured, not counted or without a number)
* Budgets: MASTER_SPEC.md section 7 (numbers in ci/budgets.env); MB = 1024 × 1024 bytes

| Metric | Value | Budget | Device / environment | Result |
|---|---|---|---|---|
| Android base download | 1.61 MB | ≤ 30.00 MB | build output (androidApp-release-unsigned.apk) | pass |
| Web initial load | 4.08 MB | ≤ 5.00 MB | build output | pass |
| Linux package, installed | 127.72 MB | ≤ 150.00 MB | Linux app image, built on WSL2, kernel 6.18.33.2-microsoft-standard-WSL2 | pass |
| Android cold start to first frame | 273 ms | ≤ 1500 ms | SM-A556E, Android 17, 7.3 GB RAM | pass |
| Android RAM on Home | 51.86 MB | ≤ 150.00 MB | SM-A556E, Android 17, 7.3 GB RAM | pass |
| Linux cold start to first frame | 681 ms | ≤ 1500 ms | WSLg (WSL2, kernel 6.18.33.2-microsoft-standard-WSL2) | not counted: WSLg, the budget is for a native Linux desktop; AppCDS archive 41.160 MB |
| Linux desktop RAM at rest | 201.95 MB | ≤ 300.00 MB | WSLg (WSL2, kernel 6.18.33.2-microsoft-standard-WSL2) | not counted: WSLg, the budget is for a native Linux desktop |
| Windows package, installed | 110.83 MB | ≤ 150.00 MB | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44938807296 commit=a4e97e1 branch=dev built=2026-11-21T19:02:56Z | pass |
| Windows cold start to first frame | 937 ms | ≤ 1500 ms | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44938807296 commit=a4e97e1 branch=dev built=2026-11-21T19:02:56Z | pass |
| Windows RAM at rest | 233.41 MB | ≤ 300.00 MB | Microsoft Windows 11 Home 10.0.26300, 31.3 GB RAM, native; CI image run=44938807296 commit=a4e97e1 branch=dev built=2026-11-21T19:02:56Z | pass |

How each value is measured:

* Android base download: release APK, arm64-v8a, minified, unsigned.
* Web initial load: distribution without source maps, gzip -9.
* Linux package, installed: createDistributable app image (jlink'ed JVM + app), du -sb; the folder a package installs.
* Android cold start to first frame: `am start -W` TotalTime after force-stop, median; 5 cold launches after force-stop, warm-up excluded; runs: 263 ms, 273 ms, 287 ms, 297 ms, 264 ms.
* Android RAM on Home: `dumpsys meminfo` TOTAL PSS (graphics included), median; Home after 5 s; runs: 50.54 MB, 51.86 MB, 52.36 MB, 53.76 MB, 50.27 MB.
* Linux cold start to first frame: process start to second frame (FAI_BUDGET_PROBE), median; WSLg, the budget is for a native Linux desktop; AppCDS archive 41.160 MB; runs: 695 ms, 681 ms, 663 ms, 695 ms, 675 ms.
* Linux desktop RAM at rest: process PSS after the settle time (FAI_BUDGET_PROBE), median; WSLg, the budget is for a native Linux desktop; runs: 202.13 MB, 201.95 MB, 198.55 MB, 204.17 MB, 201.19 MB.
* Windows package, installed: CI app image (createDistributable on windows-latest) as installed on the PC, du -sb.
* Windows cold start to first frame: process start to second frame (FAI_BUDGET_PROBE), median; 5 launches, warm-up excluded; AppCDS archive 38.062 MB; runs: 992 ms, 932 ms, 926 ms, 937 ms, 972 ms.
* Windows RAM at rest: process working set (Get-Process WorkingSet64: resident pages, shared ones in full, unlike Linux PSS) after the settle time (FAI_BUDGET_PROBE), median; at rest after 5 s; private bytes median 885.0 MB; runs: 244.92 MB, 226.93 MB, 224.95 MB, 233.41 MB, 241.12 MB.
