/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Width class boundaries of DESIGN.md section 6 and the short touch window rule. */
class ShellLayoutTest {
    private fun layout(
        width: Int,
        height: Int = 900,
        touch: Boolean = false,
    ) = shellLayoutFor(width.dp, height.dp, touch)

    @Test
    fun widthBoundaries() {
        assertEquals(ShellLayout.Phone, layout(390))
        assertEquals(ShellLayout.Phone, layout(599))
        assertEquals(ShellLayout.Medium, layout(600))
        assertEquals(ShellLayout.Medium, layout(899))
        assertEquals(ShellLayout.CompactDesktop, layout(900))
        assertEquals(ShellLayout.CompactDesktop, layout(1279))
        assertEquals(ShellLayout.Desktop, layout(1280))
        assertEquals(ShellLayout.Desktop, layout(1920))
    }

    @Test
    fun shortTouchWindowUsesPhoneLayout() {
        assertEquals(ShellLayout.Phone, layout(800, 480, touch = true))
        assertEquals(ShellLayout.Phone, layout(1280, 500, touch = true))
        assertEquals(ShellLayout.Medium, layout(800, 501, touch = true))
        // Without touch a short window keeps its width class (desktop window made short by hand).
        assertEquals(ShellLayout.Medium, layout(800, 480, touch = false))
    }
}
