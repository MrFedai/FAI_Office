/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The [FileAccess] contract. Every implementation runs it: the in-memory fake in common tests, [FileAccess] on
 * desktop against real files in a temporary folder, and later the Android implementation in JVM tests.
 */
@OptIn(SafeSaveOnly::class)
abstract class FileAccessContract {
    /** An empty folder ([root]) in the store under test, with ways to seed it outside the API. */
    interface Fixture {
        val access: FileAccess
        val root: FileRef

        /** Creates a file at [path] (`/`-separated, relative to [root]), creating missing folders. */
        fun file(
            path: String,
            bytes: ByteArray,
        ): FileRef

        fun folder(path: String): FileRef

        /** A reference to [path] where nothing exists. */
        fun missing(path: String): FileRef

        /** Makes a file read-only; false when the platform cannot here (for example tests running as root). */
        fun makeReadOnly(ref: FileRef): Boolean

        fun close()
    }

    protected abstract fun fixture(): Fixture

    private fun contract(body: suspend TestScope.(Fixture) -> Unit): TestResult =
        runTest {
            val fixture = fixture()
            try {
                body(fixture)
            } finally {
                fixture.close()
            }
        }

    @Test
    fun infoReportsMetadataWithTurkishName() =
        contract { f ->
            val name = "Çalışma Öğüşİı ŞĞÜ.txt"
            val ref = f.file("Belgeler/$name", "merhaba".encodeToByteArray())
            val info = f.access.info(ref).getOrThrow()
            assertEquals(name, info.name)
            assertEquals(EntryKind.File, info.kind)
            assertEquals(7L, info.size)
            assertNotNull(info.modifiedEpochMillis)
            assertTrue(info.writable)
            assertEquals(ref, info.ref)

            val folder = f.access.info(f.folder("Belgeler")).getOrThrow()
            assertEquals("Belgeler", folder.name)
            assertEquals(EntryKind.Folder, folder.kind)
            assertNull(folder.size)
        }

    @Test
    fun referenceSurvivesSerialisation() =
        contract { f ->
            val ref = f.file("Arşiv/Ödev 1.docx", byteArrayOf(1, 2, 3))
            val restored = FileRef.parse(ref.serialize())
            assertEquals(ref, restored)
            assertEquals(
                3L,
                f.access
                    .info(restored)
                    .getOrThrow()
                    .size,
            )
        }

    @Test
    fun missingFileIsNotFound() =
        contract { f ->
            val ref = f.missing("yok.txt")
            assertEquals(FileError.NotFound, f.access.info(ref).errorOrNull())
            assertEquals(FileError.NotFound, f.access.readRandom(ref) { it.size }.errorOrNull())
            assertEquals(FileError.NotFound, f.access.readStream(ref) { 0 }.errorOrNull())
            assertEquals(FileError.NotFound, f.access.list(ref).errorOrNull())
        }

    @Test
    fun listReturnsDirectChildrenOnly() =
        contract { f ->
            f.file("Klasör/a.txt", ByteArray(5))
            f.file("Klasör/İç/b.txt", ByteArray(9))
            f.folder("Klasör/Boş")
            val children =
                f.access
                    .list(f.folder("Klasör"))
                    .getOrThrow()
                    .associateBy { it.name }
            assertEquals(setOf("a.txt", "İç", "Boş"), children.keys)
            assertEquals(EntryKind.File, children.getValue("a.txt").kind)
            assertEquals(5L, children.getValue("a.txt").size)
            assertEquals(EntryKind.Folder, children.getValue("İç").kind)
            assertEquals(
                listOf("b.txt"),
                f.access
                    .list(children.getValue("İç").ref)
                    .getOrThrow()
                    .map { it.name },
            )
        }

    @Test
    fun wrongKindIsReported() =
        contract { f ->
            val file = f.file("dosya.bin", ByteArray(1))
            val folder = f.folder("klasör")
            assertEquals(FileError.WrongKind(EntryKind.Folder), f.access.list(file).errorOrNull())
            assertEquals(FileError.WrongKind(EntryKind.File), f.access.readRandom(folder) { 0 }.errorOrNull())
            assertEquals(FileError.WrongKind(EntryKind.File), f.access.readStream(folder) { 0 }.errorOrNull())
        }

    @Test
    fun randomAccessReadsHeadAndTailWithinBounds() =
        contract { f ->
            val bytes = pattern(100_000)
            val ref = f.file("büyük.zip", bytes)
            f.access
                .readRandom(ref) { reader ->
                    assertEquals(100_000L, reader.size)
                    assertContentEquals(bytes.copyOfRange(0, 4), reader.readBytes(0, 4))
                    assertContentEquals(bytes.copyOfRange(99_978, 100_000), reader.readBytes(99_978, 22))
                    assertContentEquals(bytes.copyOfRange(99_990, 100_000), reader.readBytes(99_990, 64))
                    assertEquals(0, reader.readBytes(100_000, 16).size)
                    assertEquals(-1, reader.readAt(100_000, ByteArray(8)))
                    assertEquals(0, reader.readAt(5, ByteArray(8), 0, 0))
                }.getOrThrow()
        }

    @Test
    fun streamReadsWholeFileAndHonoursLimit() =
        contract { f ->
            val bytes = pattern(70_001)
            val ref = f.file("akış.bin", bytes)
            assertContentEquals(bytes, f.access.readStream(ref) { it.readBytes(1_000_000) }.getOrThrow())
            assertContentEquals(bytes.copyOf(1_000), f.access.readStream(ref) { it.readBytes(1_000) }.getOrThrow())
            val empty = f.file("boş.txt", ByteArray(0))
            assertEquals(0, f.access.bytes(empty).size)
        }

    @Test
    fun handleErrorBecomesFailureOtherExceptionsPropagate() =
        contract { f ->
            val ref = f.file("a.txt", ByteArray(3))
            val failed = f.access.readRandom(ref) { throw FileAccessException(FileError.Locked) }
            assertEquals(FileError.Locked, failed.errorOrNull())
            assertFailsWith<IllegalStateException> { f.access.readStream(ref) { error("caller bug") } }
        }

    @Test
    fun cancellationIsNotAnError() =
        contract { f ->
            val folder = f.folder("çok")
            repeat(50) { f.file("çok/$it.txt", ByteArray(1)) }
            val ref = f.file("a.bin", ByteArray(64))
            assertIs<CancellationException>(
                cancelledOutcome {
                    cancel()
                    f.access.list(folder)
                },
            )
            assertIs<CancellationException>(
                cancelledOutcome {
                    f.access.readRandom(ref) {
                        cancel()
                        it.readAt(0, ByteArray(8))
                    }
                },
            )
            assertIs<CancellationException>(
                cancelledOutcome {
                    f.access.readStream(ref) {
                        cancel()
                        it.read(ByteArray(8))
                    }
                },
            )
        }

    @Test
    fun concurrentReadsAreIndependent() =
        contract { f ->
            val refs = (0 until 8).map { i -> f.file("eş/$i.bin", pattern(4_096 + i)) }
            val sizes =
                refs
                    .map { ref ->
                        async {
                            f.access
                                .readStream(ref) { it.readBytes(10_000) }
                                .getOrThrow()
                                .size
                        }
                    }.awaitAll()
            assertEquals((0 until 8).map { 4_096 + it }, sizes)
        }

    @Test
    fun writeTemporarySiblingThenReplace() =
        contract { f ->
            val writes = f.access.rawWrites ?: return@contract
            val target = f.file("Kayıt/Rapor ğ.txt", "eski".encodeToByteArray())
            val temp = writes.createSibling(target, "Rapor ğ.txt").getOrThrow()
            val other = writes.createSibling(target, "Rapor ğ.txt").getOrThrow()
            assertNotEquals(temp, other)
            assertEquals(
                0L,
                f.access
                    .info(temp)
                    .getOrThrow()
                    .size,
            )
            writes.delete(other).getOrThrow()

            val fresh = "yeni içerik".encodeToByteArray()
            writes
                .write(temp) { sink ->
                    sink.write(fresh, 0, 4)
                    sink.write(fresh, 4, fresh.size - 4)
                    sink.flush()
                }.getOrThrow()
            assertContentEquals(fresh, f.access.bytes(temp))
            assertContentEquals("eski".encodeToByteArray(), f.access.bytes(target))

            writes.replace(temp, target).getOrThrow()
            assertContentEquals(fresh, f.access.bytes(target))
            assertEquals(FileError.NotFound, f.access.info(temp).errorOrNull())
            assertEquals(
                listOf("Rapor ğ.txt"),
                f.access
                    .list(f.folder("Kayıt"))
                    .getOrThrow()
                    .map { it.name },
            )
        }

    @Test
    fun writeTruncatesAndReportsErrors() =
        contract { f ->
            val writes = f.access.rawWrites ?: return@contract
            val ref = f.file("kısa.txt", "uzun bir metin".encodeToByteArray())
            writes.write(ref) { it.write("kı".encodeToByteArray()) }.getOrThrow()
            assertContentEquals("kı".encodeToByteArray(), f.access.bytes(ref))

            val missing = f.missing("yok.txt")
            assertEquals(FileError.NotFound, writes.write(missing) { }.errorOrNull())
            assertEquals(FileError.NotFound, writes.delete(missing).errorOrNull())
            assertEquals(FileError.WrongKind(EntryKind.File), writes.write(f.folder("k")) { }.errorOrNull())

            writes.delete(ref).getOrThrow()
            assertEquals(FileError.NotFound, f.access.info(ref).errorOrNull())

            val locked = f.file("salt.txt", "sabit".encodeToByteArray())
            if (f.makeReadOnly(locked)) {
                assertEquals(
                    false,
                    f.access
                        .info(locked)
                        .getOrThrow()
                        .writable,
                )
                assertEquals(FileError.ReadOnly, writes.write(locked) { }.errorOrNull())
                assertContentEquals("sabit".encodeToByteArray(), f.access.bytes(locked))
            }
        }

    private suspend fun TestScope.cancelledOutcome(block: suspend CoroutineScope.() -> Any?): Throwable? {
        var outcome: Result<Any?>? = null
        launch { outcome = runCatching { block() } }.join()
        return (outcome ?: fail("block did not run")).exceptionOrNull()
    }

    private suspend fun FileAccess.bytes(ref: FileRef): ByteArray =
        readStream(ref) { it.readBytes(1_000_000) }.getOrThrow()

    private fun pattern(size: Int): ByteArray = ByteArray(size) { (it * 31 + it / 251).toByte() }
}
