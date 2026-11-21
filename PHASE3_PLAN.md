# FAI_Office — Phase 3 Plan: file management, format detection, recent files, safe save

Status: **draft, 2026-11-21**, after Phase 2 approval (v0.2.0). Scope from MASTER_SPEC sections 3, 5 (safe save) and
13 (Phase 3), and `PHASE1_PROPOSAL.md` section 12 row 3. Platform order since 2026-11-21: Android and Windows first,
Web keeps compiling, Linux builds stay in CI (section 2). No document viewer yet: PDF is Phase 4, viewer and archive
Phase 5, office formats Phase 6.

Item fields are read by the project's run tooling: `Efor` (agent effort: high / medium / low), `Done when`
(acceptance), `Ask owner` (decision needed before the item starts). A finished item gets `— YAPILDI (<commit>)`
in its heading.

## Decisions (owner, 2026-11-21)

* **Android storage: Storage Access Framework only.** Files and folders the user picks, persisted URI grants; no
  `MANAGE_EXTERNAL_STORAGE`, no device-wide scan. Cloud providers reached through SAF are the user's choice; the
  app itself makes no network request.
* **Category screens** (Documents, Spreadsheets, Presentations, PDF, Archive) list files of their kind from recent
  files plus **folders the user added** (Android folder tree grant, Windows folder). A folder is listed when its
  screen opens; no background scan, no index kept on disk.
* **Web: minimal.** The Web build keeps compiling; files open through a file input, recent files last for the
  session only. Full File System Access (persistent handles, save in place) moves to Phase 10. Deviation from
  `PHASE1_PROPOSAL.md` section 12 row 3 ("browser File System Access").
* **Opening a file** shows a file information page (name, location, size, detected format, what phase brings its
  viewer) with "Open in another app" on Android and Windows.

Defaults taken without asking (change if wrong): recent list keeps 100 unpinned entries, pinned ones are kept;
"Remember recent files" is on by default with a switch and "Clear" in Settings; on Windows a second launch hands its
file to the running instance; the app is offered in Windows "Open with" for the supported formats but never claims to
be the default app; new dependencies only from section 11's allowed licenses, each named in its item's result.

## Exit criteria

* One file access API in `core/` with Android (SAF), Windows (desktop files) and Web (file input) implementations;
  Linux uses the desktop implementation and keeps building in CI.
* Every file the app can be handed is identified by content, not by extension alone, and routed to its module
  family; damaged and hostile files are reported, never crash the app.
* Recent files and user-added folders work on Android and Windows: open, pin, remove, clear, missing file and lost
  permission states, survive restart (Android: also process death).
* Safe save: write to a temporary file, verify, then replace; on Android content URIs a backup until the write is
  verified. Fault-injection tests show a readable file (old or new) after a failure at every step.
* Home, Recent Files and the category screens show real files; the file information page exists; English and
  Turkish, light and dark; screenshot tests at 390x844 and 1920x950.
* Section 7 budgets still hold on the Galaxy A55 5G and the native Windows PC, measured with a seeded recent list.

## Order and gates

P3.1 first (the API every other item builds on). Then P3.2 and P3.3 in parallel (both only need P3.1), then the
platform items P3.4, P3.5, P3.6, then P3.7, P3.8 and P3.9. Everything runs as a `/surum` run.

Owner actions: the Galaxy A55 5G (adb) for P3.4 and P3.9; the native Windows PC for P3.5 and P3.9; the owner opens
files from another app (Android "Open with" / share, Windows Explorer "Open with") once in P3.4 / P3.5.

---

### P3.1 File access API — YAPILDI (dev 004b7fc)

Efor: high

Platform-neutral file access in `core/` (`core/files`): a file reference that survives restart and serialises to a
string (Android content URI, Windows / Linux path, Web session handle), display name, size, modified time, MIME
from the platform when present; bounded random-access read (detection reads the head and the ZIP central directory
without loading the file); streamed read; write only through the safe-save layer (P3.3); folder listing (children,
kind, size) with cancellation. Errors are typed (not found, permission lost, locked by another app, read-only,
damaged), not exceptions leaking from the platform. An in-memory fake implements the same contract for tests.
The design (why a reference type and not paths, how each platform maps) is written in this item's result.

Done when: the API compiles for Android, desktop and Wasm; one shared contract test suite runs against the fake
and, on desktop, against real files in a temp directory; no platform type appears in `commonMain`.

Result: `org.faioffice.core.files` in `core/commonMain`. The app holds a `FileRef` (kind + opaque location,
`serialize()` / `parse()` as `<tag>:<location>`), never a path: an Android document is a content URI with no file
name or folder, a Web file is a session handle, so name, size, time, MIME and `writable` always come from
`FileAccess.info` (`FileInfo`). Mapping: desktop `path:` = absolute normalised `java.nio` path (Windows and Linux,
`DesktopFileAccess` in `desktopMain`, minimal; P3.5 adds dialogs and Windows specifics); Android `content:` = SAF
URI, valid after restart while its persisted grant is held (P3.4); Web `web:` = per-page handle id,
`survivesRestart = false` (P3.6); `mem:` = `InMemoryFileAccess`, the fake (fault hook per operation, read-only
files) for tests. `FileAccess`: `info`, `list` (direct children), `readRandom` (positioned `readAt` + bounded
`readBytes`, for the head and the ZIP central directory; a provider that cannot seek copies to app-private storage
first) and `readStream`. Calls are `suspend`, main-safe and safe to run concurrently; handles live only inside their
block. Cancellation is cooperative (`CancellationException`, never a `FileError`); `list` checks between entries,
reads per chunk. Errors are `FileResult.Failure(FileError)`: `NotFound`, `PermissionLost`, `Locked`, `ReadOnly`,
`WrongKind`, `AlreadyExists`, `NoSpace`, `IoError` (damaged / unreadable); a handle throws `FileAccessException`,
which its call turns into a failure. Writing is `RawFileWrites` (`createSibling`, `write` truncate + `flush`
= fsync, atomic `replace`, `delete`), reached as `FileAccess.rawWrites` behind the `@SafeSaveOnly` opt-in (a
compile error outside safe save; null on Web). One contract suite (`FileAccessContract`, commonTest, Turkish
names) runs on the fake (desktop, Android host, Wasm) and on real temp files (desktop). New dependency:
kotlinx-coroutines 1.9.0 (Apache-2.0), the version Compose already ships, for cancellation and `Dispatchers.IO`.

### P3.2 Format detection

Efor: high

Content-based detection in `core/` (common Kotlin, no new native code), extension and platform MIME only as
tie-breakers: PDF (`%PDF-` in the first 1 KB); ZIP containers by their central directory, read with hard limits
(entry count, directory size; never decompress more than the one small part it needs): OOXML by
`[Content_Types].xml` main part (DOCX / XLSX / PPTX, macro-enabled and template variants), ODF and EPUB by the
`mimetype` entry, otherwise a ZIP archive; OLE2 compound files by stream names (DOC, XLS, PPT; encrypted OOXML
as "password protected"); RTF, 7Z, RAR 4 / 5, TAR, gzip / bzip2 / xz; images (PNG, JPEG, GIF, WebP, BMP, TIFF),
SVG, HTML, XML; text vs. binary (BOM, UTF-8 validity, NUL bytes), CSV and code files by extension on text.
Result: format, module family (documents, spreadsheets, presentations, PDF, archive, viewer, unknown), whether the
extension disagrees, and "damaged" for truncated or malformed containers. The format table is the seed of the
compatibility matrix (spec section 5): every detected format has an entry at level 0 "recognised"; later phases
raise levels only with passing corpus tests.

Done when: a test per format passes on small committed fixtures (generated, freely redistributable; `poc/corpus`
files where they fit) and on the typical documents; hostile cases (truncated ZIP, central directory claiming
millions of entries, zip bomb part, empty file, renamed extension) give a result within the limits; detection time
on a 100 MB file is measured on desktop and reported (head and central directory only).

Depends on: P3.1.

Result: `org.faioffice.core.formats` in `core/commonMain`, common Kotlin with no new dependency and no platform
type. `FormatDetector(fileAccess).detect(ref)` reads `FileAccess.info` (name, MIME) and then works inside one
`readRandom` block; file errors stay `FileResult.Failure` (a folder is `WrongKind(File)`), damaged content is a
successful `FormatDetection(format, damaged, passwordProtected, extensionMismatch)` with `family` and `level`;
`detectFormat(reader, name, mime)` is the same without `FileAccess`. Order: empty file, then the 8 KB head: OLE2
signature, ZIP local or empty-archive signature, `%PDF-` in the first 1 KB (damaged when the last 1 KB has no
`%%EOF`), fixed signatures (RTF, 7Z, RAR 4 and 5, xz, gzip, bzip2, PNG, JPEG, GIF 87a / 89a, WebP, TIFF II / MM, BMP
with a valid info-header size, TAR `ustar` at 257), else text: a BOM (UTF-8, UTF-16 LE / BE) means text, a NUL
byte means binary, strict UTF-8 (a sequence split by the sample end allowed) means text, otherwise legacy 8-bit text
(Windows-1254) when at most 2 % are C0 control bytes; on text, markup in the first 2 KB gives HTML (doctype or
`<html`), SVG (`<svg`), XML (`<?xml`, or a tag with a closing tag), plain text by extension becomes CSV (csv, tsv)
or source code (kt, py, json, md, ...). ZIP: the end record in the last 64 KB + 22 bytes (ZIP64 locator and
record honoured), at most 512 KB of central directory and 4096 entries parsed, only `mimetype` and
`[Content_Types].xml` kept; a directory past its end record, or one claiming more entries than its bytes hold
(65535 classic, five million ZIP64, a terabyte directory) is damaged. `mimetype` (≤ 256 bytes) names ODT / OTT /
ODS / OTS / ODP / OTP / EPUB; `[Content_Types].xml` (≤ 256 KB compressed read, ≤ 64 KB inflated) names DOCX, DOCM,
DOTX, DOTM, XLSX, XLSM, XLTX, XLTM, PPTX, PPTM, POTX, POTM, PPSX, PPSM by the main-part `ContentType`; another OPC
package (XPS, XLSB) is a ZIP archive. Deflate is our own raw-DEFLATE decoder (`Inflate.kt`, stored, fixed and
dynamic Huffman, after zlib's `puff`) that stops at the output limit: a zip bomb part costs 64 KB and still yields
the type when it is named first. A ZIP without end record (cut file) is damaged; its first local entry still gives
an ODF type, else the extension picks among ZIP-based formats. OLE2: the header, FAT sectors via the DIFAT (header
109 entries, chain ≤ 64 sectors) and ≤ 32 directory sectors (loops stop there); stream names give DOC
(`WordDocument`), XLS (`Workbook` / `Book`), PPT (`PowerPoint Document`), and `EncryptionInfo` + `EncryptedPackage`
give password-protected OOXML (DOCX / XLSX / PPTX by extension, else `EncryptedOoxml`); a broken header or chain is
damaged. Extension and MIME are tie-breakers only (text kinds, damaged or encrypted containers); the MIME type
stands in for a missing extension; `extensionMismatch` is set only when the name's extension belongs to another
known format (an unknown extension is no disagreement). The format table (`FileFormat`: family, MIME, extensions;
48 entries) seeds the compatibility matrix: `CompatibilityMatrix.levels` holds every format except `Empty` and
`Unknown` (46) at level 0 `Recognised` (levels 1–3 = spec section 5). Identifiers are lower-cased with the invariant
rules (`lowercase("und")`, never the Turkish dotless i). Tests: commonTest (desktop, Android host, Wasm) 32 tests,
one per format on fixtures generated in the test (ZIP writer, fixed-Huffman deflater, OLE2 writer; a zlib
dynamic-Huffman `[Content_Types].xml` as hex), hostile cases (truncated ZIP, directory past its end, millions of
entries, 5096-entry directory over the limit, 20 MB zip bomb part declared as 1 GB, empty file, renamed extensions,
cut and looping OLE2 chains); desktopTest detects the eight typical documents from `poc/corpus/out` when present
(8 / 8 locally; skipped in CI, the files are not in git). Measured on desktop (WSL2, temp files, mean of 10 after a
warm-up): 100 MB DOCX-shaped ZIP (content types first, a 100 MB stored part, directory last) 2.4 ms, 100 MB PDF
1.6 ms; only the head, the tail and the directory are read.

### P3.3 Safe-save layer

Efor: high

The only write path for documents (spec section 5, "Safe save"). Desktop files: write a temporary file in the same
folder, flush to disk, verify (re-read: size and SHA-256 of what was written, plus an optional format validator
hook the engines plug in later), then atomic replace; where atomic replace is not available, keep a backup until
the new file is verified. Android content URIs: back up the original to app-private storage, write in truncate
mode, verify by re-reading, then drop the backup; on failure restore it. Leftover temp or backup files are found at
the next start and the user is told (recovery, never silent deletion of the user's data). "Save as copy" and
"locked by another app" (Windows: file open in another program) are part of the API. The warning before saving
non-round-trippable elements is an engine hook here, used from Phase 7.

Done when: fault-injection tests stop the save after each step (temp written, flushed, verified, replace started,
Android truncate written) and recovery leaves a readable file equal to the old or the new content every time, on
desktop and, for content URIs, in JVM tests with a fake content resolver (no device needed); a real save round trip
passes on the Windows CI runner.

Depends on: P3.1.

### P3.4 Android file access

Efor: high

SAF implementation of P3.1: open documents (`ACTION_OPEN_DOCUMENT`, several MIME types), add a folder
(`ACTION_OPEN_DOCUMENT_TREE`) and list it through `DocumentsContract`, persisted read / write grants (taken when a
file is kept in recents or a folder is added, released when removed; the platform grant limit handled), display
name and size from the provider. Incoming files: `ACTION_VIEW` ("Open with") and `ACTION_SEND` / `SEND_MULTIPLE`
(share to FAI_Office) for the detected formats, also while the app is running. "Open in another app": chooser
with a read grant. No storage permission in the manifest.

Done when: on the A55, a file picked from the Files app, a file shared from another app and a file opened with
"Open with" reach the file information page with the right format; a removed grant shows the lost-permission
state; a folder with 1 000 files lists and its time is reported; the merged manifest has no storage permission
(CI check).

Depends on: P3.1, P3.2.

### P3.5 Windows / desktop file access

Efor: high

Desktop implementation of P3.1 (`java.nio`, Windows first, Linux uses it unchanged): native open file and choose
folder dialogs (no new dependency unless the JDK dialogs fail the Windows look check; then a section 11 library,
named), files from the command line, drag and drop onto the window, single instance (a second launch passes its
files to the running app over a local socket in the user's app data folder, no network port), "Open in another
app" and "Show in folder". Windows installer: "Open with" entries for the detected formats, removed on uninstall,
no default-app claim. Long paths and non-ASCII names (Turkish characters) work.

Done when: on the owner's Windows PC a file opened from Explorer "Open with", one dropped on the window and one
picked in the dialog reach the file information page; a second launch with a file brings the running window
forward; Windows CI runs the P3.1 contract tests on real NTFS paths, including a Turkish file name and a path
longer than 260 characters.

Depends on: P3.1, P3.2.

### P3.6 Web file access (minimal)

Efor: medium

Web implementation of P3.1 within the minimal scope: open files through a file input (several at once), read them
for detection and the information page, session-only recent list; saving and folders report "not available on
Web yet" through the typed errors. No File System Access API, no IndexedDB in this phase.

Done when: the Wasm build passes CI with the initial load still ≤ 5 MB; in Chromium a picked file reaches the file
information page (browser test or a scripted check in `ci/web.sh`).

Depends on: P3.1, P3.2.

### P3.7 Recent files and folder library

Efor: medium

Store in `core/`: recent entries (reference, display name, format, family, size, last opened, pinned) and user-added
folders, persisted in app-private storage (Android files dir, Windows `%APPDATA%`, Linux XDG data dir) through the
safe-save layer, one small file, schema-versioned. 100 unpinned entries kept, oldest dropped; pinned entries kept.
State checked when shown, not in the background: available, missing, permission lost. Remove, pin / unpin, clear
all, "Remember recent files" switch (off clears and stops recording). Folder listing for category screens: bounded
depth and file count, cancellable, results in memory only.

Done when: unit tests cover ordering, the limit, pinning, schema migration from version 1 and a damaged store file
(app starts with an empty list and keeps the damaged file aside); restart on desktop and process death on Android
keep the list.

Depends on: P3.1, P3.2, P3.3.

### P3.8 File screens

Efor: high

Home: "Open file", "Add folder" and the latest recent files; Recent Files: full list with filter by family, pin,
remove; Documents, Spreadsheets, Presentations, PDF and Archive: files of their family from recents and added
folders, sort by name / date / size, empty state when none; Settings: recent files switch, clear, added folders.
File information page: name, location, size, modified, detected format and family, compatibility level ("recognised;
viewer arrives in Phase 4 / 5 / 6"), Open in another app, Show in folder (Windows), Remove from recent; states for
missing, permission lost (pick again), damaged, password protected. File Converter stays an empty state (Phase 6).
Phone and desktop layouts from `DESIGN.md`; new patterns (file row, sort control, information page) added there.
Android: the open file page survives process death; desktop keyboard navigation through lists.

Done when: the owner approves the new `DESIGN.md` patterns and the screens; screenshot tests cover every new screen
and state in both themes, EN and TR, at 390x844 and 1920x950; no hard-coded colour, size or user-visible literal
(existing lint rules); the screen package (`ux.sh`) refreshes.

Depends on: P3.4, P3.5, P3.6, P3.7.

Ask owner: approval of the `DESIGN.md` additions (at the review of this item).

### P3.9 Phase 3 close

Efor: medium

Budget run (`ci/budget.sh`) with a seeded recent list (100 entries, two added folders): Android cold start and Home
PSS on the A55, APK size, desktop first frame, package and RAM at rest on the native Windows PC, Web initial load;
report committed. Full CI once, screen package and `DESIGN.md` shown to the owner, `PHASE1_PROPOSAL.md` section 12
row 3 updated (Windows instead of Linux, Web file access in Phase 10), status note updated, version and
`CHANGELOG.md` for v0.3.0.

Done when: every section 7 budget measured in Phase 2 still holds on the named devices; the owner approves Phase 3.

Depends on: P3.1–P3.8; the A55 and the Windows PC.
