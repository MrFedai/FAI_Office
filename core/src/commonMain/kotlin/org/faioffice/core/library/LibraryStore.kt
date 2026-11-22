/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.faioffice.core.files.ByteSink
import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileError
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.FileResult
import org.faioffice.core.files.SafeSave
import org.faioffice.core.files.SaveResult
import org.faioffice.core.files.SaveSource
import org.faioffice.core.files.readBytes
import org.faioffice.core.formats.FileFormat

/**
 * Recent files and the user's folder library, in one small schema-versioned file ([LibraryFormat]) in app-private
 * storage, written only through [safeSave].
 *
 * [storeFile] is the store file's reference and [damagedFile] where an unreadable store is copied before it is
 * replaced; the platform layer resolves both (Android files dir, Windows `%APPDATA%`, Linux XDG data dir; P3.4,
 * P3.5, P3.8). Where [SafeSave] cannot create files (Android content URIs) the platform creates both empty first.
 * An absent or empty store is an empty library. [clock] gives epoch milliseconds.
 *
 * All operations run one at a time. Mutators return whether the change reached the disk; the in-memory state keeps
 * it either way and the next mutation writes again. Nothing here checks files in the background: [recentsWithState]
 * and [stateOf] check when asked.
 */
class LibraryStore(
    private val access: FileAccess,
    private val safeSave: SafeSave,
    private val storeFile: FileRef,
    private val damagedFile: FileRef,
    private val clock: () -> Long,
) {
    private val mutex = Mutex()
    private var data = LibraryData()
    private var loaded = false

    /** Recent entries, newest first (most recently opened at the front). */
    suspend fun recents(): List<RecentEntry> =
        mutex.withLock {
            load()
            data.recents
        }

    suspend fun folders(): List<LibraryFolder> =
        mutex.withLock {
            load()
            data.folders
        }

    suspend fun rememberRecent(): Boolean =
        mutex.withLock {
            load()
            data.rememberRecent
        }

    /**
     * Notes that [ref] was opened now: one entry per reference, moved to the front (name, format and size are
     * refreshed, a pin is kept). Nothing happens while "Remember recent files" is off. More than
     * [MAX_UNPINNED] unpinned entries drop the oldest; pinned entries are never dropped.
     */
    suspend fun recordOpened(
        ref: FileRef,
        name: String,
        format: FileFormat,
        size: Long?,
    ): Boolean =
        mutex.withLock {
            load()
            if (!data.rememberRecent) return true
            val old = data.recents.firstOrNull { it.ref == ref }
            val entry = RecentEntry(ref, name, format, size, clock(), old?.pinned ?: false)
            val all = listOf(entry) + data.recents.filter { it.ref != ref }
            var unpinned = 0
            val kept = all.filter { it.pinned || ++unpinned <= MAX_UNPINNED }
            mutate(data.copy(recents = kept))
        }

    suspend fun remove(ref: FileRef): Boolean =
        mutex.withLock {
            load()
            mutate(data.copy(recents = data.recents.filter { it.ref != ref }))
        }

    suspend fun setPinned(
        ref: FileRef,
        pinned: Boolean,
    ): Boolean =
        mutex.withLock {
            load()
            mutate(data.copy(recents = data.recents.map { if (it.ref == ref) it.copy(pinned = pinned) else it }))
        }

    /** Removes every recent entry, pinned ones too. Folders stay. */
    suspend fun clearRecents(): Boolean =
        mutex.withLock {
            load()
            mutate(data.copy(recents = emptyList()))
        }

    /** "Remember recent files": off clears the list and stops recording; on starts recording again. */
    suspend fun setRememberRecent(enabled: Boolean): Boolean =
        mutex.withLock {
            load()
            mutate(data.copy(rememberRecent = enabled, recents = if (enabled) data.recents else emptyList()))
        }

    /** Adds [ref] to the folder library; an existing one only gets the new [name]. */
    suspend fun addFolder(
        ref: FileRef,
        name: String,
    ): Boolean =
        mutex.withLock {
            load()
            val old = data.folders.firstOrNull { it.ref == ref }
            val folder = LibraryFolder(ref, name, old?.addedEpochMillis ?: clock())
            val folders =
                if (old == null) data.folders + folder else data.folders.map { if (it.ref == ref) folder else it }
            mutate(data.copy(folders = folders))
        }

    suspend fun removeFolder(ref: FileRef): Boolean =
        mutex.withLock {
            load()
            mutate(data.copy(folders = data.folders.filter { it.ref != ref }))
        }

    /**
     * Writes the budget seed ([LibrarySeed]): the library becomes [recents] and [folders], but only when it is empty
     * or holds nothing but an earlier seed (every reference [isSeed]) with "Remember recent files" on. Anything of
     * the user's (or an unreadable store) is left alone: [SeedResult.Outcome.Refused]. The same seed already there
     * is not written again ([SeedResult.Outcome.AlreadySeeded]), so launches after the seeding one only read.
     */
    suspend fun seed(
        recents: List<RecentEntry>,
        folders: List<LibraryFolder>,
        isSeed: (FileRef) -> Boolean,
    ): SeedResult =
        mutex.withLock {
            load()
            val current = data
            val own = current.recents.any { !isSeed(it.ref) } || current.folders.any { !isSeed(it.ref) }
            val outcome =
                when {
                    !loaded || own || !current.rememberRecent -> {
                        SeedResult.Outcome.Refused
                    }

                    current.recents.map { it.ref } == recents.map { it.ref } &&
                        current.folders.map { it.ref } == folders.map { it.ref } -> {
                        SeedResult.Outcome.AlreadySeeded
                    }

                    mutate(LibraryData(rememberRecent = true, recents = recents, folders = folders)) -> {
                        SeedResult.Outcome.Seeded
                    }

                    else -> {
                        SeedResult.Outcome.NotSaved
                    }
                }
            SeedResult(outcome, data.recents.size, data.folders.size)
        }

    /**
     * State of [ref] now, from [FileAccess.info]: found is [EntryState.Available], [FileError.NotFound] and a wrong
     * kind or I/O error are [EntryState.Missing], [FileError.PermissionLost] is [EntryState.PermissionLost]; a locked,
     * read-only or full medium does not make a file that exists unavailable.
     */
    suspend fun stateOf(ref: FileRef): EntryState =
        when (val result = access.info(ref)) {
            is FileResult.Success -> {
                EntryState.Available
            }

            is FileResult.Failure -> {
                when (result.error) {
                    FileError.PermissionLost -> EntryState.PermissionLost
                    FileError.NotFound, is FileError.WrongKind, is FileError.IoError -> EntryState.Missing
                    else -> EntryState.Available
                }
            }
        }

    /** Recent entries (newest first) with their state, each checked now. */
    suspend fun recentsWithState(): List<RecentStatus> = recents().map { RecentStatus(it, stateOf(it.ref)) }

    private suspend fun mutate(next: LibraryData): Boolean {
        data = next
        return persist()
    }

    private suspend fun persist(): Boolean {
        if (!loaded) return false
        val bytes = LibraryFormat.encode(data)
        return safeSave.save(storeFile, BytesSaveSource(bytes)) is SaveResult.Saved
    }

    /** Reads the store once. Unreadable data is copied to [damagedFile] and the library starts empty. */
    private suspend fun load() {
        if (loaded) return
        val read = access.readStream(storeFile) { it.readBytes(MAX_STORE_BYTES) }
        val bytes =
            when (read) {
                is FileResult.Success -> {
                    read.value
                }

                is FileResult.Failure -> {
                    // Not found: first start. Anything else: stay unloaded (and unwritten) so the file is not lost.
                    if (read.error == FileError.NotFound) loaded = true
                    return
                }
            }
        if (bytes.isEmpty()) {
            loaded = true
            return
        }
        val decoded = LibraryFormat.decode(bytes)
        if (decoded != null) {
            data = decoded
            loaded = true
        } else if (safeSave.saveAsCopy(damagedFile, BytesSaveSource(bytes), overwrite = true) is SaveResult.Saved) {
            data = LibraryData()
            loaded = true
        }
    }

    private class BytesSaveSource(
        private val bytes: ByteArray,
    ) : SaveSource {
        override suspend fun writeTo(sink: ByteSink) = sink.write(bytes)
    }

    companion object {
        /** Unpinned recent entries kept; the oldest beyond this is dropped. */
        const val MAX_UNPINNED = 100

        private const val MAX_STORE_BYTES = 4 * 1024 * 1024
    }
}
