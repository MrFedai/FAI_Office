/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.faioffice.core.AppInfo

/** Root of the shell on every platform. Placeholder until the design system (P2.4) and navigation (P2.5). */
@Composable
fun App() {
    Box(Modifier.fillMaxSize().background(Color(0xFF111418)), contentAlignment = Alignment.Center) {
        BasicText(AppInfo.NAME, style = TextStyle(color = Color(0xFFE6E8EB)))
    }
}
