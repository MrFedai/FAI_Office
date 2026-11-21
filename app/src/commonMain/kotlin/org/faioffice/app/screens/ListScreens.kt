/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.faioffice.app.files.FileItem
import org.faioffice.app.files.FileSort
import org.faioffice.app.files.FilesModel
import org.faioffice.app.files.SortKey
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.action_add_folder
import org.faioffice.app.resources.action_open_file
import org.faioffice.app.resources.action_pin
import org.faioffice.app.resources.action_remove_from_recent
import org.faioffice.app.resources.action_unpin
import org.faioffice.app.resources.column_date
import org.faioffice.app.resources.column_opened
import org.faioffice.app.resources.column_source
import org.faioffice.app.resources.column_type
import org.faioffice.app.resources.empty_body_home
import org.faioffice.app.resources.empty_body_recent_files
import org.faioffice.app.resources.empty_title_home
import org.faioffice.app.resources.family_count
import org.faioffice.app.resources.family_folders_skipped
import org.faioffice.app.resources.family_scanning
import org.faioffice.app.resources.family_truncated
import org.faioffice.app.resources.filter_all
import org.faioffice.app.resources.filter_other
import org.faioffice.app.resources.home_recent_title
import org.faioffice.app.resources.home_see_all
import org.faioffice.app.resources.recent_filter_empty
import org.faioffice.app.resources.source_recent
import org.faioffice.app.shell.Destination
import org.faioffice.app.shell.DestinationBadge
import org.faioffice.app.shell.EmptyState
import org.faioffice.app.shell.FaiIcon
import org.faioffice.app.shell.FaiIcons
import org.faioffice.app.shell.FilterChip
import org.faioffice.app.shell.HomeTiles
import org.faioffice.app.shell.IconButton
import org.faioffice.app.shell.LinkButton
import org.faioffice.app.shell.PrimaryButton
import org.faioffice.app.shell.SecondaryButton
import org.faioffice.app.shell.ShellLayout
import org.faioffice.app.shell.ShellNavigator
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.faioffice.core.formats.ModuleFamily
import org.jetbrains.compose.resources.stringResource

/** Module family listed by a destination's screen; null for Home, Recent Files, File Converter and Settings. */
val Destination.family: ModuleFamily?
    get() =
        when (this) {
            Destination.Documents -> ModuleFamily.Documents
            Destination.Spreadsheets -> ModuleFamily.Spreadsheets
            Destination.Presentations -> ModuleFamily.Presentations
            Destination.Pdf -> ModuleFamily.Pdf
            Destination.Archive -> ModuleFamily.Archive
            else -> null
        }

/** What every list screen needs from the shell. */
class ScreenScope(
    val files: FilesModel,
    val navigator: ShellNavigator,
    val layout: ShellLayout,
    /** Visible page height: an empty state fills it. */
    val pageHeight: Dp,
) {
    val gap: Dp get() = if (layout == ShellLayout.Phone) FaiSpace.s16 else FaiSpace.s24

    fun open(item: FileItem) = files.open(item.ref, navigator)
}

/** Sort order kept per screen across configuration changes and process death. */
@Composable
private fun rememberSort(): MutableState<FileSort> {
    var key by rememberSaveable { mutableStateOf(SortKey.Date.name) }
    var descending by rememberSaveable { mutableStateOf(true) }
    val sort = FileSort(SortKey.valueOf(key), descending)
    return object : MutableState<FileSort> {
        override var value: FileSort
            get() = sort
            set(next) {
                key = next.key.name
                descending = next.descending
            }

        override fun component1() = value

        override fun component2(): (FileSort) -> Unit = { value = it }
    }
}

private const val HOME_ROWS_PHONE = 5
private const val HOME_ROWS_WIDE = 8

/**
 * Home (P3.8): the start card with "Open file" and "Add folder", the latest recent files (pinned first) with a jump
 * to the full list, then the file-type tiles.
 */
@Composable
fun HomeScreen(scope: ScreenScope) {
    val layout = scope.layout
    Column(verticalArrangement = Arrangement.spacedBy(scope.gap)) {
        StartCard(scope)
        val recents = scope.files.recents
        Section(
            layout,
            stringResource(Res.string.home_recent_title),
            trailing =
                if (recents.isEmpty()) {
                    null
                } else {
                    {
                        LinkButton(stringResource(Res.string.home_see_all), layout.controlHeight) {
                            scope.navigator.navigate(Destination.RecentFiles)
                        }
                    }
                },
        ) {
            if (recents.isEmpty()) {
                SectionNote(layout, stringResource(Res.string.empty_body_recent_files))
            } else {
                val rows = if (layout.wide) HOME_ROWS_WIDE else HOME_ROWS_PHONE
                val shown = (recents.filter { it.pinned } + recents.filter { !it.pinned }).take(rows)
                FileList(layout, shown, typeColumns(), scope::open, actionCount = 0) { item ->
                    if (item.pinned) PinMark()
                }
            }
        }
        HomeTiles(layout, scope.navigator::navigate)
    }
}

@Composable
private fun PinMark() {
    FaiIcon(FaiIcons.pin, FaiTheme.colors.primaryInk, size = FaiSize.iconSmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartCard(scope: ScreenScope) {
    val layout = scope.layout
    val files = scope.files
    val colors = FaiTheme.colors
    val bodyStyle = if (layout == ShellLayout.Phone) FaiTheme.type.bodyMobile else FaiTheme.type.body
    val buttons =
        @Composable {
            PrimaryButton(stringResource(Res.string.action_open_file), FaiIcons.folderOpen, layout.controlHeight) {
                files.openFiles()
            }
            if (files.actions.canAddFolder) {
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
        }
    val text =
        @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(FaiSpace.s4)) {
                BasicText(
                    stringResource(Res.string.empty_title_home),
                    style = FaiTheme.type.cardTitle.copy(color = colors.text),
                )
                BasicText(stringResource(Res.string.empty_body_home), style = bodyStyle.copy(color = colors.text2))
            }
        }
    Section(layout, title = null) {
        val pad =
            Modifier.padding(
                horizontal =
                    if (layout ==
                        ShellLayout.Phone
                    ) {
                        FaiSpace.s12
                    } else {
                        FaiSpace.cardPad
                    },
                vertical = FaiSpace.s12,
            )
        if (layout.wide) {
            Row(
                pad.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FaiSpace.s16),
            ) {
                DestinationBadge(Destination.Home)
                text(Modifier.weight(1f))
                buttons()
            }
        } else {
            Column(pad.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FaiSpace.s12)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12),
                ) {
                    DestinationBadge(Destination.Home)
                    text(Modifier.weight(1f))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
                    verticalArrangement = Arrangement.spacedBy(FaiSpace.s8),
                ) { buttons() }
            }
        }
    }
}

/** Recent Files columns: type, opened, size. */
@Composable
private fun typeColumns() =
    FileColumns(
        thirdTitle = stringResource(Res.string.column_type),
        dateTitle = stringResource(Res.string.column_opened),
        third = { stringResource(it.family.label) },
    )

/** Family screen columns: source (recent or the added folder), date, size. */
@Composable
private fun sourceColumns(): FileColumns {
    val recent = stringResource(Res.string.source_recent)
    return FileColumns(
        thirdTitle = stringResource(Res.string.column_source),
        dateTitle = stringResource(Res.string.column_date),
        third = { it.folder ?: recent },
    )
}

/** Families offered as filters on Recent Files; "Other" covers images, text and unknown content. */
private val filterFamilies =
    listOf(
        ModuleFamily.Documents,
        ModuleFamily.Spreadsheets,
        ModuleFamily.Presentations,
        ModuleFamily.Pdf,
        ModuleFamily.Archive,
    )

private const val FILTER_ALL = "all"
private const val FILTER_OTHER = "other"

/**
 * Recent Files (P3.8): every recent entry with a family filter, the sort control, and pin / remove on each row;
 * the empty state when the list is empty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentFilesScreen(scope: ScreenScope) {
    val layout = scope.layout
    val files = scope.files
    if (files.recents.isEmpty()) {
        EmptyState(
            Destination.RecentFiles,
            layout,
            Modifier.heightIn(min = scope.pageHeight),
            onAction = { files.openFiles() },
        )
        return
    }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var sort by rememberSort()
    val shown =
        files.recents.filter { item ->
            when (filter) {
                FILTER_ALL -> true
                FILTER_OTHER -> item.family !in filterFamilies
                else -> item.family.name == filter
            }
        }
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                if (layout ==
                    ShellLayout.Phone
                ) {
                    FaiSpace.s12
                } else {
                    FaiSpace.s16
                },
            ),
    ) {
        val chips =
            @Composable { modifier: Modifier ->
                FlowRow(
                    modifier,
                    horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
                    verticalArrangement = Arrangement.spacedBy(FaiSpace.s8),
                ) {
                    val height = layout.controlHeight
                    FilterChip(stringResource(Res.string.filter_all), filter == FILTER_ALL, height) {
                        filter =
                            FILTER_ALL
                    }
                    for (family in filterFamilies) {
                        FilterChip(stringResource(family.label), filter == family.name, height) { filter = family.name }
                    }
                    FilterChip(stringResource(Res.string.filter_other), filter == FILTER_OTHER, height) {
                        filter =
                            FILTER_OTHER
                    }
                }
            }
        if (layout == ShellLayout.Phone) {
            chips(Modifier.fillMaxWidth())
            SortControl(layout, sort, { sort = it }, Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(FaiSpace.s16)) {
                chips(Modifier.weight(1f))
                SortControl(layout, sort, { sort = it })
            }
        }
        Section(layout, title = null) {
            if (shown.isEmpty()) {
                SectionNote(layout, stringResource(Res.string.recent_filter_empty))
            } else {
                FileList(layout, sorted(shown, sort), typeColumns(), scope::open, actionCount = 2) { item ->
                    RecentActions(scope, item)
                }
            }
        }
    }
}

@Composable
private fun RecentActions(
    scope: ScreenScope,
    item: FileItem,
) {
    val target = scope.layout.controlHeight
    IconButton(
        icon = FaiIcons.pin,
        label = stringResource(if (item.pinned) Res.string.action_unpin else Res.string.action_pin),
        touchTarget = target,
        selected = item.pinned,
    ) { scope.files.setPinned(item.ref, !item.pinned) }
    IconButton(
        icon = FaiIcons.x,
        label = stringResource(Res.string.action_remove_from_recent),
        touchTarget = target,
    ) { scope.files.remove(item.ref) }
}

/**
 * Documents, Spreadsheets, Presentations, PDF and Archive (P3.8): the family's files from recent files and every
 * added folder (read when the screen opens, never in the background), sortable; the empty state when none.
 */
@Composable
fun FamilyScreen(
    scope: ScreenScope,
    destination: Destination,
    family: ModuleFamily,
) {
    val layout = scope.layout
    val files = scope.files
    LaunchedEffect(family, files.version) { files.loadFamily(family) }
    val listing = files.family(family)
    if (listing.items.isEmpty() && !listing.loading) {
        EmptyState(
            destination,
            layout,
            Modifier.heightIn(min = scope.pageHeight),
            onAction = { files.openFiles() },
            secondary =
                if (files.actions.canAddFolder) {
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
        )
        return
    }
    var sort by rememberSort()
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                if (layout ==
                    ShellLayout.Phone
                ) {
                    FaiSpace.s12
                } else {
                    FaiSpace.s16
                },
            ),
    ) {
        val status =
            listOfNotNull(
                stringResource(Res.string.family_count, listing.items.size),
                if (listing.loading) stringResource(Res.string.family_scanning) else null,
                if (listing.skippedFolders >
                    0
                ) {
                    stringResource(Res.string.family_folders_skipped, listing.skippedFolders)
                } else {
                    null
                },
                if (listing.truncated) stringResource(Res.string.family_truncated, listing.items.size) else null,
            ).joinToString(" · ")
        val statusText =
            @Composable { modifier: Modifier ->
                BasicText(status, style = FaiTheme.type.small.copy(color = FaiTheme.colors.text2), modifier = modifier)
            }
        if (layout == ShellLayout.Phone) {
            statusText(Modifier)
            SortControl(layout, sort, { sort = it }, Modifier.fillMaxWidth())
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FaiSpace.s16),
            ) {
                statusText(Modifier.weight(1f))
                SortControl(layout, sort, { sort = it })
            }
        }
        Section(layout, title = null) {
            if (listing.items.isEmpty()) {
                SectionNote(layout, stringResource(Res.string.family_scanning))
            } else {
                FileList(layout, sorted(listing.items, sort), sourceColumns(), scope::open)
            }
        }
    }
}
