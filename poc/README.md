# Phase 1 — proofs of concept

Throwaway code only (MASTER_SPEC section 15). Nothing here becomes product code. Downloaded binaries and build
output are not committed; they live under `~/dev/cache/fai-office/` and each PoC's `out/`.

## Work packages

| # | Work package | Spec | Needs | Status |
|---|---|---|---|---|
| 0 | Environment and pinned inputs | 12 | — | done |
| 1 | PDFium renders a corpus PDF on Linux | 15.1 | prebuilt PDFium | done |
| 2 | PDFium renders a corpus PDF in a browser (WASM) | 15.1, 9 | prebuilt PDFium WASM | done |
| 3 | Seed corpus: freely redistributable PDF, DOCX, XLSX, PPTX | 5 | LibreOffice (local) | done (seed) |
| 4 | Office engine (LibreOfficeKit) opens DOCX / XLSX / PPTX on Linux | 15.2 | LibreOffice (local) + LOK headers | done |
| 5 | Flutter shell shows PDFium tiles on Android | 15.1, 15.4 | real Android device (owner connects one) | done on Galaxy A55 (`flutter-pdf/`); 4 GB reference phone still open |
| 6 | Office engine on Android as a separate module | 15.2, 3 | LibreOffice Android build (hours) | done: built, license-checked, measured on the phone (`lok-android/`) |
| 7 | Office engine as WASM in a browser: size, load time, memory | 15.3, 9 | WASM build of LibreOffice | done untrimmed (`lo-wasm/`): opens DOCX / XLSX / PPTX; trimmed build open |
| 8 | Second shell candidate for comparison (Compose Multiplatform) | 12, 15 | — | done: Android (`compose-pdf/`), Linux + Web (`shell-size/`) |
| 9 | License audit of LibreOffice bundled externals | 11 | — | done (`LICENSE_AUDIT.md`), build check in WP6 |
| 10 | Proposal document with the 11 deliverables of section 15 | 15 | 1–9 | |

Order: 1 → 2 → 3 → 4 → 9 → 5 → 6 → 7 → 8 → 10. The cheap Linux and Web proofs come first; the hours-long
LibreOffice builds run in the background while the rest proceeds.

## Environment (measured 2026-11-17)

* Host: WSL2 Linux, 16 cores, 23 GB RAM, ~600 GB free disk.
* Present: Flutter 3.47.7 / Dart 3.13.5, JDK 17, Android SDK + NDK 28.2, CMake 3.28, Ninja, Rust (stable),
  Node 26, Python 3.12, Docker.
* Headless Chromium from the Playwright cache (`~/.cache/ms-playwright/chromium-1248`).
* Missing: Android device or emulator image, Emscripten. No sudo: LibreOffice is unpacked from the official
  .deb tarball into the cache.

Results: `RESULTS.md`.

## Pinned inputs

Versions are pinned to releases at least two weeks old.

| Input | Version | License | Source |
|---|---|---|---|
| PDFium prebuilt (linux-x64, android-arm64, android-x64, wasm) | chromium/8111 | BSD-3-Clause (+ bundled third-party, see audit) | github.com/bblanchon/pdfium-binaries |
| LibreOffice (Linux x86-64, official .deb tarball, unpacked) | 26.8.2 | MPL-2.0 (+ bundled externals, see audit) | download.documentfoundation.org |
| LibreOfficeKit headers | tag libreoffice-26.8.2.2 | MPL-2.0 | github.com/LibreOffice/core `include/LibreOfficeKit/` |
