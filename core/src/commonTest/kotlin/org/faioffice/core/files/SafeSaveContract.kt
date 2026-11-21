/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The safe-save contract, run on every store: the in-memory fake and real desktop files, each on the atomic path
 * and, with [contentUri], on the backup path of Android content URIs (a provider without atomic replace).
 */
abstract class SafeSaveContract {
    /** A documents folder and an app-private journal folder, both empty, in the store under test. */
    interface Fixture {
        val inner: FileAccess
        val folder: FileRef
        val journal: FileRef

        fun file(
            name: String,
            bytes: ByteArray,
        ): FileRef

        fun missing(name: String): FileRef

        fun close()
    }

    protected abstract fun fixture(): Fixture

    /** True to run the content-URI (backup, truncate-write) path instead of the atomic one. */
    protected open val contentUri: Boolean = false

    private class Store(
        val fixture: Fixture,
        val access: FaultyFileAccess,
    ) {
        val safeSave = SafeSave(access, fixture.journal)
    }

    private fun store(): Store {
        val f = fixture()
        return Store(f, FaultyFileAccess(f.inner, atomicReplace = !contentUri))
    }

    private fun contract(body: suspend TestScope.(Store) -> Unit): TestResult =
        runTest {
            val s = store()
            try {
                body(s)
            } finally {
                s.fixture.close()
            }
        }

    @Test
    fun saveRoundTripWithTurkishName() =
        contract { s ->
            val target = s.fixture.file(NAME, OLD)
            val result = assertIs<SaveResult.Saved>(s.safeSave.save(target, BytesSource(NEW)))
            assertEquals(target, result.target)
            assertEquals(NEW.size.toLong(), result.size)
            assertEquals(Sha256.hex(NEW), result.sha256)
            assertContentEquals(NEW, s.access.bytesOf(target))
            assertEquals(
                listOf(NAME),
                s.access
                    .list(s.fixture.folder)
                    .getOrThrow()
                    .map { it.name },
            )
            assertNoLeftovers(s)

            assertIs<SaveResult.Saved>(s.safeSave.save(target, BytesSource(ByteArray(0))))
            assertContentEquals(ByteArray(0), s.access.bytesOf(target))
        }

    @Test
    fun newFileNeedsAtomicPath() =
        contract { s ->
            val target = s.fixture.missing("Yeni Belge ğüşiöç.txt")
            val result = s.safeSave.save(target, BytesSource(NEW))
            if (contentUri) {
                assertEquals(SaveError.File(FileError.NotFound), assertIs<SaveResult.Failed>(result).error)
            } else {
                assertIs<SaveResult.Saved>(result)
                assertContentEquals(NEW, s.access.bytesOf(target))
            }
            assertNoLeftovers(s)
        }

    @Test
    fun crashAfterEveryStepLeavesOldOrNewContent() =
        runTest {
            val full = crashRun(Int.MAX_VALUE)
            val kinds = full.ops.map { it.first }.toSet()
            assertTrue(kinds.containsAll(listOf(FileOp.Write, FileOp.Flush, FileOp.Read)), "steps: $kinds")
            assertEquals(!contentUri, FileOp.Replace in kinds)
            for (limit in 0..full.ops.size) crashRun(limit)
        }

    /** Saves with a power cut after [limit] operations, recovers at the "next start" and checks the result. */
    private suspend fun crashRun(limit: Int): Crash {
        val s = store()
        try {
            val target = s.fixture.file(NAME, OLD)
            val crash = Crash(limit)
            s.access.faults = crash::fault
            val result = s.safeSave.save(target, BytesSource(NEW))
            s.access.faults = { _, _ -> null }
            if (!crash.crashed) assertIs<SaveResult.Saved>(result)

            val next = SafeSave(s.access, s.fixture.journal)
            for (item in next.findLeftovers().getOrThrow()) {
                if (item.kind ==
                    RecoveryKind.Backup
                ) {
                    next.restore(item).getOrThrow()
                } else {
                    next.discard(item).getOrThrow()
                }
            }
            val content = s.access.bytesOf(target)
            assertTrue(content.contentEquals(OLD) || content.contentEquals(NEW), "crash after $limit ops: ${crash.ops}")
            assertNoLeftovers(s, next)
            val others =
                s.access
                    .list(s.fixture.folder)
                    .getOrThrow()
                    .filter { it.ref != target }
            // The documented gap: a crash between creating the temporary file and journalling it leaves it empty.
            assertTrue(
                others.all { it.size == 0L && JournalEntry.TEMP_MARKER in it.name },
                "crash after $limit: $others",
            )
            return crash
        } finally {
            s.fixture.close()
        }
    }

    @Test
    fun errorAtEveryStepRestoresOriginal() =
        runTest {
            val total = crashRunOps()
            for (index in 0 until total) {
                val s = store()
                try {
                    val target = s.fixture.file(NAME, OLD)
                    var count = 0
                    s.access.faults = { _, _ -> if (count++ == index) FileError.IoError("injected") else null }
                    val result = s.safeSave.save(target, BytesSource(NEW))
                    s.access.faults = { _, _ -> null }
                    if (result is SaveResult.Failed && !result.leftoverKept) {
                        assertContentEquals(OLD, s.access.bytesOf(target), "error at op $index: $result")
                        assertEquals(
                            listOf(NAME),
                            s.access
                                .list(s.fixture.folder)
                                .getOrThrow()
                                .map { it.name },
                        )
                        assertEquals(emptyList(), s.access.list(s.fixture.journal).getOrThrow())
                    }
                    if (result is SaveResult.Saved) assertContentEquals(NEW, s.access.bytesOf(target))
                    for (item in s.safeSave.findLeftovers().getOrThrow()) {
                        if (item.kind == RecoveryKind.Backup) s.safeSave.restore(item) else s.safeSave.discard(item)
                    }
                    val content = s.access.bytesOf(target)
                    assertTrue(content.contentEquals(OLD) || content.contentEquals(NEW), "error at op $index")
                    assertNoLeftovers(s)
                } finally {
                    s.fixture.close()
                }
            }
        }

    private suspend fun crashRunOps(): Int = crashRun(Int.MAX_VALUE).ops.size

    @Test
    fun leftoversAreFoundAtNextStartAndNeverDeletedSilently() =
        runTest {
            // Cut the power right after the new document was flushed (temporary file, or the target in place).
            val documentFlush = if (contentUri) 3 else 2
            val full = crashRun(Int.MAX_VALUE)
            val limit =
                full.ops
                    .withIndex()
                    .filter { it.value.first == FileOp.Flush }[documentFlush - 1]
                    .index + 1
            val s = store()
            try {
                val target = s.fixture.file(NAME, OLD)
                s.access.faults = Crash(limit)::fault
                assertIs<SaveResult.Failed>(s.safeSave.save(target, BytesSource(NEW)))
                s.access.faults = { _, _ -> null }

                val next = SafeSave(s.access, s.fixture.journal)
                val found = next.findLeftovers().getOrThrow()
                assertEquals(found, next.findLeftovers().getOrThrow(), "finding must not change anything")
                val item = found.single()
                assertEquals(if (contentUri) RecoveryKind.Backup else RecoveryKind.UnsavedTemporary, item.kind)
                assertEquals(target, item.target)
                assertEquals(NAME, item.targetName)
                item.files.forEach { assertIs<FileResult.Success<FileInfo>>(s.access.info(it)) }

                next.restore(item).getOrThrow()
                // Backup: the old content comes back. Temporary file: the user chose the newer, complete version.
                assertContentEquals(if (contentUri) OLD else NEW, s.access.bytesOf(target))
                assertNoLeftovers(s, next)
            } finally {
                s.fixture.close()
            }
        }

    @Test
    fun orphanBackupIsReportedAndDiscardedOnRequest() =
        runTest {
            if (!contentUri) return@runTest
            // Cut the power after the backup was flushed, before its journal entry was complete.
            val full = crashRun(Int.MAX_VALUE)
            val limit =
                full.ops
                    .withIndex()
                    .first { it.value.first == FileOp.Flush }
                    .index + 1
            val s = store()
            try {
                val target = s.fixture.file(NAME, OLD)
                s.access.faults = Crash(limit)::fault
                s.safeSave.save(target, BytesSource(NEW))
                s.access.faults = { _, _ -> null }
                val item =
                    s.safeSave
                        .findLeftovers()
                        .getOrThrow()
                        .single()
                assertEquals(RecoveryKind.Orphan, item.kind)
                assertContentEquals(OLD, s.access.bytesOf(target))
                assertContentEquals(OLD, s.access.bytesOf(item.files.single()))
                assertIs<FileResult.Failure>(s.safeSave.restore(item))
                s.safeSave.discard(item).getOrThrow()
                assertNoLeftovers(s)
            } finally {
                s.fixture.close()
            }
        }

    @Test
    fun formatValidatorRejectsWrittenFile() =
        contract { s ->
            val target = s.fixture.file(NAME, OLD)
            val result =
                assertIs<SaveResult.Failed>(s.safeSave.save(target, BytesSource(NEW, problem = "no ZIP directory")))
            assertEquals(SaveError.VerificationFailed("no ZIP directory"), result.error)
            assertEquals(SaveStep.Verify, result.step)
            assertFalse(result.leftoverKept)
            assertContentEquals(OLD, s.access.bytesOf(target))
            assertEquals(
                listOf(NAME),
                s.access
                    .list(s.fixture.folder)
                    .getOrThrow()
                    .map { it.name },
            )
            assertNoLeftovers(s)
        }

    @Test
    fun lossyElementsNeedConfirmationOrCopy() =
        contract { s ->
            val target = s.fixture.file(NAME, OLD)
            val losses = listOf(LossyElement("tracked-changes", 3))
            val result = assertIs<SaveResult.Failed>(s.safeSave.save(target, BytesSource(NEW, losses)))
            assertEquals(SaveError.LossyElements(losses), result.error)
            assertContentEquals(OLD, s.access.bytesOf(target))

            val copy = copyDestination(s, "Kopya Çizelge.txt")
            assertIs<SaveResult.Saved>(s.safeSave.saveAsCopy(copy, BytesSource(NEW, losses), overwrite = contentUri))
            assertContentEquals(OLD, s.access.bytesOf(target))
            assertContentEquals(NEW, s.access.bytesOf(copy))

            assertIs<SaveResult.Saved>(s.safeSave.save(target, BytesSource(NEW, losses), acceptLosses = true))
            assertContentEquals(NEW, s.access.bytesOf(target))
            assertNoLeftovers(s)
        }

    @Test
    fun saveAsCopyKeepsOriginalAndRefusesToOverwriteSilently() =
        contract { s ->
            val original = s.fixture.file(NAME, OLD)
            val other = s.fixture.file("Başka.txt", "başka".encodeToByteArray())
            val refused = assertIs<SaveResult.Failed>(s.safeSave.saveAsCopy(other, BytesSource(NEW)))
            assertEquals(SaveError.File(FileError.AlreadyExists), refused.error)
            assertContentEquals("başka".encodeToByteArray(), s.access.bytesOf(other))

            val copy = copyDestination(s, "Rapor kopyası.txt")
            assertIs<SaveResult.Saved>(s.safeSave.saveAsCopy(copy, BytesSource(NEW), overwrite = contentUri))
            assertContentEquals(NEW, s.access.bytesOf(copy))
            assertContentEquals(OLD, s.access.bytesOf(original))

            assertIs<SaveResult.Saved>(s.safeSave.saveAsCopy(other, BytesSource(NEW), overwrite = true))
            assertContentEquals(NEW, s.access.bytesOf(other))
            assertNoLeftovers(s)
        }

    @Test
    fun lockedTargetFailsCleanlyAndCopyStillWorks() =
        contract { s ->
            val target = s.fixture.file(NAME, OLD)
            var lockedOnce = false
            // Desktop: replace fails (Windows: another program holds the file); content URI: opening for write fails.
            val lockedOp = if (contentUri) FileOp.Write else FileOp.Replace
            s.access.faults = { op, ref ->
                if (op == lockedOp && ref == target &&
                    !lockedOnce
                ) {
                    FileError.Locked.also { lockedOnce = true }
                } else {
                    null
                }
            }
            val result = s.safeSave.save(target, BytesSource(NEW))
            assertTrue(result.isLocked, "$result")
            assertFalse(assertIs<SaveResult.Failed>(result).leftoverKept)
            assertContentEquals(OLD, s.access.bytesOf(target))
            assertNoLeftovers(s)

            val copy = copyDestination(s, "Kilitli kopya.txt")
            assertIs<SaveResult.Saved>(s.safeSave.saveAsCopy(copy, BytesSource(NEW), overwrite = contentUri))
            assertContentEquals(NEW, s.access.bytesOf(copy))
        }

    @Test
    fun folderTargetIsRefusedBeforeWriting() =
        contract { s ->
            val folderTarget = s.fixture.folder
            val result = assertIs<SaveResult.Failed>(s.safeSave.save(folderTarget, BytesSource(NEW)))
            assertEquals(SaveError.File(FileError.WrongKind(EntryKind.File)), result.error)
            assertNoLeftovers(s)
        }

    @Test
    fun writerExceptionPropagatesAfterCleanup() =
        contract { s ->
            val target = s.fixture.file(NAME, OLD)
            val failing =
                object : SaveSource {
                    override suspend fun writeTo(sink: ByteSink) {
                        sink.write(NEW, 0, 10)
                        error("engine bug")
                    }
                }
            assertFailsWith<IllegalStateException> { s.safeSave.save(target, failing) }
            assertContentEquals(OLD, s.access.bytesOf(target))
            assertEquals(
                listOf(NAME),
                s.access
                    .list(s.fixture.folder)
                    .getOrThrow()
                    .map { it.name },
            )
            assertNoLeftovers(s)
        }

    private fun copyDestination(
        s: Store,
        name: String,
    ): FileRef = if (contentUri) s.fixture.file(name, ByteArray(0)) else s.fixture.missing(name)

    private suspend fun assertNoLeftovers(
        s: Store,
        safeSave: SafeSave = s.safeSave,
    ) {
        assertEquals(emptyList(), safeSave.findLeftovers().getOrThrow())
        assertEquals(emptyList(), s.access.list(s.fixture.journal).getOrThrow())
    }

    companion object {
        const val NAME = "Çalışma Öğüş İı.txt"
        val OLD = ByteArray(2500) { (it * 7 + 3).toByte() }
        val NEW = ByteArray(3100) { (it * 13 + 5).toByte() }
    }
}

/** A document of [bytes], written in chunks; [losses] and [problem] drive the engine hooks. */
class BytesSource(
    private val bytes: ByteArray,
    private val losses: List<LossyElement> = emptyList(),
    private val problem: String? = null,
) : SaveSource {
    override suspend fun writeTo(sink: ByteSink) {
        var done = 0
        while (done < bytes.size) {
            val n = minOf(CHUNK, bytes.size - done)
            sink.write(bytes, done, n)
            done += n
        }
    }

    override fun lossyElements(): List<LossyElement> = losses

    override suspend fun validate(written: RandomAccessReader): String? = problem

    private companion object {
        const val CHUNK = 1000
    }
}

suspend fun FileAccess.bytesOf(ref: FileRef): ByteArray =
    readStream(ref) {
        it.readBytes(16 * 1024 * 1024)
    }.getOrThrow()
