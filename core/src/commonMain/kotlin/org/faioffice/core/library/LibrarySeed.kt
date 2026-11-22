/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import org.faioffice.core.files.FileRef
import org.faioffice.core.formats.FileFormat

/** What [LibraryStore.seed] did, with the library's counts after it. */
data class SeedResult(
    val outcome: Outcome,
    val recents: Int,
    val folders: Int,
) {
    enum class Outcome { Seeded, AlreadySeeded, Refused, NotSaved }

    /** The line ci/budget.sh reads (logcat, stdout, FAI_BUDGET_OUT): `fai_budget seed 100 2 seeded`. */
    val line: String get() = "fai_budget seed $recents $folders ${outcome.name.lowercase()}"
}

/**
 * The budget seed (PHASE3_PLAN P3.9): a full library for ci/budget.sh to measure Home with, [RECENTS] recent files
 * and [FOLDERS] added folders. Only an explicit trigger writes it (Android intent extra, desktop environment
 * variable), through [LibraryStore.seed], which never replaces a library holding anything of the user's.
 *
 * The list is the same on every platform and every run: names of every family and of short to long length, a few
 * pinned. The platform turns each [File] into a reference ([recents], [folders]): the desktop creates real files and
 * folders (except the [File.missing] ones), Android points at shared storage documents it holds no grant for.
 */
object LibrarySeed {
    const val RECENTS = LibraryStore.MAX_UNPINNED
    const val FOLDERS = 2

    /** Names of the two added folders. */
    val folderNames: List<String> = listOf("Proje belgeleri", "Faturalar ve makbuzlar 2026")

    /**
     * One seeded recent file: it lies in the added folder [folder] (index into [folderNames]) or, when null,
     * elsewhere. A [missing] file is not created: it stands for one deleted or moved since it was opened.
     */
    data class File(
        val name: String,
        val format: FileFormat,
        val size: Long,
        val folder: Int?,
        val missing: Boolean,
        val pinned: Boolean,
    )

    /** The [RECENTS] seeded files, newest first. */
    fun files(): List<File> =
        List(RECENTS) { i ->
            val format = FORMATS[(i * FORMAT_STRIDE) % FORMATS.size]
            val name = STEMS[i % STEMS.size] + SUFFIXES[i / STEMS.size] + "." + format.canonicalExtension
            val slot = i % PLACE_CYCLE
            File(
                name = name,
                format = format,
                size = MIN_SIZE + (i.toLong() * SIZE_PRIME) % SIZE_SPREAD,
                folder = if (slot < FIRST_FOLDER_SLOTS) 0 else if (slot < SECOND_FOLDER_SLOTS) 1 else null,
                missing = slot == MISSING_SLOT,
                pinned = i in PINNED,
            )
        }

    /** Recent entries for [files] at [refOf], opened one after another before [now] (epoch milliseconds). */
    fun recents(
        files: List<File>,
        now: Long,
        refOf: (File) -> FileRef,
    ): List<RecentEntry> =
        files.mapIndexed { i, file ->
            RecentEntry(refOf(file), file.name, file.format, file.size, now - (i + 1) * OPEN_GAP_MS, file.pinned)
        }

    /** The [FOLDERS] added folders at [refOf] (index into [folderNames]), added before every recent file. */
    fun folders(
        now: Long,
        refOf: (Int) -> FileRef,
    ): List<LibraryFolder> =
        folderNames.mapIndexed { i, name -> LibraryFolder(refOf(i), name, now - (RECENTS + FOLDERS - i) * OPEN_GAP_MS) }

    private val FORMATS =
        listOf(
            FileFormat.Docx,
            FileFormat.Xlsx,
            FileFormat.Pdf,
            FileFormat.Pptx,
            FileFormat.Odt,
            FileFormat.Ods,
            FileFormat.Csv,
            FileFormat.PlainText,
            FileFormat.Jpeg,
            FileFormat.Png,
            FileFormat.Zip,
            FileFormat.Odp,
            FileFormat.Doc,
            FileFormat.Xls,
            FileFormat.Epub,
            FileFormat.Rtf,
        )

    // 25 stems x 4 suffixes give 100 distinct names, from "Not" to well over 60 characters.
    private val STEMS =
        listOf(
            "Not",
            "Rapor",
            "Bütçe 2026",
            "Toplantı tutanağı",
            "Sunum taslağı - müşteri ziyareti",
            "IMG_20261104_181233",
            "Kira sözleşmesi",
            "Q3 satış rakamları bölge bazında karşılaştırma ve öngörü",
            "CV",
            "Fatura-2026-0412",
            "Proje planı",
            "Ders notları hafta 7",
            "Yıllık faaliyet raporu taslak sürüm üzerinde son okuma notlarıyla birlikte",
            "Menü",
            "Envanter listesi",
            "Seyahat masrafları",
            "Tez bölüm 3 yöntem",
            "Ekran görüntüsü",
            "Dilekçe",
            "Haftalık vardiya çizelgesi",
            "Kurulum kılavuzu",
            "Teklif",
            "Aile fotoğrafları arşivi",
            "Okuma listesi",
            "Vergi beyannamesi ekleri",
        )
    private val SUFFIXES = listOf("", " (2)", " son hali", " - kopya 2025-12-30")

    private const val FORMAT_STRIDE = 7
    private const val PLACE_CYCLE = 10
    private const val FIRST_FOLDER_SLOTS = 4 // 40 in the first folder
    private const val SECOND_FOLDER_SLOTS = 7 // 30 in the second, then 20 elsewhere
    private const val MISSING_SLOT = 9 // 10 of the elsewhere files are missing
    private const val MIN_SIZE = 512L
    private const val SIZE_PRIME = 7919L
    private const val SIZE_SPREAD = 96_000L
    private const val OPEN_GAP_MS = 37L * 60 * 1000
    private const val PIN_NEWEST = 2
    private const val PIN_MIDDLE = 11
    private const val PIN_OLDER = 37
    private val PINNED = setOf(PIN_NEWEST, PIN_MIDDLE, PIN_OLDER)
}
