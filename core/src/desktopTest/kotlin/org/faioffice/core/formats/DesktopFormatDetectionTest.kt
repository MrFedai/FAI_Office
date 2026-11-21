/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.core.formats

import kotlinx.coroutines.runBlocking
import org.faioffice.core.files.DesktopFileAccess
import org.faioffice.core.files.getOrThrow
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Detection on real files: the typical documents (when generated locally) and the 100 MB timing. */
class DesktopFormatDetectionTest {
    private val detector = FormatDetector(DesktopFileAccess())

    private fun detect(path: Path): FormatDetection =
        runBlocking {
            detector.detect(DesktopFileAccess.refOf(path)).getOrThrow()
        }

    /** `poc/corpus/out` holds files made by `poc/corpus/gen.py`; it is not in git, so CI skips this test. */
    @Test
    fun typicalDocuments() {
        val root =
            generateSequence(File("").absoluteFile) {
                it.parentFile
            }.firstOrNull { File(it, "settings.gradle.kts").isFile }
        val out = root?.resolve("poc/corpus/out")
        if (out == null || !out.isDirectory) {
            println("typicalDocuments: poc/corpus/out yok, atlandı")
            return
        }
        val expected =
            mapOf(
                "typical-20p.docx" to FileFormat.Docx,
                "typical-20p.odt" to FileFormat.Odt,
                "typical-20p.pdf" to FileFormat.Pdf,
                "long-100p.pdf" to FileFormat.Pdf,
                "typical.xlsx" to FileFormat.Xlsx,
                "typical.ods" to FileFormat.Ods,
                "typical.pptx" to FileFormat.Pptx,
                "typical.odp" to FileFormat.Odp,
            )
        var seen = 0
        for ((name, format) in expected) {
            val file = out.resolve(name)
            if (!file.isFile) continue
            assertEquals(FormatDetection(format), detect(file.toPath()), name)
            seen++
        }
        println("typicalDocuments: $seen / ${expected.size} dosya algılandı")
    }

    /** 100 MB files: only the head and the tail (ZIP central directory, PDF trailer) are read. */
    @Test
    fun hundredMegabyteFiles() {
        val size = 100L * 1024 * 1024
        val docxType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
        val zip = Files.createTempFile("fai-100mb-", ".docx")
        val pdf = Files.createTempFile("fai-100mb-", ".pdf")
        try {
            writeBigDocx(zip, size, contentTypes(docxType))
            RandomAccessFile(pdf.toFile(), "rw").use { file ->
                file.write("%PDF-1.7\n".toByteArray())
                file.setLength(size - 6)
                file.seek(size - 6)
                file.write("%%EOF\n".toByteArray())
            }
            for ((path, format) in listOf(zip to FileFormat.Docx, pdf to FileFormat.Pdf)) {
                detect(path) // warm-up: class loading, JIT
                val start = System.nanoTime()
                val runs = 10
                repeat(runs) { assertEquals(FormatDetection(format), detect(path)) }
                val millis = (System.nanoTime() - start) / 1e6 / runs
                println(
                    "100 MB ${format.name}: ${Files.size(path) / (1024 * 1024)} MB, ortalama %.2f ms".format(millis),
                )
                assertTrue(millis < 1000, "100 MB detection took $millis ms")
            }
        } finally {
            zip.deleteIfExists()
            pdf.deleteIfExists()
        }
    }

    /** A DOCX-shaped ZIP of [size] bytes: `[Content_Types].xml` first, one large stored part, the directory last. */
    private fun writeBigDocx(
        path: Path,
        size: Long,
        contentTypes: ByteArray,
    ) {
        val ctName = "[Content_Types].xml".encodeToByteArray()
        val bigName = "word/media/big.bin".encodeToByteArray()
        val ctLocal = 30 + ctName.size + contentTypes.size
        val bigData = size - ctLocal - (30 + bigName.size) - (46 * 2 + ctName.size + bigName.size) - 22
        check(bigData > 0)
        RandomAccessFile(path.toFile(), "rw").use { file ->
            file.write(local(ctName, contentTypes.size.toLong()))
            file.write(contentTypes)
            val bigOffset = file.filePointer
            file.write(local(bigName, bigData))
            file.setLength(file.filePointer + bigData)
            file.seek(file.filePointer + bigData)
            val directoryOffset = file.filePointer
            file.write(central(ctName, contentTypes.size.toLong(), 0))
            file.write(central(bigName, bigData, bigOffset))
            val directorySize = file.filePointer - directoryOffset
            file.write(
                Out()
                    .u32(
                        0x06054B50,
                    ).u16(0)
                    .u16(0)
                    .u16(2)
                    .u16(2)
                    .u32(directorySize)
                    .u32(directoryOffset)
                    .u16(0)
                    .toByteArray(),
            )
        }
    }

    private fun local(
        name: ByteArray,
        size: Long,
    ): ByteArray =
        Out()
            .u32(
                0x04034B50,
            ).u16(20)
            .u16(0)
            .u16(0)
            .u16(0)
            .u16(0)
            .u32(0)
            .u32(size)
            .u32(size)
            .u16(name.size)
            .u16(0)
            .bytes(name)
            .toByteArray()

    private fun central(
        name: ByteArray,
        size: Long,
        offset: Long,
    ): ByteArray =
        Out()
            .u32(0x02014B50)
            .u16(20)
            .u16(20)
            .u16(0)
            .u16(0)
            .u16(0)
            .u16(0)
            .u32(0)
            .u32(size)
            .u32(size)
            .u16(name.size)
            .u16(0)
            .u16(0)
            .u16(0)
            .u16(0)
            .u32(0)
            .u32(offset)
            .bytes(name)
            .toByteArray()
}
