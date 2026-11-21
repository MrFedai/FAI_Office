/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import org.faioffice.core.files.FileRef
import org.faioffice.core.files.GrantSync
import org.faioffice.core.files.PersistedGrants
import org.faioffice.core.library.LibraryStore
import org.faioffice.core.library.syncGrants

/**
 * The recent files and folder library ([store]) with their persisted grants kept in line: every change that adds or
 * removes an entry syncs the grants ([syncGrants]), so a file kept in recents or an added folder keeps its grant and
 * a removed one gives it back; over the platform limit the least important entries lose theirs. Mutators return
 * whether the change reached the disk, like [LibraryStore]; when it did not, grants are only taken, never released
 * (the store may not have loaded, so its list may be incomplete). The screens (P3.8) call these instead of the store.
 */
class FileLibrary(
    val store: LibraryStore,
    private val grants: PersistedGrants,
) {
    /** Keeps an opened file in recent files (when it was read and "Remember recent files" is on). */
    suspend fun keepRecent(request: OpenRequest): Boolean {
        val info = request.info
        val detection = request.detection
        if (info == null || detection == null) return false
        val saved = store.recordOpened(request.ref, info.name, detection.format, info.size)
        syncGrants(releaseUnwanted = saved)
        return saved
    }

    /** Adds a folder; true when its grant is held afterwards (it stays reachable after a restart). */
    suspend fun addFolder(
        ref: FileRef,
        name: String,
    ): Boolean {
        val sync = syncGrants(releaseUnwanted = store.addFolder(ref, name))
        return ref !in sync.failed && ref !in sync.dropped
    }

    suspend fun remove(ref: FileRef): Boolean = store.remove(ref).also { syncGrants(releaseUnwanted = it) }

    suspend fun removeFolder(ref: FileRef): Boolean = store.removeFolder(ref).also { syncGrants(releaseUnwanted = it) }

    suspend fun setPinned(
        ref: FileRef,
        pinned: Boolean,
    ): Boolean = store.setPinned(ref, pinned).also { syncGrants(releaseUnwanted = it) }

    suspend fun clearRecents(): Boolean = store.clearRecents().also { syncGrants(releaseUnwanted = it) }

    suspend fun setRememberRecent(enabled: Boolean): Boolean =
        store.setRememberRecent(enabled).also { syncGrants(releaseUnwanted = it) }

    /** Brings the grants in line with the library; [releaseUnwanted] false only takes (see the class comment). */
    suspend fun syncGrants(releaseUnwanted: Boolean = true): GrantSync = store.syncGrants(grants, releaseUnwanted)
}
