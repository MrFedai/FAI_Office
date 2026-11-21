/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.formats

import kotlinx.coroutines.test.runTest
import org.faioffice.core.files.EntryKind
import org.faioffice.core.files.FileError
import org.faioffice.core.files.InMemoryFileAccess
import org.faioffice.core.files.errorOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One test per detected format, on small fixtures generated here. */
class FormatDetectorTest {
    private suspend fun assertDetects(
        expected: FileFormat,
        name: String,
        bytes: ByteArray,
    ) {
        val result = detectBytes(name, bytes)
        assertEquals(FormatDetection(expected), result, "$name: $result")
        assertEquals(expected.family, result.family)
        assertEquals(if (expected == FileFormat.Unknown) null else CompatLevel.Recognised, result.level)
    }

    private fun ascii(s: String): ByteArray = s.encodeToByteArray()

    private fun padded(
        head: ByteArray,
        size: Int = 64,
    ): ByteArray = head + ByteArray(maxOf(0, size - head.size)) { 0x11 }

    @Test
    fun pdf() =
        runTest {
            assertDetects(FileFormat.Pdf, "a.pdf", ascii("%PDF-1.7\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"))
            assertDetects(FileFormat.Pdf, "junk.pdf", ByteArray(500) { 0x20 } + ascii("%PDF-1.4\n%%EOF"))
        }

    @Test
    fun ooxmlMainPartsAndVariants() =
        runTest {
            val cases =
                mapOf(
                    FileFormat.Docx to
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
                    FileFormat.Docm to "application/vnd.ms-word.document.macroEnabled.main+xml",
                    FileFormat.Dotx to
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
                    FileFormat.Dotm to "application/vnd.ms-word.template.macroEnabledTemplate.main+xml",
                    FileFormat.Xlsx to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
                    FileFormat.Xlsm to "application/vnd.ms-excel.sheet.macroEnabled.main+xml",
                    FileFormat.Xltx to "application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml",
                    FileFormat.Xltm to "application/vnd.ms-excel.template.macroEnabled.main+xml",
                    FileFormat.Pptx to
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
                    FileFormat.Pptm to "application/vnd.ms-powerpoint.presentation.macroEnabled.main+xml",
                    FileFormat.Potx to "application/vnd.openxmlformats-officedocument.presentationml.template.main+xml",
                    FileFormat.Potm to "application/vnd.ms-powerpoint.template.macroEnabled.main+xml",
                    FileFormat.Ppsx to
                        "application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml",
                    FileFormat.Ppsm to "application/vnd.ms-powerpoint.slideshow.macroEnabled.main+xml",
                )
            for ((format, type) in cases) {
                assertDetects(format, "f.${format.canonicalExtension}", ooxml(type))
                assertDetects(format, "stored.${format.canonicalExtension}", ooxml(type, deflate = false))
            }
        }

    @Test
    fun ooxmlWithDynamicHuffmanContentTypes() =
        runTest {
            val zip =
                ZipBuilder()
                    .raw("[Content_Types].xml", 8, bytesOfHex(InflateTest.DOCX_CONTENT_TYPES_DEFLATED), 671)
                    .deflated("word/document.xml", "<w:document/>".encodeToByteArray())
                    .build()
            assertDetects(FileFormat.Docx, "dinamik.docx", zip)
        }

    @Test
    fun odfAndEpubByMimetype() =
        runTest {
            for (format in listOf(
                FileFormat.Odt,
                FileFormat.Ott,
                FileFormat.Ods,
                FileFormat.Ots,
                FileFormat.Odp,
                FileFormat.Otp,
                FileFormat.Epub,
            )) {
                assertDetects(format, "f.${format.canonicalExtension}", odf(checkNotNull(format.mimeType)))
            }
        }

    @Test
    fun plainZipArchive() =
        runTest {
            assertDetects(
                FileFormat.Zip,
                "a.zip",
                ZipBuilder().deflated("notlar/ödev.txt", ascii("merhaba merhaba")).build(),
            )
            assertDetects(FileFormat.Zip, "bos.zip", ZipBuilder().build())
            assertDetects(FileFormat.Zip, "other.zip", odf("application/x-unknown"))
        }

    @Test
    fun legacyOfficeByStreamNames() =
        runTest {
            assertDetects(
                FileFormat.Doc,
                "a.doc",
                compoundFile(listOf("WordDocument", "1Table", "\u0005SummaryInformation")),
            )
            assertDetects(FileFormat.Xls, "a.xls", compoundFile(listOf("Workbook")))
            assertDetects(FileFormat.Xls, "eski.xls", compoundFile(listOf("Book")))
            assertDetects(FileFormat.Ppt, "a.ppt", compoundFile(listOf("PowerPoint Document", "Current User")))
            assertDetects(FileFormat.Doc, "4k.doc", compoundFile(listOf("WordDocument"), sectorShift = 12))
            // Six entries span two 512-byte directory sectors: the FAT chain is followed.
            val many = listOf("A", "B", "C", "D", "E", "PowerPoint Document")
            assertDetects(FileFormat.Ppt, "zincir.ppt", compoundFile(many))
        }

    @Test
    fun encryptedOoxmlIsPasswordProtected() =
        runTest {
            val encrypted = compoundFile(listOf("EncryptionInfo", "EncryptedPackage", "\u0006DataSpaces"))
            assertEquals(
                FormatDetection(FileFormat.Xlsx, passwordProtected = true),
                detectBytes("gizli.xlsx", encrypted),
            )
            assertEquals(
                FormatDetection(FileFormat.EncryptedOoxml, passwordProtected = true),
                detectBytes("gizli", encrypted),
            )
            assertEquals(CompatLevel.Recognised, FormatDetection(FileFormat.EncryptedOoxml).level)
        }

    @Test
    fun unknownCompoundFile() =
        runTest {
            assertDetects(FileFormat.Unknown, "a.msg", compoundFile(listOf("__properties_version1.0")))
        }

    @Test
    fun fixedSignatures() =
        runTest {
            assertDetects(FileFormat.Rtf, "a.rtf", ascii("{\\rtf1\\ansi Merhaba}"))
            assertDetects(
                FileFormat.SevenZip,
                "a.7z",
                padded(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4)),
            )
            assertDetects(FileFormat.Rar, "rar4.rar", padded(ascii("Rar!\u001A\u0007\u0000")))
            assertDetects(FileFormat.Rar, "rar5.rar", padded(ascii("Rar!\u001A\u0007\u0001\u0000")))
            assertDetects(FileFormat.Gzip, "a.gz", padded(byteArrayOf(0x1F, 0x8B.toByte(), 8, 0)))
            assertDetects(FileFormat.Bzip2, "a.bz2", padded(ascii("BZh91AY&SY")))
            assertDetects(FileFormat.Xz, "a.xz", padded(byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0)))
            val tar = ByteArray(1024).also { ascii("dosya.txt").copyInto(it) }
            ascii("ustar\u000000").copyInto(tar, 257)
            assertDetects(FileFormat.Tar, "a.tar", tar)
        }

    @Test
    fun images() =
        runTest {
            assertDetects(
                FileFormat.Png,
                "a.png",
                padded(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
            )
            assertDetects(
                FileFormat.Jpeg,
                "a.jpg",
                padded(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())),
            )
            assertDetects(FileFormat.Gif, "a.gif", padded(ascii("GIF89a")))
            assertDetects(FileFormat.Gif, "old.gif", padded(ascii("GIF87a")))
            assertDetects(FileFormat.Webp, "a.webp", padded(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")))
            assertDetects(FileFormat.Tiff, "a.tif", padded(byteArrayOf(0x49, 0x49, 0x2A, 0)))
            assertDetects(FileFormat.Tiff, "be.tiff", padded(byteArrayOf(0x4D, 0x4D, 0, 0x2A)))
            val bmp =
                Out()
                    .ascii("BM")
                    .u32(70)
                    .u32(0)
                    .u32(54)
                    .u32(40)
                    .zeros(40)
                    .toByteArray()
            assertDetects(FileFormat.Bmp, "a.bmp", bmp)
        }

    @Test
    fun markup() =
        runTest {
            assertDetects(
                FileFormat.Svg,
                "a.svg",
                ascii("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>"),
            )
            assertDetects(FileFormat.Svg, "b.svg", ascii("<svg width=\"10\"></svg>"))
            assertDetects(FileFormat.Html, "a.html", ascii("<!DOCTYPE html>\n<html><body>Ç</body></html>"))
            assertDetects(FileFormat.Html, "b.htm", ascii("  <html lang=\"tr\"><head></head></html>"))
            assertDetects(FileFormat.Xml, "a.xml", ascii("<?xml version=\"1.0\" encoding=\"UTF-8\"?><kök/>"))
            assertDetects(FileFormat.Xml, "b.xml", ascii("<note><to>Ali</to></note>"))
        }

    @Test
    fun textVersusBinary() =
        runTest {
            assertDetects(FileFormat.PlainText, "a.txt", ascii("Merhaba dünya, ışık ğüşiöç\n"))
            assertDetects(
                FileFormat.PlainText,
                "bom.txt",
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + ascii("metin"),
            )
            val html = "<html>ş</html>"
            val utf16 = ByteArray(html.length * 2) { i -> (html[i / 2].code shr (8 * (i % 2))).toByte() }
            assertDetects(FileFormat.Html, "u16.html", byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + utf16)
            // Windows-1254 Turkish text: not UTF-8, but no NUL and no control bytes.
            assertDetects(
                FileFormat.PlainText,
                "eski.txt",
                ascii("Kalem ") + byteArrayOf(0xFE.toByte(), 0xF0.toByte(), 0xFD.toByte()) + ascii(" son"),
            )
            assertDetects(FileFormat.Unknown, "veri.bin", ByteArray(256) { it.toByte() })
            assertDetects(FileFormat.Unknown, "nul.dat", ascii("abc\u0000def"))
        }

    @Test
    fun utf8SplitAtSampleEndIsStillText() {
        val bytes = ascii("abcç").copyOf(4) // "ç" is two bytes; the sample ends after its first
        assertTrue(TextSniffer.isUtf8(bytes, cut = true))
        assertFalse(TextSniffer.isUtf8(bytes, cut = false))
        assertFalse(TextSniffer.isUtf8(byteArrayOf(0xC0.toByte(), 0x80.toByte()), cut = false)) // overlong
        val surrogate = byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte())
        assertFalse(TextSniffer.isUtf8(surrogate, cut = false))
    }

    @Test
    fun csvAndCodeByExtensionOnText() =
        runTest {
            assertDetects(FileFormat.Csv, "a.csv", ascii("ad;soyad\nAli;Veli\n"))
            assertDetects(FileFormat.Csv, "a.tsv", ascii("a\tb\n1\t2\n"))
            assertDetects(FileFormat.SourceCode, "Main.kt", ascii("fun main() = println(\"merhaba\")\n"))
            assertDetects(FileFormat.SourceCode, "ayar.json", ascii("{\"a\": 1}"))
            assertDetects(FileFormat.PlainText, "README", ascii("Okunacak notlar"))
        }

    @Test
    fun mimeTypeStandsInForMissingExtension() =
        runTest {
            val csv = ascii("a,b\n1,2\n")
            assertEquals(
                FormatDetection(FileFormat.Csv),
                detectBytes("indirilen", csv, mimeType = "text/csv; charset=utf-8"),
            )
            assertEquals(FormatDetection(FileFormat.PlainText), detectBytes("indirilen", csv))
            // The extension wins over the MIME type, and content wins over both.
            val pdf = ascii("%PDF-1.7\n%%EOF")
            assertEquals(FormatDetection(FileFormat.Pdf), detectBytes("belge", pdf, mimeType = "application/zip"))
        }

    @Test
    fun damagedPdfWithoutEndMarker() =
        runTest {
            assertEquals(
                FormatDetection(FileFormat.Pdf, damaged = true),
                detectBytes("kesik.pdf", ascii("%PDF-1.7\n1 0 obj<<")),
            )
        }

    @Test
    fun fileErrorsComeBackAsFailures() =
        runTest {
            val fake = InMemoryFileAccess()
            val detector = FormatDetector(fake)
            assertEquals(FileError.NotFound, detector.detect(fake.ref("/yok.pdf")).errorOrNull())
            assertEquals(FileError.WrongKind(EntryKind.File), detector.detect(fake.addFolder("/klasör")).errorOrNull())
            val ref = fake.addFile("/a.pdf", ascii("%PDF-1.7"))
            fake.faults = { _, _ -> FileError.PermissionLost }
            assertEquals(FileError.PermissionLost, detector.detect(ref).errorOrNull())
        }

    @Test
    fun everyFormatIsInTheMatrixAtLevelZero() {
        val formats = FileFormat.entries - FileFormat.Empty - FileFormat.Unknown
        assertEquals(formats.toSet(), CompatibilityMatrix.levels.keys)
        assertTrue(CompatibilityMatrix.levels.values.all { it == CompatLevel.Recognised })
        assertEquals(null, CompatibilityMatrix.levelOf(FileFormat.Unknown))
    }

    @Test
    fun extensions() {
        assertEquals("docx", extensionOf("C:\\Belgeler\\Rapor.DOCX"))
        assertEquals("gz", extensionOf("/a/arşiv.tar.gz"))
        assertEquals(null, extensionOf("/a.d/README"))
        assertEquals(null, extensionOf("nokta."))
        assertEquals(null, extensionOf(null))
    }
}
