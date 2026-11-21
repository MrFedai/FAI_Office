/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.test.runTest
import java.io.RandomAccessFile
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

/** Real files in a temporary folder: documents in `Belgeler`, the journal in an "app-private" folder beside it. */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
private fun desktopFixture(): SafeSaveContract.Fixture =
    object : SafeSaveContract.Fixture {
        val dir: Path = Files.createTempDirectory("fai-save-")
        val documents: Path = dir.resolve("Belgeler Öğüş").createDirectories()
        override val inner: FileAccess = DesktopFileAccess()
        override val folder: FileRef = DesktopFileAccess.refOf(documents)
        override val journal: FileRef = DesktopFileAccess.refOf(dir.resolve("uygulama/kayıt").createDirectories())

        override fun file(
            name: String,
            bytes: ByteArray,
        ): FileRef = DesktopFileAccess.refOf(documents.resolve(name).also { it.writeBytes(bytes) })

        override fun missing(name: String): FileRef = DesktopFileAccess.refOf(documents.resolve(name))

        override fun close() {
            dir.deleteRecursively()
        }
    }

/** Atomic path on the real file system: the save round trip the Windows CI runner also runs (ci/windows.sh). */
class DesktopSafeSaveTest : SafeSaveContract() {
    override fun fixture(): Fixture = desktopFixture()

    @Test
    fun realSaveRoundTripKeepsNoTemporaryFiles() =
        runTest {
            val f = desktopFixture()
            try {
                val safeSave = SafeSave(f.inner, f.journal)
                val target = f.file("Çizelge Şubat.txt", "eski".encodeToByteArray())
                val path = DesktopFileAccess.pathOf(target)
                repeat(3) { round ->
                    val bytes = "sürüm $round ğüşiöçĞÜŞİÖÇ".encodeToByteArray()
                    val saved = assertIs<SaveResult.Saved>(safeSave.save(target, BytesSource(bytes)))
                    assertEquals(Sha256.hex(bytes), saved.sha256)
                    assertContentEquals(bytes, path.readBytes())
                }
                assertEquals(listOf("Çizelge Şubat.txt"), path.parent.listDirectoryEntries().map { it.name })
                assertEquals(emptyList(), DesktopFileAccess.pathOf(f.journal).listDirectoryEntries())
            } finally {
                f.close()
            }
        }

    @Test
    fun fileHeldOpenByAnotherProgram() =
        runTest {
            val f = desktopFixture()
            try {
                val safeSave = SafeSave(f.inner, f.journal)
                val old = "eski".encodeToByteArray()
                val target = f.file("Açık Rapor.txt", old)
                val path = DesktopFileAccess.pathOf(target)
                val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
                // java.io opens without FILE_SHARE_DELETE on Windows, like most programs: the file cannot be replaced.
                val result =
                    RandomAccessFile(path.toFile(), "rw").use {
                        safeSave.save(target, BytesSource("yeni".encodeToByteArray()))
                    }
                if (windows) {
                    assertTrue(result.isLocked, "$result")
                    assertContentEquals(old, path.readBytes())
                } else {
                    // POSIX renames over an open file; the other program keeps reading the old inode.
                    assertIs<SaveResult.Saved>(result)
                }
                assertEquals(listOf("Açık Rapor.txt"), path.parent.listDirectoryEntries().map { it.name })
                assertEquals(emptyList(), safeSave.findLeftovers().getOrThrow())
            } finally {
                f.close()
            }
        }
}

/** Content-URI path (backup, truncate write, restore) over real files, as a provider without atomic replace. */
class DesktopContentUriSafeSaveTest : SafeSaveContract() {
    override val contentUri: Boolean = true

    override fun fixture(): Fixture = desktopFixture()
}
