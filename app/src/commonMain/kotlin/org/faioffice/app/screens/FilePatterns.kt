/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.faioffice.app.files.FileItem
import org.faioffice.app.files.FileSort
import org.faioffice.app.files.SortKey
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.column_date
import org.faioffice.app.resources.column_name
import org.faioffice.app.resources.column_size
import org.faioffice.app.resources.sort_ascending
import org.faioffice.app.resources.sort_date
import org.faioffice.app.resources.sort_descending
import org.faioffice.app.resources.sort_label
import org.faioffice.app.resources.sort_name
import org.faioffice.app.resources.sort_size
import org.faioffice.app.resources.state_missing
import org.faioffice.app.resources.state_permission_lost
import org.faioffice.app.shell.FaiIcon
import org.faioffice.app.shell.FaiIcons
import org.faioffice.app.shell.Hairline
import org.faioffice.app.shell.ShellLayout
import org.faioffice.app.shell.activateOnKeys
import org.faioffice.app.shell.focusRing
import org.faioffice.app.shell.rememberInteraction
import org.faioffice.app.theme.FaiAlpha
import org.faioffice.app.theme.FaiRadius
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.library.EntryState
import org.faioffice.core.text.lowercase
import org.jetbrains.compose.resources.stringResource

/** Side navigation layouts: lists get columns, pages two columns. */
val ShellLayout.wide: Boolean get() = this == ShellLayout.CompactDesktop || this == ShellLayout.Desktop

/** Touch-first layouts: 44 controls instead of 36. */
val ShellLayout.touch: Boolean get() = this == ShellLayout.Phone || this == ShellLayout.Medium

/** Control height of a layout: touch target on phone and medium, desktop control otherwise. */
val ShellLayout.controlHeight: Dp get() = if (touch) FaiSize.touchMin else FaiSize.control

/** Horizontal padding of a card's content on a layout. */
private val ShellLayout.cardPadX: Dp get() = if (this == ShellLayout.Phone) FaiSpace.s12 else FaiSpace.cardPad

/**
 * Section card (DESIGN.md section 6, file screens): bordered `surface` card, radius 10, optional [title] row
 * (`cardTitle`) with [trailing] controls at its end; [content] runs edge to edge so list rows can draw their own
 * hover fill.
 */
@Composable
fun Section(
    layout: ShellLayout,
    title: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FaiTheme.colors
    val shape = RoundedCornerShape(FaiRadius.card)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(FaiSize.border, colors.border, shape)
            .padding(vertical = FaiSpace.s4),
    ) {
        if (title != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = layout.controlHeight + FaiSpace.s8)
                    .padding(horizontal = layout.cardPadX),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
            ) {
                BasicText(
                    title,
                    style = FaiTheme.type.cardTitle.copy(color = colors.text),
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke(this)
            }
        }
        content()
    }
}

/** One sentence inside a [Section] (`text2`), for an empty list or a note. */
@Composable
fun SectionNote(
    layout: ShellLayout,
    text: String,
    modifier: Modifier = Modifier,
) {
    val style = if (layout == ShellLayout.Phone) FaiTheme.type.bodyMobile else FaiTheme.type.body
    BasicText(
        text,
        style = style.copy(color = FaiTheme.colors.text2),
        modifier = modifier.fillMaxWidth().padding(horizontal = layout.cardPadX, vertical = FaiSpace.s12),
    )
}

/**
 * File badge (DESIGN.md section 3): the extension in the format's meaning colour on its 14 % tint, 36 (radius 8) or
 * [large] 56 (radius 10).
 */
@Composable
fun FileBadge(
    format: FileFormat,
    large: Boolean = false,
) {
    val ink = format.color()
    Box(
        Modifier
            .size(if (large) FaiSize.emptyBadge else FaiSize.fileBadge)
            .background(
                ink.copy(alpha = FaiAlpha.FILE_TINT),
                RoundedCornerShape(if (large) FaiRadius.card else FaiRadius.control),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val style = if (large) FaiTheme.type.cardTitle else FaiTheme.type.label
        BasicText(format.badge, style = style.copy(color = ink, textAlign = TextAlign.Center), maxLines = 1)
    }
}

/** "! Missing" / "! Access lost" in `warn` (sign + word, never colour alone); null for an available file. */
@Composable
fun stateText(state: EntryState): String? =
    when (state) {
        EntryState.Available -> null
        EntryState.Missing -> "! " + stringResource(Res.string.state_missing)
        EntryState.PermissionLost -> "! " + stringResource(Res.string.state_permission_lost)
    }

/** What a file list shows in its desktop columns besides name, date and size. */
class FileColumns(
    /** Header of the third column ("Type" on Recent Files, "Source" on the family screens). */
    val thirdTitle: String,
    /** Header of the date column ("Opened", "Date"). */
    val dateTitle: String,
    /** The third column's value for one file. */
    val third: @Composable (FileItem) -> String,
)

/** Width of [count] trailing icon buttons with their gaps, so the header lines up with the rows. */
private fun actionsWidth(count: Int): Dp = FaiSize.control * count + FaiSpace.s4 * (count - 1)

/**
 * File list (DESIGN.md section 6, file row): rows separated by hairlines; on side-navigation layouts a column header
 * and the columns name · [FileColumns.third] · date · size, on phone two lines under the name. Up / Down move the
 * focus from row to row (Tab still walks every control), Enter or Space opens.
 * [actionCount] icon buttons drawn by [actions] sit at the end of each row.
 */
@Composable
fun FileList(
    layout: ShellLayout,
    items: List<FileItem>,
    columns: FileColumns,
    onOpen: (FileItem) -> Unit,
    actionCount: Int = 0,
    actions: @Composable RowScope.(FileItem) -> Unit = {},
) {
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    Column(Modifier.fillMaxWidth()) {
        if (layout.wide) {
            FileListHeader(layout, columns, actionCount)
            Hairline()
        }
        items.forEachIndexed { index, item ->
            if (index > 0) Hairline()
            val move = { delta: Int -> requesters.getOrNull(index + delta)?.requestFocus() != null }
            FileRow(layout, item, columns, requesters[index], move, { onOpen(item) }) { actions(item) }
        }
    }
}

@Composable
private fun FileListHeader(
    layout: ShellLayout,
    columns: FileColumns,
    actionCount: Int,
) {
    val style = FaiTheme.type.label.copy(color = FaiTheme.colors.muted)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = layout.cardPadX, vertical = FaiSpace.s6),
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(FaiSize.fileBadge))
        BasicText(stringResource(Res.string.column_name), style = style, modifier = Modifier.weight(1f))
        BasicText(columns.thirdTitle, style = style, modifier = Modifier.width(FaiSize.fileColumn))
        BasicText(columns.dateTitle, style = style, modifier = Modifier.width(FaiSize.fileColumn))
        BasicText(
            stringResource(Res.string.column_size),
            style = style.copy(textAlign = TextAlign.End),
            modifier = Modifier.width(FaiSize.fileColumn),
        )
        if (actionCount > 0) Spacer(Modifier.width(actionsWidth(actionCount)))
    }
}

@Composable
private fun FileRow(
    layout: ShellLayout,
    item: FileItem,
    columns: FileColumns,
    focus: FocusRequester,
    move: (Int) -> Boolean,
    onOpen: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val interaction = rememberInteraction()
    val colors = FaiTheme.colors
    val type = FaiTheme.type
    val shape = RoundedCornerShape(FaiRadius.control)
    val language = currentLanguage()
    val state = stateText(item.state)
    val nameColor = if (item.state == EntryState.Available) colors.text else colors.text2
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (layout.wide) FaiSize.fileBadge + FaiSpace.s16 else FaiSize.touchMin)
            .focusRing(interaction, shape)
            .background(if (interaction.hovered) colors.surface2 else colors.surface)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> move(1)
                    Key.DirectionUp -> move(-1)
                    else -> false
                }
            }.focusRequester(focus)
            .activateOnKeys(onOpen)
            .clickable(interaction.source, null, role = Role.Button, onClick = onOpen)
            .padding(horizontal = layout.cardPadX, vertical = FaiSpace.s8),
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileBadge(item.format)
        val size = sizeText(item.size)
        val date = item.date?.let { dateText(it, language) }
        if (layout.wide) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaiSpace.s2)) {
                BasicText(
                    item.name,
                    style = type.body.copy(color = nameColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RowLocation(item, state)
            }
            Cell(columns.third(item), type.small.copy(color = colors.text2))
            Cell(date.orEmpty(), type.numberSmall.copy(color = colors.text2))
            Cell(size.orEmpty(), type.number.copy(color = colors.text2, textAlign = TextAlign.End))
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FaiSpace.s2)) {
                BasicText(
                    item.name,
                    style = type.bodyMobile.copy(color = nameColor),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RowLocation(item, state)
                val meta = listOfNotNull(size, date).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    BasicText(
                        meta,
                        style = type.numberSmall.copy(color = colors.muted),
                        maxLines = 1,
                    )
                }
            }
        }
        actions()
    }
}

/** Second line of a row: the state word (warn) when the file is not available, then the folder breadcrumb. */
@Composable
private fun RowLocation(
    item: FileItem,
    state: String?,
) {
    val type = FaiTheme.type
    Row(horizontalArrangement = Arrangement.spacedBy(FaiSpace.s6), verticalAlignment = Alignment.CenterVertically) {
        if (state != null) BasicText(state, style = type.small.copy(color = FaiTheme.colors.warn), maxLines = 1)
        BasicText(
            shortLocation(item.ref),
            style = type.small.copy(color = FaiTheme.colors.muted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Cell(
    text: String,
    style: TextStyle,
) {
    BasicText(
        text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(FaiSize.fileColumn),
    )
}

/**
 * Sort control (DESIGN.md section 6): segmented Name · Date · Size on a `surface2` track (radius 9); the selected
 * segment is a `surface` chip with a border and an arrow for the direction. Choosing it again flips the direction.
 */
@Composable
fun SortControl(
    layout: ShellLayout,
    sort: FileSort,
    onChange: (FileSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FaiTheme.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
    ) {
        BasicText(stringResource(Res.string.sort_label), style = FaiTheme.type.small.copy(color = colors.muted))
        val phone = layout == ShellLayout.Phone
        Row(
            (if (phone) Modifier.weight(1f) else Modifier)
                .background(colors.surface2, RoundedCornerShape(FaiRadius.segment))
                .padding(FaiSpace.s2),
            horizontalArrangement = Arrangement.spacedBy(FaiSpace.s2),
        ) {
            val keys =
                listOf(
                    SortKey.Name to Res.string.sort_name,
                    SortKey.Date to Res.string.sort_date,
                    SortKey.Size to Res.string.sort_size,
                )
            for ((key, label) in keys) {
                val segment = if (phone) Modifier.weight(1f) else Modifier
                SortSegment(layout, stringResource(label), sort, key, segment) { onChange(sort.choose(key)) }
            }
        }
    }
}

@Composable
private fun SortSegment(
    layout: ShellLayout,
    label: String,
    sort: FileSort,
    key: SortKey,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val interaction = rememberInteraction()
    val colors = FaiTheme.colors
    val selected = sort.key == key
    val shape = RoundedCornerShape(FaiRadius.control)
    val direction = stringResource(if (sort.descending) Res.string.sort_descending else Res.string.sort_ascending)
    Row(
        modifier
            .heightIn(min = layout.controlHeight - FaiSpace.s4)
            .focusRing(interaction, shape)
            .background(
                when {
                    selected -> colors.surface
                    interaction.hovered -> colors.bg
                    else -> colors.surface2
                },
                shape,
            ).border(FaiSize.border, if (selected) colors.border2 else colors.surface2, shape)
            .activateOnKeys(onSelect)
            .selectable(selected, interaction.source, null, role = Role.RadioButton, onClick = onSelect)
            .semantics { if (selected) stateDescription = direction }
            .padding(horizontal = FaiSpace.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s4, Alignment.CenterHorizontally),
    ) {
        BasicText(
            label,
            style = FaiTheme.type.button.copy(color = if (selected) colors.text else colors.text2),
            maxLines = 1,
        )
        if (selected) {
            FaiIcon(
                if (sort.descending) FaiIcons.arrowDown else FaiIcons.arrowUp,
                colors.text2,
                size = FaiSize.iconSmall,
            )
        }
    }
}

/** Sorts [items] by [sort] with names folded in the interface language. */
@Composable
fun sorted(
    items: List<FileItem>,
    sort: FileSort,
): List<FileItem> {
    val language = currentLanguage()
    return remember(items, sort, language) { sort.apply(items) { it.lowercase(language) } }
}
