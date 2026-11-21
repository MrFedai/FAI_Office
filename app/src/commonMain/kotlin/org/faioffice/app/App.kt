/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.faioffice.app.i18n.AppLanguage
import org.faioffice.app.i18n.AppLanguageProvider
import org.faioffice.app.shell.Shell
import org.faioffice.app.shell.ShellNavigator
import org.faioffice.app.shell.ShellTheme
import org.faioffice.app.shell.rememberShellNavigator
import org.faioffice.app.theme.FaiTheme
import org.faioffice.app.theme.ThemeMode

/**
 * Root of the shell on every platform. [themeMode] and [language] are the starting values (default: follow the
 * system); the top bar theme button changes the theme. [navigator] is created by the caller when the platform wires
 * its own back (Android); it lives above the language provider so a language switch keeps the destination.
 * [touch] marks a touch-first window (Android, coarse-pointer Web): short touch windows use the phone layout.
 */
@Composable
fun App(
    themeMode: ThemeMode = ThemeMode.System,
    language: AppLanguage = AppLanguage.System,
    navigator: ShellNavigator = rememberShellNavigator(),
    touch: Boolean = false,
) {
    var mode by remember { mutableStateOf(themeMode) }
    AppLanguageProvider(language) {
        FaiTheme(mode) {
            Shell(navigator, ShellTheme(mode) { mode = it }, touch)
        }
    }
}
