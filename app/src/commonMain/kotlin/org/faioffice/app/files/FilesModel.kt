/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.faioffice.app.shell.ShellNavigator
import org.faioffice.core.files.FileError
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.getOrNull
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.ModuleFamily
import org.faioffice.core.library.EntryState
import org.faioffice.core.library.LibraryFolder
import org.faioffice.core.library.RecentEntry
import org.faioffice.core.library.scanFolder
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * One file on a list screen. [date] is when it was last opened (a recent file) or modified (a file found in an added
 * folder); [folder] names the added folder it was found in, null for a recent file.
 */
data class FileItem(
    val ref: FileRef,
    val name: String,
    val format: FileFormat,
    val size: Long?,
    val date: Long?,
    val pinned: Boolean,
    val inRecents: Boolean,
    val folder: String?,
    val state: EntryState = EntryState.Available,
) {
    val family: ModuleFamily get() = format.family
}

/** Sort order of a file list. */
enum class SortKey { Name, Date, Size }

data class FileSort(
    val key: SortKey = SortKey.Date,
    val descending: Boolean = true,
) {
    /** Choosing the selected key again flips the direction; a new key starts in its natural direction. */
    fun choose(next: SortKey): FileSort =
        if (next == key) copy(descending = !descending) else FileSort(next, descending = next != SortKey.Name)

    /** Sorted copy; ties keep the given order. Names compare without case ([fold] lowers them in the UI language). */
    fun apply(
        items: List<FileItem>,
        fold: (String) -> String,
    ): List<FileItem> {
        val comparator: Comparator<FileItem> =
            when (key) {
                SortKey.Name -> compareBy { fold(it.name) }
                SortKey.Date -> compareBy { it.date ?: Long.MIN_VALUE }
                SortKey.Size -> compareBy { it.size ?: -1L }
            }
        return items.sortedWith(if (descending) comparator.reversed() else comparator)
    }
}

/** Files of one family for its screen: [loading] while the added folders are read. */
data class FamilyListing(
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    val skippedFolders: Int = 0,
    val truncated: Boolean = false,
)

/**
 * State of the file screens (P3.8) over the UI-less file channel ([FileServices]): recent files with their state,
 * added folders, the "Remember recent files" switch, the files of each family screen and the open file. [run]
 * collects [FileHub.events] for the lifetime of the screens: every opened file is kept in recent files and its
 * information page shown through the [ShellNavigator].
 *
 * Without a library (minimal Web) recent files live in this object for the session only. Without [services] (tests,
 * previews) every action is a no-op. Changes run in [scope]; readers are snapshot state.
 */
@Stable
class FilesModel(
    val services: FileServices?,
    private val scope: CoroutineScope,
) {
    val actions: FileActions = services?.actions ?: NoFileActions
    private val library: FileLibrary? get() = services?.library

    /** Whether recent files and folders are kept on this platform (false: session only, no folders). */
    val keepsLibrary: Boolean get() = library != null

    var recents: List<FileItem> by mutableStateOf(emptyList())
        private set
    var folders: List<LibraryFolder> by mutableStateOf(emptyList())
        private set
    var rememberRecent: Boolean by mutableStateOf(true)
        private set

    /** The open file: what [FileHub.resolve] found for it (null while it is read). */
    var opened: OpenRequest? by mutableStateOf(null)
        private set

    /** The last folder that could not be added, with its error; shown in Settings until the next success. */
    var folderError: FileError? by mutableStateOf(null)
        private set

    private val families = mutableStateMapOf<ModuleFamily, FamilyListing>()
    private val pending = mutableSetOf<FileRef>()
    private var session: List<RecentEntry> = emptyList()

    /** The listing of [family] as last loaded ([loadFamily]). */
    fun family(family: ModuleFamily): FamilyListing = families[family] ?: FamilyListing()

    /** Whether [ref] is in recent files. */
    fun inRecents(ref: FileRef): Boolean = recents.any { it.ref == ref }

    /** Loads the library, then handles file events until cancelled. */
    suspend fun run(navigator: ShellNavigator) {
        refresh()
        services?.hub?.events?.collect { handle(it, navigator) }
    }

    /** Re-reads recent files (each state checked now), folders and the switch. */
    suspend fun refresh() {
        val lib = library
        if (lib == null) {
            recents = session.map { it.toItem(EntryState.Available) }
            return
        }
        recents = lib.store.recentsWithState().map { it.entry.toItem(it.state) }
        folders = lib.store.folders()
        rememberRecent = lib.store.rememberRecent()
    }

    /** One event of the file channel. */
    suspend fun handle(
        event: FileEvent,
        navigator: ShellNavigator,
    ) {
        when (event) {
            is FileEvent.Opened -> {
                val request = event.request
                keep(request)
                pending -= request.ref
                opened = request
                navigator.openFile(request.ref)
                refresh()
                families.clear()
            }

            is FileEvent.FolderAdded -> {
                folderError = null
                refresh()
                families.clear()
            }

            is FileEvent.FolderFailed -> {
                folderError = event.error
            }
        }
    }

    /** Shows [ref]'s information page now and reads it again (a recent file or a file of an added folder). */
    fun open(
        ref: FileRef,
        navigator: ShellNavigator,
    ) {
        val hub = services?.hub ?: return
        if (opened?.ref != ref) opened = null
        pending += ref
        navigator.openFile(ref)
        hub.open(listOf(ref), OpenOrigin.Library)
    }

    /**
     * Makes sure the page of [ref] has its data: after Android re-created the process the navigator still names the
     * file, so it is read again from its reference (a lost grant then shows as such).
     */
    suspend fun ensureOpened(ref: FileRef) {
        if (opened?.ref == ref || ref in pending) return
        val hub = services?.hub ?: return
        opened = hub.resolve(ref, OpenOrigin.Library)
    }

    /** Files of [family] from recent files and every added folder, recents first; folders are read now. */
    suspend fun loadFamily(family: ModuleFamily) {
        val fromRecents = recents.filter { it.family == family }
        val lib = library
        val access = services?.access
        if (lib == null || access == null || folders.isEmpty()) {
            families[family] = FamilyListing(fromRecents)
            return
        }
        families[family] = family(family).copy(items = fromRecents, loading = true)
        val known = fromRecents.mapTo(mutableSetOf()) { it.ref }
        val found = mutableListOf<FileItem>()
        var skipped = 0
        var truncated = false
        for (folder in folders) {
            val scan = access.scanFolder(folder.ref, family).getOrNull()
            if (scan == null) {
                skipped++
                continue
            }
            skipped += scan.skippedFolders
            truncated = truncated || scan.truncated
            for (file in scan.files) {
                if (!known.add(file.info.ref)) continue
                found +=
                    FileItem(
                        ref = file.info.ref,
                        name = file.info.name,
                        format = file.format,
                        size = file.info.size,
                        date = file.info.modifiedEpochMillis,
                        pinned = false,
                        inRecents = false,
                        folder = folder.name,
                    )
            }
        }
        families[family] = FamilyListing(fromRecents + found, loading = false, skipped, truncated)
    }

    fun openFiles(): Boolean = actions.openFiles()

    fun addFolder(): Boolean = actions.addFolder()

    fun setPinned(
        ref: FileRef,
        pinned: Boolean,
    ) = change {
        library?.setPinned(ref, pinned)
            ?: run { session = session.map { if (it.ref == ref) it.copy(pinned = pinned) else it } }
    }

    fun remove(ref: FileRef) =
        change {
            library?.remove(ref) ?: run { session = session.filter { it.ref != ref } }
        }

    fun clearRecents() =
        change {
            library?.clearRecents() ?: run { session = emptyList() }
        }

    fun switchRememberRecent(enabled: Boolean) = change { library?.setRememberRecent(enabled) }

    fun removeFolder(ref: FileRef) = change { library?.removeFolder(ref) }

    private fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            refresh()
            families.clear()
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun keep(request: OpenRequest) {
        val lib = library
        if (lib != null) {
            lib.keepRecent(request)
            return
        }
        val info = request.info ?: return
        val detection = request.detection ?: return
        val old = session.firstOrNull { it.ref == request.ref }
        val entry =
            RecentEntry(
                request.ref,
                info.name,
                detection.format,
                info.size,
                Clock.System.now().toEpochMilliseconds(),
                old?.pinned ?: false,
            )
        session = (listOf(entry) + session.filter { it.ref != request.ref }).take(SESSION_LIMIT)
    }

    private fun RecentEntry.toItem(state: EntryState) =
        FileItem(ref, name, format, size, lastOpenedEpochMillis, pinned, inRecents = true, folder = null, state = state)

    private companion object {
        const val SESSION_LIMIT = 100
    }
}

/** The file screens' state for [services], kept across recomposition; changes run in the composition's scope. */
@Composable
fun rememberFilesModel(services: FileServices?): FilesModel {
    val scope = rememberCoroutineScope()
    return remember(services) { FilesModel(services, scope) }
}
