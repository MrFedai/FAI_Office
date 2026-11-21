/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebFileAccessTest {
    private class Fake(
        override val name: String,
        private val bytes: ByteArray,
    ) : WebFileSource {
        override val size = bytes.size.toLong()
        override val mimeType: String? = "text/plain"
        override val lastModifiedEpochMillis: Long? = 42
        var failing = false

        override suspend fun read(
            position: Long,
            length: Int,
        ): ByteArray {
            if (failing) error("boom")
            return bytes.copyOfRange(position.toInt(), minOf(bytes.size, position.toInt() + length))
        }
    }

    @Test
    fun infoAndReads() =
        runTest {
            val access = WebFileAccess()
            val ref = access.register(Fake("a.txt", "hello world".encodeToByteArray()))
            assertEquals(FileRef.Kind.WebSession, ref.kind)
            val info = (access.info(ref) as FileResult.Success).value
            assertEquals("a.txt", info.name)
            assertEquals(11L, info.size)
            assertEquals(false, info.writable)
            val head = access.readRandom(ref) { it.readBytes(6, 5) }.getOrNull()
            assertContentEquals("world".encodeToByteArray(), head)
            val all = access.readStream(ref) { it.readBytes(100) }.getOrNull()
            assertContentEquals("hello world".encodeToByteArray(), all)
        }

    @Test
    fun unknownAndForeignReferencesAreNotFound() =
        runTest {
            val access = WebFileAccess()
            assertEquals(FileError.NotFound, access.info(FileRef(FileRef.Kind.WebSession, "9")).errorOrNull())
            assertEquals(FileError.NotFound, access.info(FileRef(FileRef.Kind.Path, "/x")).errorOrNull())
        }

    @Test
    fun listingAndWritingAreNotAvailable() =
        runTest {
            val access = WebFileAccess()
            val ref = access.register(Fake("a.txt", ByteArray(1)))
            val error = access.list(ref).errorOrNull()
            assertEquals(WebFileAccess.notAvailable(), error)
            assertIs<FileError.IoError>(error)
            assertEquals("not available on Web yet", error.detail)
            @OptIn(SafeSaveOnly::class)
            assertNull(access.rawWrites)
        }

    @Test
    fun browserReadErrorBecomesIoError() =
        runTest {
            val access = WebFileAccess()
            val source = Fake("a.txt", ByteArray(4))
            val ref = access.register(source)
            source.failing = true
            assertIs<FileError.IoError>(access.readStream(ref) { it.readBytes(4) }.errorOrNull())
        }

    @Test
    fun recentsAreNewestFirstDeduplicatedAndBounded() {
        val recents = SessionRecents(limit = 2)
        val a = FileRef(FileRef.Kind.WebSession, "1")
        val b = FileRef(FileRef.Kind.WebSession, "2")
        val c = FileRef(FileRef.Kind.WebSession, "3")
        recents.add(a)
        recents.add(b)
        recents.add(a)
        assertEquals(listOf(a, b), recents.recent)
        recents.add(c)
        assertEquals(listOf(c, a), recents.recent)
        recents.clear()
        assertEquals(emptyList(), recents.recent)
    }
}
