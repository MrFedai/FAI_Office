# FAI Office — DESIGN

Extends the shared design base `claude-ortak/tasarim/TEMEL.md` (kit v1.0, tokens 1.1.0). Only the differences and
the office-specific parts are written here; everything else follows TEMEL. Where the two disagree, this file wins.
Status: approved by the owner (P2.4, 2026-11-18). File screen patterns (section 6, "File screens", P3.8,
2026-11-22): approved by the owner (2026-11-22).

## 1. Identity

- One visual identity for every module (MASTER_SPEC section 10): Home, Recent Files, Documents, Presentations,
  Spreadsheets, PDF, File Converter, Archive and Settings share one shell, one palette, one type scale. A module
  never gets its own accent, header colour or logo.
- Calm work desk (TEMEL 1): flat surfaces, thin lines, three surface steps `bg` → `surface` → `surface2`; shadow only
  on floating elements (menus, toasts, sheets).
- The document is the hero. Chrome (bars, panels) stays quiet so the page content carries the colour.
- Blue (`primary`) means interaction only: selected destination, primary button, link, focus ring.

## 2. Theme

- Two equal themes, dark and light. **Default: follow the system** (Android 10+, desktop, browser), owner decision
  2026-11-18 (TEMEL's "dark by default" does not apply). Settings override light / dark / system arrives in P2.5.
- Code: `ThemeMode { System, Light, Dark }`, `FaiTheme(mode) { … }`, colours through `FaiTheme.colors`, text through
  `FaiTheme.type` (`app/src/commonMain/kotlin/org/faioffice/app/theme/`).

### Language

- Strings live in `app/src/commonMain/composeResources/values/strings.xml` (English, default) and `values-tr/`; keys
  snake_case. No user-visible literal in code.
- Default: the system language. Override `AppLanguage { System, English, Turkish }` through `AppLanguageProvider`
  (`LocalAppLocale`, per-platform actual); the Settings control arrives in P2.5.
- Case conversion only through `String.uppercase(language)` / `lowercase(language)` (`core/.../text/Casing.kt`):
  tr and az map i -> İ, ı -> I, I -> ı, İ -> i. Bare `.uppercase()` is allowed in that file only.
- Gate: `ci/i18n.sh` (called by `ci/lint.sh`) checks literals, key parity and casing.

## 3. Colour

Kit colours unchanged, except three light-theme text colours the kit gets wrong (below 4.5:1 on `bg`):

| Token | Kit light | FAI Office light | Why |
|---|---|---|---|
| `pos` | `#0E8A5C` (3.96:1 on bg) | `#0B7A50` | text contrast |
| `warn` | `#A86300` (4.32:1) | `#9A5B00` | text contrast |
| `violet` | `#7C5AD9` (4.45:1) | `#6F4ACF` | text contrast |

Added: `text2` (second text step, TEMEL 3; the kit has only `text` and `muted`) and `onPrimary` (text on the primary
button). Not carried over (FAI Trade meanings): `posZ`, `negZ`, `violetOn`, `chartGrid`, the long / short / stop /
candle / price semantics and `planPanel`.

Status colours keep their kit names: `pos` success, `neg` error, `warn` warning; always with a word or a sign
(✓ ✕ !), never colour alone.

### File-type meaning colours

A file type is shown as a badge: the extension (or a sign) in the type colour on a 14 % tint of the same colour,
with the type name beside it. The colour is a meaning colour (TEMEL 2): it sits above blue and is never used for
buttons, links or selection. PDF red is close to `neg`; the badge always carries the extension, so they are never
confused.

| Kind | Token | Dark | Light | Badge |
|---|---|---|---|---|
| Document (DOCX, ODT, RTF, TXT) | `fileDocument` | `#4DB3F2` | `#0B67A6` | DOCX |
| Spreadsheet (XLSX, ODS, CSV) | `fileSpreadsheet` | `#3CC77F` | `#137A3E` | XLSX |
| Presentation (PPTX, ODP) | `filePresentation` | `#F28B50` | `#B0480C` | PPTX |
| PDF | `filePdf` | `#F4707C` | `#BD2440` | PDF |
| Archive (ZIP, 7Z, TAR, RAR) | `fileArchive` | `#D6A65C` | `#855A12` | ZIP |
| File converter (tool) | `fileConverter` = `violet` | `#A78BFA` | `#6F4ACF` | ↔ |
| Image (PNG, JPG, WebP, SVG) | `fileImage` | `#E07FD0` | `#9C2C86` | PNG |

Document is cyan-leaning (hue ≈ 203°) so it never reads as the interaction blue (≈ 219°).

### Contrast

Every text colour (`text`, `text2`, `muted`, `primaryInk`, `pos`, `neg`, `warn`, `violet`, every file colour) on
every surface (`bg`, `surface`, `surface2`, `input`), `onPrimary` on `primary`, and `text` / `primaryInk` / `violet`
on their tints over every surface reach ≥ 4.5:1 in both themes (73 pairs per theme; lowest 4.65 dark, 4.61 light).
Enforced by `ContrastTest` (desktop JVM) and printed by the generator. Not text colours, so not tested: `primary`
(fill and focus ring), `brand` (logo mark only), `border`, `border2`, `primarySoft`, `violetSoft` (tints).

## 4. Type

IBM Plex Sans for labels and sentences, IBM Plex Mono for numbers (sizes, page counts, dates, times, percentages).
Styles (`FaiTheme.type`), sizes in sp:

| Style | Size / line | Weight | Font | Use |
|---|---|---|---|---|
| `pageTitle` | 18 / 24 | 600 | Sans | top bar title |
| `cardTitle` | 14 / 20 | 600 | Sans | card and section titles |
| `bodyMobile` | 14 / 20 | 400 | Sans | body text on phone |
| `body` | 13 / 18 | 400 | Sans | body text on desktop, list rows |
| `button` | 13 / 18 | 500 | Sans | buttons |
| `small` | 12 / 16 | 400 | Sans | secondary lines, status |
| `label` | 11 / 14 | 500 | Sans | tags, tab labels, badges |
| `hero` | 26 / 32 | 600 | Mono | the one hero number (kit `price`) |
| `number` | 13 / 18 | 400 | Mono | sizes, counts |
| `numberSmall` | 12 / 16 | 400 | Mono | dates, times |

Document content uses the document's own fonts and the metric-compatible set of MASTER_SPEC section 6, never the
UI fonts.

### Fonts and size budget

- Bundled: Sans 400 / 500 / 600 / 700, Mono 400 / 500 (OFL 1.1), subset to Basic Latin, Latin-1, Latin Extended-A
  (Turkish İ ı Ş ş Ğ ğ), common punctuation, € ₺, arrows and math signs; hinting removed. Script:
  `bash tools/fonts/subset.sh [SOURCE_DIR]` (fonttools, MIT, in `~/dev/venv-fonttools`), output
  `app/src/commonMain/composeResources/font/` with the OFL texts beside the fonts.
- Measured (subset script, 18.11.2026): six faces 779 KB → 225 KB uncompressed, 102 KB gzip (-9). That is < 1 % of
  the Android 30 MB budget and ≈ 2 % of the Web 5 MB budget.

## 5. Spacing, radius, size

- Spacing scale (TEMEL 4): `FaiSpace.s2 … s32` = 2 · 4 · 6 · 8 · 10 · 12 · 14 · 16 · 20 · 24 · 32; kit names
  `pagePadX` 16, `pagePadXCompact` 12, `pagePadY` 14, `gap` 12, `cardPad` 16, `mobilePad` 12, `mobileGap` 10.
- Radius: control 8, segment 9, card 10, modal 14, chip 999.
- Sizes: top bar 52, phone tab bar 68, touch target ≥ 44, desktop control 36 (44 large), side navigation 232
  (rail 72), document toolbar 48, icon 20 (small 16), file badge 36, dialog ≤ 720, line 1; file screens (P3.8):
  list column `fileColumn` 128, detail label `detailLabel` 136, switch track `switchWidth` 40 x `switchHeight` 24
  with thumb `switchThumb` 18.
- Disabled 45 % opacity, file tint 14 %. Motion: hover 120 ms, state 220 ms, `CubicBezier(.2, .7, .2, 1)`; reduced
  motion → 0.

## 6. Layout per breakpoint

Width classes by window width in dp (`FaiBreakpoint`); content decides, not the device.

| Class | Width | Navigation | Page |
|---|---|---|---|
| Phone | < 600 | top bar + **bottom bar, 5 entries** | one column, page scrolls |
| Medium (tablet portrait, phone landscape) | 600–899 | top bar + **side rail** (72, icon + short label) | one or two columns, page scrolls |
| Compact desktop | 900–1279 | top bar + **side navigation** (232, collapsible to the rail) | fits the window, no page scroll |
| Desktop | ≥ 1280 | top bar + **side navigation** (232) | fits the window, multi-column |

Differences from TEMEL 5 (kit: phone < 900, desktop top tabs): an office suite has nine destinations, too many for
top tabs, and a tablet shows documents better with a side rail than with a phone bottom bar. Height ≤ 500 with touch
(phone landscape) uses the phone layout.

- **Phone bottom bar (≤ 5):** Home · Recent · Documents · PDF · Converter. Presentations, Spreadsheets and Archive
  open from Home (file-type tiles); Settings is a top bar icon (TEMEL 5). Proposal for owner approval.
- **Side navigation:** all nine destinations in spec order, Settings last; selected item `primarySoft` tint +
  `primaryInk` text.
- **Top bar:** title of the current destination, search, settings, theme; on desktop also open-file actions.
- Forms and dialogs: bottom sheet on phone, centred dialog ≤ 720 elsewhere (✕ + Esc).
- **Shell code** (P2.5a): `app/src/commonMain/kotlin/org/faioffice/app/shell/`; `shellLayoutFor(width, height,
  touch)` picks the layout, `ShellNavigator` is the back stack (Home is the root; back at Home goes to the system).
  Keyboard: Tab / Shift+Tab move focus, Enter / Space select, Alt+Left or Esc go back; the focus ring (`primary`,
  2 dp) shows only after a key press. Android: predictive back scales and fades the content.
- **Empty state** (P2.5b, `shell/EmptyState.kt`): every destination opens on one bordered `surface` card that fills
  the visible page (no blank page under a strip; Home keeps it at its own height above the tiles), content centred:
  large badge (`emptyBadge` 56, icon `iconLarge` 28, destination meaning colour on its 14 % tint), short title
  (`pageTitle`), one sentence (`body`, `bodyMobile` on phone, `text2`), then the primary action and the violet
  "In development" chip. An action that does not work yet is a `PrimaryButton` without handler: 45 % opacity, no
  focus, no click, marked disabled for accessibility; no fake function. Settings has no primary action. Home keeps
  the five file-type tiles below the card (order fixed).
- **Icons:** Lucide (ISC), stroke 2 on the 24 grid, drawn at `icon` 20 and tinted with theme colours; path data
  local in `shell/FaiIcons.kt` with the ISC notice. No remote icon or icon font.
- **Empty state with a working action** (P3.8): once the action works the card keeps its layout, the primary button
  is enabled and may be followed by a secondary one ("Add folder"); the violet "In development" chip stays only next
  to an action that does not work yet (File Converter until Phase 6).

### File screens (P3.8)

Code: `app/src/commonMain/kotlin/org/faioffice/app/screens/` (patterns in `FilePatterns.kt`, screens beside it),
state in `files/FilesModel.kt` over the file channel (`FileHub`, `FileLibrary`). Every value below is a token.

**Patterns**

- **Section card:** bordered `surface` card, radius `card` 10, vertical padding 4; optional title row (`cardTitle`,
  `text`, at least control height + 8) with its controls at the end; content runs edge to edge (rows draw their own
  hover). Padding inside: 12 on phone, `cardPad` 16 elsewhere. A one-sentence note inside it uses `body` /
  `bodyMobile` in `text2`.
- **File row:** file badge 36 (extension in the format's meaning colour on its 14 % tint; text formats read as
  documents, images as `fileImage`, no format `muted` with "?"), then the name. Desktop and compact desktop: one line
  of name (`body`, `text`, ellipsis), a second line with the folder breadcrumb (`small`, `muted`, last three folders
  joined by " › "), then three columns of `fileColumn` 128: a label (`small`, `text2`), the date (`numberSmall`,
  Mono) and the size (`number`, Mono, right aligned); row height badge + 16. Phone and medium: the name on up to two
  lines (`bodyMobile`), the breadcrumb, then "size · date" (`numberSmall`, `muted`); at least the touch target tall.
  A file that is gone or lost its access shows "! Missing" / "! Access lost" in `warn` before the breadcrumb and its
  name in `text2` (sign and word, never colour alone). Hover: `surface2`. Row actions (icon buttons, 36 / 44) sit at
  the end; pin is an `IconButton` that shows `primarySoft` when pinned. Rows are separated by hairlines; the whole row
  opens the file.
- **File list:** rows in a section card; on desktop layouts a column header (`label`, `muted`) above a hairline lines
  up with the columns. Keyboard: Up / Down move the focus from row to row, Enter / Space open, Tab still reaches every
  row action. Long lists flow and the page scrolls (difference from TEMEL 5 "page does not scroll", see below).
- **Sort control:** "Sort" (`small`, `muted`) then a segmented control: `surface2` track, radius `segment` 9,
  padding 2, three segments Name · Date · Size (`button`). The selected one is a `surface` chip with a `border2` line,
  `text` label and an arrow (down = descending, up = ascending, `iconSmall`, `text2`); the others `text2`. Choosing
  the selected segment again flips the direction; a new key starts descending (Date, Size) or ascending (Name).
  Default: Date, newest first. Segment height control - 4 (32 desktop, 40 touch inside the 44 track); full width with
  equal segments on phone. Radio semantics, the direction as state description.
- **Filter chips:** radius 999, control height; unselected `surface` with a `border` line and `text2` label, selected
  `primarySoft` fill and `primaryInk` label (the interaction blue, as for selected navigation). They wrap (flow row).
- **Secondary button:** `surface` fill, `border2` line, `text` label, icon `iconSmall` in `text2`, `surface2` on
  hover; same heights and disabled rule as the primary button. **Link button:** `primaryInk` label and chevron, no
  fill (hover `surface2`), for "All recent files".
- **Switch:** track 40 x 24 (radius 999) inside a 44 touch target; on: `primary` track with an `onPrimary` thumb at
  the end; off: `border2` track with a `surface` thumb at the start. The setting row carries the words (title `body`,
  sentence `small` in `text2`).
- **Information page:** a page over the current destination (the navigation keeps the destination selected); the top
  bar shows a back arrow and "File information"; back, Esc and Alt+Left close it; the navigator saves it, so on
  Android it returns after process death and the file is read again from its reference. Content, in this order:
  header card (badge 56 with the extension in `cardTitle`, the name in `pageTitle` wrapping in full, the breadcrumb),
  state card (icon and title in the status colour with the word, one sentence in `text2`, the fix as a primary
  button; recognised files: "Recognised · PDF" and the phase of the viewer), the actions (secondary buttons: Open in another app, Show in folder on desktop, Remove from recent; shown
  only where they work), then the details card: Name, Location (full path), Size, Modified (date and time, Mono),
  Format, Kind, Compatibility, as label (`small`, `muted`) and value rows separated by hairlines. Desktop layouts:
  two equal columns (header, state, actions | details), the label column `detailLabel` 136. Phone: one column, the
  label above its value so long names and paths keep the width.
- **States of the information page:** reading ("Reading the file…" note), recognised (`pos`, check: "Recognised;
  the viewer arrives in Phase 4 / 5 / 6", PDF 4, viewer and archive 5, office formats 6; "Not supported" for unknown
  content), missing (`neg`, alert), access lost (`warn`, lock, "Pick again" opens the picker), damaged (`warn`,
  triangle), password protected (`warn`, lock), unreadable (`neg`, alert: locked or I/O error).
- **Numbers:** sizes "820 B", "412 KB", "3,8 MB", "18,4 MB", "246 MB" (one decimal under 100, Turkish decimal comma), dates "21.11.2026"
  in Turkish and ISO "2026-11-21" otherwise (no month names to translate, never ambiguous), local time zone.

**Screens per layout**

| Screen | Phone (< 600) and medium | Desktop and compact desktop |
|---|---|---|
| Home | start card (Home badge, title, sentence, "Open file" + "Add folder" wrapping), recent files card with the latest 5 (pinned first, pin mark) and "All recent files", file-type tiles | start card in one row (badge, text, buttons at the end), latest 8 recent files with columns, tiles in one row |
| Recent Files | chips (All, the five families, Other) wrapping, sort control full width, list with pin and remove | chips and sort control on one row, list with columns Type · Opened · Size and the two actions |
| Documents … Archive | "Files: n" (and "Reading added folders…", folders not read, cut at the file limit) above the sort control, list | status and sort control on one row, list with columns Source (Recent or the folder name) · Date · Size |
| Settings | Recent files (switch, clear), Added folders (sentence, list with remove, "Add folder" in the title row), Language and theme | two columns: Recent files and Language and theme | Added folders |
| File information | one column | two columns |

No files: Recent Files and the five family screens show their empty state with the working action (family screens
add "Add folder" where folders exist); File Converter keeps its disabled action and chip. Web: no folders (Settings
says so, no "Add folder"), recent files last for the tab, no "Open in another app".

**Differences from TEMEL (approved by the owner, 2026-11-22):** long file lists (up to 100 recent files plus the pinned ones, a
folder's files) scroll with the page on desktop instead of paging (TEMEL 5 order ① columns ② paging): a file list is
natural list content and keyboard Up / Down follow it; the sort and filter controls scroll with it. Removing a recent
file or clearing the list has no undo toast yet (TEMEL 6); left as a follow-up.

## 7. Document view vs edit mode (touch)

- Documents open in **view mode** (spec 10): full-width page, top bar with title, a small toolbar (search, zoom,
  share, more) and one primary action **Edit**. No editing chrome is visible.
- **Edit mode** starts only from that explicit action. The top bar shows Done (primary) and undo / redo; a contextual
  toolbar (height 48) sits **above the on-screen keyboard** and changes with the selection (text, table cell, image,
  slide). No desktop ribbon on a phone.
- The layout stays usable with the keyboard open: the toolbar moves with the keyboard inset, the caret line stays
  visible, the bottom bar hides in edit mode.
- Desktop: the same document opens in view mode too; Edit switches the toolbar to the full editing set; keyboard
  shortcuts work in both.
- Features missing on a layout (phone vs tablet vs desktop) are recorded in the compatibility matrix, not hidden.

## 8. Tokens: source and generation

- `design/tokens/fai.tokens.json`: verbatim copy of the kit (CI has no access to `claude-ortak`); provenance in
  `design/tokens/README.md`.
- `design/tokens/office.tokens.json`: FAI Office additions, renames, drops and the light-theme overrides above.
- `python3 tools/tokens/gen_compose.py` merges both into
  `app/src/commonMain/kotlin/org/faioffice/app/theme/FaiTokens.kt` (generated, committed, do not edit) and prints the
  contrast summary. `--check` fails with the difference when the file is out of date; `ci/lint.sh` runs it.
- The generated file is the only source of colour, type, spacing and radius in `app/`: no `Color(0x…)`, sp or dp
  literal elsewhere. Gate: `ci/lint.sh` step `hardcode` scans `app/src/commonMain` outside `theme/` for
  `Color(0x…)` / `Color(<number>…)`, named colours (`Color.Red` …) and bare `<number>.dp` / `.sp` / `Dp(<number>)`.
  Allowed: `Color.Transparent`, `Color.Unspecified`, comments, and a line ending in `// hardcode-ok: <reason>`
  (reviewed exception; today only the Lucide placeholder stroke). A new size goes to
  `design/tokens/office.tokens.json` and is generated. The step first checks itself against planted violations.

## 9. Screenshots

`kmp_test app` renders a token preview (palette, file types, type scale, controls, surfaces, recent files with
real-length TR / EN names) to `app/build/bakis/<theme>_<size>_tokens.png`, themes `acik` / `koyu`, sizes 390x844
and 1920x950 (desktop JVM, `ImageComposeScene`). P2.7 extends this into the screen package.

Shell shots (`ShellShotTest`): every destination screen as `app/build/bakis/<theme>_<size>_<screen>.png` at 390x844
and 1920x950 (screens `home`, `recent_files`, `documents`, `presentations`, `spreadsheets`, `pdf`, `file_converter`,
`archive`, `settings`: 36 shots), plus `<theme>_<size>_shell.png` with Home at 700x900 and 1100x800; light shots in
Turkish, dark shots in English. `ShellScreensTest` opens each destination and checks its title and its action
(enabled since P3.8, File Converter's disabled).

File screen shots (`FileShotTest`, P3.8): `<theme>_<size>_<screen>_<lang>.png` in both themes, `en` and `tr`, at
390x844 and 1920x950, with a sample library of real-length Turkish and English names (pinned, missing and
access-lost entries, two added folders): `home_files`, `recent_files_list`, `documents_list`, `spreadsheets_list`,
`presentations_list`, `pdf_list`, `archive_list`, `settings_files`, the empty `recent_files_empty`,
`documents_empty`, `settings_empty`, and the information page `file_info`, `file_info_missing`,
`file_info_permission`, `file_info_damaged`, `file_info_protected` (128 shots). `FileListKeyboardTest` walks the
Recent Files list with Tab, Up / Down and Enter.
