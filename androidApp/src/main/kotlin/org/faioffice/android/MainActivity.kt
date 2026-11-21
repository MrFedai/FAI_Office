/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.android

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import org.faioffice.app.App
import org.faioffice.app.files.AndroidFiles
import org.faioffice.app.shell.rememberShellNavigator

class MainActivity :
    ComponentActivity(),
    AndroidFiles.Host {
    private lateinit var files: AndroidFiles

    // SAF pickers (P3.4): registered before the activity starts, results go to the process-wide file layer.
    private val openDocuments =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files.onDocumentsPicked(it) }
    private val openFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { files.onFolderPicked(it) }

    override val component: ComponentName get() = componentName

    override fun pickDocuments(mimeTypes: Array<String>) = openDocuments.launch(mimeTypes)

    override fun pickFolder() = openFolder.launch(null)

    override fun start(intent: Intent) = startActivity(intent)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        files = AndroidFiles.get(this)
        files.attach(this)
        // A re-created activity gets its old intent again; it was handled the first time.
        if (savedInstanceState == null) files.onIntent(intent)
        setContent {
            val navigator = rememberShellNavigator()
            // Predictive back: the gesture progress scales the content; release goes back, cancel restores.
            // Disabled at the root (Home), so the system handles back there (back-to-home animation, exit).
            PredictiveBackHandler(enabled = navigator.canGoBack) { progress ->
                try {
                    progress.collect { navigator.backProgress = it.progress }
                    navigator.back()
                } finally {
                    navigator.backProgress = 0f
                }
            }
            App(navigator = navigator, touch = true)
        }
    }

    /** singleTask: "Open with" and "Share" while the app runs arrive here instead of a second window. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        files.onIntent(intent)
    }

    override fun onDestroy() {
        files.detach(this)
        super.onDestroy()
    }
}
