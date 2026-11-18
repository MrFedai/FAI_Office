/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** WCAG 2 contrast: every text colour on every surface, text on fills and on tints, both themes (TEMEL 2). */
class ContrastTest {
    private val themes = mapOf("dark" to FaiPalette.dark, "light" to FaiPalette.light)

    @Test
    fun everyTextColourReachesTheMinimumOnEverySurface() {
        val failures =
            themes.flatMap { (theme, colors) ->
                val pairs =
                    colors.textColors.flatMap { (text, fg) ->
                        colors.surfaces.map { (surface, bg) -> "$text on $surface" to contrast(fg, bg) }
                    } +
                        colors.onFill.map { (name, pair) -> name to contrast(pair.first, pair.second) } +
                        colors.onTint.flatMap { (name, pair) ->
                            colors.surfaces.map { (surface, bg) ->
                                "$name over $surface" to contrast(pair.first, pair.second.compositeOver(bg))
                            }
                        }
                pairs.filter { it.second < CONTRAST_MINIMUM }.map { "$theme: ${it.first} = ${"%.2f".format(it.second)}" }
            }
        assertTrue(failures.isEmpty(), "below $CONTRAST_MINIMUM:1\n" + failures.joinToString("\n"))
    }

    @Test
    fun bothThemesCheckTheSameRoles() {
        assertEquals(FaiPalette.dark.textColors.keys, FaiPalette.light.textColors.keys)
        assertEquals(FaiPalette.dark.surfaces.keys, FaiPalette.light.surfaces.keys)
        assertTrue(FaiPalette.dark.fileTypes.values.all { it in FaiPalette.dark.textColors.values })
    }

    @Test
    fun contrastFormulaMatchesWcag() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.001)
        assertEquals(1.0, contrast(Color.White, Color.White), 0.001)
    }
}

/** WCAG 2 relative luminance of an opaque sRGB colour. */
internal fun luminance(color: Color): Double {
    fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

internal fun contrast(
    a: Color,
    b: Color,
): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
