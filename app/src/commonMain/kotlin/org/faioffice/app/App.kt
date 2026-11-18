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
import org.faioffice.app.theme.FaiTheme
import org.faioffice.app.theme.ThemeMode
import org.faioffice.core.AppInfo

/** Root of the shell on every platform. Placeholder until navigation (P2.5); the theme follows the system. */
@Composable
fun App(themeMode: ThemeMode = ThemeMode.System) {
    FaiTheme(themeMode) {
        Box(Modifier.fillMaxSize().background(FaiTheme.colors.bg), contentAlignment = Alignment.Center) {
            BasicText(AppInfo.NAME, style = FaiTheme.type.pageTitle.copy(color = FaiTheme.colors.text))
        }
    }
}
