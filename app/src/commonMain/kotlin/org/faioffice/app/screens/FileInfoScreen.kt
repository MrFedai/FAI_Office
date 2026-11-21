/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import org.faioffice.app.files.FileItem
import org.faioffice.app.files.OpenRequest
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.action_open_other_app
import org.faioffice.app.resources.action_pick_again
import org.faioffice.app.resources.action_remove_from_recent
import org.faioffice.app.resources.action_show_in_folder
import org.faioffice.app.resources.info_compat
import org.faioffice.app.resources.info_damaged_body
import org.faioffice.app.resources.info_damaged_title
import org.faioffice.app.resources.info_family
import org.faioffice.app.resources.info_format
import org.faioffice.app.resources.info_location
import org.faioffice.app.resources.info_missing_body
import org.faioffice.app.resources.info_missing_title
import org.faioffice.app.resources.info_modified
import org.faioffice.app.resources.info_name
import org.faioffice.app.resources.info_ok_title
import org.faioffice.app.resources.info_permission_body
import org.faioffice.app.resources.info_permission_title
import org.faioffice.app.resources.info_protected_body
import org.faioffice.app.resources.info_protected_title
import org.faioffice.app.resources.info_reading
import org.faioffice.app.resources.info_size
import org.faioffice.app.resources.info_unknown
import org.faioffice.app.resources.info_unreadable_body
import org.faioffice.app.resources.info_unreadable_title
import org.faioffice.app.resources.info_unsupported_body
import org.faioffice.app.resources.info_unsupported_title
import org.faioffice.app.shell.FaiIcon
import org.faioffice.app.shell.FaiIcons
import org.faioffice.app.shell.Hairline
import org.faioffice.app.shell.PrimaryButton
import org.faioffice.app.shell.SecondaryButton
import org.faioffice.app.shell.ShellLayout
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.faioffice.core.files.EntryKind
import org.faioffice.core.files.FileError
import org.faioffice.core.files.FileRef
import org.faioffice.core.formats.FileFormat
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the information page reports about a file. */
enum class InfoState {
    /** Not read yet. */
    Loading,

    /** Read and recognised (or an unknown format): the compatibility sentence. */
    Ok,

    /** Gone: moved, renamed, deleted, drive not connected. */
    Missing,

    /** The grant or the folder permission is gone: pick the file again. */
    PermissionLost,

    /** A truncated or malformed container. */
    Damaged,

    /** Encrypted content. */
    Protected,

    /** Locked by another program or an I/O error. */
    Unreadable,
    ;

    companion object {
        fun of(request: OpenRequest?): InfoState {
            if (request == null) return Loading
            val detection = request.detection
            return when {
                request.error == FileError.NotFound || request.error is FileError.WrongKind -> Missing
                request.error == FileError.PermissionLost -> PermissionLost
                request.error != null || detection == null -> Unreadable
                detection.passwordProtected -> Protected
                detection.damaged -> Damaged
                else -> Ok
            }
        }
    }
}

/** Everything the page shows, from the read [request] and, when it failed, the recent entry. */
private class InfoView(
    val ref: FileRef,
    val request: OpenRequest?,
    val recent: FileItem?,
) {
    val state = InfoState.of(request)
    val name: String? = request?.info?.name ?: recent?.name
    val format: FileFormat? = request?.detection?.format ?: recent?.format
    val size: Long? = request?.info?.size ?: recent?.size
    val modified: Long? = request?.info?.modifiedEpochMillis
    val mimeType: String? = request?.info?.mimeType ?: format?.mimeType
    val isFile: Boolean = request?.info?.kind == EntryKind.File
}

/**
 * File information page (P3.8, DESIGN.md section 6): header (badge, name, location), the state card (recognised and
 * the phase of its viewer, or missing / access lost / damaged / password protected / unreadable), the actions (Open in
 * another app, Show in folder, Remove from recent) and the details. Two columns on side-navigation layouts.
 * The navigator keeps [ref] across process death; the file is then read again ([org.faioffice.app.files.FilesModel.ensureOpened]).
 */
@Composable
fun FileInfoScreen(
    scope: ScreenScope,
    ref: FileRef,
) {
    val files = scope.files
    LaunchedEffect(ref) { files.ensureOpened(ref) }
    val view = InfoView(ref, files.opened?.takeIf { it.ref == ref }, files.recents.firstOrNull { it.ref == ref })
    if (scope.layout.wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(scope.gap), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(scope.gap)) {
                InfoHeader(scope.layout, view)
                StateCard(scope, view)
                InfoActions(scope, view)
            }
            Column(Modifier.weight(1f)) { Details(scope.layout, view) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(FaiSpace.s12)) {
            InfoHeader(scope.layout, view)
            StateCard(scope, view)
            InfoActions(scope, view)
            Details(scope.layout, view)
        }
    }
}

private fun ShellLayout.padX() = if (this == ShellLayout.Phone) FaiSpace.s12 else FaiSpace.cardPad

@Composable
private fun InfoHeader(
    layout: ShellLayout,
    view: InfoView,
) {
    val colors = FaiTheme.colors
    Section(layout, title = null) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = layout.padX(), vertical = FaiSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FaiSpace.s16),
        ) {
            FileBadge(view.format ?: FileFormat.Unknown, large = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaiSpace.s4)) {
                BasicText(
                    view.name ?: stringResource(Res.string.info_unknown),
                    style = FaiTheme.type.pageTitle.copy(color = colors.text),
                )
                BasicText(shortLocation(view.ref), style = FaiTheme.type.small.copy(color = colors.muted))
            }
        }
    }
}

/** Icon, colour, title and sentence of a state. */
private class StateLook(
    val icon: ImageVector,
    val color: Color,
    val title: StringResource,
    val body: StringResource,
    /** Recognised format named after the title ("Recognised · PDF"). */
    val format: FileFormat? = null,
)

@Composable
private fun lookOf(view: InfoView): StateLook? {
    val c = FaiTheme.colors
    return when (view.state) {
        InfoState.Loading -> {
            null
        }

        InfoState.Ok -> {
            val phase = viewerPhaseText(view.format ?: FileFormat.Unknown)
            if (phase == null) {
                StateLook(
                    FaiIcons.circleAlert,
                    c.warn,
                    Res.string.info_unsupported_title,
                    Res.string.info_unsupported_body,
                )
            } else {
                StateLook(FaiIcons.circleCheck, c.pos, Res.string.info_ok_title, phase, view.format)
            }
        }

        InfoState.Missing -> {
            StateLook(FaiIcons.circleAlert, c.neg, Res.string.info_missing_title, Res.string.info_missing_body)
        }

        InfoState.PermissionLost -> {
            StateLook(FaiIcons.lock, c.warn, Res.string.info_permission_title, Res.string.info_permission_body)
        }

        InfoState.Damaged -> {
            StateLook(FaiIcons.triangleAlert, c.warn, Res.string.info_damaged_title, Res.string.info_damaged_body)
        }

        InfoState.Protected -> {
            StateLook(FaiIcons.lock, c.warn, Res.string.info_protected_title, Res.string.info_protected_body)
        }

        InfoState.Unreadable -> {
            StateLook(FaiIcons.circleAlert, c.neg, Res.string.info_unreadable_title, Res.string.info_unreadable_body)
        }
    }
}

/** The state card: icon and title in the status colour (always with the word), one sentence, the fix if there is one. */
@Composable
private fun StateCard(
    scope: ScreenScope,
    view: InfoView,
) {
    val layout = scope.layout
    val colors = FaiTheme.colors
    val bodyStyle = if (layout == ShellLayout.Phone) FaiTheme.type.bodyMobile else FaiTheme.type.body
    Section(layout, title = null) {
        val look = lookOf(view)
        if (look == null) {
            SectionNote(layout, stringResource(Res.string.info_reading))
            return@Section
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = layout.padX(), vertical = FaiSpace.s12),
            horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12),
        ) {
            FaiIcon(look.icon, look.color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaiSpace.s6)) {
                val title = stringResource(look.title)
                val named = look.format?.let { "$title · ${formatName(it)}" } ?: title
                BasicText(named, style = FaiTheme.type.cardTitle.copy(color = look.color))
                BasicText(stringResource(look.body), style = bodyStyle.copy(color = colors.text2))
                if (view.state == InfoState.PermissionLost) {
                    Row(Modifier.padding(top = FaiSpace.s4)) {
                        PrimaryButton(
                            stringResource(Res.string.action_pick_again),
                            FaiIcons.folderOpen,
                            layout.controlHeight,
                        ) {
                            scope.files.openFiles()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoActions(
    scope: ScreenScope,
    view: InfoView,
) {
    val files = scope.files
    val actions = files.actions
    val height = scope.layout.controlHeight
    val readable =
        view.state != InfoState.Missing && view.state != InfoState.PermissionLost && view.state != InfoState.Loading
    val openOther = actions.canOpenInOtherApp && readable && view.isFile
    val showFolder = actions.canShowInFolder && view.state != InfoState.Missing && view.state != InfoState.Loading
    val removable = view.recent != null
    if (!openOther && !showFolder && !removable) return
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
        verticalArrangement = Arrangement.spacedBy(FaiSpace.s8),
    ) {
        if (openOther) {
            SecondaryButton(stringResource(Res.string.action_open_other_app), FaiIcons.externalLink, height, {
                actions.openInOtherApp(view.ref, view.mimeType)
            })
        }
        if (showFolder) {
            SecondaryButton(stringResource(Res.string.action_show_in_folder), FaiIcons.folder, height, {
                actions.showInFolder(view.ref)
            })
        }
        if (removable) {
            SecondaryButton(stringResource(Res.string.action_remove_from_recent), FaiIcons.trash, height, {
                files.remove(view.ref)
            })
        }
    }
}

@Composable
private fun Details(
    layout: ShellLayout,
    view: InfoView,
) {
    val type = FaiTheme.type
    val colors = FaiTheme.colors
    val unknown = stringResource(Res.string.info_unknown)
    val language = currentLanguage()
    val text = (if (layout == ShellLayout.Phone) type.bodyMobile else type.body).copy(color = colors.text)
    val format = view.format
    val rows =
        listOf(
            Triple(Res.string.info_name, view.name ?: unknown, text),
            Triple(Res.string.info_location, fullLocation(view.ref), text),
            Triple(Res.string.info_size, sizeText(view.size) ?: unknown, type.number.copy(color = colors.text)),
            Triple(
                Res.string.info_modified,
                view.modified?.let { dateText(it, language, withTime = true) } ?: unknown,
                type.numberSmall.copy(color = colors.text),
            ),
            Triple(Res.string.info_format, format?.let { formatName(it) } ?: unknown, text),
            Triple(Res.string.info_family, format?.let { stringResource(it.family.label) } ?: unknown, text),
            Triple(
                Res.string.info_compat,
                format?.takeIf { view.state != InfoState.Loading }?.let { stringResource(compatibilityText(it)) }
                    ?: unknown,
                text,
            ),
        )
    Section(layout, title = null) {
        rows.forEachIndexed { i, (label, value, style) ->
            if (i > 0) Hairline()
            DetailRow(layout, stringResource(label), value, style)
        }
    }
}

@Composable
private fun DetailRow(
    layout: ShellLayout,
    label: String,
    value: String,
    style: TextStyle,
) {
    val labelStyle = FaiTheme.type.small.copy(color = FaiTheme.colors.muted)
    val padding = Modifier.fillMaxWidth().padding(horizontal = layout.padX(), vertical = FaiSpace.s10)
    if (layout == ShellLayout.Phone) {
        // Label above the value: a long name or path keeps the full width.
        Column(padding, verticalArrangement = Arrangement.spacedBy(FaiSpace.s2)) {
            BasicText(label, style = labelStyle)
            BasicText(value, style = style)
        }
    } else {
        Row(padding, horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12)) {
            BasicText(label, style = labelStyle, modifier = Modifier.width(FaiSize.detailLabel))
            BasicText(value, style = style, modifier = Modifier.weight(1f))
        }
    }
}
