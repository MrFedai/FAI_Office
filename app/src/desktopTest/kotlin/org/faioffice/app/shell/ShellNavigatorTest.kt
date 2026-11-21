/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Back stack: forward, back, Home at the root hands back to the system. */
class ShellNavigatorTest {
    @Test
    fun startsAtHomeAndRootBackGoesToSystem() {
        val nav = ShellNavigator()
        assertEquals(Destination.Home, nav.current)
        assertFalse(nav.canGoBack)
        assertFalse(nav.back(), "back at Home must be left to the system")
        assertEquals(Destination.Home, nav.current)
    }

    @Test
    fun forwardThenBack() {
        val nav = ShellNavigator()
        nav.navigate(Destination.Documents)
        nav.navigate(Destination.Pdf)
        assertEquals(listOf(Destination.Home, Destination.Documents, Destination.Pdf), nav.entries)
        assertTrue(nav.back())
        assertEquals(Destination.Documents, nav.current)
        assertTrue(nav.back())
        assertEquals(Destination.Home, nav.current)
        assertFalse(nav.back())
    }

    @Test
    fun eachDestinationOnceAndHomeClears() {
        val nav = ShellNavigator()
        nav.navigate(Destination.Documents)
        nav.navigate(Destination.Archive)
        nav.navigate(Destination.Documents)
        nav.navigate(Destination.Documents)
        assertEquals(listOf(Destination.Home, Destination.Archive, Destination.Documents), nav.entries)
        nav.navigate(Destination.Home)
        assertEquals(listOf(Destination.Home), nav.entries)
    }

    @Test
    fun phoneTabs() {
        assertEquals(5, Destination.PhoneTabs.size)
        assertEquals(Destination.Home, Destination.Archive.phoneTab)
        assertEquals(Destination.Pdf, Destination.Pdf.phoneTab)
        assertEquals(null, Destination.Settings.phoneTab)
        // Every destination is reachable on the phone: bottom bar, Home tiles or the top bar (Settings).
        val reachable = Destination.PhoneTabs + Destination.HomeTiles + Destination.Settings
        assertEquals(Destination.entries.toSet(), reachable.toSet())
    }
}
