/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.faioffice.app.files.WebFiles

/** True when the primary pointer is coarse (finger): short windows then use the phone layout. */
private fun coarsePointer(): Boolean = js("window.matchMedia('(pointer: coarse)').matches")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val touch = coarsePointer()
    // File layer (P3.6): picked files reach WebFiles.hub; the screens (P3.8) bind to WebFiles.services.
    val files = WebFiles()
    files.services
    ComposeViewport { App(touch = touch) }
}
