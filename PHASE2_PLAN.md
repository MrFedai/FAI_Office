# FAI_Office — Phase 2 Plan: shell, design system, CI

Status: **draft, 2026-11-18**, after Phase 1 approval. Scope from MASTER_SPEC section 13 (Phase 2) and
`PHASE1_PROPOSAL.md` sections 3, 7 and 12. Product code starts here; `poc/` stays as reference and is not built
by the product.

Item fields are read by the project's run tooling: `Efor` (agent effort: high / medium / low), `Done when`
(acceptance), `Ask owner` (decision needed before the item starts). A finished item gets `— YAPILDI (<commit>)`
in its heading.

## Exit criteria

* Android, Windows, Linux and Web builds of the shell come from one code base and pass CI (platform order changed
  by the owner on 2026-11-21: Android and Windows first, then Web, then Linux, macOS / iOS maybe later).
* Every screen of section 10's navigation exists (empty states), in English and Turkish, light and dark.
* `DESIGN.md` is written and approved by the owner.
* Phase 2 budgets hold, measured, not estimated: Android base download, cold start and Home RAM on the Galaxy
  A55 5G (reference device, owner decision 2026-11-21); desktop cold start, package size and RAM at rest on
  the owner's native Windows PC; Web initial load (section 7). Native Linux is measured later, when the owner says.
* R1 (shell memory on the reference phone) is closed as go, R7 (JVM start, native Windows) and R8 (Web build in
  CI) as go.

## Order and gates

P2.1 → P2.2 → P2.3 in a normal session (the run tooling is not set up before P2.3). P2.4 onward as a `/surum`
run. **Gate before P2.5:** the reference phone has arrived and R1 passes with the Phase 1 PoC APKs
(`poc/compose-pdf`: Home ≤ 150 MB, PDF viewing ≤ 300 MB). If R1 fails, the shell choice is reopened before any
screen is built.

Owner actions: provide the reference phone, the Galaxy A55 5G (replaces the A17 5G, owner decision 2026-11-21;
needed for the P2.5 gate and P2.8); the Windows PC for P2.10 and P2.8 (WSLg numbers do not count). A native
Linux machine comes later, when the owner says.

---

### P2.1 Project skeleton and build — YAPILDI (dev 4a84b74)

Efor: high

Gradle Kotlin Multiplatform project in the layout of `PHASE1_PROPOSAL.md` section 3, only what Phase 2 needs:
`app/` (commonMain, androidMain, desktopMain, wasmJsMain) and `core/` (settings, localisation). Version catalog
with pinned versions (Kotlin, Compose Multiplatform, AGP; the PoC versions are the starting point). Android:
`minSdk 29`, `targetSdk` / `compileSdk 36`, `arm64-v8a`. Desktop: jlink'ed JVM. Web: Kotlin/Wasm, served with
COOP / COEP in development. `LICENSE` (MPL-2.0) and the source header rule.

Done when: `./gradlew check`, the Android debug APK, the desktop distribution and the Wasm distribution build
from a clean checkout; each starts and shows an empty window; no dependency outside section 11's allowed list.

Result: AGP 9 keeps `com.android.application` out of KMP modules, so the Android entry point is a separate
`androidApp/` module and `app/` is a KMP library (deviation from the section 3 layout). Compose Multiplatform 1.12
needs `compileSdk 37`, hence AGP 9.5.1 and Gradle 9.6.0; `targetSdk` stays 36. Placeholder shell: release APK
1.09 MB (A55), Web first load 3.81 MB gzip (Chromium), desktop distributable 128 MB (WSLg, software rendering).
Runtime dependencies: AndroidX, JetBrains Compose / Kotlin / skiko, Guava listenablefuture, JSpecify; all
Apache-2.0.

### P2.2 CI scripts — YAPILDI (dev 79185c7)

Efor: medium

`ci/` scripts, each runnable alone and printing progress: lint (ktlint, detekt), unit tests, Android build,
desktop package, Wasm build with a gzip size check against the 5 MB budget, dependency license check (fails on a
forbidden license, section 11). `ci/all.sh` runs everything. A GitHub Actions workflow calls the same scripts and
becomes active once a public remote exists.

Done when: `ci/all.sh` passes from a clean checkout; lowering the Wasm size limit makes it fail; adding a GPL test
dependency makes the license check fail.

Ask owner: create the public GitHub remote now (CI runs on every push) or later (CI runs locally until R1).
Answered 2026-11-18: now; https://github.com/MrFedai/FAI_Office (public, `dev` only).

Result: `ci/all.sh` passes locally (2 min 23 s) and on GitHub Actions (6 min 8 s, cold cache). The license gate
is our own Gradle task (`ci/license-check.gradle.kts`, POM licenses incl. parent POMs) because the
dependency-license-report plugin fails on Gradle 9.6. Red runs checked: `WEB_LIMIT_KB=1000`, a GPL dependency
(`mysql-connector-j`, reported as FORBIDDEN) and a Kotlin file without the MPL header each fail. Also checked: APK
≤ 30 MB, desktop distribution ≤ 150 MB.

### P2.3 Run tooling (`/surum kur`) — YAPILDI (local config, devops-mcp 1bef00d)

Efor: medium

`.devops.toml`: fix `tasks.worktree_dir` (must be outside the repo, `~/PROJECTS/FAI_Office-wt`); jobs for the P2.2
scripts (`lint` as the gate, test jobs by path); `[surum]` with `list = "PHASE2_PLAN.md"`, `state` = the status note,
no release command in Phase 2. `.mcp.json` for the devops server. Backup through `devops-mcp/projects/fai-office/`.

Done when: the devops server loads the project (`project_info`) and the gate job passes once.

Result: `.devops.toml` and `.mcp.json` are local (`.git/info/exclude`), backed up in devops-mcp
`projects/fai-office/`. Jobs: `lint` (gate, `ci/lint.sh`), `kmp_test` (argument `core` | `app`), `web`, `ci_all`
(after merge), `release_dry` (main tree). Workers `claude_cli` / `claude_cli_b` are on the same account, so
`accounts = 1`. `look` = `app/src/`, `DESIGN.md`; `design_check` comes with P2.7. `project_info` loads the project
and `job_run lint` passed over MCP stdio; this session's devops tools stay off until the session is reopened.

### P2.4 DESIGN.md and design tokens — YAPILDI (dev 4ff0092)

Efor: high

`DESIGN.md` extends the shared design base (`claude-ortak/tasarim/TEMEL.md`, kit v1.0) for an office suite: one
visual identity for all modules (spec section 10), file-type meaning colours, layouts per breakpoint (phone
bottom bar, desktop top bar and side navigation), document view vs. edit mode on touch. Compose tokens generated
from `kit/tokens/fai.tokens.json` by a script (the kit has Flutter and Web outputs, no Compose yet). IBM Plex Sans
and Mono (OFL), subset, with their size counted against the Android 30 MB and Web 5 MB budgets.

Done when: the owner approves `DESIGN.md`; generated tokens are the only source of colour, type, spacing and
radius in `app/`; a contrast test (≥ 4.5:1, every text colour on every surface, both themes) passes.

Ask owner: theme default, follow the system theme (Android 10+, desktop, browser) or dark by default.

### P2.5 Shell and navigation — YAPILDI (dev e9fae7b)

Efor: high

All destinations of spec section 10 (Home, Recent Files, Documents, Presentations, Spreadsheets, PDF, File
Converter, Archive, Settings) as empty-state screens. Phone: bottom bar with at most five entries, the rest under
Home; tablet / desktop: side navigation. Android back and predictive back, desktop keyboard navigation, window
size classes per `DESIGN.md`.

Done when: every destination opens on Android, Linux and Web; screenshot tests cover each screen in both themes
at 390x844 and 1920x950; no hard-coded colour or size (lint rule).

Depends on: P2.4; the R1 gate.

### P2.6 Localisation EN / TR — YAPILDI (dev 7c1f203)

Efor: medium

Compose resources for English and Turkish, system language by default, override in Settings. Turkish casing
(`İ` / `ı`) through locale-aware functions only.

Done when: no user-visible literal outside resources (lint check); a test switches the language and finds no
missing key; a casing test passes with `tr` locale.

### P2.7 Screen package — YAPILDI (dev 1a3307a)

Efor: medium

`[ux]` section for the shared screen package tool (`claude-ortak/arac/ux.sh`): screenshot runner on Compose
Desktop, file names `<theme>_<size>_<screen>.png`, package root under `~/fai_notes/`, git hook keeps `guncel/`
current.

Done when: `ux.sh` produces the package for every P2.5 screen; a commit that changes a screen refreshes it.

### P2.8 Budget tests — YAPILDI (dev 3427b01)

Efor: high

Scripts that measure and compare with section 7, writing one report per run: APK size; cold start to first frame
(`am start -W`) and Home PSS (`dumpsys meminfo`) on the Galaxy A55 5G over adb; desktop first frame, installed
size and RAM at rest on the native Windows PC (Linux later); Web initial load (gzip). The desktop RAM budget gets its number
from this measurement (owner approves).

Done when: every Phase 2 budget in the exit criteria is measured on the named device and inside its budget; the
report is committed; a deliberate regression makes the script fail.

Depends on: P2.5; P2.10; reference phone; the Windows PC.

### P2.10 Windows build — YAPILDI (dev 763d1ca)

Efor: high

Owner decision 2026-11-21: Windows is first priority together with Android. Compose Desktop app image and
installer for Windows (x64) from the same code base; built in CI on a Windows runner (GitHub Actions) with the
size gate; the desktop measurement hook works on Windows (first frame, RAM at rest without `/proc`); a way to run
the built app on the owner's Windows PC from the repo (artifact download, or a script runnable from WSL).
Windows-specific look checked: fonts, scaling (125 % / 150 %), window chrome, keyboard.

Done when: the Windows build passes CI; the app opens on the owner's Windows PC with every P2.5 screen;
`ci/budget.sh` (or its Windows step) reports desktop first frame, installed size and RAM at rest measured there.

Depends on: P2.5; P2.8 scripts.

### P2.9 Phase 2 close — YAPILDI (dev 695e1ed; owner approved Phase 2 on 2026-11-21)

Efor: medium

Full CI once, budget report, screen package and `DESIGN.md` shown to the owner, risks R1 / R7 / R8 updated in
`PHASE1_PROPOSAL.md` section 11, status note updated.

Done when: the owner approves Phase 2.
