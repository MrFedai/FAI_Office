/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.faioffice.core.AppInfo

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = AppInfo.NAME,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            App()
            BudgetProbe()
        }
    }
