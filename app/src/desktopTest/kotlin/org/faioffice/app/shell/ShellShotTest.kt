/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.shell

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.faioffice.app.App
import org.faioffice.app.i18n.AppLanguage
import org.faioffice.app.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Shell screenshots into build/bakis/: every destination screen (empty state) as <theme>_<size>_<screen>.png at
 * phone 390x844 and desktop 1920x950 (9 x 2 x 2 = 36), plus <theme>_<size>_shell.png with Home at the two middle
 * width classes (medium 700x900, compact desktop 1100x800). Light shots are Turkish (longer labels), dark shots
 * English, so both languages are looked at.
 */
class ShellShotTest {
    private val outDir = File(System.getProperty("bakis.dir") ?: "build/bakis")
    private val themes =
        listOf(
            Triple("acik", ThemeMode.Light, AppLanguage.Turkish),
            Triple("koyu", ThemeMode.Dark, AppLanguage.English),
        )
    private val screenSizes = listOf(390 to 844, 1920 to 950)
    private val shellSizes = listOf(700 to 900, 1100 to 800)
    private val systemLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(systemLocale)

    private fun shot(
        name: String,
        size: Pair<Int, Int>,
        mode: ThemeMode,
        language: AppLanguage,
        destination: Destination,
    ) {
        val nav = ShellNavigator().apply { navigate(destination) }
        val file = File(outDir, name)
        val scene = ImageComposeScene(size.first, size.second, Density(1f)) { App(mode, language, navigator = nav) }
        try {
            scene.render()
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)
            file.writeBytes(checkNotNull(png).bytes)
        } finally {
            scene.close()
        }
        assertTrue(file.length() > 0, "empty shot: $file")
    }

    /** File name part of a destination: RecentFiles -> recent_files. */
    private val Destination.screenName: String
        get() = name.replace(Regex("(?<=[a-z])(?=[A-Z])"), "_").lowercase(Locale.ROOT)

    @Test
    fun writesShellShots() {
        outDir.mkdirs()
        for ((themeName, mode, language) in themes) {
            for ((w, h) in screenSizes) {
                for (d in Destination.entries) {
                    shot("${themeName}_${w}x${h}_${d.screenName}.png", w to h, mode, language, d)
                }
            }
            for ((w, h) in shellSizes) {
                shot("${themeName}_${w}x${h}_shell.png", w to h, mode, language, Destination.Home)
            }
        }
    }
}
