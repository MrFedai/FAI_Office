/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A folder under a temporary folder whose absolute path is longer than the classic Windows limit (`MAX_PATH`, 260)
 * and whose names carry Turkish letters. Each segment stays well under the 255-byte name limit of NTFS and ext4, so
 * the same tree works on Windows (where `java.nio` adds the `\\?\` prefix itself) and Linux. On Linux the JVM must
 * run with a UTF-8 locale (core/build.gradle.kts sets `LC_ALL` for the desktop tests).
 */
internal fun longTurkishFolder(base: Path): Path {
    var dir = base
    var segment = 0
    while (dir.toAbsolutePath().toString().length <= LONG_PATH) {
        dir = dir.resolve("Çalışma ğüşıöç İĞÜŞÖÇ belgeleri klasörü ${segment++}")
    }
    return dir.createDirectories()
}

/** Longer than `MAX_PATH` (260) with room for the file names the contracts add. */
internal const val LONG_PATH = 300

/** The [FileAccess] contract with its root deep in [longTurkishFolder] (P3.5; the Windows CI runs it on NTFS). */
class DesktopLongPathFileAccessTest : FileAccessContract() {
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    override fun fixture(): FileAccessContract.Fixture =
        object : FileAccessContract.Fixture {
            val base: Path = Files.createTempDirectory("fai-long-")
            val dir: Path = longTurkishFolder(base)
            override val access: FileAccess = DesktopFileAccess()
            override val root: FileRef = DesktopFileAccess.refOf(dir)

            override fun file(
                path: String,
                bytes: ByteArray,
            ): FileRef {
                val file = dir.resolve(path)
                file.parent.createDirectories()
                file.writeBytes(bytes)
                return DesktopFileAccess.refOf(file)
            }

            override fun folder(path: String): FileRef = DesktopFileAccess.refOf(dir.resolve(path).createDirectories())

            override fun missing(path: String): FileRef = DesktopFileAccess.refOf(dir.resolve(path))

            override fun makeReadOnly(ref: FileRef): Boolean {
                val path = DesktopFileAccess.pathOf(ref)
                path.toFile().setWritable(false)
                return !Files.isWritable(path)
            }

            override fun close() {
                base.toFile().walk().forEach { it.setWritable(true) }
                base.deleteRecursively()
            }
        }

    @OptIn(SafeSaveOnly::class, kotlin.io.path.ExperimentalPathApi::class)
    @Test
    fun turkishNameOnLongPathRoundTrips() =
        runTest {
            val base = Files.createTempDirectory("fai-long-")
            try {
                val dir = longTurkishFolder(base)
                val name = "Şubat raporu ğüşıöç İĞÜŞÖÇ.docx"
                val bytes = "içerik ğüşıöç".encodeToByteArray()
                val file = dir.resolve(name).also { it.writeBytes(bytes) }
                val ref = DesktopFileAccess.refOf(file)
                assertTrue(ref.location.length > 260, "path length ${ref.location.length}")

                val info = DesktopFileAccess().info(ref).getOrThrow()
                assertEquals(name, info.name)
                assertEquals(bytes.size.toLong(), info.size)
                val listed = DesktopFileAccess().list(DesktopFileAccess.refOf(dir)).getOrThrow()
                assertEquals(listOf(name), listed.map { it.name })
                val read = DesktopFileAccess().readRandom(ref) { it.readBytes(0, it.size.toInt()) }.getOrThrow()
                assertContentEquals(bytes, read)

                val journal = base.resolve("uygulama günlüğü").createDirectories()
                val newBytes = "yeni sürüm ĞÜŞİÖÇ".encodeToByteArray()
                val access = DesktopFileAccess()
                assertIs<SaveResult.Saved>(
                    SafeSave(access, DesktopFileAccess.refOf(journal)).save(ref, BytesSource(newBytes)),
                )
                assertContentEquals(newBytes, file.readBytes())
                assertEquals(listOf(name), dir.listDirectoryEntries().map { it.name })
            } finally {
                base.deleteRecursively()
            }
        }
}

/** The atomic safe-save contract with the documents folder deep in [longTurkishFolder]. */
class DesktopLongPathSafeSaveTest : SafeSaveContract() {
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    override fun fixture(): Fixture =
        object : Fixture {
            val base: Path = Files.createTempDirectory("fai-long-save-")
            val documents: Path = longTurkishFolder(base)
            override val inner: FileAccess = DesktopFileAccess()
            override val folder: FileRef = DesktopFileAccess.refOf(documents)
            override val journal: FileRef =
                DesktopFileAccess.refOf(base.resolve("uygulama/kayıt").createDirectories())

            override fun file(
                name: String,
                bytes: ByteArray,
            ): FileRef = DesktopFileAccess.refOf(documents.resolve(name).also { it.writeBytes(bytes) })

            override fun missing(name: String): FileRef = DesktopFileAccess.refOf(documents.resolve(name))

            override fun close() {
                base.deleteRecursively()
            }
        }
}
