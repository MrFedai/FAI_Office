/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.preview

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.faioffice.app.theme.FaiTheme
import org.faioffice.app.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the token preview, light and dark, at phone and desktop size into build/bakis/<theme>_<size>_tokens.png. */
class TokenShotTest {
    private val outDir = File(System.getProperty("bakis.dir") ?: "build/bakis")
    private val themes = listOf("acik" to ThemeMode.Light, "koyu" to ThemeMode.Dark)
    private val sizes = listOf(390 to 844, 1920 to 950)

    @Test
    fun writesTokenPreviewShots() {
        outDir.mkdirs()
        for ((themeName, mode) in themes) {
            for ((width, height) in sizes) {
                val file = File(outDir, "${themeName}_${width}x${height}_tokens.png")
                val label = if (mode == ThemeMode.Dark) "Koyu · Dark" else "Açık · Light"
                val scene = ImageComposeScene(width, height, Density(1f)) { FaiTheme(mode) { TokenPreview(label) } }
                try {
                    val png = scene.render().encodeToData(EncodedImageFormat.PNG)
                    file.writeBytes(checkNotNull(png).bytes)
                } finally {
                    scene.close()
                }
                assertTrue(file.length() > 0, "empty shot: $file")
            }
        }
    }
}
