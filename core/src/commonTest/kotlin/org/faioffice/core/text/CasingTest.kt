/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class CasingTest {
    @Test
    fun turkishUpperCaseUsesDottedCapitalI() {
        assertEquals("İSTANBUL", "istanbul".uppercase("tr"))
        assertEquals("ISIK", "ısık".uppercase("tr-TR"))
    }

    @Test
    fun turkishLowerCaseUsesDotlessI() {
        assertEquals("ışık", "IŞIK".lowercase("tr"))
        assertEquals("istanbul", "İSTANBUL".lowercase("tr"))
    }

    @Test
    fun azerbaijaniFollowsTurkish() {
        assertEquals("İ", "i".uppercase("az"))
    }

    @Test
    fun otherLanguagesUseInvariantRules() {
        assertEquals("TITLE", "title".uppercase("en"))
        assertEquals("title", "TITLE".lowercase("en"))
        assertEquals("I", "i".uppercase("de-CH"))
    }
}
