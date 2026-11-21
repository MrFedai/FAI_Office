/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileError
import org.faioffice.core.files.FileInfo
import org.faioffice.core.files.FileOp
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.FileResult
import org.faioffice.core.files.InMemoryFileAccess
import org.faioffice.core.files.NoPersistedGrants
import org.faioffice.core.files.SafeSave
import org.faioffice.core.files.SafeSaveOnly
import org.faioffice.core.files.map
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.FormatDetection
import org.faioffice.core.library.LibraryStore

/** File actions of a desktop-like platform that only record what was asked. */
class RecordingActions : FileActions {
    val calls = mutableListOf<String>()

    override val canAddFolder: Boolean = true

    override val canShowInFolder: Boolean = true

    override fun openFiles(): Boolean = calls.add("openFiles")

    override fun addFolder(): Boolean = calls.add("addFolder")

    override fun openInOtherApp(
        ref: FileRef,
        mimeType: String?,
    ): Boolean = calls.add("openInOtherApp $ref")

    override fun showInFolder(ref: FileRef): Boolean = calls.add("showInFolder $ref")
}

/**
 * [InMemoryFileAccess] with real-looking sizes and modification times (the fake counts bytes and ticks), so the
 * screenshots show "18,4 MB" and "17.11.2026" instead of "12 B" and 1970.
 */
@OptIn(SafeSaveOnly::class)
class SampleAccess(
    private val inner: InMemoryFileAccess,
) : FileAccess by inner {
    val meta = mutableMapOf<FileRef, Pair<Long, Long>>()

    private fun FileInfo.real(): FileInfo =
        meta[ref]?.let { (size, modified) -> copy(size = size, modifiedEpochMillis = modified) } ?: this

    override suspend fun info(ref: FileRef): FileResult<FileInfo> = inner.info(ref).map { it.real() }

    override suspend fun list(folder: FileRef): FileResult<List<FileInfo>> =
        inner.list(folder).map { all ->
            all.map {
                it.real()
            }
        }
}

/**
 * Sample library for the file screens' tests and screenshots: real-length names in Turkish and English (Turkish
 * letters, long titles), ten recent files (two pinned, one missing, one with its access lost) and two added folders,
 * on the in-memory file system. Dates around 21.11.2026.
 */
class SampleFiles(
    populated: Boolean = true,
) {
    val fake = InMemoryFileAccess()
    val access = SampleAccess(fake)
    val actions = RecordingActions()
    private var now = NOW
    val store =
        LibraryStore(
            fake,
            SafeSave(fake, fake.addFolder("/app/safe-save")),
            fake.ref("/app/library.txt"),
            fake.ref("/app/library-damaged.txt"),
        ) { now }
    val library = FileLibrary(store, NoPersistedGrants)
    val hub = FileHub(access, CoroutineScope(Dispatchers.Unconfined), library)
    val services = FileServices(access, hub, actions, library)

    /** Refs by short key, for the tests. */
    val refs = mutableMapOf<String, FileRef>()

    init {
        if (populated) runBlocking { populate() }
    }

    private fun file(
        key: String,
        path: String,
        size: Long,
        modified: Long,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ): FileRef {
        val ref = fake.addFile(path, bytes)
        access.meta[ref] = size to modified
        refs[key] = ref
        return ref
    }

    private suspend fun recent(
        key: String,
        path: String,
        format: FileFormat,
        size: Long,
        hoursAgo: Long,
        exists: Boolean = true,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ) {
        val ref =
            if (exists) {
                file(key, path, size, NOW - (hoursAgo + 30) * HOUR, bytes)
            } else {
                fake.ref(path).also {
                    refs[key] =
                        it
                }
            }
        now = NOW - hoursAgo * HOUR
        store.recordOpened(ref, path.substringAfterLast('/'), format, size)
    }

    private suspend fun populate() {
        // Oldest first: recordOpened puts the newest at the front.
        recent(
            "lecture",
            "/Users/ayse/Belgeler/Okul/Ders Notları — Diferansiyel Denklemler Hafta 7.pdf",
            FileFormat.Pdf,
            2_621_440,
            400,
            bytes = PDF,
        )
        recent(
            "manual",
            "/Users/ayse/İndirilenler/Kılavuzlar/Kullanım Kılavuzu — Çamaşır Makinesi Model WŞ-9120 (Türkçe, 148 sayfa).pdf",
            FileFormat.Pdf,
            13_526_630,
            300,
            bytes = PDF,
        )
        recent("csv", "/Users/ayse/Downloads/Inventory_Warehouse_İzmir_Q3.csv", FileFormat.Csv, 5_872_025, 240)
        recent(
            "png",
            "/Users/ayse/Resimler/Ekran/Ekran görüntüsü 2026-08-28 çağrı kaydı.png",
            FileFormat.Png,
            2_202_009,
            200,
        )
        recent(
            "notes",
            "/Users/ayse/Documents/Notes/Meeting notes — Product roadmap review with the localisation team.odt",
            FileFormat.Odt,
            88_064,
            150,
        )
        recent(
            "zip",
            "/Users/ayse/Arşiv/Fotoğraflar_Ağustos_Tatil_Ölüdeniz.zip",
            FileFormat.Zip,
            257_949_696,
            100,
            exists = false,
        )
        recent(
            "report",
            "/Users/ayse/Belgeler/FAI Office/Proje Raporu – Çevrimdışı Ofis Paketi Mimari Kararları.docx",
            FileFormat.Docx,
            421_888,
            75,
        )
        recent(
            "lease",
            "/Users/ayse/İndirilenler/Kira Sözleşmesi_İstanbul_Şişli_imzalı_taranmış.pdf",
            FileFormat.Pdf,
            3_984_588,
            50,
            bytes = PDF,
        )
        recent(
            "deck",
            "/Users/ayse/Downloads/Quarterly Board Presentation – Q3 Results and Outlook FINAL v7.pptx",
            FileFormat.Pptx,
            19_293_798,
            28,
        )
        recent(
            "budget",
            "/Users/ayse/Belgeler/Muhasebe/2026/2026 Yılı Bütçe Taslağı — Şirket İçi Gözden Geçirme (son sürüm).xlsx",
            FileFormat.Xlsx,
            1_258_291,
            3,
        )
        store.setPinned(refs.getValue("budget"), true)
        store.setPinned(refs.getValue("report"), true)
        val lost = refs.getValue("csv")
        fake.faults = { op, ref -> if (op == FileOp.Info && ref == lost) FileError.PermissionLost else null }

        val projects = fake.addFolder("/Users/ayse/Belgeler/Projeler")
        file(
            "offer",
            "/Users/ayse/Belgeler/Projeler/Teklif Mektubu — Güneş Enerjisi Kurulumu (Çanakkale).docx",
            98_304,
            NOW - 52 * HOUR,
        )
        file("survey", "/Users/ayse/Belgeler/Projeler/Saha Ölçümleri_Ekim.xlsx", 757_760, NOW - 26 * DAY)
        file(
            "launch",
            "/Users/ayse/Belgeler/Projeler/Lansman Sunumu – Ürün Ailesi 2027.pptx",
            25_165_824,
            NOW - 9 * DAY,
        )
        file("spec", "/Users/ayse/Belgeler/Projeler/Teknik Şartname_Rev3.pdf", 2_306_867, NOW - 4 * DAY, DAMAGED_PDF)
        file("old", "/Users/ayse/Belgeler/Projeler/Eski Sürümler.zip", 60_817_408, NOW - 120 * DAY)
        file(
            "minutes",
            "/Users/ayse/Belgeler/Projeler/Arşiv 2025/Toplantı Tutanakları 2025.odt",
            143_360,
            NOW - 330 * DAY,
        )
        file(
            "slides",
            "/Users/ayse/Belgeler/Projeler/Arşiv 2025/Eğitim Semineri — Yeni Başlayanlar İçin Tablo Kullanımı.odp",
            6_920_601,
            NOW - 300 * DAY,
        )
        library.addFolder(projects, "Projeler")

        val downloads = fake.addFolder("/Users/ayse/İndirilenler")
        file("invoice", "/Users/ayse/İndirilenler/Fatura_Kasım_2026.pdf", 184_320, NOW - 30 * HOUR, PDF)
        file(
            "annual",
            "/Users/ayse/İndirilenler/Annual Report 2025 — Sustainability and Governance.pdf",
            9_227_468,
            NOW - 18 * DAY,
        )
        file("setup", "/Users/ayse/İndirilenler/kurulum_paketi_sürüm_3.2.7z", 32_505_856, NOW - 40 * DAY)
        file("budgetCopy", "/Users/ayse/İndirilenler/Bütçe_2026_paylaşılan_kopya.ods", 512_000, NOW - 6 * DAY)
        library.addFolder(downloads, "İndirilenler")
    }

    /** A model over these files, loaded (recent states checked) and with every family read. */
    fun model(): FilesModel =
        FilesModel(services, CoroutineScope(Dispatchers.Unconfined)).also { model ->
            runBlocking {
                model.refresh()
                org.faioffice.core.formats.ModuleFamily.entries
                    .forEach { model.loadFamily(it) }
            }
        }

    /** The information page request of [key] as the hub reads it. */
    fun resolve(key: String): OpenRequest = runBlocking { hub.resolve(refs.getValue(key), OpenOrigin.Library) }

    /** [key]'s request as if its content were an encrypted OOXML package. */
    fun protectedRequest(key: String): OpenRequest =
        resolve(
            key,
        ).let { it.copy(detection = FormatDetection(FileFormat.Docx, passwordProtected = true), error = null) }

    companion object {
        /** 21.11.2026 14:05 in Istanbul (UTC+3). */
        const val NOW = 1_795_259_100_000L
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR

        val PDF = "%PDF-1.7\n1 0 obj <<>> endobj\ntrailer <<>>\n%%EOF\n".encodeToByteArray()
        val DAMAGED_PDF = "%PDF-1.7\n1 0 obj <<>> endobj\n".encodeToByteArray()
    }
}
