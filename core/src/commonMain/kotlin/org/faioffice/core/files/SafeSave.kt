/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What a document gives safe save; the engines implement it. */
interface SaveSource {
    /** Writes the whole document from the start. Called once per save; must not keep [sink]. */
    suspend fun writeTo(sink: ByteSink)

    /**
     * Engine hook (used from Phase 7): elements the target format cannot keep. When not empty, [SafeSave.save] stops
     * with [SaveError.LossyElements] unless the user accepted the loss; "Save as copy" is the offered alternative.
     */
    fun lossyElements(): List<LossyElement> = emptyList()

    /**
     * Engine hook: checks the format of the written file before it replaces the original (for example that a ZIP
     * central directory is readable). Null when valid, otherwise a short reason for the log.
     */
    suspend fun validate(written: RandomAccessReader): String? = null
}

/** A kind of document element the target format would lose, for example `"tracked-changes"`, and how many. */
data class LossyElement(
    val kind: String,
    val count: Int = 1,
)

/** Where a save stopped. */
enum class SaveStep { Prepare, Backup, Write, Flush, Verify, Replace }

/** Why a save did not happen. */
sealed interface SaveError {
    /** A file operation failed; [FileError.Locked] means another program holds the file (offer "Save as copy"). */
    data class File(
        val error: FileError,
    ) : SaveError

    /** The platform cannot write files in place (Web): offer a download instead. */
    data object Unsupported : SaveError

    /** What was read back differs from what was written, or the engine's format check failed. */
    data class VerificationFailed(
        val reason: String,
    ) : SaveError

    /** The document has elements the format cannot keep and the user has not accepted the loss. */
    data class LossyElements(
        val elements: List<LossyElement>,
    ) : SaveError
}

/** Outcome of a save. */
sealed interface SaveResult {
    /** The target now holds the verified document: [size] bytes with SHA-256 [sha256] (lower-case hex). */
    data class Saved(
        val target: FileRef,
        val size: Long,
        val sha256: String,
    ) : SaveResult

    /**
     * Nothing was saved and the target holds its previous content, unless [leftoverKept]: then cleanup or restoring
     * the backup also failed and [SafeSave.findLeftovers] lists what is left for the user to decide.
     */
    data class Failed(
        val error: SaveError,
        val step: SaveStep,
        val leftoverKept: Boolean = false,
    ) : SaveResult
}

/** Whether the save failed because another program holds the file open. */
val SaveResult.isLocked: Boolean
    get() = ((this as? SaveResult.Failed)?.error as? SaveError.File)?.error == FileError.Locked

/** What a crash left behind; see [SafeSave.findLeftovers]. */
enum class RecoveryKind {
    /** A temporary file next to the document; the document itself was not touched or was already replaced. */
    UnsavedTemporary,

    /** A backup of a document whose in-place write did not finish; restoring it brings the old content back. */
    Backup,

    /** A backup without a journal entry (the crash came before the original was touched); only discarding fits. */
    Orphan,
}

/**
 * One leftover found at start. [files] are the leftover files ([RecoveryKind.UnsavedTemporary]: the temporary file,
 * otherwise the backup in app-private storage); [target] and [targetName] are null for [RecoveryKind.Orphan].
 */
data class RecoveryItem(
    val kind: RecoveryKind,
    val target: FileRef?,
    val targetName: String?,
    val files: List<FileRef>,
    val entry: FileRef?,
)

/**
 * The only write path for documents (spec section 5, "Safe save"). One instance per app; saves run one at a time.
 *
 * Where [RawFileWrites.atomicReplace] holds (desktop file systems), a save writes a temporary file in the target's
 * folder, flushes it to disk, verifies it (re-read: size and SHA-256 of what was written, then the engine's
 * [SaveSource.validate]) and replaces the target atomically. Elsewhere (Android content URIs) it copies the original
 * to a verified backup in [journalFolder], writes the target in truncate mode, verifies it the same way and drops the
 * backup; on failure it writes the backup back.
 *
 * Every save keeps a journal entry in [journalFolder] (an existing app-private folder that [access] can write) while
 * it runs, so [findLeftovers] finds what a crash left at the next start. Leftovers are never deleted silently: the
 * user decides with [restore] or [discard]. Journal entries that hold no user data (a leftover already gone, an entry
 * cut off while it was written) are dropped by [findLeftovers]. One gap remains: a crash between creating the
 * temporary file and recording it leaves that temporary file, still empty, in the document's folder.
 */
@OptIn(SafeSaveOnly::class)
class SafeSave(
    private val access: FileAccess,
    private val journalFolder: FileRef,
) {
    private val mutex = Mutex()

    /** Saves [source] to [target] (an existing document or, on desktop, a new path). */
    suspend fun save(
        target: FileRef,
        source: SaveSource,
        acceptLosses: Boolean = false,
    ): SaveResult =
        mutex.withLock {
            if (!acceptLosses) {
                val losses = source.lossyElements()
                if (losses.isNotEmpty()) return SaveResult.Failed(SaveError.LossyElements(losses), SaveStep.Prepare)
            }
            write(target, source)
        }

    /**
     * Saves [source] as a new file [destination], leaving the open document's file as it is. No loss check: the copy
     * is what the loss warning offers. An existing [destination] is replaced only with [overwrite] (the platform
     * picker already asked; Android's "create document" hands over an existing empty file).
     */
    suspend fun saveAsCopy(
        destination: FileRef,
        source: SaveSource,
        overwrite: Boolean = false,
    ): SaveResult =
        mutex.withLock {
            if (!overwrite) {
                when (val existing = access.info(destination)) {
                    is FileResult.Success -> {
                        return fileFailure(FileError.AlreadyExists, SaveStep.Prepare)
                    }

                    is FileResult.Failure -> {
                        if (existing.error !=
                            FileError.NotFound
                        ) {
                            return fileFailure(existing.error, SaveStep.Prepare)
                        }
                    }
                }
            }
            write(destination, source)
        }

    /** Leftovers of interrupted saves, for the user to [restore] or [discard]; call at start. */
    suspend fun findLeftovers(): FileResult<List<RecoveryItem>> =
        mutex.withLock {
            val writes = access.rawWrites ?: return FileResult.Success(emptyList())
            val children =
                when (val listed = access.list(journalFolder)) {
                    is FileResult.Success -> listed.value.filter { it.kind == EntryKind.File }
                    is FileResult.Failure -> return listed
                }
            val orphans =
                children
                    .filter {
                        it.name.startsWith(
                            "${JournalEntry.BACKUP_PREFIX}.",
                        )
                    }.map { it.ref }
                    .toMutableSet()
            val items = mutableListOf<RecoveryItem>()
            for (child in children.filter { it.name.startsWith("${JournalEntry.ENTRY_PREFIX}.") }) {
                // An entry that cannot be read now (I/O error) is left for the next start.
                val bytes =
                    access.readStream(child.ref) { it.readBytes(JournalEntry.MAX_BYTES) }.getOrNull() ?: continue
                val entry = JournalEntry.decode(bytes)
                if (entry == null) {
                    writes.delete(child.ref)
                    continue
                }
                orphans.remove(entry.leftover)
                if (access.info(entry.leftover) !is FileResult.Success) {
                    writes.delete(child.ref)
                    continue
                }
                val kind = if (entry.mode == SaveMode.Atomic) RecoveryKind.UnsavedTemporary else RecoveryKind.Backup
                items += RecoveryItem(kind, entry.target, entry.targetName, listOf(entry.leftover), child.ref)
            }
            orphans.forEach { items += RecoveryItem(RecoveryKind.Orphan, null, null, listOf(it), null) }
            FileResult.Success(items)
        }

    /**
     * The user's choice to bring a leftover back: a [RecoveryKind.Backup] is written back into its document and
     * verified; an [RecoveryKind.UnsavedTemporary] replaces its document (the user looked at it and wants it).
     * [RecoveryKind.Orphan] has no document to restore into. The leftover and its entry are gone afterwards.
     */
    suspend fun restore(item: RecoveryItem): FileResult<Unit> =
        mutex.withLock {
            val writes = access.rawWrites ?: return FileResult.Failure(FileError.ReadOnly)
            val entryRef = item.entry ?: return FileResult.Failure(FileError.IoError("no document to restore into"))
            val entry =
                access
                    .readStream(entryRef) { it.readBytes(JournalEntry.MAX_BYTES) }
                    .getOrNull()
                    ?.let(JournalEntry::decode)
                    ?: return FileResult.Failure(FileError.IoError("unreadable journal entry"))
            when (entry.mode) {
                SaveMode.Atomic -> {
                    writes.replace(entry.leftover, entry.target).let { if (it is FileResult.Failure) return it }
                    writes.delete(entryRef)
                    FileResult.Success(Unit)
                }

                SaveMode.Backup -> {
                    restoreBackup(writes, entry).let { if (it is FileResult.Failure) return it }
                    dropBackup(writes, entryRef, entry.leftover)
                    FileResult.Success(Unit)
                }
            }
        }

    /** The user's choice to drop a leftover: its files and journal entry are deleted, the document stays as it is. */
    suspend fun discard(item: RecoveryItem): FileResult<Unit> =
        mutex.withLock {
            val writes = access.rawWrites ?: return FileResult.Failure(FileError.ReadOnly)
            for (file in item.files) {
                val deleted = writes.delete(file)
                if (deleted is FileResult.Failure && deleted.error != FileError.NotFound) return deleted
            }
            item.entry?.let { writes.delete(it) }
            FileResult.Success(Unit)
        }

    private suspend fun write(
        target: FileRef,
        source: SaveSource,
    ): SaveResult {
        val writes = access.rawWrites ?: return SaveResult.Failed(SaveError.Unsupported, SaveStep.Prepare)
        val info =
            when (val found = access.info(target)) {
                is FileResult.Success -> {
                    found.value
                }

                is FileResult.Failure -> {
                    if (found.error ==
                        FileError.NotFound
                    ) {
                        null
                    } else {
                        return fileFailure(found.error, SaveStep.Prepare)
                    }
                }
            }
        if (info?.kind == EntryKind.Folder) return fileFailure(FileError.WrongKind(EntryKind.File), SaveStep.Prepare)
        if (info != null && !info.writable) return fileFailure(FileError.ReadOnly, SaveStep.Prepare)
        return if (writes.atomicReplace) {
            atomicSave(writes, target, info?.name, source)
        } else {
            // Writing in truncate mode needs the file to exist (the content URI picker creates it).
            info ?: return fileFailure(FileError.NotFound, SaveStep.Prepare)
            backupSave(writes, target, info.name, source)
        }
    }

    // Any exception (an engine bug, cancellation) must still clean up before it propagates.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun atomicSave(
        writes: RawFileWrites,
        target: FileRef,
        name: String?,
        source: SaveSource,
    ): SaveResult {
        var entry: FileRef? = null
        var temp: FileRef? = null
        try {
            entry = writes.createIn(journalFolder, JournalEntry.ENTRY_PREFIX).at(SaveStep.Prepare)
            val displayName = name ?: DEFAULT_NAME
            temp =
                writes
                    .createSibling(
                        target,
                        "${displayName.take(MAX_HINT)}.${JournalEntry.TEMP_MARKER}",
                    ).at(SaveStep.Prepare)
            writeJournal(writes, entry, JournalEntry(SaveMode.Atomic, target, displayName, temp)).at(SaveStep.Prepare)
            val written = writeDocument(writes, temp, source)
            verify(temp, written, source)
            writes.replace(temp, target).at(SaveStep.Replace)
            temp = null
            // A stale entry whose temporary file is gone holds no user data; findLeftovers drops it.
            writes.delete(entry)
            return SaveResult.Saved(target, written.size, written.sha256)
        } catch (abort: SaveAbort) {
            val kept = withContext(NonCancellable) { dropTemporary(writes, entry, temp) }
            return abort.failed.copy(leftoverKept = kept)
        } catch (e: Throwable) {
            withContext(NonCancellable) { dropTemporary(writes, entry, temp) }
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun backupSave(
        writes: RawFileWrites,
        target: FileRef,
        name: String,
        source: SaveSource,
    ): SaveResult {
        var entryRef: FileRef? = null
        var backup: FileRef? = null
        var entry: JournalEntry? = null
        try {
            backup = writes.createIn(journalFolder, JournalEntry.BACKUP_PREFIX).at(SaveStep.Backup)
            val original = copy(writes, target, backup).at(SaveStep.Backup)
            hashOf(backup).at(SaveStep.Backup).let {
                if (it !=
                    original
                ) {
                    throw SaveAbort(
                        SaveError.VerificationFailed("backup differs from the original"),
                        SaveStep.Backup,
                    )
                }
            }
            entryRef = writes.createIn(journalFolder, JournalEntry.ENTRY_PREFIX).at(SaveStep.Backup)
            val complete = JournalEntry(SaveMode.Backup, target, name, backup, original.size, original.sha256)
            // The complete entry marks the backup as valid; only then is the original touched.
            writeJournal(writes, entryRef, complete).at(SaveStep.Backup)
            entry = complete
            val written = writeDocument(writes, target, source)
            verify(target, written, source)
            dropBackup(writes, entryRef, backup)
            return SaveResult.Saved(target, written.size, written.sha256)
        } catch (abort: SaveAbort) {
            val kept = withContext(NonCancellable) { undoBackupSave(writes, entryRef, backup, entry) }
            return abort.failed.copy(leftoverKept = kept)
        } catch (e: Throwable) {
            withContext(NonCancellable) { undoBackupSave(writes, entryRef, backup, entry) }
            throw e
        }
    }

    /** Restores the original if it was touched, then drops the backup; true when something had to be kept. */
    private suspend fun undoBackupSave(
        writes: RawFileWrites,
        entryRef: FileRef?,
        backup: FileRef?,
        entry: JournalEntry?,
    ): Boolean {
        if (entry != null && restoreBackup(writes, entry) is FileResult.Failure) return true
        return !dropBackup(writes, entryRef, backup)
    }

    /** Writes the backup of [entry] back into its target and checks it; a damaged backup leaves the target alone. */
    private suspend fun restoreBackup(
        writes: RawFileWrites,
        entry: JournalEntry,
    ): FileResult<Unit> {
        val expected = Hashed(entry.size ?: -1, entry.sha256.orEmpty())
        val backupHash = hashOf(entry.leftover).let { if (it is FileResult.Failure) return it else it.getOrThrow() }
        if (backupHash != expected) return FileResult.Failure(FileError.IoError("backup is damaged"))
        val copied =
            copy(writes, entry.leftover, entry.target).let {
                if (it is FileResult.Failure) return it else it.getOrThrow()
            }
        val readBack = hashOf(entry.target).let { if (it is FileResult.Failure) return it else it.getOrThrow() }
        if (copied != expected ||
            readBack != expected
        ) {
            return FileResult.Failure(FileError.IoError("restored file differs from the backup"))
        }
        return FileResult.Success(Unit)
    }

    /** Deletes the backup, then its entry; true when the backup is gone. */
    private suspend fun dropBackup(
        writes: RawFileWrites,
        entryRef: FileRef?,
        backup: FileRef?,
    ): Boolean {
        if (backup != null && !writes.delete(backup).deletedOrMissing()) return false
        entryRef?.let { writes.delete(it) }
        return true
    }

    /** Deletes the temporary file, then the entry; true when the temporary file had to be kept. */
    private suspend fun dropTemporary(
        writes: RawFileWrites,
        entry: FileRef?,
        temp: FileRef?,
    ): Boolean {
        if (temp != null && !writes.delete(temp).deletedOrMissing()) return true
        entry?.let { writes.delete(it) }
        return false
    }

    private suspend fun writeJournal(
        writes: RawFileWrites,
        ref: FileRef,
        entry: JournalEntry,
    ): FileResult<Unit> =
        writes.write(ref) { sink ->
            sink.write(entry.encode())
            sink.flush()
        }

    /** Writes the document into [ref], hashing what goes out, and flushes it. */
    private suspend fun writeDocument(
        writes: RawFileWrites,
        ref: FileRef,
        source: SaveSource,
    ): Hashed {
        val sha = Sha256()
        var size = 0L
        var flushing = false
        val result =
            writes.write(ref) { sink ->
                source.writeTo(
                    object : ByteSink {
                        override suspend fun write(
                            buffer: ByteArray,
                            offset: Int,
                            length: Int,
                        ) {
                            sink.write(buffer, offset, length)
                            sha.update(buffer, offset, length)
                            size += length
                        }

                        override suspend fun flush() = sink.flush()
                    },
                )
                flushing = true
                sink.flush()
            }
        result.at(if (flushing) SaveStep.Flush else SaveStep.Write)
        return Hashed(size, sha.digest().toHex())
    }

    /** Re-reads [ref] and checks it against [written], then lets the engine check the format. */
    private suspend fun verify(
        ref: FileRef,
        written: Hashed,
        source: SaveSource,
    ) {
        val readBack = hashOf(ref).at(SaveStep.Verify)
        if (readBack.size != written.size) {
            throw SaveAbort(
                SaveError.VerificationFailed("size ${readBack.size}, expected ${written.size}"),
                SaveStep.Verify,
            )
        }
        if (readBack.sha256 !=
            written.sha256
        ) {
            throw SaveAbort(SaveError.VerificationFailed("SHA-256 differs"), SaveStep.Verify)
        }
        val problem = access.readRandom(ref) { source.validate(it) }.at(SaveStep.Verify)
        if (problem != null) throw SaveAbort(SaveError.VerificationFailed(problem), SaveStep.Verify)
    }

    /** Copies [from] into the existing file [to] (truncating it) and flushes; the hash of what was copied. */
    private suspend fun copy(
        writes: RawFileWrites,
        from: FileRef,
        to: FileRef,
    ): FileResult<Hashed> =
        writes.write(to) { sink ->
            val hashed =
                access
                    .readStream(from) { input ->
                        streamHash(input) { buffer, length -> sink.write(buffer, 0, length) }
                    }.getOrThrow()
            sink.flush()
            hashed
        }

    private suspend fun hashOf(ref: FileRef): FileResult<Hashed> =
        access.readStream(ref) { input -> streamHash(input) { _, _ -> } }

    private suspend fun streamHash(
        input: ByteSource,
        each: suspend (ByteArray, Int) -> Unit,
    ): Hashed {
        val sha = Sha256()
        val buffer = ByteArray(BUFFER)
        var size = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            sha.update(buffer, 0, n)
            each(buffer, n)
            size += n
        }
        return Hashed(size, sha.digest().toHex())
    }

    private data class Hashed(
        val size: Long,
        val sha256: String,
    )

    /** Ends a save at [step]; caught by the save, which cleans up and returns [failed]. */
    private class SaveAbort(
        error: SaveError,
        step: SaveStep,
    ) : Exception(error.toString()) {
        val failed = SaveResult.Failed(error, step)
    }

    private fun <T> FileResult<T>.at(step: SaveStep): T =
        when (this) {
            is FileResult.Success -> value
            is FileResult.Failure -> throw SaveAbort(SaveError.File(error), step)
        }

    private fun FileResult<Unit>.deletedOrMissing(): Boolean =
        this is FileResult.Success || errorOrNull() == FileError.NotFound

    private fun fileFailure(
        error: FileError,
        step: SaveStep,
    ) = SaveResult.Failed(SaveError.File(error), step)

    private companion object {
        const val BUFFER = 64 * 1024
        const val MAX_HINT = 80
        const val DEFAULT_NAME = "document"
    }
}
