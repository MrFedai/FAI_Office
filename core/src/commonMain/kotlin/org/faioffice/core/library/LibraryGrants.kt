/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import org.faioffice.core.files.FileRef
import org.faioffice.core.files.GrantSync
import org.faioffice.core.files.PersistedGrants
import org.faioffice.core.files.sync

/**
 * The references the library wants grants for, most important first: added folders (oldest first), then pinned
 * recent files, then the other recent files, newest first. Over the platform limit the tail loses its grant.
 */
fun wantedGrants(
    recents: List<RecentEntry>,
    folders: List<LibraryFolder>,
): List<FileRef> {
    val (pinned, unpinned) = recents.sortedByDescending { it.lastOpenedEpochMillis }.partition { it.pinned }
    return folders.sortedBy { it.addedEpochMillis }.map { it.ref } + pinned.map { it.ref } + unpinned.map { it.ref }
}

/**
 * Takes the grants the library's recent files and folders need and releases the others ([wantedGrants]). Call it
 * after every change of the library (a file kept in recents, a folder added, an entry removed, recents cleared or
 * turned off). Pass [releaseUnwanted] false when the change did not reach the disk: the in-memory library may then be
 * incomplete, and a grant released by mistake cannot be taken back without the user picking the file again.
 */
suspend fun LibraryStore.syncGrants(
    grants: PersistedGrants,
    releaseUnwanted: Boolean = true,
): GrantSync = grants.sync(wantedGrants(recents(), folders()), releaseUnwanted)
