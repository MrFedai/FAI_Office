/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalLayoutDirection
import java.util.Locale

actual object LocalAppLocale {
    private var systemDefault: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val system = systemDefault ?: Locale.getDefault().also { systemDefault = it }
        Locale.setDefault(if (value == null) system else Locale.forLanguageTag(value))
        return LocalLayoutDirection provides LocalLayoutDirection.current
    }
}
