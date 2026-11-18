/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalLayoutDirection

@JsFun("() => navigator.language")
private external fun browserLanguage(): String

@JsFun(
    "(tag) => { Object.defineProperty(navigator, 'language', { value: tag, configurable: true });" +
        " Object.defineProperty(navigator, 'languages', { value: [tag], configurable: true }); }",
)
private external fun overrideBrowserLanguage(tag: String)

actual object LocalAppLocale {
    private var systemTag: String? = null

    actual val current: String
        @Composable get() = browserLanguage()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val system = systemTag ?: browserLanguage().also { systemTag = it }
        overrideBrowserLanguage(value ?: system)
        return LocalLayoutDirection provides LocalLayoutDirection.current
    }
}
