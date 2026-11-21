/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/** True when the primary pointer is coarse (finger): short windows then use the phone layout. */
private fun coarsePointer(): Boolean = js("window.matchMedia('(pointer: coarse)').matches")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val touch = coarsePointer()
    ComposeViewport { App(touch = touch) }
}
