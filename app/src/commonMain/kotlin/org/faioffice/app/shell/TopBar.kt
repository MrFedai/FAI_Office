/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.action_back
import org.faioffice.app.resources.app_name
import org.faioffice.app.resources.destination_settings
import org.faioffice.app.resources.info_title
import org.faioffice.app.resources.nav_collapse
import org.faioffice.app.resources.nav_expand
import org.faioffice.app.resources.theme_dark
import org.faioffice.app.resources.theme_light
import org.faioffice.app.resources.theme_system
import org.faioffice.app.resources.theme_toggle
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.faioffice.app.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

/** Next theme of the top bar button: System -> Light -> Dark -> System. */
fun ThemeMode.next(): ThemeMode =
    when (this) {
        ThemeMode.System -> ThemeMode.Light
        ThemeMode.Light -> ThemeMode.Dark
        ThemeMode.Dark -> ThemeMode.System
    }

/** What the top bar shows and does; one object keeps the composable signatures short. */
class TopBarState(
    val layout: ShellLayout,
    val current: Destination,
    val themeMode: ThemeMode,
    val navCollapsed: Boolean,
    /** A file information page is open: back button and its title instead of the destination's. */
    val fileOpen: Boolean = false,
)

/** Actions of the top bar. */
class TopBarActions(
    val onThemeChange: (ThemeMode) -> Unit,
    val onSettings: () -> Unit,
    val onToggleNav: () -> Unit,
    val onBack: () -> Unit = {},
)

/**
 * Top bar (52 + status bar inset): on side-navigation layouts a brand block as wide as the navigation (with the
 * collapse toggle on compact desktop), then the current destination title, theme and settings.
 */
@Composable
fun TopBar(
    state: TopBarState,
    actions: TopBarActions,
) {
    val phone = state.layout == ShellLayout.Phone
    val target = if (phone) FaiSize.touchMin else FaiSize.control
    Column(Modifier.fillMaxWidth().background(FaiTheme.colors.surface)) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .fillMaxWidth()
                .height(FaiSize.desktopTopBar - FaiSize.border)
                .padding(end = if (phone) FaiSpace.s4 else FaiSpace.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FaiSpace.s4),
        ) {
            NavHeader(state, actions)
            if (state.fileOpen) {
                Box(Modifier.padding(start = if (phone) FaiSpace.s4 else FaiSpace.s8)) {
                    IconButton(
                        FaiIcons.arrowLeft,
                        stringResource(Res.string.action_back),
                        target,
                        onClick = actions.onBack,
                    )
                }
            }
            BasicText(
                stringResource(if (state.fileOpen) Res.string.info_title else state.current.title),
                style = FaiTheme.type.pageTitle.copy(color = FaiTheme.colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(
                            start =
                                if (state.fileOpen) {
                                    FaiSpace.s4
                                } else if (phone) {
                                    FaiSpace.mobilePad
                                } else {
                                    FaiSpace.pagePadX
                                },
                        ),
            )
            ThemeButton(state.themeMode, target) { actions.onThemeChange(state.themeMode.next()) }
            IconButton(
                icon = FaiIcons.settings,
                label = stringResource(Res.string.destination_settings),
                touchTarget = target,
                selected = state.current == Destination.Settings,
                onClick = actions.onSettings,
            )
        }
        Hairline()
    }
}

/** Width of the brand block: matches the navigation column below it. */
private fun headerWidth(state: TopBarState): Dp? =
    when (state.layout) {
        ShellLayout.Phone, ShellLayout.Medium -> null
        ShellLayout.CompactDesktop -> if (state.navCollapsed) FaiSize.sideNavRail else FaiSize.sideNav
        ShellLayout.Desktop -> FaiSize.sideNav
    }

@Composable
private fun NavHeader(
    state: TopBarState,
    actions: TopBarActions,
) {
    val width = headerWidth(state) ?: return
    val compact = state.layout == ShellLayout.CompactDesktop
    Row(
        Modifier.width(width).fillMaxHeight().padding(horizontal = FaiSpace.s16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
    ) {
        if (compact) {
            IconButton(
                icon = if (state.navCollapsed) FaiIcons.panelLeftOpen else FaiIcons.panelLeftClose,
                label = stringResource(if (state.navCollapsed) Res.string.nav_expand else Res.string.nav_collapse),
                touchTarget = FaiSize.control,
                onClick = actions.onToggleNav,
            )
        }
        if (!(compact && state.navCollapsed)) {
            BasicText(
                stringResource(Res.string.app_name),
                style = FaiTheme.type.cardTitle.copy(color = FaiTheme.colors.text),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ThemeButton(
    mode: ThemeMode,
    target: Dp,
    onClick: () -> Unit,
) {
    val (icon, name) =
        when (mode) {
            ThemeMode.System -> FaiIcons.monitor to Res.string.theme_system
            ThemeMode.Light -> FaiIcons.sun to Res.string.theme_light
            ThemeMode.Dark -> FaiIcons.moon to Res.string.theme_dark
        }
    IconButton(
        icon = icon,
        label = stringResource(Res.string.theme_toggle, stringResource(name)),
        touchTarget = target,
        onClick = onClick,
    )
}
