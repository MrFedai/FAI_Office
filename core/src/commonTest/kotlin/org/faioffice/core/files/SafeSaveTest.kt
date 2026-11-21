/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private fun memoryFixture(): SafeSaveContract.Fixture =
    object : SafeSaveContract.Fixture {
        val fake = InMemoryFileAccess()
        override val inner: FileAccess = fake
        override val folder: FileRef = fake.addFolder("/Belgeler")
        override val journal: FileRef = fake.addFolder("/uygulama/kayıt")

        override fun file(
            name: String,
            bytes: ByteArray,
        ): FileRef = fake.addFile("/Belgeler/$name", bytes)

        override fun missing(name: String): FileRef = fake.ref("/Belgeler/$name")

        override fun close() = Unit
    }

/** Atomic path (desktop file systems) on the in-memory fake. */
class InMemorySafeSaveTest : SafeSaveContract() {
    override fun fixture(): Fixture = memoryFixture()
}

/** Content-URI path (backup, truncate write, restore) on the in-memory fake without atomic replace. */
class InMemoryContentUriSafeSaveTest : SafeSaveContract() {
    override val contentUri: Boolean = true

    override fun fixture(): Fixture = memoryFixture()
}

class SafeSaveTest {
    @Test
    fun unsupportedWithoutRawWrites() =
        runTest {
            val fake = InMemoryFileAccess()
            val target = fake.addFile("/a.txt", "eski".encodeToByteArray())
            val web = FaultyFileAccess(fake, writable = false)
            val safeSave = SafeSave(web, fake.addFolder("/kayıt"))
            val result = assertIs<SaveResult.Failed>(safeSave.save(target, BytesSource(byteArrayOf(1))))
            assertEquals(SaveError.Unsupported, result.error)
            assertEquals(emptyList(), safeSave.findLeftovers().getOrThrow())
            assertContentEquals("eski".encodeToByteArray(), fake.contents(target))
        }

    @Test
    fun missingJournalFolderFailsBeforeTouchingTheDocument() =
        runTest {
            val fake = InMemoryFileAccess()
            val target = fake.addFile("/a.txt", "eski".encodeToByteArray())
            val result = SafeSave(fake, fake.ref("/yok")).save(target, BytesSource(byteArrayOf(1)))
            assertEquals(SaveError.File(FileError.NotFound), assertIs<SaveResult.Failed>(result).error)
            assertContentEquals("eski".encodeToByteArray(), fake.contents(target))
            assertEquals(setOf("/", "/a.txt"), fake.paths())
        }

    @Test
    fun journalEntryRoundTripsAndRejectsCutOffEntries() {
        val entry =
            JournalEntry(
                SaveMode.Backup,
                FileRef(FileRef.Kind.ContentUri, "content://belgeler/document/12%3AÇizelge"),
                "Satır\nsonu \\ ve Öğüş.xlsx",
                FileRef(FileRef.Kind.Path, "/data/kayıt/fai-save-backup.3.tmp"),
                size = 42,
                sha256 = Sha256.hex(byteArrayOf(1)),
            )
        val bytes = entry.encode()
        assertEquals(entry, JournalEntry.decode(bytes))
        for (cut in bytes.indices) assertNull(JournalEntry.decode(bytes.copyOf(cut)), "cut at $cut")
        assertNull(JournalEntry.decode("başka bir dosya".encodeToByteArray()))
    }

    @Test
    fun sha256MatchesKnownVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".encodeToByteArray()),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
        val million = ByteArray(1_000_000) { 'a'.code.toByte() }
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.hex(million))
        // Same hash when fed in odd chunks across block boundaries.
        val sha = Sha256()
        var done = 0
        var step = 1
        while (done < million.size) {
            val n = minOf(step, million.size - done)
            sha.update(million, done, n)
            done += n
            step = step * 3 % 977 + 1
        }
        assertEquals(Sha256.hex(million), sha.digest().toHex())
        // Padding edge cases: 55, 56 and 64 bytes.
        assertEquals(
            "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            Sha256.hex(ByteArray(55) { 'a'.code.toByte() }),
        )
        assertEquals(
            "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            Sha256.hex(ByteArray(56) { 'a'.code.toByte() }),
        )
        assertEquals(
            "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            Sha256.hex(ByteArray(64) { 'a'.code.toByte() }),
        )
    }
}
