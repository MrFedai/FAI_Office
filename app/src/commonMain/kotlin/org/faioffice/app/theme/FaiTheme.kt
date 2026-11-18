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
import androidx.compose.ui.text.font.FontWeight
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.ibm_plex_mono_medium
import org.faioffice.app.resources.ibm_plex_mono_regular
import org.faioffice.app.resources.ibm_plex_sans_bold
import org.faioffice.app.resources.ibm_plex_sans_medium
import org.faioffice.app.resources.ibm_plex_sans_regular
import org.faioffice.app.resources.ibm_plex_sans_semibold
import org.jetbrains.compose.resources.Font

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

/** IBM Plex Sans, bundled subset (DESIGN.md section 4, fonts). */
@Composable
private fun plexSans(): FontFamily =
    FontFamily(
        Font(Res.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_sans_medium, FontWeight.Medium),
        Font(Res.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
        Font(Res.font.ibm_plex_sans_bold, FontWeight.Bold),
    )

/** IBM Plex Mono, bundled subset, for numbers (sizes, counts, dates). */
@Composable
private fun plexMono(): FontFamily =
    FontFamily(
        Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
    )

/** Provides the design tokens and the bundled IBM Plex fonts to [content]. */
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
    val sans = plexSans()
    val mono = plexMono()
    val type = remember(sans, mono) { FaiTypography(sans = sans, mono = mono) }
    CompositionLocalProvider(
        localFaiColors provides colorsFor(dark),
        localFaiType provides type,
        content = content,
    )
}
