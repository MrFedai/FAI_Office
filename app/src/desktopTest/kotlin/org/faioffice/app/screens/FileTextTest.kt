/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.screens

import org.faioffice.app.files.FileItem
import org.faioffice.app.files.FileSort
import org.faioffice.app.files.SortKey
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.compat_phase_4
import org.faioffice.app.resources.compat_phase_5
import org.faioffice.app.resources.compat_phase_6
import org.faioffice.core.files.FileRef
import org.faioffice.core.formats.FileFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class FileTextTest {
    private val hour = 3_600_000L

    @Test
    fun datesInTurkishAndIso() {
        val epoch = 1_795_259_100_000L // 2026-11-21 11:05 UTC
        assertEquals("21.11.2026", dateText(epoch, "tr", offsetMillis = 3 * hour))
        assertEquals("2026-11-21 14:05", dateText(epoch, "en", withTime = true, offsetMillis = 3 * hour))
        assertEquals("1969-12-31 23:00", dateText(0, "en", withTime = true, offsetMillis = -hour))
        assertEquals(Triple(2024, 2, 29), civilDate(19_782))
    }

    @Test
    fun folderSegmentsOfEachReferenceKind() {
        assertEquals(
            listOf("C:", "Belgeler", "Muhasebe"),
            folderSegments(FileRef(FileRef.Kind.Path, "C:\\Belgeler\\Muhasebe\\Bütçe.xlsx")),
        )
        val saf =
            "content://com.android.externalstorage.documents/document/" +
                "primary%3ADownload%2FK%C4%B1lavuzlar%2Fx.pdf"
        assertEquals(listOf("Download", "Kılavuzlar"), folderSegments(FileRef(FileRef.Kind.ContentUri, saf)))
        assertEquals(emptyList(), folderSegments(FileRef(FileRef.Kind.ContentUri, "content://media/external/file/42")))
        assertEquals(emptyList(), folderSegments(FileRef(FileRef.Kind.WebSession, "7")))
        assertEquals("100%", percentDecode("100%"))
    }

    @Test
    fun badgesAndPhases() {
        assertEquals("DOCX", FileFormat.Docx.badge)
        assertEquals("?", FileFormat.Unknown.badge)
        assertEquals("TIF", FileFormat.Tiff.badge)
        assertEquals(Res.string.compat_phase_4, compatibilityText(FileFormat.Pdf))
        assertEquals(Res.string.compat_phase_5, compatibilityText(FileFormat.SevenZip))
        assertEquals(Res.string.compat_phase_6, compatibilityText(FileFormat.Xlsx))
    }

    @Test
    fun sortChoosesAndFlips() {
        fun item(
            name: String,
            date: Long,
            size: Long?,
        ) = FileItem(FileRef(FileRef.Kind.Memory, "/$name"), name, FileFormat.Pdf, size, date, false, true, null)
        val items = listOf(item("ışık", 1, 30), item("Ilık", 3, null), item("arı", 2, 10))
        val byDate = FileSort()
        assertEquals(listOf("Ilık", "arı", "ışık"), byDate.apply(items) { it.lowercase() }.map { it.name })
        val byName = byDate.choose(SortKey.Name)
        assertEquals(FileSort(SortKey.Name, descending = false), byName)
        assertEquals(FileSort(SortKey.Name, descending = true), byName.choose(SortKey.Name))
        val bySize = byName.choose(SortKey.Size)
        assertEquals(listOf("ışık", "arı", "Ilık"), bySize.apply(items) { it }.map { it.name })
    }
}
