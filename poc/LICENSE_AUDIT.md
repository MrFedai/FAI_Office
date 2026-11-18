# Phase 1 — license audit (WP9)

Policy: MASTER_SPEC section 11. Allowed: MPL-2.0, Apache-2.0, BSD, MIT, ISC, zlib, public domain, OFL (fonts).
Case by case: LGPL (dynamic, replaceable). Forbidden: AGPL, GPL-only, SSPL, non-free.

Sources read: the third-party notice of the official LibreOffice 26.8.2 Linux build (`LICENSE.html`, every
component section), `configure.ac` at tag `libreoffice-26.8.2.2`, the `licenses/` folder of PDFium chromium/8111,
Graphite2 upstream `LICENSE`. Classification is from the notice text, not from memory.

## Verdict

* **PDFium: passes.** Every bundled component is permissive.
* **LibreOffice core: passes, conditionally.** The core is MPL-2.0 (OpenOffice.org heritage under Apache-2.0).
  Every bundled component that violates the policy is optional and has a `configure` switch that removes it.
  None of them is needed for opening, rendering, editing or converting office documents.
  The condition is checked in WP6: the notice file and linked libraries of the actual Android engine build must
  contain none of the "remove" rows below. If one cannot be removed, the candidate fails (section 11).

## LibreOffice Android configure (checked 2026-11-17, `lo-android/`)

* LibreOffice's Android target sets `--enable-mpl-subset` by itself: configure refuses or drops every piece that is
  not MPL or more liberal (poppler, GPGME, PostgreSQL, report builder, GPL icon themes, …). `MPL_SUBSET=TRUE` in
  the generated `config_host.mk`.
* The same Android block forces CoinMP (CPL-1.0) on and ignores `--disable-coinmp`. `lo-android/build.sh` patches
  that one line; result `ENABLE_COINMP=` (off). A second spot, `bin/lo-all-static-libs`, adds the CoinMP
  static libraries to the Android link unconditionally; the script wraps those lines in `ENABLE_COINMP`.
  The product build carries both patches.
* Also off in the result: poppler, libcmis, GPGME, MariaDB, Firebird, PostgreSQL, lpsolve, LDAP, Breakpad,
  bundled fonts, dictionaries, help. Theme: Colibre only.
* Still in the build list, all allowed: curl (MIT-style), HSQLDB and Java WebSocket (Java side, BSD / MIT, not
  in the native library), SANE (public-domain header).

## LibreOffice — components that must be removed

| Component | License (from notice) | What it does | Switch (26.8.2.2) |
|---|---|---|---|
| poppler | GPL-2 / GPL-3 | PDF import into Draw | `--disable-poppler` (PDF is handled by PDFium in the light core) |
| GPGME, libassuan, libgpg-error | LGPL-2.1 | OpenPGP document signing / encryption | `--disable-gpgmepp` |
| MariaDB Connector/C | LGPL-2.1 | Base: MariaDB / MySQL driver | `--disable-mariadb-sdbc` |
| Firebird | IPL-1.0 / IDPL-1.0 (not on the allowed list) | Base: embedded database | `--disable-firebird-sdbc` |
| lpsolve | LGPL-2.1 | Calc solver backend | `--disable-lpsolve` |
| CoinMP | CPL-1.0 (not on the allowed list) | Calc solver backend | `--disable-coinmp`; on Android two patches, see above |
| Solver for Nonlinear Programming | LGPL-3 | Calc extension (Java) | `--without-java` |
| Pentaho Reporting, Flute, SAC, beanshell, Rhino, HSQLDB, Java WebSocket | LGPL-2.1 / W3C / Apache / MPL / BSD | Java-only parts: Base reports, scripting, old DB | `--without-java`, `--disable-report-builder`, `--disable-scripting-beanshell`, `--disable-scripting-javascript` |
| Culmus fonts | GPL-2 (font exception) | Hebrew fonts | `--without-fonts` (FAI_Office bundles its own font set, section 6; Hebrew from Alef / Libre Hebrew, OFL) |
| Breeze, Elementary icon themes | GPL-2+ | LibreOffice UI icons | `--with-theme=colibre` (Colibre is CC0; LOK draws no LibreOffice UI) |
| Karasa Jaga icons | LGPL-3 | UI icons | same |
| Sifr icons | CC-BY-SA 3.0 | UI icons | same |
| Spelling / hyphenation dictionaries | mixed, several GPL / LGPL / CC-BY-SA | proofing data | `--without-myspell-dicts`; dictionaries are picked one by one later (Turkish spelling dictionary is MPL-2.0) |

Also disabled because they are cloud / network or server features outside the spec (section 8), not because of
license: `--disable-libcmis` (CMIS cloud storage), `--disable-ldap`, `--disable-postgresql-sdbc`,
`--disable-online-update`, `--disable-breakpad`, `--disable-python`, `--without-help`.

Headers only, no code linked: SANE (scanner, public-domain header), unixODBC (LGPL-2.1 headers, driver loaded
from the system). Both must be absent from the Android build; to be confirmed in WP6.

## LibreOffice — components kept

| License | Components |
|---|---|
| MPL-2.0 | LibreOffice core, liborcus, libabw, libcdr, libe-book, libeot, libepubgen, libetonyek, libfreehand, libmspub, libpagemaker, libqxp, libvisio, libzmf, NSS |
| MPL-2.0 chosen from a dual license (with LGPL) | librevenge, libwpd, libwpg, libwps, libmwaw, libodfgen, libstaroffice, liblangtag |
| MPL-1.1 chosen from MPL-1.1 / LGPL / GPL (MPL-1.1 §6.2 allows MPL-2.0) — written justification needed | Hunspell, Hyphen (only when spell check / hyphenation ships) |
| MIT / MPL-2.0 chosen from Graphite2's four-way license (upstream `LICENSE`) | Graphite2 |
| Apache-2.0 chosen from a dual license (with LGPL) | CLucene (help search only; goes with `--without-help`), Raptor / Rasqal / Redland |
| Apache-2.0 | AFDKO, Argon2, Dragonbox (LLVM exception), frozen, OpenSSL 3, ZXing-C++, Caladea font |
| BSD-style | Breakpad, bzip2, HSQLDB, IAccessible2 (Windows), libjpeg-turbo, libnumbertext, libtiff, libwebp, MyThes, PDFium, Random123, Skia, SVGPathSeg, Zstandard (BSD chosen over GPL-2) |
| MIT-style | Boost (BSL), Box2D, CLEW, fast_float, Epoxy, expat, GLM, HarfBuzz, ICU (Unicode), libcurl, libfixmath, libffi, libxml2, libxslt, lcms2, md4c, mdds, xmlsec, XSLT MathML, zxcvbn-c |
| 0BSD / public domain | liblzma, LibTomMath, SQLite |
| Other permissive | libpng (libpng license), OpenLDAP (OpenLDAP PL, removed anyway), PostgreSQL (PostgreSQL License, removed anyway), Python (PSF, removed anyway) |
| OFL-1.1 | Agdasima, Alef, Amiri, Bacasime Antique, Belanosima, Caprasimo, Carlito, Liberation (2.x), Linux Libertine G (OFL chosen over GPL+FE), Libre Hebrew, Lugrasimo, Lumanosimo, Lunasima, Noto, Reem Kufi, Scheherazade |
| Bitstream Vera / Arev (permissive) | DejaVu |
| CC0 | Colibre icon theme |

## PDFium chromium/8111 — bundled components

All permissive: PDFium (BSD-3), abseil (Apache-2.0), AGG 2.3 (BSD-style), dragonbox (Apache-2.0 LLVM / Boost),
fast_float (Apache / MIT / Boost), FreeType (FTL), HarfBuzz (MIT), highway (Apache-2.0),
ICU (Unicode), lcms2 (MIT), libjpeg-turbo (IJG / BSD-3 / zlib), OpenJPEG (BSD-2), libpng, llvm-libc (Apache-2.0
LLVM), simdutf (Apache-2.0 / MIT), zlib.

## Obligations carried into Phase 2

* One third-party notice file for the product, generated from the actual build, never hand-written.
* LibreOffice and other trademarks never appear in product or module names (section 11).
* MPL-2.0 file-level copyleft: changes to MPL files of LibreOffice or other MPL components are published as
  source. FAI_Office is MPL-2.0 itself, so this costs nothing.
* FreeType FTL requires a credit line in the documentation.

## Android build output check (WP6, 2026-11-17)

Checked on the built `liblo-native-code.so` and the build tree, not on configure flags:

* Unpacked and built third-party sources (`workdir/UnpackedTarball/`): no poppler, GPGME, libassuan,
  libgpg-error, CoinMP, lpsolve, MariaDB, Firebird, PostgreSQL, libcmis, OpenLDAP, Python, dictionaries, fonts.
* Text search in the library: no poppler, GPGME, MariaDB, lpsolve, libcmis, LDAP identifiers. The only hits for
  CoinMP and Firebird are service-name constants (`com.sun.star.comp.Calc.CoinMPSolver`, `sdbc:firebird:`), no code.
* Android-only components not in the desktop notice, all allowed: cairo (MPL-1.1 chosen from LGPL-2.1 / MPL-1.1,
  same written justification as Hunspell), pixman (MIT), fontconfig (MIT-style), libexttextcat (BSD-3).
* Present and allowed as listed above: Hunspell, Hyphen, MyThes, Graphite2, HarfBuzz, ICU, NSS, OpenSSL, curl,
  PDFium, and the MPL-2.0 import libraries.

Result: **the LibreOffice engine candidate passes the section 11 license gate on Android**, with the two CoinMP
patches in `lo-android/build.sh`.

## OpenSymbol font (checked 2026-11-18)

* Bundled with both engines (Android: part of LibreOffice's own fonts; Web: kept in the WP7 trimmed set) for
  bullets, numbering symbols and math characters.
* Not a third-party component: it is built from `extras/source/truetype/symbol/OpenSymbol.sfd` in the LibreOffice
  core repository (the build fetches the matching `opens___.ttf`, pinned by SHA-256 in `download.lst`, when
  FontForge is not available). That is why LibreOffice's notice file has no separate OpenSymbol section.
* The font itself carries no license string, only copyright lines (name ID 0): Sun Microsystems 2009 (the
  OpenOffice.org original), Google 2010 (digits and a few symbols) and later individual contributors.
* License therefore = the core repository's: MPL-2.0, with OpenOffice.org heritage under Apache-2.0.
  **Pass.** Obligation: name OpenSymbol under the LibreOffice entry of the generated notice file, because the font
  file does not state its license.

## LibreOffice WASM build output check (WP7, 2026-11-18)

* `lo-wasm/autogen.input` does not set `--enable-mpl-subset`, and unlike Android the Emscripten target does not
  set it by itself: `MPL_SUBSET=` and `WITH_FONTS=TRUE` in the generated `config_host.mk`.
* Off anyway through explicit switches: poppler, GPGME, MariaDB, Firebird, PostgreSQL, lpsolve, CoinMP, report
  builder, libcmis, LDAP, Lotus Word Pro import, extra extensions, dictionaries, help. Icon theme: Colibre only.
* Notice of the build (`instdir/LICENSE`): the only component against the policy is **Culmus** (GPL fonts),
  present in the untrimmed file-system image (18 files). The trimmed image (`lo-wasm/trim.sh`) contains only
  Liberation, Carlito, Caladea and OpenSymbol, so **no Culmus file is shipped there**. Hunspell, cairo and
  liblangtag appear as on Android (MPL choice from their dual licenses).
* Result: **the trimmed WASM engine passes the license gate.** The untrimmed PoC image would not.
  For the product build, add `--enable-mpl-subset` and `--without-fonts` to the WASM configuration, so configure
  enforces the subset and the notice file is generated for it, instead of relying on the trim step.
