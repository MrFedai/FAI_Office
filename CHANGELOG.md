# Changelog

## v0.2.0: Phase 2 — application shell (2026-11-21)

No release in Phase 2: `dev` only; merging into `main` and the `v0.2.0` tag are the owner's.

* One Compose Multiplatform code base for Android (`androidApp/`, arm64-v8a, minSdk 29), Windows (MSI, per-user
  install), Linux (app image) and Web (Kotlin/Wasm), all built in CI (`ci/all.sh`, GitHub Actions on `dev`).
* Every screen of the navigation as an empty state (Home, recent files, documents, spreadsheets, presentations, PDF,
  file converter, archive, settings), English and Turkish, light and dark; `DESIGN.md` with design tokens generated
  into Compose, IBM Plex subset, contrast test.
* Screen package (`ci/ux-cekim.sh`, shared `ux.sh`): screenshots refreshed by a git hook on every screen change.
* Budget tests (`ci/budget.sh`, one report per run under `reports/budget/`): Galaxy A55 5G cold start 273 ms and
  Home RAM 51.9 MB; APK 1.6 MB; Web initial load 4.08 MB gzip; Windows package 110.8 MB, first frame 937 ms, RAM at
  rest 233 MB. A deliberate regression makes the script fail.
* Desktop start: the installed app writes an AppCDS archive on its first start (risk R7: 1600 → 937 ms on Windows).
* Budgets: reference phone Galaxy A55 5G (was Galaxy A17 5G 4 GB); desktop RAM at rest ≤ 300 MB (owner decisions
  2026-11-21).
