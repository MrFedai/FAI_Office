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
import org.faioffice.app.files.SampleFiles
import org.faioffice.app.i18n.AppLanguage
import org.faioffice.app.theme.ThemeMode
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Keyboard through the Recent Files list on the desktop layout (1920 x 950) with the sample library: Tab reaches
 * the first row after the seven filter chips and the three sort segments, Down / Up move from row to row, Enter opens
 * the focused file's information page, Esc closes it again. Rows are sorted newest first: budget, deck, lease.
 */
class FileListKeyboardTest {
    private val systemLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(systemLocale)

    @OptIn(InternalComposeUiApi::class)
    private fun ImageComposeScene.press(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        render()
    }

    @Test
    fun arrowsMoveThroughRowsAndEnterOpens() {
        val sample = SampleFiles()
        val model = sample.model()
        val nav = ShellNavigator().apply { navigate(Destination.RecentFiles) }
        val scene =
            ImageComposeScene(1920, 950, Density(1f)) {
                App(ThemeMode.Light, AppLanguage.English, navigator = nav, files = model)
            }
        try {
            scene.render()
            repeat(11) { scene.press(Key.Tab) }
            scene.press(Key.DirectionDown)
            scene.press(Key.DirectionDown)
            scene.press(Key.Enter)
            assertEquals(sample.refs["lease"], nav.openedFile, "Tab x11, Down x2, Enter opens the third row")
            assertEquals(Destination.RecentFiles, nav.current)

            scene.press(Key.Escape)
            assertNull(nav.openedFile, "Esc closes the information page")
            assertEquals(Destination.RecentFiles, nav.current)
        } finally {
            scene.close()
        }
    }

    @Test
    fun upGoesBackARow() {
        val sample = SampleFiles()
        val model = sample.model()
        val nav = ShellNavigator().apply { navigate(Destination.RecentFiles) }
        val scene =
            ImageComposeScene(1920, 950, Density(1f)) {
                App(ThemeMode.Light, AppLanguage.English, navigator = nav, files = model)
            }
        try {
            scene.render()
            repeat(11) { scene.press(Key.Tab) }
            scene.press(Key.DirectionDown)
            scene.press(Key.DirectionDown)
            scene.press(Key.DirectionUp)
            scene.press(Key.Spacebar)
            assertEquals(sample.refs["deck"], nav.openedFile, "Down x2, Up, Space opens the second row")
        } finally {
            scene.close()
        }
    }
}
