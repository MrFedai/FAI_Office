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
 * Shell screenshots into build/bakis/<theme>_<size>_shell.png at the four width classes, Home selected; plus
 * <theme>_<size>_shell_pdf.png with PDF selected at phone and desktop size. Light shots are Turkish (longer
 * labels), dark shots English, so both languages are looked at.
 */
class ShellShotTest {
    private val outDir = File(System.getProperty("bakis.dir") ?: "build/bakis")
    private val themes =
        listOf(
            Triple("acik", ThemeMode.Light, AppLanguage.Turkish),
            Triple("koyu", ThemeMode.Dark, AppLanguage.English),
        )
    private val sizes = listOf(390 to 844, 700 to 900, 1100 to 800, 1920 to 950)
    private val pdfSizes = listOf(390 to 844, 1920 to 950)
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

    @Test
    fun writesShellShots() {
        outDir.mkdirs()
        for ((themeName, mode, language) in themes) {
            for ((w, h) in sizes) {
                shot("${themeName}_${w}x${h}_shell.png", w to h, mode, language, Destination.Home)
            }
            for ((w, h) in pdfSizes) {
                shot("${themeName}_${w}x${h}_shell_pdf.png", w to h, mode, language, Destination.Pdf)
            }
        }
    }
}
