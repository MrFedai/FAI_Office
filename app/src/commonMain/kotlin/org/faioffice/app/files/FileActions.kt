/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileRef

/**
 * The platform's file dialogs and hand-offs, without UI of their own in the app: the screens (P3.8) bind their
 * buttons to these. Results do not come back here: picked files arrive on [FileHub.events] as [OpenOrigin.Picker],
 * a chosen folder as [FileEvent.FolderAdded]. Each call returns false when the platform could not show the dialog
 * (no picker app, not available on this platform, no window attached yet).
 */
interface FileActions {
    /** Whether [addFolder] is available here (Android, desktop; not the minimal Web). */
    val canAddFolder: Boolean

    /** Shows the open dialog for one or more documents of the detected formats. */
    fun openFiles(): Boolean

    /** Shows the choose-folder dialog; the folder joins the library with a lasting grant. */
    fun addFolder(): Boolean

    /** Offers [ref] to another app with read access only (Android: a chooser with a read grant). */
    fun openInOtherApp(
        ref: FileRef,
        mimeType: String?,
    ): Boolean
}

/** No dialogs (tests, a platform before its P3.5 / P3.6 implementation). */
object NoFileActions : FileActions {
    override val canAddFolder: Boolean = false

    override fun openFiles(): Boolean = false

    override fun addFolder(): Boolean = false

    override fun openInOtherApp(
        ref: FileRef,
        mimeType: String?,
    ): Boolean = false
}

/**
 * Everything the screens need for files on one platform: [access] to read, [hub] for opened files and added
 * folders, [actions] for the dialogs, [library] for recent files and folders (null where the platform has none yet).
 */
class FileServices(
    val access: FileAccess,
    val hub: FileHub,
    val actions: FileActions,
    val library: FileLibrary?,
)
