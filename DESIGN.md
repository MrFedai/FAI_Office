# FAI Office — DESIGN

Extends the shared design base `claude-ortak/tasarim/TEMEL.md` (kit v1.0, tokens 1.1.0). Only the differences and
the office-specific parts are written here; everything else follows TEMEL. Where the two disagree, this file wins.
Status: draft for owner approval (P2.4, 2026-11-18).

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
- **Not bundled yet:** the P2.4 run could not read the source TTFs outside its worktree. Until the script runs, the
  theme uses the platform sans and monospace families (one switch in `FaiTheme.kt`).
- Expected size (estimate from Plex glyph counts, to be replaced by the script's measurement): about 40 KB per
  subset face, ≈ 240 KB for six faces, ≈ 130 KB gzip. That is < 1 % of the Android 30 MB budget and ≈ 2.6 % of the
  Web 5 MB budget.

## 5. Spacing, radius, size

- Spacing scale (TEMEL 4): `FaiSpace.s2 … s32` = 2 · 4 · 6 · 8 · 10 · 12 · 14 · 16 · 20 · 24 · 32; kit names
  `pagePadX` 16, `pagePadXCompact` 12, `pagePadY` 14, `gap` 12, `cardPad` 16, `mobilePad` 12, `mobileGap` 10.
- Radius: control 8, segment 9, card 10, modal 14, chip 999.
- Sizes: top bar 52, phone tab bar 68, touch target ≥ 44, desktop control 36 (44 large), side navigation 232
  (rail 72), document toolbar 48, icon 20 (small 16), file badge 36, dialog ≤ 720, line 1.
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
  literal elsewhere (a lint rule follows in P2.5).

## 9. Screenshots

`kmp_test app` renders a token preview (palette, file types, type scale, controls, surfaces, recent files with
real-length TR / EN names) to `app/build/bakis/<theme>_<size>_tokens.png`, themes `acik` / `koyu`, sizes 390x844
and 1920x950 (desktop JVM, `ImageComposeScene`). P2.7 extends this into the screen package.
