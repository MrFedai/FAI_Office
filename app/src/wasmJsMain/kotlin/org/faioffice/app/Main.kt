/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.faioffice.app.files.WebFiles
import org.faioffice.app.files.rememberFilesModel

/** True when the primary pointer is coarse (finger): short windows then use the phone layout. */
private fun coarsePointer(): Boolean = js("window.matchMedia('(pointer: coarse)').matches")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val touch = coarsePointer()
    // File layer (P3.6): picked files reach WebFiles.hub; the file screens (P3.8) bind to its services.
    val files = WebFiles()
    ComposeViewport { App(touch = touch, files = rememberFilesModel(files.services)) }
}
