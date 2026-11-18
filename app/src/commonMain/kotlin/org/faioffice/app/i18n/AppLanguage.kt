/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Interface language. [System] follows the platform language and is the default; [tag] is the BCP 47 override. */
enum class AppLanguage(
    val tag: String?,
) {
    System(null),
    English("en"),
    Turkish("tr"),
}

/** Platform hook for the language override (Compose Multiplatform "custom locale" pattern). */
expect object LocalAppLocale {
    /** Language tag currently in effect. */
    val current: String
        @Composable get

    /** Applies [value] (null = system language) and returns the provider that makes resources recompose. */
    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/** Holds the chosen [AppLanguage]; the Settings screen (P2.5) reads and sets it. */
class AppLanguageState(
    initial: AppLanguage,
) {
    var language: AppLanguage by mutableStateOf(initial)
}

val LocalAppLanguageState = staticCompositionLocalOf { AppLanguageState(AppLanguage.System) }

/** Keeps the language state and provides it with the locale override to [content]. */
@Composable
fun AppLanguageProvider(
    initial: AppLanguage = AppLanguage.System,
    content: @Composable () -> Unit,
) {
    val state = remember { AppLanguageState(initial) }
    CompositionLocalProvider(
        LocalAppLanguageState provides state,
        LocalAppLocale provides state.language.tag,
    ) {
        // Recreate the subtree so every string resource is read again with the new locale.
        key(state.language) { content() }
    }
}
