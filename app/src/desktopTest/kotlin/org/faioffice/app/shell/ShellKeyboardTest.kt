/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.shell

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import org.faioffice.app.App
import org.faioffice.app.i18n.AppLanguage
import org.faioffice.app.theme.ThemeMode
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Keyboard on the desktop layout (1920 x 950): Tab moves focus through top bar and side navigation, Enter / Space
 * select, Esc and Alt+Left go back, Esc at Home does nothing.
 * Focus order on Home: content pane (focused at start) -> five file-type tiles -> theme -> settings -> side
 * navigation Home -> Recent Files -> Documents ...
 */
class ShellKeyboardTest {
    private val systemLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(systemLocale)

    // Synthetic key events: the plain KeyEvent factory is internal to compose-ui (test only, same pinned version).
    @OptIn(InternalComposeUiApi::class)
    private fun ImageComposeScene.press(
        key: Key,
        alt: Boolean = false,
    ) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, isAltPressed = alt))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp, isAltPressed = alt))
        render()
    }

    @Test
    fun tabEnterSpaceAndBackKeys() {
        val nav = ShellNavigator()
        val scene =
            ImageComposeScene(1920, 950, Density(1f)) {
                App(ThemeMode.Light, AppLanguage.English, navigator = nav)
            }
        try {
            scene.render()
            repeat(9) { scene.press(Key.Tab) }
            scene.press(Key.Enter)
            assertEquals(Destination.RecentFiles, nav.current, "Tab x9 + Enter selects Recent Files")

            scene.press(Key.Tab)
            scene.press(Key.Spacebar)
            assertEquals(Destination.Documents, nav.current, "Tab + Space selects Documents")

            scene.press(Key.Escape)
            assertEquals(Destination.RecentFiles, nav.current, "Esc goes back")
            scene.press(Key.DirectionLeft, alt = true)
            assertEquals(Destination.Home, nav.current, "Alt+Left goes back")
            scene.press(Key.Escape)
            assertEquals(Destination.Home, nav.current, "Esc at Home stays (system handles back)")
        } finally {
            scene.close()
        }
    }
}
