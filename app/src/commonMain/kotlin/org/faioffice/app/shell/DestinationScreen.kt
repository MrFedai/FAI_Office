/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import org.faioffice.app.files.FilesModel
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.home_open_by_type
import org.faioffice.app.screens.FamilyScreen
import org.faioffice.app.screens.HomeScreen
import org.faioffice.app.screens.RecentFilesScreen
import org.faioffice.app.screens.ScreenScope
import org.faioffice.app.screens.SettingsScreen
import org.faioffice.app.screens.family
import org.faioffice.app.theme.FaiAlpha
import org.faioffice.app.theme.FaiRadius
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.jetbrains.compose.resources.stringResource

/** Meaning colour of a destination: the file-type colour for file modules, primary for the rest. */
@Composable
fun Destination.accent(): Color {
    val c = FaiTheme.colors
    return when (this) {
        Destination.Documents -> c.fileDocument
        Destination.Spreadsheets -> c.fileSpreadsheet
        Destination.Presentations -> c.filePresentation
        Destination.Pdf -> c.filePdf
        Destination.Archive -> c.fileArchive
        Destination.FileConverter -> c.fileConverter
        else -> c.primaryInk
    }
}

/** Icon on a 14 % tint of the destination colour: file badge 36 / icon 20, or [large] 56 / 28 (empty state). */
@Composable
fun DestinationBadge(
    destination: Destination,
    large: Boolean = false,
) {
    val accent = destination.accent()
    Box(
        Modifier
            .size(if (large) FaiSize.emptyBadge else FaiSize.fileBadge)
            .background(
                accent.copy(alpha = FaiAlpha.FILE_TINT),
                RoundedCornerShape(if (large) FaiRadius.card else FaiRadius.control),
            ),
        contentAlignment = Alignment.Center,
    ) {
        FaiIcon(destination.icon, accent, size = if (large) FaiSize.iconLarge else FaiSize.icon)
    }
}

/**
 * Screen of a destination: Home, Recent Files, the five family screens and Settings show the file screens of P3.8;
 * File Converter keeps its empty state (Phase 6), filling the visible [pageHeight] so no blank page remains under it.
 */
@Composable
fun DestinationScreen(
    destination: Destination,
    layout: ShellLayout,
    pageHeight: Dp,
    files: FilesModel,
    navigator: ShellNavigator,
) {
    val scope = ScreenScope(files, navigator, layout, pageHeight)
    val family = destination.family
    when {
        destination == Destination.Home -> HomeScreen(scope)
        destination == Destination.RecentFiles -> RecentFilesScreen(scope)
        family != null -> FamilyScreen(scope, destination, family)
        destination == Destination.Settings -> SettingsScreen(scope)
        else -> EmptyState(destination, layout, Modifier.heightIn(min = pageHeight))
    }
}

/** File-type tiles: list rows on phone and medium, one row of equal tiles on desktop. */
@Composable
fun HomeTiles(
    layout: ShellLayout,
    onSelect: (Destination) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FaiSpace.s10)) {
        BasicText(
            stringResource(Res.string.home_open_by_type),
            style = FaiTheme.type.cardTitle.copy(color = FaiTheme.colors.text),
        )
        val wide = layout == ShellLayout.CompactDesktop || layout == ShellLayout.Desktop
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(FaiSpace.gap)) {
                for (d in Destination.HomeTiles) {
                    Box(Modifier.weight(1f)) { FileTypeTile(d, vertical = true) { onSelect(d) } }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FaiSpace.mobileGap)) {
                for (d in Destination.HomeTiles) FileTypeTile(d, vertical = false) { onSelect(d) }
            }
        }
    }
}

@Composable
private fun FileTypeTile(
    destination: Destination,
    vertical: Boolean,
    onSelect: () -> Unit,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.card)
    val colors = FaiTheme.colors
    val modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = FaiSize.touchMin)
            .focusRing(interaction, shape)
            .background(if (interaction.hovered) colors.surface2 else colors.surface, shape)
            .border(FaiSize.border, colors.border, shape)
            .activateOnKeys(onSelect)
            .clickable(interaction.source, null, role = Role.Button, onClick = onSelect)
            .padding(if (vertical) FaiSpace.cardPad else FaiSpace.s12)
    val label = @Composable {
        BasicText(stringResource(destination.title), style = FaiTheme.type.cardTitle.copy(color = colors.text))
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(FaiSpace.s12)) {
            DestinationBadge(destination)
            label()
        }
    } else {
        Row(
            modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12),
        ) {
            DestinationBadge(destination)
            label()
        }
    }
}
