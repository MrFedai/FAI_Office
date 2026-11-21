/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import org.faioffice.core.files.FileRef
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.ModuleFamily

/** One recent file. [format] comes from detection when it was opened; [family] follows from it. */
data class RecentEntry(
    val ref: FileRef,
    val name: String,
    val format: FileFormat,
    val size: Long?,
    val lastOpenedEpochMillis: Long,
    val pinned: Boolean,
) {
    val family: ModuleFamily get() = format.family
}

/** A folder the user added to the library. */
data class LibraryFolder(
    val ref: FileRef,
    val name: String,
    val addedEpochMillis: Long,
)

/** Whether a stored reference can be used right now; checked when shown, never in the background. */
enum class EntryState { Available, Missing, PermissionLost }

/** A recent entry with the state found when it was checked. */
data class RecentStatus(
    val entry: RecentEntry,
    val state: EntryState,
)

/** The whole content of the store file. */
internal data class LibraryData(
    val rememberRecent: Boolean = true,
    val recents: List<RecentEntry> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
)
