# FAI_Office — Master Project Specification (v2)

## 0. DECISIONS ALREADY MADE

These are settled. Do not reopen them unless Phase 1 measurements prove one impossible.

1. **Architecture direction: hybrid.** A light core (shell, file management, PDF, viewer, archive) is always
   installed. The heavy office engine (Writer, Sheets, Slides, office conversion) is a separate module,
   delivered and loaded only when needed.
2. **Product license: open source, MPL-2.0.** The source is public.
3. **Dependency license policy:** see section 11. AGPL and GPL-only dependencies are forbidden.
4. **Primary engine candidates:** PDFium for PDF; LibreOffice core through LibreOfficeKit for office formats.
   Phase 1 must prove or disprove them with measurements. They are replaced only if they fail the budgets in
   section 7 or the license policy in section 11, and the replacement must be justified in writing.
5. **Team:** one developer working with AI coding agents, no fixed deadline. Phases are gated by acceptance
   criteria, not dates.
6. **UI languages for the first release:** English and Turkish.
7. **Performance budgets:** the numbers in section 7 stay provisional until Phase 1 measures them.

Still open, to be decided in Phase 1: shell framework, office engine delivery on Web, minimum OS versions.

## 1. PROJECT IDENTITY

Project name: FAI_Office

FAI_Office is a free, open-source, offline-first, cross-platform office suite.

The goal is one unified application that covers the essential everyday functionality of Microsoft Office and
LibreOffice: document creation, editing, viewing, conversion, and file management.

The application must prioritize performance, compatibility, simplicity, and low resource consumption.

"Lightweight" applies to the light core at all times, and to the office engine only while it is loaded. The
measurable meaning of "lightweight" is section 7.

## 2. PLATFORM PRIORITY

1. Android
2. Linux
3. Web
4. Windows
5. iOS
6. macOS

The architecture must support all six platforms without separate product implementations. Platform-specific
adaptations are acceptable when technically necessary.

Priority is enforced from the start, not at the end:

* Android and Linux are built and tested in CI from Phase 2 onward, in every phase.
* The Web build of the light core must keep compiling from Phase 2 onward, even though the Web release is later.
* No engine may be adopted unless Phase 1 shows a working path to Android, Linux, and Web.

## 3. CRITICAL ARCHITECTURAL REQUIREMENT

FAI_Office MUST be ONE APPLICATION. Users install one application. Do not create separate applications for
Writer, Slides, Sheets, PDF, or other modules.

The application must have:

* One application interface.
* One navigation system.
* One file management system.
* One settings system.
* One shared design system.
* Independent internal document engines.
* Shared platform and file access services.

Engines are loaded only when needed and unloaded when idle. Never keep every engine in memory at once.

### Module delivery per platform

How the office engine reaches the device depends on the store, because stores have different rules.

| Platform / channel             | Light core      | Office engine                                                        |
|--------------------------------|-----------------|----------------------------------------------------------------------|
| Android — Google Play          | base module     | on-demand feature module, downloaded on first use                    |
| Android — F-Droid / direct APK | in the APK      | bundled in the same APK ("full" variant)                             |
| Linux                          | in the package  | bundled, or an optional extension of the same package (Phase 1)      |
| Web                            | initial load    | fetched on first use, cached by the browser (see section 9)          |
| Windows / macOS                | in the installer| bundled, loaded lazily                                               |
| iOS                            | in the app      | bundled, loaded lazily (the App Store forbids downloading executable code) |

Rules:

* A fully offline "full" build with every engine bundled must always exist for Android and Linux.
* On-demand download is a size optimization, never the only way to get a feature.
* Before any download the user sees the size and confirms. Nothing downloads silently.
* The user still experiences one product in every channel.

## 4. CORE MODULES

### Light core (always installed)

FAI PDF — engine candidate: PDFium

* PDF viewing, including PDF/A files.
* Text selection and search.
* Annotations and form filling.
* Page manipulation: reorder, rotate, delete, extract.
* Merge and split.
* Printing.
* "Basic editing" means: annotations, form fields, page operations, and adding text or images as new objects.
  It does NOT mean reflowing or rewriting existing text.
* PDF/A export is provided by the office engine. PDF/A validation is out of initial scope.

FAI Viewer

* Images, SVG.
* HTML, XML.
* EPUB.
* Text and code files.

FAI Archive

* ZIP, 7Z, RAR, TAR.
* Browsing and extraction.
* RAR is read-only by license; RAR creation is never offered.

### Office engine (separate module) — engine candidate: LibreOffice core via LibreOfficeKit

FAI Writer

* DOCX, ODT (priority); RTF, TXT; DOC (legacy).
* Document creation and editing.
* Tables, images, headers, footers, page numbering.
* Formatting and printing.

FAI Sheets

* XLSX, ODS (priority); CSV; XLS (legacy).
* Formulas and functions.
* Charts and tables.
* Sorting, filtering, and data operations.

FAI Slides

* PPTX, ODP (priority); PPT (legacy).
* Slide creation and editing.
* Themes, layouts, transitions, animations.
* Presentation mode.

FAI Convert

* Document, spreadsheet, presentation, PDF, and image conversion.
* Batch conversion.
* Office conversions need the office engine; image and PDF page conversions work with the light core alone.

Legacy binary formats (DOC, XLS, PPT) are lower priority. They get whatever level the engine provides, verified
by tests. The default save target offered to the user is the modern format.

Do not claim full editing support for a format unless it is actually implemented and tested.

## 5. FILE COMPATIBILITY

File compatibility is one of the most important requirements.

Prioritize: DOCX, XLSX, PPTX, PDF, ODT, ODS, ODP.

Three compatibility levels:

1. Open and view.
2. Edit and save.
3. Preserve formatting and structure with high fidelity.

The compatibility matrix is the single source of truth. A format or feature moves up a level only when the
corpus tests for that level pass. The app shows a format read-only, with a clear notice, until it reaches Level 2.

### How fidelity is measured

* A fixed test corpus lives in the repository. Every file in it must be freely redistributable.
* Each corpus file has a reference render: for OOXML, a PDF exported from Microsoft Office; for ODF, a PDF
  exported from LibreOffice desktop.
* Automated checks per file: page count matches, extracted text matches, per-page visual similarity is above a
  threshold. Thresholds are proposed in Phase 1.
* Round-trip check: open, save, reopen, compare. Content the app does not understand must survive.
* The "typical documents" used by the budgets in section 7 are named files in this corpus.

### Safe save

* Never silently destroy unsupported document elements.
* Never overwrite the original in place without protection: write to a temporary file, verify it, then replace.
  Where atomic replace is unavailable (Android content URIs), keep a backup until the write is verified.
* If a document contains elements that cannot be round-tripped, warn before saving and offer "Save as copy".
* A crash or power loss during save must never leave the user without a readable file.

## 6. TEXT, FONTS, AND LANGUAGES

Fonts are the largest single cause of layout differences, because Microsoft fonts are absent on Android and Linux.

* Bundle open metric-compatible fonts: Carlito (Calibri), Caladea (Cambria), Liberation Sans / Serif / Mono
  (Arial / Times New Roman / Courier New).
* Maintain a font substitution table. When a font is substituted, tell the user; do not hide it.
* Use fonts embedded in the document and fonts installed by the user when available.
* Never bundle or download proprietary fonts.
* Extra script coverage (for example Noto families) is downloaded on demand, not bundled in the base.

Languages:

* UI languages for the first release: English and Turkish. All UI strings are localizable from day one.
* Document content: full Unicode with complex text shaping. Right-to-left and CJK documents must render
  correctly at Level 1. Editing quality for those scripts is recorded separately in the compatibility matrix.

## 7. PERFORMANCE BUDGETS

Performance is designed in from the beginning and checked at the end of every phase. There is no separate
"optimize later" phase.

Reference device: mid-range Android phone, 4 GB RAM. Exact model fixed in Phase 1.

All numbers below are provisional. Phase 1 measures the proofs of concept and either confirms each number or
proposes a new one with evidence. The owner approves the final numbers at the Phase 1 review.

| Metric                                                      | Budget                    |
|-------------------------------------------------------------|---------------------------|
| Android base download (light core, one ABI)                 | ≤ 30 MB                   |
| Office engine module download (one ABI)                     | ≤ 120 MB                  |
| Cold start to usable Home screen                            | ≤ 1.5 s                   |
| Open a 100-page PDF, first page visible                     | ≤ 1 s                     |
| Open a typical 20-page DOCX, engine not yet loaded          | ≤ 5 s                     |
| Open a typical 20-page DOCX, engine already loaded          | ≤ 2 s                     |
| RAM on Home screen, no document open                        | ≤ 150 MB                  |
| RAM while viewing a PDF                                     | ≤ 300 MB                  |
| RAM while editing a typical office document                 | ≤ 600 MB                  |
| Web initial load, light core, compressed                    | ≤ 5 MB                    |
| Web office engine download                                  | measured in Phase 1, then budgeted |

Standing requirements:

* Lazy loading of modules; idle engines are unloaded.
* No background processes or services, except an operation the user started (for example batch conversion).
* No mandatory internet connection, account, or cloud storage.
* No advertising. No subscription.
* A budget regression blocks the phase from closing.

## 8. AI AND CLOUD POLICY

AI integration is NOT part of the initial development scope.

Do not introduce paid AI APIs, cloud processing, mandatory accounts, or proprietary server dependencies.

Google Drive integration may be considered later as an optional external storage integration.

FAI_Office must not operate its own mandatory cloud storage infrastructure.

Downloading an on-demand module comes only from the app store or the project's public release hosting, only
after user confirmation, and is not a cloud service in the sense of this section.

## 9. WEB VERSION

The web version is a lightweight tool for occasional office tasks: open a local DOCX, edit a document, view a
PDF, do basic spreadsheet work, convert a file, download the result.

* Processing happens in the browser. No server-side document storage or processing.
* The light core (shell, PDF, viewer, archive) must run in the browser within the section 7 budget.
* The office engine on Web is an open question. Phase 1 must measure a WebAssembly build of the engine
  candidate: download size, load time, memory.
* If the engine exceeds what is acceptable on Web, the first Web release ships with reduced office scope, and
  the compatibility matrix says so plainly. A server-side fallback is not allowed without explicit approval.
* The web version does not need every desktop feature in its first release.

## 10. INTERFACE DESIGN

Create a modern, minimal, clean interface. The application should feel like one coherent office suite. Do not
create separate visual identities for each module.

Main navigation:

* Home
* Recent Files
* Documents
* Presentations
* Spreadsheets
* PDF
* File Converter
* Archive
* Settings

On Android, use a mobile-first interface. On Linux and Windows, use a desktop-oriented interface. Both come
from the same design system.

Editing on touch devices:

* Documents open in view mode. Editing starts with an explicit action.
* Touch-first, contextual toolbars; no desktop ribbon shrunk onto a phone.
* The layout stays usable with the on-screen keyboard open.
* Phone, tablet, and desktop layouts may expose different feature sets. The compatibility matrix records the
  difference instead of hiding it.

## 11. LICENSING

Product: MPL-2.0, public source repository.

Dependency policy:

* **Allowed:** MPL-2.0, Apache-2.0, BSD, MIT, ISC, zlib, public domain; SIL OFL for fonts.
* **Case by case, with written justification:** LGPL, only when dynamically linked and replaceable by the user.
  Check each platform separately; static linking on iOS usually rules it out.
* **Forbidden:** AGPL, GPL-only, SSPL, and any non-free or "source-available" license.

Consequences already known:

* MuPDF and ONLYOFFICE are excluded (AGPL).
* RAR support must use a permissively licensed reader. The official unrar source is not acceptable.

Obligations:

* Every dependency, direct or transitive, is recorded with its license in one third-party notice file.
* Phase 1 includes a license audit of the office engine's bundled third-party components. Components that
  violate the policy must be removable from the build or the engine candidate fails.
* Do not use third-party trademarks (LibreOffice, Microsoft, and others) in product or module names.

## 12. TECHNICAL DECISION PROCESS

Before implementing anything:

1. Analyze the requirements.
2. Evaluate cross-platform shell frameworks. Include: how well each displays engine-rendered tiles or
   textures, behavior on Android and Web, binary size, and license under section 11.
3. Validate the office engine candidate with a proof of concept.
4. Validate the PDF engine candidate with a proof of concept.
5. Verify Android, Linux, and browser compatibility by running code, not by reading documentation.
6. Audit licensing under section 11.
7. Measure performance and memory against section 7.
8. Identify dependencies and technical limitations.

Investigate existing technologies before building any document engine from scratch.

Shell framework candidates include Flutter, Qt, and Kotlin/Compose Multiplatform. Rust and WebAssembly are
candidates for shared services and the Web target. These are candidates, not mandatory choices. Do not choose
a technology because it is popular. Justify every choice against this specification.

## 13. DEVELOPMENT STRATEGY

Work in phases. Do not implement everything simultaneously. Each module must be independently testable while
remaining part of the same application.

### Release ladder

| Release | Content                                                                                     | Platforms            |
|---------|---------------------------------------------------------------------------------------------|----------------------|
| R1      | "Open everything": shell, file management, PDF tools, viewer, archive, office formats at Level 1 (view), conversion | Android, Linux |
| R2      | Writer editing (Level 2)                                                                    | Android, Linux       |
| R3      | Sheets editing (Level 2)                                                                    | Android, Linux       |
| R4      | Slides editing (Level 2) and presentation mode                                              | Android, Linux       |
| R5      | Web version                                                                                 | + Web                |
| R6      | Desktop and Apple platforms                                                                 | + Windows, iOS, macOS|

R1 is the minimum viable product. Nothing from R2 onward may delay it.

### Phases

* Phase 1: Architecture, proofs of concept, license audit, budget confirmation.
* Phase 2: Application shell, navigation, design system. CI for Android and Linux, plus Web build of the core.
* Phase 3: File management, format detection, recent files, safe-save layer.
* Phase 4: PDF module.
* Phase 5: Viewer and Archive modules.
* Phase 6: Office engine integration: module delivery, Level 1 viewing of all office formats, conversion. → R1
* Phase 7: Writer editing. → R2
* Phase 8: Sheets editing. → R3
* Phase 9: Slides editing and presentation mode. → R4
* Phase 10: Web version. → R5
* Phase 11: Windows, iOS, and macOS adaptation. → R6

Every phase closes only when its tests pass and the section 7 budgets hold on Android and Linux.

## 14. IMPORTANT DEVELOPMENT RULES

* Do not generate unnecessary files.
* Do not introduce unnecessary dependencies.
* Do not duplicate functionality.
* Do not build separate applications for individual modules.
* Do not use cloud services without explicit approval.
* Do not introduce paid services.
* Do not add a dependency that violates section 11.
* Do not sacrifice compatibility for superficial simplicity.
* Do not claim features are complete without testing.
* Document architectural decisions.
* Keep the codebase maintainable.
* Design for future expansion.

## 15. FIRST TASK — NO PRODUCT CODE YET

Your first task is Phase 1: a technical architecture proposal backed by measurements.

Throwaway proofs of concept are allowed and required. Keep them in a `poc/` directory, separate from any
future product code. Do not write product code until the proposal has been reviewed and approved.

Required proofs of concept:

1. PDF engine rendering a corpus PDF on Android, on Linux, and in a browser.
2. Office engine opening a DOCX, an XLSX, and a PPTX on Android and on Linux, delivered as a separate module.
3. Office engine as WebAssembly in a browser: download size, load time, memory.
4. One shell framework candidate displaying engine output smoothly on Android.

Deliver:

1. Recommended technology stack.
2. Architecture diagram.
3. Application structure.
4. Module architecture, including module delivery per platform.
5. File format compatibility matrix with the level each format is expected to reach in each release.
6. Recommended libraries and their licenses, with the section 11 audit result.
7. Android / Linux / Web implementation strategy.
8. Proof-of-concept results measured against every budget in section 7, with confirmed or revised numbers.
9. Fidelity test plan: corpus contents, reference renders, thresholds.
10. Main technical challenges and risks, each with a go / no-go criterion.
11. Development phases and dependencies, mapped to the release ladder.

The hybrid direction is decided. For each choice that is still open — shell framework, office engine delivery
on Web, Linux packaging — compare at least two realistic options and explain the advantages and disadvantages
of each.

The ultimate objective is to build FAI_Office as one unified, free, open-source, lightweight, offline-first
office application that works across Android, Linux, Web, Windows, iOS, and macOS.
