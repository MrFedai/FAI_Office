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
import org.faioffice.app.i18n.AppLanguage
import org.faioffice.app.i18n.AppLanguageProvider
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.app_name
import org.faioffice.app.theme.FaiTheme
import org.faioffice.app.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

/**
 * Root of the shell on every platform. Placeholder until navigation (P2.5); the theme and the language follow the
 * system unless overridden.
 */
@Composable
fun App(
    themeMode: ThemeMode = ThemeMode.System,
    language: AppLanguage = AppLanguage.System,
) {
    AppLanguageProvider(language) {
        FaiTheme(themeMode) {
            Box(Modifier.fillMaxSize().background(FaiTheme.colors.bg), contentAlignment = Alignment.Center) {
                BasicText(
                    stringResource(Res.string.app_name),
                    style = FaiTheme.type.pageTitle.copy(color = FaiTheme.colors.text),
                )
            }
        }
    }
}
