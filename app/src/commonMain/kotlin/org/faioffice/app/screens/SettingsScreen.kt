/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.action_add_folder
import org.faioffice.app.resources.action_clear
import org.faioffice.app.resources.action_remove_folder
import org.faioffice.app.resources.empty_body_settings
import org.faioffice.app.resources.error_not_found
import org.faioffice.app.resources.error_other
import org.faioffice.app.resources.error_permission
import org.faioffice.app.resources.folder_failed
import org.faioffice.app.resources.home_recent_title
import org.faioffice.app.resources.settings_appearance_title
import org.faioffice.app.resources.settings_clear
import org.faioffice.app.resources.settings_clear_body
import org.faioffice.app.resources.settings_folders_body
import org.faioffice.app.resources.settings_folders_empty
import org.faioffice.app.resources.settings_folders_title
import org.faioffice.app.resources.settings_folders_web
import org.faioffice.app.resources.settings_remember
import org.faioffice.app.resources.settings_remember_body
import org.faioffice.app.resources.settings_session_recent
import org.faioffice.app.shell.FaiIcon
import org.faioffice.app.shell.FaiIcons
import org.faioffice.app.shell.Hairline
import org.faioffice.app.shell.IconButton
import org.faioffice.app.shell.SecondaryButton
import org.faioffice.app.shell.ShellLayout
import org.faioffice.app.shell.ToggleSwitch
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.faioffice.core.files.FileError
import org.faioffice.core.library.LibraryFolder
import org.jetbrains.compose.resources.stringResource

/**
 * Settings (P3.8): recent files (remember switch, clear) and added folders (list, add, remove); language and theme
 * stay a note until their controls arrive. Two columns on side-navigation layouts, one on phone.
 */
@Composable
fun SettingsScreen(scope: ScreenScope) {
    val layout = scope.layout
    if (layout.wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(scope.gap), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(scope.gap)) {
                RecentSettings(scope)
                AppearanceNote(layout)
            }
            Column(Modifier.weight(1f)) { FolderSettings(scope) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(scope.gap)) {
            RecentSettings(scope)
            FolderSettings(scope)
            AppearanceNote(layout)
        }
    }
}

/** A setting: title and one sentence, its control at the end. */
@Composable
private fun SettingRow(
    layout: ShellLayout,
    title: String,
    body: String,
    control: @Composable () -> Unit,
) {
    val colors = FaiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal =
                    if (layout ==
                        ShellLayout.Phone
                    ) {
                        FaiSpace.s12
                    } else {
                        FaiSpace.cardPad
                    },
                vertical = FaiSpace.s10,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaiSpace.s2)) {
            val style = if (layout == ShellLayout.Phone) FaiTheme.type.bodyMobile else FaiTheme.type.body
            BasicText(title, style = style.copy(color = colors.text))
            BasicText(body, style = FaiTheme.type.small.copy(color = colors.text2))
        }
        control()
    }
}

@Composable
private fun RecentSettings(scope: ScreenScope) {
    val layout = scope.layout
    val files = scope.files
    Section(layout, stringResource(Res.string.home_recent_title)) {
        if (files.keepsLibrary) {
            val title = stringResource(Res.string.settings_remember)
            SettingRow(layout, title, stringResource(Res.string.settings_remember_body)) {
                ToggleSwitch(files.rememberRecent, title) { files.switchRememberRecent(it) }
            }
        } else {
            SectionNote(layout, stringResource(Res.string.settings_session_recent))
        }
        Hairline()
        SettingRow(layout, stringResource(Res.string.settings_clear), stringResource(Res.string.settings_clear_body)) {
            SecondaryButton(
                stringResource(Res.string.action_clear),
                FaiIcons.trash,
                layout.controlHeight,
                if (files.recents.isEmpty()) null else ({ files.clearRecents() }),
            )
        }
    }
}

@Composable
private fun FolderSettings(scope: ScreenScope) {
    val layout = scope.layout
    val files = scope.files
    val canAdd = files.actions.canAddFolder
    Section(
        layout,
        stringResource(Res.string.settings_folders_title),
        trailing =
            if (canAdd) {
                {
                    SecondaryButton(
                        stringResource(
                            Res.string.action_add_folder,
                        ),
                        FaiIcons.folderPlus,
                        layout.controlHeight,
                        {
                            files.addFolder()
                        },
                    )
                }
            } else {
                null
            },
    ) {
        if (!canAdd) {
            SectionNote(layout, stringResource(Res.string.settings_folders_web))
            return@Section
        }
        SectionNote(layout, stringResource(Res.string.settings_folders_body))
        files.folderError?.let { FolderError(layout, it) }
        Hairline()
        if (files.folders.isEmpty()) {
            SectionNote(layout, stringResource(Res.string.settings_folders_empty))
        } else {
            files.folders.forEachIndexed { i, folder ->
                if (i > 0) Hairline()
                FolderRow(scope, folder)
            }
        }
    }
}

@Composable
private fun FolderError(
    layout: ShellLayout,
    error: FileError,
) {
    val reason =
        when (error) {
            FileError.NotFound -> Res.string.error_not_found
            FileError.PermissionLost -> Res.string.error_permission
            else -> Res.string.error_other
        }
    BasicText(
        "! " + stringResource(Res.string.folder_failed, stringResource(reason)),
        style = FaiTheme.type.small.copy(color = FaiTheme.colors.neg),
        modifier =
            Modifier
                .padding(
                    horizontal =
                        if (layout ==
                            ShellLayout.Phone
                        ) {
                            FaiSpace.s12
                        } else {
                            FaiSpace.cardPad
                        },
                ).padding(bottom = FaiSpace.s8),
    )
}

@Composable
private fun FolderRow(
    scope: ScreenScope,
    folder: LibraryFolder,
) {
    val layout = scope.layout
    val colors = FaiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal =
                    if (layout ==
                        ShellLayout.Phone
                    ) {
                        FaiSpace.s12
                    } else {
                        FaiSpace.cardPad
                    },
                vertical = FaiSpace.s8,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12),
    ) {
        FaiIcon(FaiIcons.folder, colors.text2)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaiSpace.s2)) {
            BasicText(
                folder.name,
                style = FaiTheme.type.body.copy(color = colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                folderLocation(folder.ref),
                style = FaiTheme.type.small.copy(color = colors.muted),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            icon = FaiIcons.x,
            label = stringResource(Res.string.action_remove_folder),
            touchTarget = layout.controlHeight,
        ) { scope.files.removeFolder(folder.ref) }
    }
}

@Composable
private fun AppearanceNote(layout: ShellLayout) {
    Section(layout, stringResource(Res.string.settings_appearance_title)) {
        SectionNote(layout, stringResource(Res.string.empty_body_settings), Modifier.padding(bottom = FaiSpace.s4))
    }
}
