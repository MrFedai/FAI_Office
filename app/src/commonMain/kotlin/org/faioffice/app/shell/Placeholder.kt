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
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.home_open_by_type
import org.faioffice.app.resources.status_in_development
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

/** Icon on a 14 % tint of the destination colour (file badge, 36). */
@Composable
fun DestinationBadge(destination: Destination) {
    val accent = destination.accent()
    Box(
        Modifier
            .size(FaiSize.fileBadge)
            .background(accent.copy(alpha = FaiAlpha.FILE_TINT), RoundedCornerShape(FaiRadius.control)),
        contentAlignment = Alignment.Center,
    ) {
        FaiIcon(destination.icon, accent)
    }
}

/**
 * Placeholder of a destination until its screen exists (P2.5 second half): title, one-line description and an
 * honest "in development" chip. Home also shows the file-type tiles, the phone's way to Presentations,
 * Spreadsheets and Archive.
 */
@Composable
fun DestinationPlaceholder(
    destination: Destination,
    layout: ShellLayout,
    onSelect: (Destination) -> Unit,
) {
    val gap = if (layout == ShellLayout.Phone) FaiSpace.s16 else FaiSpace.s24
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        PlaceholderHeader(destination)
        if (destination == Destination.Home) HomeTiles(layout, onSelect)
    }
}

@Composable
private fun PlaceholderHeader(destination: Destination) {
    Row(horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12)) {
        DestinationBadge(destination)
        Column(verticalArrangement = Arrangement.spacedBy(FaiSpace.s4), modifier = Modifier.weight(1f)) {
            BasicText(
                stringResource(destination.title),
                style = FaiTheme.type.pageTitle.copy(color = FaiTheme.colors.text),
            )
            BasicText(
                stringResource(destination.description),
                style = FaiTheme.type.bodyMobile.copy(color = FaiTheme.colors.text2),
            )
            StatusChip()
        }
    }
}

@Composable
private fun StatusChip() {
    Box(Modifier.padding(top = FaiSpace.s4)) {
        BasicText(
            stringResource(Res.string.status_in_development),
            style = FaiTheme.type.label.copy(color = FaiTheme.colors.violet),
            modifier =
                Modifier
                    .background(FaiTheme.colors.violetSoft, RoundedCornerShape(FaiRadius.chip))
                    .padding(horizontal = FaiSpace.s8, vertical = FaiSpace.s2),
        )
    }
}

/** File-type tiles: list rows on phone and medium, one row of equal tiles on desktop. */
@Composable
private fun HomeTiles(
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
