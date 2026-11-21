/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.core.formats

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Hostile and broken input: every case ends with a result inside the detector's limits. */
class HostileFilesTest {
    private val docxType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"

    /** Bytes of [zip] with its 22-byte end record (no comment) replaced by [end]. */
    private fun withEnd(
        zip: ByteArray,
        end: ByteArray,
    ): ByteArray = zip.copyOf(zip.size - 22) + end

    private fun directoryOffset(zip: ByteArray): Long = zip.u32(zip.size - 6)

    private fun directorySize(zip: ByteArray): Long = zip.u32(zip.size - 10)

    @Test
    fun truncatedZip() =
        runTest {
            val docx = ooxml(docxType)
            val cut = docx.copyOf(docx.size / 2)
            assertEquals(FormatDetection(FileFormat.Docx, damaged = true), detectBytes("yarım.docx", cut))
            assertEquals(FormatDetection(FileFormat.Zip, damaged = true), detectBytes("yarım.bin", cut))
            // ODF names its type in the first local entry, so a cut file is still recognised.
            val odt = odf(checkNotNull(FileFormat.Odt.mimeType))
            assertEquals(
                FormatDetection(FileFormat.Odt, damaged = true),
                detectBytes("yarım", odt.copyOf(odt.size - 30)),
            )
            assertEquals(
                FormatDetection(FileFormat.Zip, damaged = true),
                detectBytes("x.zip", "PK\u0003\u0004".encodeToByteArray()),
            )
        }

    @Test
    fun directoryPointingPastItsEnd() =
        runTest {
            val docx = ooxml(docxType)
            val end =
                Out()
                    .u32(0x06054B50)
                    .u16(0)
                    .u16(0)
                    .u16(3)
                    .u16(3)
                    .u32(directorySize(docx))
                    .u32(1L shl 30)
                    .u16(0)
            assertEquals(
                FormatDetection(FileFormat.Docx, damaged = true),
                detectBytes("a.docx", withEnd(docx, end.toByteArray())),
            )
        }

    @Test
    fun directoryClaimingMillionsOfEntries() =
        runTest {
            val docx = ooxml(docxType)
            val size = directorySize(docx)
            val offset = directoryOffset(docx)
            // Classic record: 65535 entries in a three-entry directory.
            val classic =
                Out()
                    .u32(0x06054B50)
                    .u16(0)
                    .u16(0)
                    .u16(0xFFFF)
                    .u16(0xFFFF)
                    .u32(size)
                    .u32(offset)
                    .u16(0)
            assertEquals(
                FormatDetection(FileFormat.Docx, damaged = true),
                detectBytes("a.docx", withEnd(docx, classic.toByteArray())),
            )
            // ZIP64 record: five million entries; then a directory of a terabyte.
            for ((entries, dirSize) in listOf(5_000_000L to size, 5_000_000L to (1L shl 40))) {
                val body = docx.copyOf(docx.size - 22)
                val zip64At = body.size.toLong()
                val end =
                    Out()
                        .u32(
                            0x06064B50,
                        ).u64(44)
                        .u16(45)
                        .u16(45)
                        .u32(0)
                        .u32(0)
                        .u64(entries)
                        .u64(entries)
                        .u64(dirSize)
                        .u64(offset)
                        .u32(0x07064B50)
                        .u32(0)
                        .u64(zip64At)
                        .u32(1)
                        .u32(0x06054B50)
                        .u16(0)
                        .u16(0)
                        .u16(0xFFFF)
                        .u16(0xFFFF)
                        .u32(0xFFFFFFFFL)
                        .u32(0xFFFFFFFFL)
                        .u16(0)
                val result = detectBytes("a.docx", body + end.toByteArray())
                assertEquals(
                    FormatDetection(FileFormat.Docx, damaged = true),
                    result,
                    "$entries entries, $dirSize bytes",
                )
            }
        }

    @Test
    fun directoryLargerThanTheEntryLimit() =
        runTest {
            val zip = ZipBuilder()
            repeat(ZipContainer.MAX_ENTRIES + 1000) { zip.stored("d/$it.txt", byteArrayOf()) }
            zip.deflated("[Content_Types].xml", contentTypes(docxType)) // beyond the limit: not looked at
            assertEquals(FormatDetection(FileFormat.Zip), detectBytes("çok.zip", zip.build()))
        }

    @Test
    fun zipBombPart() =
        runTest {
            val bomb = FixedDeflater().literal(0)
            repeat(80_000) { bomb.repeatLast(258) } // 20 MB of zeros, declared as 1 GB
            val zeros = ZipBuilder().raw("[Content_Types].xml", 8, bomb.finish(), 1L shl 30).build()
            assertEquals(FormatDetection(FileFormat.Docx, damaged = true), detectBytes("bomba.docx", zeros))
            assertEquals(FormatDetection(FileFormat.Zip, damaged = true), detectBytes("bomba.zip", zeros))
            // A part that names its type first and then explodes is still recognised.
            val named = FixedDeflater().data(contentTypes(docxType))
            repeat(80_000) { named.repeatLast(258) }
            val zip = ZipBuilder().raw("[Content_Types].xml", 8, named.finish(), 1L shl 30).build()
            assertEquals(FormatDetection(FileFormat.Docx), detectBytes("bomba2.docx", zip))
        }

    @Test
    fun emptyFile() =
        runTest {
            assertEquals(FormatDetection(FileFormat.Empty), detectBytes("boş.docx", ByteArray(0)))
            assertEquals(ModuleFamily.Unknown, detectBytes("boş", ByteArray(0)).family)
        }

    @Test
    fun renamedExtension() =
        runTest {
            val pdf = "%PDF-1.7\n%%EOF".encodeToByteArray()
            assertEquals(
                FormatDetection(FileFormat.Pdf, extensionMismatch = true),
                detectBytes("aslında-pdf.docx", pdf),
            )
            assertEquals(
                FormatDetection(FileFormat.Docx, extensionMismatch = true),
                detectBytes("rapor.pdf", ooxml(docxType)),
            )
            assertEquals(
                FormatDetection(FileFormat.Docx, extensionMismatch = true),
                detectBytes("rapor.zip", ooxml(docxType)),
            )
            assertEquals(
                FormatDetection(FileFormat.PlainText, extensionMismatch = true),
                detectBytes("metin.xlsx", "merhaba".encodeToByteArray()),
            )
            // An extension no format claims is not a disagreement.
            assertEquals(FormatDetection(FileFormat.Pdf), detectBytes("belge.yedek", pdf))
        }

    @Test
    fun brokenCompoundFiles() =
        runTest {
            val doc = compoundFile(listOf("WordDocument"))
            assertEquals(FormatDetection(FileFormat.Doc, damaged = true), detectBytes("kesik.doc", doc.copyOf(512)))
            assertEquals(FormatDetection(FileFormat.Unknown, damaged = true), detectBytes("kesik", doc.copyOf(100)))
            // Bad sector size.
            val bad = doc.copyOf().also { it[0x1E] = 7 }
            assertEquals(
                FormatDetection(FileFormat.Xls, damaged = true, extensionMismatch = false),
                detectBytes("a.xls", bad),
            )
            // Directory chain looping onto itself: the walk stops at its sector limit.
            val loop =
                compoundFile(
                    listOf("PowerPoint Document"),
                ).also { byteArrayOf(1, 0, 0, 0).copyInto(it, 512 + 4) }
            assertEquals(FormatDetection(FileFormat.Ppt), detectBytes("döngü.ppt", loop))
        }
}
