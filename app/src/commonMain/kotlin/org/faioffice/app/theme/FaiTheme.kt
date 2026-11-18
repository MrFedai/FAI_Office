/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/** Theme choice. [System] follows the platform (Android 10+, desktop, browser) and is the default. */
enum class ThemeMode { System, Light, Dark }

/** The dark or the light palette. */
fun colorsFor(dark: Boolean): FaiColors = if (dark) FaiPalette.dark else FaiPalette.light

private val localFaiColors = staticCompositionLocalOf { FaiPalette.dark }
private val localFaiType = staticCompositionLocalOf { FaiTypography(FontFamily.SansSerif, FontFamily.Monospace) }

/** Tokens of the current theme; spacing, radius and sizes are theme independent ([FaiSpace], [FaiRadius]). */
object FaiTheme {
    val colors: FaiColors
        @Composable
        @ReadOnlyComposable
        get() = localFaiColors.current

    val type: FaiTypography
        @Composable
        @ReadOnlyComposable
        get() = localFaiType.current
}

/**
 * Provides the design tokens to [content]. Fonts: IBM Plex Sans and Mono once bundled (DESIGN.md, fonts);
 * until then the platform sans and monospace families.
 */
@Composable
fun FaiTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark =
        when (mode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val type = remember { FaiTypography(sans = FontFamily.SansSerif, mono = FontFamily.Monospace) }
    CompositionLocalProvider(
        localFaiColors provides colorsFor(dark),
        localFaiType provides type,
        content = content,
    )
}
