# Budget report

* Date: 2026-11-21 20:34 +03
* Commit: 333be1b (task/6-p2-8a-butce-olcum-betikleri-rapo)
* Build host: WSL2, kernel 6.18.33.2-microsoft-standard-WSL2
* Timed runs: 5 per metric, median compared; settle time 5 s
* Result: **pass** (3 pass, 0 over budget, 4 not measured, not counted or without a number)
* Budgets: MASTER_SPEC.md section 7 (numbers in ci/budgets.env); MB = 1024 × 1024 bytes

| Metric | Value | Budget | Device / environment | Result |
|---|---|---|---|---|
| Android base download | 1.61 MB | ≤ 30.00 MB | build output (androidApp-release-unsigned.apk) | pass |
| Web initial load | 4.08 MB | ≤ 5.00 MB | build output | pass |
| Linux package, installed | 127.23 MB | ≤ 150.00 MB | Linux app image, built on WSL2, kernel 6.18.33.2-microsoft-standard-WSL2 | pass |
| Android cold start to first frame | – | ≤ 1500 ms | – | not measured: no device |
| Android RAM on Home | – | ≤ 150.00 MB | – | not measured: no device |
| Linux cold start to first frame | 2019 ms | ≤ 1500 ms | WSLg (WSL2, kernel 6.18.33.2-microsoft-standard-WSL2) | not counted: WSLg, the budget is for a native Linux desktop |
| Desktop RAM at rest | 231.58 MB | awaiting owner approval | WSLg (WSL2, kernel 6.18.33.2-microsoft-standard-WSL2) | not counted: WSLg, the budget is for a native Linux desktop |

How each value is measured:

* Android base download: release APK, arm64-v8a, minified, unsigned.
* Web initial load: distribution without source maps, gzip -9.
* Linux package, installed: createDistributable app image (jlink'ed JVM + app), du -sb; the folder a package installs.
* Android cold start to first frame: `am start -W` TotalTime after force-stop, median.
* Android RAM on Home: `dumpsys meminfo` TOTAL PSS (graphics included), median.
* Linux cold start to first frame: process start to second frame (FAI_BUDGET_PROBE), median; WSLg, the budget is for a native Linux desktop; runs: 2019 ms, 1924 ms, 2210 ms, 2946 ms, 1969 ms.
* Desktop RAM at rest: process PSS after the settle time (FAI_BUDGET_PROBE), median; WSLg, the budget is for a native Linux desktop; runs: 219.31 MB, 235.45 MB, 241.97 MB, 231.58 MB, 219.76 MB.
