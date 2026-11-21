/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.action_choose_file
import org.faioffice.app.resources.action_open_archive
import org.faioffice.app.resources.action_open_document
import org.faioffice.app.resources.action_open_file
import org.faioffice.app.resources.action_open_pdf
import org.faioffice.app.resources.action_open_presentation
import org.faioffice.app.resources.action_open_spreadsheet
import org.faioffice.app.resources.destination_settings
import org.faioffice.app.resources.empty_body_archive
import org.faioffice.app.resources.empty_body_documents
import org.faioffice.app.resources.empty_body_file_converter
import org.faioffice.app.resources.empty_body_home
import org.faioffice.app.resources.empty_body_pdf
import org.faioffice.app.resources.empty_body_presentations
import org.faioffice.app.resources.empty_body_recent_files
import org.faioffice.app.resources.empty_body_settings
import org.faioffice.app.resources.empty_body_spreadsheets
import org.faioffice.app.resources.empty_title_archive
import org.faioffice.app.resources.empty_title_documents
import org.faioffice.app.resources.empty_title_file_converter
import org.faioffice.app.resources.empty_title_home
import org.faioffice.app.resources.empty_title_pdf
import org.faioffice.app.resources.empty_title_presentations
import org.faioffice.app.resources.empty_title_recent_files
import org.faioffice.app.resources.empty_title_spreadsheets
import org.faioffice.app.resources.status_in_development
import org.faioffice.app.theme.FaiRadius
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Empty state of a destination: short [title], one-sentence [body] and, where it fits, the primary [action]. */
class EmptyStateContent(
    val title: StringResource,
    val body: StringResource,
    val action: StringResource?,
)

/** Empty state of each destination; Settings has no primary action. */
val Destination.emptyState: EmptyStateContent
    get() =
        when (this) {
            Destination.Home -> {
                EmptyStateContent(
                    Res.string.empty_title_home,
                    Res.string.empty_body_home,
                    Res.string.action_open_file,
                )
            }

            Destination.RecentFiles -> {
                EmptyStateContent(
                    Res.string.empty_title_recent_files,
                    Res.string.empty_body_recent_files,
                    Res.string.action_open_file,
                )
            }

            Destination.Documents -> {
                EmptyStateContent(
                    Res.string.empty_title_documents,
                    Res.string.empty_body_documents,
                    Res.string.action_open_document,
                )
            }

            Destination.Presentations -> {
                EmptyStateContent(
                    Res.string.empty_title_presentations,
                    Res.string.empty_body_presentations,
                    Res.string.action_open_presentation,
                )
            }

            Destination.Spreadsheets -> {
                EmptyStateContent(
                    Res.string.empty_title_spreadsheets,
                    Res.string.empty_body_spreadsheets,
                    Res.string.action_open_spreadsheet,
                )
            }

            Destination.Pdf -> {
                EmptyStateContent(
                    Res.string.empty_title_pdf,
                    Res.string.empty_body_pdf,
                    Res.string.action_open_pdf,
                )
            }

            Destination.FileConverter -> {
                EmptyStateContent(
                    Res.string.empty_title_file_converter,
                    Res.string.empty_body_file_converter,
                    Res.string.action_choose_file,
                )
            }

            Destination.Archive -> {
                EmptyStateContent(
                    Res.string.empty_title_archive,
                    Res.string.empty_body_archive,
                    Res.string.action_open_archive,
                )
            }

            Destination.Settings -> {
                // Settings shows its controls instead (P3.8); kept so every destination has an empty state.
                EmptyStateContent(
                    Res.string.destination_settings,
                    Res.string.empty_body_settings,
                    null,
                )
            }
        }

/**
 * Empty-state card (DESIGN.md section 6): large destination badge in its meaning colour, title, one sentence, then
 * the primary action, centred on a bordered surface ([modifier] may give it a minimum height; the content stays
 * centred). With [onAction] the action works and [secondary] (for example "Add folder") may follow it; without it the
 * action is drawn disabled next to the "in development" chip (File Converter until Phase 6).
 */
@Composable
fun EmptyState(
    destination: Destination,
    layout: ShellLayout,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    secondary: (@Composable () -> Unit)? = null,
) {
    val content = destination.emptyState
    val colors = FaiTheme.colors
    val phone = layout == ShellLayout.Phone
    val shape = RoundedCornerShape(FaiRadius.card)
    val center = TextAlign.Center
    val bodyStyle = if (phone) FaiTheme.type.bodyMobile else FaiTheme.type.body
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(FaiSize.border, colors.border, shape)
            .padding(horizontal = if (phone) FaiSpace.s16 else FaiSpace.s32, vertical = FaiSpace.s32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FaiSpace.s12, Alignment.CenterVertically),
    ) {
        DestinationBadge(destination, large = true)
        BasicText(
            stringResource(content.title),
            style = FaiTheme.type.pageTitle.copy(color = colors.text, textAlign = center),
        )
        BasicText(
            stringResource(content.body),
            style = bodyStyle.copy(color = colors.text2, textAlign = center),
        )
        Row(
            Modifier.padding(top = FaiSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
        ) {
            val action = content.action
            if (action != null) {
                val touch = layout == ShellLayout.Phone || layout == ShellLayout.Medium
                PrimaryButton(
                    label = stringResource(action),
                    icon = FaiIcons.folderOpen,
                    height = if (touch) FaiSize.touchMin else FaiSize.control,
                    onClick = onAction,
                )
            }
            secondary?.invoke()
            if (onAction == null) StatusChip()
        }
    }
}

/** Honest "in development" chip (violet on its tint). */
@Composable
private fun StatusChip() {
    BasicText(
        stringResource(Res.string.status_in_development),
        style = FaiTheme.type.label.copy(color = FaiTheme.colors.violet),
        modifier =
            Modifier
                .background(FaiTheme.colors.violetSoft, RoundedCornerShape(FaiRadius.chip))
                .padding(horizontal = FaiSpace.s8, vertical = FaiSpace.s2),
    )
}
