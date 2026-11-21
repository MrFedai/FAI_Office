/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.faioffice.app.files.DesktopDrop
import org.faioffice.app.files.DesktopFiles
import org.faioffice.core.AppInfo

fun main(args: Array<String>) {
    // Before any window: a second launch hands its files to the running app and ends here (P3.5).
    val files = DesktopFiles.start(args) ?: return
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = AppInfo.NAME,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            DisposableEffect(window) {
                files.attach(window)
                onDispose { files.detach(window) }
            }
            Box(Modifier.fillMaxSize().fileDrop(files)) { App() }
            CdsArchiveAfterFirstFrame()
            BudgetProbe()
        }
    }
}

/** Files dropped anywhere on the window go to the file hub; nothing is drawn for the drag. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
private fun Modifier.fileDrop(files: DesktopFiles): Modifier =
    dragAndDropTarget(
        shouldStartDragAndDrop = { DesktopDrop.accepts(it.awtTransferable) },
        target =
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean = files.drop(event.awtTransferable)
            },
    )
