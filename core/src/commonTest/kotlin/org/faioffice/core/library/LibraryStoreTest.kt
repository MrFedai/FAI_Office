/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.faioffice.core.files.FaultyFileAccess
import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileError
import org.faioffice.core.files.FileInfo
import org.faioffice.core.files.FileOp
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.FileResult
import org.faioffice.core.files.InMemoryFileAccess
import org.faioffice.core.files.SafeSave
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.ModuleFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InMemoryLibraryStoreTest : LibraryStoreContract() {
    override fun fixture(): Fixture =
        object : Fixture {
            val fake = InMemoryFileAccess()
            override val access: FileAccess = fake
            override val appFolder: FileRef = fake.addFolder("/uygulama")
            override val storeFile: FileRef = fake.ref("/uygulama/kitaplik.txt")
            override val damagedFile: FileRef = fake.ref("/uygulama/kitaplik.damaged")
            override val journal: FileRef = fake.addFolder("/uygulama/kayıt")

            override fun document(name: String): FileRef = fake.addFile("/Belgeler/$name", byteArrayOf(1))

            override fun missing(name: String): FileRef = fake.ref("/Belgeler/$name")

            override fun writeStore(bytes: ByteArray) {
                fake.addFile("/uygulama/kitaplik.txt", bytes)
            }

            override fun readStore(): ByteArray? = fake.contents(storeFile)

            override fun readDamaged(): ByteArray? = fake.contents(damagedFile)

            override fun close() = Unit
        }
}

class LibraryStoreFaultTest {
    @Test
    fun permissionLostAndOtherErrorsMapToStates() =
        runTest {
            val fake = InMemoryFileAccess()
            val faulty = FaultyFileAccess(fake)
            val store =
                LibraryStore(faulty, SafeSave(faulty, fake.addFolder("/k")), fake.ref("/s.txt"), fake.ref("/s.damaged")) { 1L }
            val a = fake.addFile("/a.docx", byteArrayOf(1))
            faulty.faults = { op, _ -> if (op == FileOp.Info) FileError.PermissionLost else null }
            assertEquals(EntryState.PermissionLost, store.stateOf(a))
            faulty.faults = { op, _ -> if (op == FileOp.Info) FileError.Locked else null }
            assertEquals(EntryState.Available, store.stateOf(a))
            faulty.faults = { op, _ -> if (op == FileOp.Info) FileError.IoError("x") else null }
            assertEquals(EntryState.Missing, store.stateOf(a))
            faulty.faults = { _, _ -> null }
            assertEquals(EntryState.Available, store.stateOf(a))
        }

    @Test
    fun unreadableStoreIsNotOverwritten() =
        runTest {
            val fake = InMemoryFileAccess()
            val faulty = FaultyFileAccess(fake)
            val original = "FAI-LIBRARY 2\nremember\t1\n".encodeToByteArray()
            val storeRef = fake.addFile("/s.txt", original)
            val store = LibraryStore(faulty, SafeSave(faulty, fake.addFolder("/k")), storeRef, fake.ref("/s.damaged")) { 1L }
            faulty.faults = { op, _ -> if (op == FileOp.Read) FileError.Locked else null }
            assertFalse(store.recordOpened(fake.ref("/a.docx"), "a", FileFormat.Docx, null))
            assertTrue(fake.contents(storeRef)!!.contentEquals(original))
            faulty.faults = { _, _ -> null }
            assertTrue(store.recordOpened(fake.ref("/a.docx"), "a", FileFormat.Docx, null))
        }

    private fun assertFalse(value: Boolean) = assertEquals(false, value)
}

class FolderScanTest {
    private fun tree(): InMemoryFileAccess =
        InMemoryFileAccess().apply {
            addFile("/r/a.docx", byteArrayOf(1))
            addFile("/r/B.XLSX", byteArrayOf(1))
            addFile("/r/notes", byteArrayOf(1))
            addFile("/r/d1/c.pdf", byteArrayOf(1))
            addFile("/r/d1/d2/e.docx", byteArrayOf(1))
            addFile("/r/d1/d2/d3/f.docx", byteArrayOf(1))
            addFile("/r/d1/d2/d3/d4/g.docx", byteArrayOf(1))
        }

    @Test
    fun depthLimitAndFamilyFilter() =
        runTest {
            val fake = tree()
            val all = assertIs<FileResult.Success<FolderScan>>(fake.scanFolder(fake.ref("/r"))).value
            assertEquals(
                setOf("a.docx", "B.XLSX", "notes", "c.pdf", "e.docx", "f.docx"),
                all.files.map { it.info.name }.toSet(),
            )
            assertEquals(1, all.skippedFolders)
            assertEquals(FileFormat.Xlsx, all.files.first { it.info.name == "B.XLSX" }.format)
            assertEquals(FileFormat.Unknown, all.files.first { it.info.name == "notes" }.format)
            val shallow = fake.scanFolder(fake.ref("/r"), limits = ScanLimits(maxDepth = 0))
            assertEquals(3, assertIs<FileResult.Success<FolderScan>>(shallow).value.files.size)
            val docs = fake.scanFolder(fake.ref("/r"), family = ModuleFamily.Documents)
            assertEquals(
                setOf("a.docx", "e.docx", "f.docx"),
                assertIs<FileResult.Success<FolderScan>>(docs).value.files.map { it.info.name }.toSet(),
            )
        }

    @Test
    fun fileLimitTruncates() =
        runTest {
            val fake = tree()
            val scan =
                assertIs<FileResult.Success<FolderScan>>(
                    fake.scanFolder(fake.ref("/r"), limits = ScanLimits(maxFiles = 2)),
                ).value
            assertEquals(2, scan.files.size)
            assertTrue(scan.truncated)
        }

    @Test
    fun missingRootFailsAndFailedSubfolderIsSkipped() =
        runTest {
            val fake = tree()
            assertEquals(
                FileResult.Failure(FileError.NotFound),
                fake.scanFolder(fake.ref("/yok")),
            )
            val faulty = FaultyFileAccess(fake)
            faulty.faults = { op, ref -> if (op == FileOp.List && ref.location == "/r/d1") FileError.PermissionLost else null }
            val scan = assertIs<FileResult.Success<FolderScan>>(faulty.scanFolder(fake.ref("/r"))).value
            assertEquals(3, scan.files.size)
            assertEquals(1, scan.skippedFolders)
        }

    @Test
    fun cancellationStopsTheScan() =
        runTest {
            val fake = tree()
            val gate = CompletableDeferred<Unit>()
            val slow =
                object : FileAccess by fake {
                    override suspend fun list(folder: FileRef): FileResult<List<FileInfo>> {
                        gate.await()
                        return fake.list(folder)
                    }
                }
            var cancelled = false
            val job =
                launch {
                    try {
                        slow.scanFolder(fake.ref("/r"))
                    } catch (e: CancellationException) {
                        cancelled = true
                        throw e
                    }
                }
            testScheduler.runCurrent()
            job.cancel()
            job.join()
            assertTrue(cancelled)
        }
}
