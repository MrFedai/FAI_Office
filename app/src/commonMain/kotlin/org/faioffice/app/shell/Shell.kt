/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.faioffice.app.theme.ThemeMode

/** Content scale and fade at the end of a predictive back gesture (transform + opacity only, TEMEL 6). */
private const val BACK_SCALE = 0.06f
private const val BACK_FADE = 0.2f

/** Alt+Left or Esc: back (desktop and Web). */
fun isBackKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.key == Key.Escape || (event.isAltPressed && event.key == Key.DirectionLeft))

/** Theme state of the shell: the top bar button changes it. */
class ShellTheme(
    val mode: ThemeMode,
    val onChange: (ThemeMode) -> Unit,
)

/**
 * The app shell: top bar, navigation per [ShellLayout] and the current destination. Back: [navigator] (Android
 * wires the system back to it), Alt+Left / Esc here. Focus rings show only after a key press.
 */
@Composable
fun Shell(
    navigator: ShellNavigator,
    theme: ShellTheme,
    touch: Boolean,
) {
    val keyboardMode = remember { mutableStateOf(false) }
    var navCollapsed by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(FaiTheme.colors.bg)
            .shellInput(navigator, keyboardMode)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val layout = shellLayoutFor(maxWidth, maxHeight, touch)
        val select: (Destination) -> Unit = navigator::navigate
        CompositionLocalProvider(LocalFocusRingVisible provides keyboardMode.value) {
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    TopBarState(layout, navigator.current, theme.mode, navCollapsed),
                    TopBarActions(
                        onThemeChange = theme.onChange,
                        onSettings = { navigator.navigate(Destination.Settings) },
                        onToggleNav = { navCollapsed = !navCollapsed },
                    ),
                )
                if (layout == ShellLayout.Phone) {
                    ShellContent(navigator, layout, Modifier.weight(1f))
                    BottomBar(navigator.current, select)
                } else {
                    Row(
                        Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                    ) {
                        val rail =
                            layout == ShellLayout.Medium || (layout == ShellLayout.CompactDesktop && navCollapsed)
                        if (rail) NavRail(navigator.current, select) else SideNav(navigator.current, select)
                        VerticalHairline()
                        ShellContent(navigator, layout, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Back keys, keyboard / pointer mode for the focus rings. */
private fun Modifier.shellInput(
    navigator: ShellNavigator,
    keyboardMode: MutableState<Boolean>,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) keyboardMode.value = true
        isBackKey(event) && navigator.back()
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) keyboardMode.value = false
            }
        }
    }

/**
 * Current destination. The pane takes focus at start so the back keys work before the first Tab; it draws no ring
 * (it is a region, not a control). Scales and fades with a predictive back gesture.
 */
@Composable
private fun ShellContent(
    navigator: ShellNavigator,
    layout: ShellLayout,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val phone = layout == ShellLayout.Phone
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = navigator.backProgress
                scaleX = 1f - BACK_SCALE * p
                scaleY = 1f - BACK_SCALE * p
                alpha = 1f - BACK_FADE * p
            }.focusRequester(focus)
            .focusable()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (phone) FaiSpace.mobilePad else FaiSpace.pagePadX,
                vertical = if (phone) FaiSpace.mobilePad else FaiSpace.pagePadY,
            ),
    ) {
        DestinationScreen(navigator.current, layout, navigator::navigate)
    }
}
