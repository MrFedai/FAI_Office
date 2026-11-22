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

### P3.2 Format detection — YAPILDI (dev 5736a53)

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

### P3.3 Safe-save layer — YAPILDI (dev c367329)

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

Result: `SafeSave` in `org.faioffice.core.files` (`core/commonMain`), the only class that opts into `@SafeSaveOnly`;
documents are written with `save(target, source, acceptLosses)` or `saveAsCopy(destination, source, overwrite)`,
where the engine's `SaveSource` writes the bytes and carries two hooks: `lossyElements()` (non-empty stops the
save with `SaveError.LossyElements` until the user accepts; the copy skips it; used from Phase 7) and
`validate(RandomAccessReader)` (format check of the written file before it counts). Atomic path
(`RawFileWrites.atomicReplace`, desktop): journal entry, temporary sibling `<name>.fai-save.<n>.tmp`, write, flush
(fsync), verify (re-read: size + SHA-256 of what was written, then `validate`), atomic `replace`, drop the entry.
Backup path (content URIs): copy the original to `fai-save-backup.*` in the app-private journal folder, flush,
verify its hash, write the journal entry (the complete entry marks the backup valid; only then is the original
touched), write the target in truncate mode, flush, verify, drop backup then entry; on any failure, exception or
cancellation the backup is written back and verified. Results: `SaveResult.Saved(target, size, sha256)` or
`Failed(error, step, leftoverKept)` with `SaveError.File(FileError)` / `Unsupported` (Web, no `rawWrites`) /
`VerificationFailed` / `LossyElements`; `leftoverKept` only when cleanup or the restore failed too. Locked:
`FileError.Locked` (`SaveResult.isLocked`), on desktop also `AccessDeniedException` from `replace` on a writable file
in a writable folder (Windows refuses to replace a file another program holds open); the UI offers "Save as copy",
which refuses an existing destination unless `overwrite`. Recovery: every save keeps a journal entry
(`fai-save-journal.*`, line-based text ending in `end`, so a cut-off entry is detected) while it runs;
`findLeftovers()` at start lists `UnsavedTemporary`, `Backup` and `Orphan` (backup without a complete entry) items,
and the user decides with `restore` (backup written back, or the temporary file made the document) or `discard`;
nothing holding user data is deleted without that call (only entries whose leftover is gone or which were cut off).
Known gap: a crash between creating the temporary file and journalling it leaves that file, still empty, next to
the document. API change in P3.1's primitives: `RawFileWrites.createIn(folder, nameHint)`, because the journal and
the backups of content URIs (which have no folder) live in an app-private folder; `FileOp.CreateIn` for the fake.
SHA-256: `Sha256`, plain common Kotlin (Wasm too), checked against the FIPS vectors, padding edges and odd chunking;
no new dependency. Tests: `SafeSaveContract` runs on the fake and on real desktop temp files, each on the atomic
and the content-URI path (`FaultyFileAccess` test wrapper: faults before every call, chunk and flush, no atomic
replace for the fake content resolver): a power cut after every single operation (temp written, flushed, verified,
replace started, truncate written, cleanup) and an injected error at every operation both end, after recovery, in
the old or the new content with nothing left over; leftovers found at the next start and unchanged by finding;
validator rejection, lossy confirmation, save as copy, locked target, writer exception, Turkish names. Real round
trip and a file held open by another program (Windows: `Locked`, POSIX: saved) in `DesktopSafeSaveTest`, which
`ci/windows.sh` now runs on the Windows runner (`:core:desktopTest`). The UI for recovery and the warning is P3.8;
the Android SAF implementation (backup folder in app-private storage) is P3.4.

### P3.4 Android file access — YAPILDI (dev ca6807b)

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

Result: `AndroidFileAccess` (`core/androidMain`, `org.faioffice.core.files`): content URIs through SAF (`SafDocuments`:
`DocumentsContract` columns for documents and tree URIs, `OpenableColumns` for other providers; display name, size,
type, `FLAG_SUPPORTS_WRITE` and the held write grant decide `writable`; a tree is listed with
`buildChildDocumentsUriUsingTree` in one query), path references (app-private storage) through `PathFileAccess`
(plain `java.nio`, so the P3.1 contract and the P3.3 safe-save contract on the backup path run on the Android host
JVM). No atomic replace on Android: safe save takes the backup path for every file, journal in `filesDir/safe-save`.
A provider that cannot seek (pipe) is copied to the cache for `readRandom`; `SecurityException` = `PermissionLost`
(a refused write with the read grant still held = `ReadOnly`). Grants: `PersistedGrants` (common) with
`planGrants` / `sync` (folders first, then pinned, then newest recent files; a file inside a wanted tree needs no
grant; over the limit the tail is dropped; releases before takes) and `SafPersistedGrants` (read + write, else read
only; limit 128 on Android 10, 512 from 11); `LibraryStore.syncGrants`; desktop / Web use `NoPersistedGrants`. UI-less
channel in `app/commonMain` (`org.faioffice.app.files`): `FileHub` (one ordered worker; `open(refs, origin)` and
`addFolder(ref)`; `events: Flow<FileEvent>` with `Opened(OpenRequest(ref, origin, info, detection, error))`,
`FolderAdded(ref, name, listing, grantKept)`, `FolderFailed`; buffered until the single collector, so a cold-start
"Open with" is not lost), `FileLibrary` (store mutations with grant sync; a failed store write only takes grants),
`FileActions` (`openFiles`, `addFolder`, `openInOtherApp`; `NoFileActions`), `FileServices`, `IncomingFiles`
(intent action + URIs to requests: `content:` only, deduplicated, at most 100). Android: `AndroidFiles` (process-wide;
library store `filesDir/library/library.txt`, created empty first), `MainActivity` as its host (activity result
`OpenMultipleDocuments` with `OpenableMimeTypes.picker`, `OpenDocumentTree`), `launchMode="singleTask"`, intents from
`onCreate` (not on re-creation or from recents) and `onNewIntent`; "Open in another app" = `ACTION_VIEW` chooser with
the read grant, this app excluded. Manifest: `VIEW` (scheme `content`) and `SEND` / `SEND_MULTIPLE` filters with
`OpenableMimeTypes.incoming` (registered types of the detected formats plus aliases, lower case), kept equal by
`AndroidManifestTest`; `ci/android.sh` fails when the merged release manifest names `READ/WRITE/MANAGE_EXTERNAL_STORAGE`
or `READ_MEDIA_*` (self-tested). Measurable: logcat tag `FaiFiles` (one line per open with format and time, per folder
listing with entry count and time); `--ez org.faioffice.extra.MEASURE_FOLDERS true` lists every library folder once.
The file information page that shows the events is P3.8; the A55 checks are the owner's.

### P3.5 Windows / desktop file access — YAPILDI (dev b351a38)

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

Result: access is the P3.1 `DesktopFileAccess` (`core/desktopMain`, plain `java.nio`; on Windows `java.nio` adds the
`\\?\` long-path prefix itself). Its near twin `PathFileAccess` (`core/androidMain`) stays separate for now (sharing
needs a jvm+android source set; "Sonra bak"). The contract and safe-save tests also run with their root deep in a
Turkish-named tree longer than 300 characters (`DesktopLongPathTest`, plus a Turkish file name round trip: info, list,
read, safe save); `ci/windows.sh` already runs `:core:desktopTest` on the runner's NTFS disk, Linux runs it with
`LC_ALL=C.UTF-8` (`core/build.gradle.kts`). Desktop layer in `app/desktopMain` (`org.faioffice.app.files`), no new
dependency: `DesktopFiles` (the `FileActions` of the desktop, `services` for P3.8; library store in the app data
folder, `NoPersistedGrants`), started in `Main.kt` before the window. App data: Windows `%APPDATA%\FAI Office`
(library, journal) and `%LOCALAPPDATA%\FAI Office` (instance lock and socket), Linux `$XDG_DATA_HOME/fai-office`,
macOS `~/Library/Application Support/FAI Office`; `-Dfai.data.dir` overrides (`DesktopDirs`). Command line:
`DesktopCommandLine` (relative paths against the working folder, `file:` URIs decoded, other URIs dropped, options
before `--` skipped, deduplicated, at most 100); on Windows the arguments are re-read from the process's UTF-16 command
line (`ProcessHandle`, split by the C runtime rules) because `main(args)` passes the ANSI code page and loses Turkish
letters on a non-Turkish Windows. Single instance: `SingleInstance` holds `instance.lock` (`FileChannel.tryLock`) and
listens on `instance.sock` (JDK 16+ `UnixDomainSocketAddress`, Windows 10 1803+ and Linux, no network port); a second
launch sends its absolute paths (`InstanceProtocol`: `FAIO`, version, count, length-prefixed UTF-8, bounded, one
accept byte; 3 s deadline, 2 s of connect retries) and exits; the running app queues them (`OpenOrigin.Desktop`) and
brings its window forward (restore, `toFront`, a moment of always-on-top so Windows raises it). When the lock or socket
fails (folder not writable, socket path not encodable in the ANSI code page or too long, peer frozen) the launch runs
standalone and opens its files itself. Drag and drop: Compose `dragAndDropTarget` on a box around `App()` (nothing
drawn), `DesktopDrop` reads the AWT file list or a `text/uri-list`. Dialogs: JDK only, AWT `FileDialog` (multiple
files, no filter: detection decides, like the Android picker) and for folders Swing `JFileChooser` (directories only,
system look and feel set on first use; macOS `FileDialog` in folder mode); the dialog title is the system default (no
new text before P3.8). If the owner's Windows look check rejects them, the section 11 candidate is LWJGL's
`nativefiledialog` binding (LWJGL BSD-3, nativefiledialog zlib); not added. "Open in another app": Windows
`OpenWith.exe <file>` (the system chooser), Linux / macOS `Desktop.open` (default app). "Show in folder" (new in
`FileActions`: `canShowInFolder`, `showInFolder(ref)`, default false, so Android and Web are unchanged): Explorer
`/select,`, macOS `open -R`, Linux freedesktop `FileManager1.ShowItems` over `dbus-send`, else the parent folder.
Installer: jpackage's own file associations write the default value of `Software\Classes\.ext` (a default-app claim),
so they are unused. `app/msi/open-with.wxs` holds one per-user component: ProgID `FAIOffice.File` (open command
`"[INSTALLDIR]fai-office.exe" "%1"`), `Applications\fai-office.exe` (`FriendlyAppName`, `SupportedTypes`) and
`Software\Classes\.<ext>\OpenWithProgids` for the 61 extensions of every detected format with a registered MIME type
(the Android set); never the `.ext` default, nothing under `RegisteredApplications`; `WindowsOpenWithTest` checks list
and rules. `packageMsi` (Compose) gets a `doLast` that takes jpackage's `main.wxs` from the packaging JDK (jrt image),
adds `<ComponentGroupRef Id="FaiOpenWith"/>` to `DefaultFeature` and the fragment, and runs jpackage again with
Compose's argument file plus `--resource-dir` last (Compose passes its own empty resource dir after any extra
argument; jpackage keeps the last value). Windows Installer removes the component's registry values on uninstall.
`ci/windows.sh` fails when the MSI has no `OpenWithProgids` strings. Measurable: stderr lines `FaiFiles: ...` (single
instance role and claim / hand-over time, incoming origin and count per way: command line, second launch, drop, picker,
folder; per open format, size and time from `FileHub`; shell hand-offs started or failed with time), no names or paths.
Unit tests: `DesktopCommandLineTest`, `InstanceProtocolTest`, `SingleInstanceTest` (real socket: hand-over, window-only
request, stale socket file, take-over after close, frozen peer, unusable folder), `DesktopShellTest`. Linux smoke run (WSLg, not a measurement): a Turkish-named file from the command line
reached the hub, a second launch with another one handed it over and exited (about 0.2 s). Not verified here: the MSI build and its registry entries (Windows CI only) and the owner's Windows checks (Explorer "Open with",
drop, dialog look, second launch raising the window).

### P3.6 Web file access (minimal) — YAPILDI (dev 9e84cbc)

Efor: medium

Web implementation of P3.1 within the minimal scope: open files through a file input (several at once), read them
for detection and the information page, session-only recent list; saving and folders report "not available on
Web yet" through the typed errors. No File System Access API, no IndexedDB in this phase.

Done when: the Wasm build passes CI with the initial load still ≤ 5 MB; in Chromium a picked file reaches the file
information page (browser test or a scripted check in `ci/web.sh`).

Result: `WebFileAccess` + `WebFileSource` + `SessionRecents` in `core/wasmJsMain` (pure logic, no JS types; tests in
`core/wasmJsTest`, run in Node): picked files get a `FileRef.Kind.WebSession` reference and are read lazily by
position; listing and writing fail with `FileError.IoError("not available on Web yet")`, `rawWrites` is null. Recent
files are memory only (newest first, 100). `WebFiles` in `app/wasmJsMain` is the `FileActions` (hidden
`<input type="file" multiple>`, no `accept` filter; `addFolder` / `openInOtherApp` return false) and puts picked files
on `FileHub` as `OpenOrigin.Picker`; `Main.kt` creates it. No screen (P3.8); the "reaches the information page" check
in Chromium belongs to P3.8, here the hub path is covered by the Node tests of the access layer only.

Depends on: P3.1, P3.2.

### P3.7 Recent files and folder library — YAPILDI (dev f6e6744)

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

Result: `org.faioffice.core.library` in `core/commonMain`; no UI (P3.8). `LibraryStore(access, safeSave, storeFile,
damagedFile, clock)` keeps recent entries (`RecentEntry`: ref, name, `FileFormat` + derived family, size, last opened,
pinned) and user folders (`LibraryFolder`) in one file written only through `SafeSave.save`. Deviation from the task
text: the store takes the store file's `FileRef` and a `damagedFile` `FileRef` instead of a folder, because a `FileRef`
is opaque and a child ref cannot be derived without the raw-write primitives; P3.4 / P3.5 / P3.8 bind both (Android
files dir, Windows `%APPDATA%`, Linux XDG data dir) and, where `SafeSave` cannot create files (content URIs), create
them empty first. Absent or empty store = empty library. Format: UTF-8 lines, tab-separated, `\`-escaped, first line
`FAI-LIBRARY <version>`; version 2 (current) has `remember`, `recent` (with `pinned`) and `folder` lines; version 1 =
`recent` lines without the `pinned` field, no switch, no folders (migrated: remember on, nothing pinned; the next
write is version 2). Unknown newer version, bad line or invalid UTF-8 = damaged: the bytes are copied to `damagedFile`
with `saveAsCopy` (never deleted), the library starts empty, the next save writes a fresh store. A read error other
than not-found (locked, I/O) leaves the store unloaded and unwritten so a good file is not overwritten; mutators return
false until a later call can read it. Operations run one at a time under a `Mutex`; mutators return whether the change
reached disk (memory keeps it either way). Recording moves an existing ref to the front (one entry per ref, pin kept);
100 unpinned entries are kept, the oldest unpinned is dropped, pinned never count. Switch off clears recents (folders
stay) and `recordOpened` becomes a no-op; the switch is persisted. State is checked only on request (`stateOf`,
`recentsWithState`) via `FileAccess.info`: success = available; `NotFound`, `WrongKind`, `IoError` = missing;
`PermissionLost` = permissionLost; `Locked`, `ReadOnly`, `NoSpace`, `AlreadyExists` = available (the file exists).
`FileAccess.scanFolder(folder, family, limits)` lists breadth first (defaults depth 3, 2000 files), filters by family
from the extension only (no content detection), honours cancellation between folders and entries, returns results in
memory; a root that cannot be listed fails, deeper failures and folders past the depth limit count in `skippedFolders`,
the file limit sets `truncated`. Tests: `LibraryStoreContract` (ordering, re-open, 100 limit with pinned, pin / remove /
clear, switch, restart, v1 migration, damaged / newer-version store, state available / missing) runs on
`InMemoryFileAccess` (commonTest: JVM, Android host, Wasm) and on real temp files with `DesktopFileAccess` (desktopTest);
restart = a new `LibraryStore` on the same files. Android process death is the same re-read of the store file, covered
by the commonTest run on the Android host (no device run). Fault tests cover permissionLost and an unreadable store;
scan tests cover depth, family, file limit, failed subfolder and cancellation. Folder locations come from the platform
layer; nothing here resolves them.

Depends on: P3.1, P3.2, P3.3.

### P3.8 File screens — YAPILDI (dev 20cb397)

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

Result: screens in `app/src/commonMain/kotlin/org/faioffice/app/screens/` (`FilePatterns.kt`, `ListScreens.kt`,
`FileInfoScreen.kt`, `SettingsScreen.kt`), state in `files/FilesModel.kt` over `FileHub` / `FileLibrary`; the
information page sits over the current destination and `ShellNavigator` is saved with a `Saver` (Android process
death). `DESIGN.md` section 6 "File screens" approved by the owner 2026-11-22, including two TEMEL differences (long
desktop lists scroll with the page; no undo toast yet). `FileShotTest` 128 shots (2 themes x EN/TR x 390x844 /
1920x950), `FileListKeyboardTest`, `FilesModelTest`, `FileTextTest`; `ci/ux-cekim.sh` runs `FileShotTest` too.
Not done: browser test that a file picked in Chromium reaches the information page (no browser driver in the repo).

### P3.9 Phase 3 close

Efor: medium

Budget run (`ci/budget.sh`) with a seeded recent list (100 entries, two added folders): Android cold start and Home
PSS on the A55, APK size, desktop first frame, package and RAM at rest on the native Windows PC, Web initial load;
report committed. Full CI once, screen package and `DESIGN.md` shown to the owner, `PHASE1_PROPOSAL.md` section 12
row 3 updated (Windows instead of Linux, Web file access in Phase 10), status note updated, version and
`CHANGELOG.md` for v0.3.0.

Done when: every section 7 budget measured in Phase 2 still holds on the named devices; the owner approves Phase 3.

Depends on: P3.1–P3.8; the A55 and the Windows PC.
