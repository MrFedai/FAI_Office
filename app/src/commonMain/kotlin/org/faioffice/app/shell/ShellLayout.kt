/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.ui.unit.Dp
import org.faioffice.app.theme.FaiBreakpoint

/** Shell layout per window width class (DESIGN.md section 6). */
enum class ShellLayout {
    /** < 600: top bar + bottom bar with five entries. */
    Phone,

    /** 600–899: top bar + side rail (72). */
    Medium,

    /** 900–1279: top bar + side navigation (232), collapsible to the rail. */
    CompactDesktop,

    /** ≥ 1280: top bar + side navigation (232). */
    Desktop,
}

/**
 * Picks the layout for a window of [width] x [height]. Content decides, not the device; the only device signal is
 * [touch]: a touch window at most [FaiBreakpoint.phoneLandscapeHeight] tall (phone landscape) gets the phone layout.
 */
fun shellLayoutFor(
    width: Dp,
    height: Dp,
    touch: Boolean,
): ShellLayout =
    when {
        touch && height <= FaiBreakpoint.phoneLandscapeHeight -> ShellLayout.Phone
        width < FaiBreakpoint.medium -> ShellLayout.Phone
        width < FaiBreakpoint.compact -> ShellLayout.Medium
        width < FaiBreakpoint.desktop -> ShellLayout.CompactDesktop
        else -> ShellLayout.Desktop
    }
