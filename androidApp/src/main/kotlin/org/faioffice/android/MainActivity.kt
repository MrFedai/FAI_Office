/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.faioffice.app.App
import org.faioffice.app.shell.rememberShellNavigator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
}
