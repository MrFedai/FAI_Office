/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.faioffice.app.theme.FaiAlpha
import org.faioffice.app.theme.FaiRadius
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme

/**
 * True while the user drives the app with the keyboard: focus rings show only then (the shell sets it on a key
 * press and clears it on a pointer press). Defaults to true so a lone component still shows its ring.
 */
val LocalFocusRingVisible = compositionLocalOf { true }

/** Focus ring width: two hairlines, so it stays visible next to a 1 dp border. */
private val focusRingWidth: Dp get() = FaiSize.border * 2

private val activateKeys = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)

/** Monochrome icon tinted with a theme colour. */
@Composable
fun FaiIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = FaiSize.icon,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Hover and keyboard-focus state of one interactive element. */
class Interaction(
    val source: MutableInteractionSource,
    val hovered: Boolean,
    val focusRing: Boolean,
)

@Composable
fun rememberInteraction(): Interaction {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    return Interaction(source, hovered, focused && LocalFocusRingVisible.current)
}

/** Draws the focus ring (primary) around [shape] while [interaction] has keyboard focus. */
@Composable
fun Modifier.focusRing(
    interaction: Interaction,
    shape: Shape,
): Modifier = if (interaction.focusRing) border(focusRingWidth, FaiTheme.colors.primary, shape) else this

/**
 * Enter and Space activate the focused element on every platform (foundation's clickable handles Enter only on
 * some). Consumes key down and up so the element does not fire twice.
 */
fun Modifier.activateOnKeys(onActivate: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.key !in activateKeys) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) onActivate()
        true
    }

/**
 * Primary button (`primary` fill, `onPrimary` text) at [height] (44 on touch, 36 desktop control). A null [onClick]
 * means the action does not work yet: the button is drawn at the disabled opacity, takes no focus or click and is
 * marked disabled for accessibility, so no fake action is offered.
 */
@Composable
fun PrimaryButton(
    label: String,
    icon: ImageVector?,
    height: Dp,
    onClick: (() -> Unit)?,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.control)
    val colors = FaiTheme.colors
    val behaviour =
        if (onClick != null) {
            Modifier
                .focusRing(interaction, shape)
                .activateOnKeys(onClick)
                .clickable(interaction.source, null, role = Role.Button, onClick = onClick)
        } else {
            Modifier.alpha(FaiAlpha.DISABLED).semantics(mergeDescendants = true) {
                role = Role.Button
                disabled()
            }
        }
    Row(
        behaviour
            .heightIn(min = height)
            .background(colors.primary, shape)
            .padding(horizontal = FaiSpace.s16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
    ) {
        if (icon != null) FaiIcon(icon, colors.onPrimary, size = FaiSize.iconSmall)
        BasicText(label, style = FaiTheme.type.button.copy(color = colors.onPrimary))
    }
}

/**
 * Square icon button: [touchTarget] size (44 on touch, 36 desktop control), hover tint, focus ring and an
 * accessible [label]. [selected] fills it with primarySoft (Settings shown in the top bar).
 */
@Composable
fun IconButton(
    icon: ImageVector,
    label: String,
    touchTarget: Dp,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.control)
    val colors = FaiTheme.colors
    val fill =
        when {
            selected -> colors.primarySoft
            interaction.hovered -> colors.surface2
            else -> Color.Transparent
        }
    Box(
        Modifier
            .size(touchTarget)
            .focusRing(interaction, shape)
            .background(fill, shape)
            .activateOnKeys(onClick)
            .clickable(interactionSource = interaction.source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        FaiIcon(icon, if (selected) colors.primaryInk else colors.text2)
    }
}

/**
 * Secondary button: `surface` fill with a `border` line and `text` label, `surface2` on hover; same heights and
 * disabled rule as [PrimaryButton] (a null [onClick] is drawn at 45 % and marked disabled).
 */
@Composable
fun SecondaryButton(
    label: String,
    icon: ImageVector?,
    height: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.control)
    val colors = FaiTheme.colors
    val behaviour =
        if (onClick != null) {
            Modifier
                .focusRing(interaction, shape)
                .activateOnKeys(onClick)
                .clickable(interaction.source, null, role = Role.Button, onClick = onClick)
        } else {
            Modifier.alpha(FaiAlpha.DISABLED).semantics(mergeDescendants = true) {
                role = Role.Button
                disabled()
            }
        }
    Row(
        modifier
            .then(behaviour)
            .heightIn(min = height)
            .background(if (interaction.hovered) colors.surface2 else colors.surface, shape)
            .border(FaiSize.border, colors.border2, shape)
            .padding(horizontal = FaiSpace.s14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
    ) {
        if (icon != null) FaiIcon(icon, colors.text2, size = FaiSize.iconSmall)
        BasicText(label, style = FaiTheme.type.button.copy(color = colors.text), maxLines = 1)
    }
}

/**
 * On / off switch (track 40 x 24, thumb 18): `primary` track with an `onPrimary` thumb when on, `border2` track with a
 * `surface` thumb when off. The whole row of the caller is usually the touch target; this draws the track only and
 * carries the switch semantics with [label] as its accessible name.
 */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    label: String,
    onChange: (Boolean) -> Unit,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.chip)
    val colors = FaiTheme.colors
    val toggle = { onChange(!checked) }
    Box(
        Modifier
            .size(FaiSize.touchMin)
            .activateOnKeys(toggle)
            .toggleable(checked, interaction.source, null, role = Role.Switch) { onChange(it) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(FaiSize.switchWidth, FaiSize.switchHeight)
                .focusRing(interaction, shape)
                .background(if (checked) colors.primary else colors.border2, shape)
                .padding(horizontal = (FaiSize.switchHeight - FaiSize.switchThumb) / 2),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(FaiSize.switchThumb)
                    .background(if (checked) colors.onPrimary else colors.surface, shape),
            )
        }
    }
}

/**
 * Filter chip (radius 999): `primarySoft` fill and `primaryInk` label when [selected], a `border` outline and
 * `text2` label otherwise; radio semantics so a row of chips reads as one choice.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    height: Dp,
    onSelect: () -> Unit,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.chip)
    val colors = FaiTheme.colors
    val fill =
        when {
            selected -> colors.primarySoft
            interaction.hovered -> colors.surface2
            else -> colors.surface
        }
    Box(
        Modifier
            .heightIn(min = height)
            .focusRing(interaction, shape)
            .background(fill, shape)
            .border(FaiSize.border, if (selected) colors.primarySoft else colors.border, shape)
            .activateOnKeys(onSelect)
            .selectable(selected, interaction.source, null, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = FaiSpace.s12),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = FaiTheme.type.button.copy(color = if (selected) colors.primaryInk else colors.text2),
            maxLines = 1,
        )
    }
}

/** Quiet link button: `primaryInk` label with a trailing chevron, `surface2` on hover; for "see all" jumps. */
@Composable
fun LinkButton(
    label: String,
    height: Dp,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(FaiRadius.control)
    val colors = FaiTheme.colors
    Row(
        Modifier
            .heightIn(min = height)
            .focusRing(interaction, shape)
            .background(if (interaction.hovered) colors.surface2 else Color.Transparent, shape)
            .activateOnKeys(onClick)
            .clickable(interaction.source, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = FaiSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s4),
    ) {
        BasicText(label, style = FaiTheme.type.button.copy(color = colors.primaryInk), maxLines = 1)
        FaiIcon(FaiIcons.chevronRight, colors.primaryInk, size = FaiSize.iconSmall)
    }
}
