/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryFileAccessTest : FileAccessContract() {
    override fun fixture(): FileAccessContract.Fixture =
        object : FileAccessContract.Fixture {
            val fake = InMemoryFileAccess()
            override val access: FileAccess = fake
            override val root: FileRef = fake.addFolder("/kök")

            override fun file(
                path: String,
                bytes: ByteArray,
            ): FileRef = fake.addFile("/kök/$path", bytes)

            override fun folder(path: String): FileRef = fake.addFolder("/kök/$path")

            override fun missing(path: String): FileRef = fake.ref("/kök/$path")

            override fun makeReadOnly(ref: FileRef): Boolean {
                fake.setReadOnly(ref)
                return true
            }

            override fun close() = Unit
        }

    @Test
    fun faultsSimulatePlatformErrors() =
        runTest {
            val fake = InMemoryFileAccess()
            val ref = fake.addFile("/a.txt", byteArrayOf(1), mimeType = "text/plain")
            assertEquals("text/plain", fake.info(ref).getOrNull()?.mimeType)
            fake.faults = { op, _ -> if (op == FileOp.Info) FileError.PermissionLost else null }
            assertEquals(FileError.PermissionLost, fake.info(ref).errorOrNull())
            fake.faults = { _, _ -> FileError.Locked }
            assertEquals(FileError.Locked, fake.readRandom(ref) { 0 }.errorOrNull())
        }

    @OptIn(SafeSaveOnly::class)
    @Test
    fun flushFaultLeavesPartialTemporaryFile() =
        runTest {
            val fake = InMemoryFileAccess()
            val target = fake.addFile("/k/belge.txt", "eski".encodeToByteArray())
            val writes = fake.rawWrites
            val temp = writes.createSibling(target, "belge.txt").getOrThrow()
            fake.faults = { op, _ -> if (op == FileOp.Flush) FileError.NoSpace else null }
            val result =
                writes.write(temp) { sink ->
                    sink.write("yeni".encodeToByteArray())
                    sink.flush()
                }
            assertEquals(FileError.NoSpace, result.errorOrNull())
            assertContentEquals("eski".encodeToByteArray(), fake.contents(target))
            assertTrue(temp.location in fake.paths())
        }

    @Test
    fun foreignReferenceIsNotFound() =
        runTest {
            val fake = InMemoryFileAccess()
            val foreign = FileRef(FileRef.Kind.Path, "/a.txt")
            assertEquals(FileError.NotFound, fake.info(foreign).errorOrNull())
        }
}
