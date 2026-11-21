/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.faioffice.app.theme.FaiRadius
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.jetbrains.compose.resources.stringResource

/** Colours of a navigation entry: selected = primarySoft tint + primaryInk (DESIGN.md section 6). */
private class EntryColors(
    val fill: Color,
    val content: Color,
    val icon: Color,
)

@Composable
private fun entryColors(
    selected: Boolean,
    hovered: Boolean,
): EntryColors {
    val c = FaiTheme.colors
    return when {
        selected -> EntryColors(c.primarySoft, c.primaryInk, c.primaryInk)
        hovered -> EntryColors(c.surface2, c.text, c.text2)
        else -> EntryColors(Color.Transparent, c.text2, c.muted)
    }
}

/** Selectable navigation entry: tab role, Enter / Space, focus ring on [shape]. */
@Composable
private fun Modifier.navEntry(
    selected: Boolean,
    interaction: Interaction,
    onSelect: () -> Unit,
): Modifier =
    activateOnKeys(onSelect)
        .selectable(
            selected = selected,
            interactionSource = interaction.source,
            indication = null,
            role = Role.Tab,
            onClick = onSelect,
        )

/** Side navigation (232): all nine destinations in spec order, Settings pinned last. */
@Composable
fun SideNav(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(FaiSize.sideNav)
            .fillMaxHeight()
            .background(FaiTheme.colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(FaiSpace.s8),
        verticalArrangement = Arrangement.spacedBy(FaiSpace.s2),
    ) {
        for (destination in Destination.entries) {
            if (destination == Destination.Settings) Spacer(Modifier.height(FaiSpace.s12))
            SideNavEntry(destination, destination == current) { onSelect(destination) }
        }
    }
}

@Composable
private fun SideNavEntry(
    destination: Destination,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.control)
    val colors = entryColors(selected, interaction.hovered)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FaiSize.control)
            .focusRing(interaction, shape)
            .background(colors.fill, shape)
            .navEntry(selected, interaction, onSelect)
            .padding(horizontal = FaiSpace.s10, vertical = FaiSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s10),
    ) {
        FaiIcon(destination.icon, colors.icon)
        BasicText(
            stringResource(destination.title),
            style = (if (selected) FaiTheme.type.button else FaiTheme.type.body).copy(color = colors.content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Side rail (72): icon in a pill + short label; scrolls when the window is short. */
@Composable
fun NavRail(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(FaiSize.sideNavRail)
            .fillMaxHeight()
            .background(FaiTheme.colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = FaiSpace.s8),
        verticalArrangement = Arrangement.spacedBy(FaiSpace.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (destination in Destination.entries) {
            if (destination == Destination.Settings) Spacer(Modifier.height(FaiSpace.s8))
            Box(Modifier.fillMaxWidth().padding(horizontal = FaiSpace.s4)) {
                StackedEntry(destination, destination == current) { onSelect(destination) }
            }
        }
    }
}

/** Phone bottom bar (68 + safe area): the five [Destination.PhoneTabs]. */
@Composable
fun BottomBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTab = current.phoneTab
    Column(modifier.fillMaxWidth().background(FaiTheme.colors.surface)) {
        Hairline()
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .fillMaxWidth()
                .height(FaiSize.mobileTabBar - FaiSize.border)
                .padding(horizontal = FaiSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (destination in Destination.PhoneTabs) {
                BottomBarSlot(destination, destination == selectedTab, onSelect)
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarSlot(
    destination: Destination,
    selected: Boolean,
    onSelect: (Destination) -> Unit,
) {
    Box(Modifier.weight(1f).padding(horizontal = FaiSpace.s2)) {
        StackedEntry(destination, selected) { onSelect(destination) }
    }
}

/** Icon in a pill over a short label: rail and bottom bar entry, at least a touch target tall. */
@Composable
private fun StackedEntry(
    destination: Destination,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.control)
    val colors = entryColors(selected, interaction.hovered)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FaiSize.touchMin)
            .focusRing(interaction, shape)
            .navEntry(selected, interaction, onSelect)
            .padding(vertical = FaiSpace.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FaiSpace.s4),
    ) {
        Box(
            Modifier
                .width(FaiSize.fileBadge + FaiSpace.s16)
                .height(FaiSize.icon + FaiSpace.s8)
                .background(colors.fill, RoundedCornerShape(FaiRadius.chip)),
            contentAlignment = Alignment.Center,
        ) {
            FaiIcon(destination.icon, colors.icon)
        }
        BasicText(
            stringResource(destination.shortLabel),
            style = FaiTheme.type.label.copy(color = colors.content, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One-line divider in the border colour. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(FaiSize.border).background(FaiTheme.colors.border))
}

/** Vertical divider in the border colour. */
@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(modifier.width(FaiSize.border).fillMaxHeight().background(FaiTheme.colors.border))
}
