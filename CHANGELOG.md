# Changelog

## v0.3.0: Phase 3 — file management (2026-11-22)

No release in Phase 3: `dev` only; merging into `main` and the `v0.3.0` tag are the owner's.

* One file access API in `core/` (`FileRef` that survives restart): Android through the Storage Access Framework only
  (persisted grants, no device-wide scan), Windows and Linux through desktop files, Web through a file input with
  recent files for the session (full File System Access moves to Phase 10).
* Format detection by content, not by extension alone, routed to the module family (documents, spreadsheets,
  presentations, PDF, archive, viewer); damaged, password-protected and hostile files are reported, never crash.
* Safe save: temporary file, verify, replace (on Android content URIs a backup until verified); fault-injection tests
  leave a readable old or new file after a failure at every step.
* Opening files from other apps: Android "Open with" and "Share"; Windows "Open with" from the MSI (never the default
  app) and a second launch handing its file to the running instance.
* Recent files (100 unpinned, pinned kept, pin / remove / clear, "Remember recent files" switch) and user-added
  folders listed when their screen opens; missing and lost-permission states; Android keeps the open file page
  across process death.
* File screens: Home, Recent Files with family filter, Documents / Spreadsheets / Presentations / PDF / Archive with
  sort, Settings, and a file information page with its states; `DESIGN.md` "File screens" patterns approved by the
  owner; 128 new screenshots (both themes, EN / TR, 390x844 and 1920x950), desktop keyboard navigation in lists.
* Budgets with a seeded library (100 recent files, 2 added folders; `ci/budget.sh`, report
  `reports/budget/2026-11-22_1000_f1ee523.md`): Galaxy A55 5G cold start 318 ms and Home RAM 54.4 MB; APK 1.86 MB;
  Web initial load 4.17 MB gzip; Windows package 111.6 MB, first frame 1318 ms, RAM at rest 245 MB. All within
  section 7.

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
