/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.faioffice.core.files.EntryKind
import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileError
import org.faioffice.core.files.FileInfo
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.FileResult
import org.faioffice.core.formats.FormatDetection
import org.faioffice.core.formats.detectFormat
import kotlin.time.TimeSource

/** Where a request to open a file came from. */
enum class OpenOrigin {
    /** The app's own "Open file" picker ([FileActions.openFiles]). */
    Picker,

    /** The system's "Open with" (Android `ACTION_VIEW`, a desktop file association). */
    OpenWith,

    /** Shared to the app (Android `ACTION_SEND` / `ACTION_SEND_MULTIPLE`). */
    Share,

    /** A recent file or a file in a library folder, chosen inside the app. */
    Library,

    /** Desktop: command line, drag and drop, or a second launch handing its files over (P3.5). */
    Desktop,
}

/**
 * A file the user asked to open, resolved off the main thread: the platform's metadata ([info]) and the detected
 * format ([detection]), or the [error] that stopped either (for example [FileError.PermissionLost]).
 */
data class OpenRequest(
    val ref: FileRef,
    val origin: OpenOrigin,
    val info: FileInfo?,
    val detection: FormatDetection?,
    val error: FileError?,
)

/** One folder listing: its entry count and how long the platform took ([measure][FileHub.measureFolders]). */
data class FolderListing(
    val ref: FileRef,
    val entries: Int,
    val millis: Long,
)

/** What the file layer reports to the UI. */
sealed interface FileEvent {
    data class Opened(
        val request: OpenRequest,
    ) : FileEvent

    /** A folder chosen with [FileActions.addFolder]; [grantKept]: it stays reachable after a restart. */
    data class FolderAdded(
        val ref: FileRef,
        val name: String,
        val listing: FolderListing,
        val grantKept: Boolean,
    ) : FileEvent

    data class FolderFailed(
        val ref: FileRef,
        val error: FileError,
    ) : FileEvent
}

/**
 * The UI-less channel between the platforms and the screens: every way of opening a file (picker, "Open with",
 * share, library, desktop command line and drag and drop) calls [open], and a chosen folder [addFolder]; the
 * screens collect [events] (the file information page, P3.8). Work runs one item at a time in [scope], in arrival
 * order, so files shared together arrive together. Events wait in an unbounded buffer until collected, so a file that
 * arrives before the first screen is composed (cold start from "Open with") is not lost; [events] is meant for one
 * collector, each event is delivered once.
 *
 * [library] (optional) keeps added folders and their grants; [log] receives one line per open and per listing with
 * its time (Android logcat, see AndroidFiles).
 */
class FileHub(
    private val access: FileAccess,
    scope: CoroutineScope,
    private val library: FileLibrary? = null,
    private val log: (String) -> Unit = {},
) {
    private sealed interface Work {
        data class Open(
            val ref: FileRef,
            val origin: OpenOrigin,
        ) : Work

        data class AddFolder(
            val ref: FileRef,
        ) : Work
    }

    private val inbox = Channel<Work>(Channel.UNLIMITED)
    private val outbox = Channel<FileEvent>(Channel.UNLIMITED)

    /** Opened files and added folders, in order; one collector. */
    val events: Flow<FileEvent> = outbox.receiveAsFlow()

    init {
        scope.launch {
            for (work in inbox) {
                val event =
                    when (work) {
                        is Work.Open -> FileEvent.Opened(resolve(work.ref, work.origin))
                        is Work.AddFolder -> folderAdded(work.ref)
                    }
                outbox.send(event)
            }
        }
    }

    /** Queues [refs] for opening; each becomes one [FileEvent.Opened]. */
    fun open(
        refs: List<FileRef>,
        origin: OpenOrigin,
    ) {
        refs.forEach { inbox.trySend(Work.Open(it, origin)) }
    }

    /** Queues a chosen folder: added to the [library] (grant taken), listed, reported as [FileEvent.FolderAdded]. */
    fun addFolder(ref: FileRef) {
        inbox.trySend(Work.AddFolder(ref))
    }

    /** Metadata and format of [ref], read now (bounded reads, never the whole file). */
    suspend fun resolve(
        ref: FileRef,
        origin: OpenOrigin,
    ): OpenRequest {
        val start = TimeSource.Monotonic.markNow()
        val info =
            when (val result = access.info(ref)) {
                is FileResult.Success -> result.value
                is FileResult.Failure -> return OpenRequest(ref, origin, null, null, result.error)
            }
        val request =
            if (info.kind != EntryKind.File) {
                OpenRequest(ref, origin, info, null, FileError.WrongKind(EntryKind.File))
            } else {
                when (val read = access.readRandom(ref) { detectFormat(it, info.name, info.mimeType) }) {
                    is FileResult.Success -> OpenRequest(ref, origin, info, read.value, null)
                    is FileResult.Failure -> OpenRequest(ref, origin, info, null, read.error)
                }
            }
        val outcome = request.detection?.format?.name ?: request.error.toString()
        log("open ${origin.name}: $outcome, ${info.size ?: "?"} bytes in ${start.elapsedNow().inWholeMilliseconds} ms")
        return request
    }

    /** Lists [folder] once and reports its entry count and time (the 1 000-file check of P3.4). */
    suspend fun measure(folder: FileRef): FileResult<FolderListing> {
        val start = TimeSource.Monotonic.markNow()
        return when (val listed = access.list(folder)) {
            is FileResult.Success -> {
                val listing = FolderListing(folder, listed.value.size, start.elapsedNow().inWholeMilliseconds)
                log("list ${listing.entries} entries in ${listing.millis} ms")
                FileResult.Success(listing)
            }

            is FileResult.Failure -> {
                log("list failed: ${listed.error}")
                listed
            }
        }
    }

    /** Lists every folder of the [library] once, logging each time; for measuring on a device. */
    suspend fun measureFolders(): List<FileResult<FolderListing>> =
        library
            ?.store
            ?.folders()
            .orEmpty()
            .map { measure(it.ref) }

    private suspend fun folderAdded(ref: FileRef): FileEvent =
        when (val info = access.info(ref)) {
            is FileResult.Failure -> {
                FileEvent.FolderFailed(ref, info.error)
            }

            is FileResult.Success -> {
                when (val measured = measure(ref)) {
                    is FileResult.Failure -> {
                        FileEvent.FolderFailed(ref, measured.error)
                    }

                    is FileResult.Success -> {
                        val name = info.value.name
                        FileEvent.FolderAdded(ref, name, measured.value, library?.addFolder(ref, name) ?: false)
                    }
                }
            }
        }
}
