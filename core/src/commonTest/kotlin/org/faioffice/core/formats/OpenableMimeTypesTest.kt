/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.formats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenableMimeTypesTest {
    @Test
    fun everyRegisteredTypeIsAnnouncedOnce() {
        val incoming = OpenableMimeTypes.incoming
        FileFormat.entries.mapNotNull { it.mimeType?.lowercase() }.forEach { assertTrue(it in incoming, it) }
        assertEquals(incoming.size, incoming.toSet().size)
        assertTrue("application/vnd.oasis.opendocument.text" in incoming)
        assertTrue("text/comma-separated-values" in incoming)
        assertTrue("application/vnd.ms-word.document.macroenabled.12" in incoming)
    }

    @Test
    fun onlyThePickerHasWildcardsAndUnknownTypes() {
        assertTrue(OpenableMimeTypes.incoming.none { it.endsWith("/*") || it == "application/octet-stream" })
        assertTrue(OpenableMimeTypes.picker.containsAll(OpenableMimeTypes.incoming))
        assertTrue("text/*" in OpenableMimeTypes.picker)
        assertTrue("application/octet-stream" in OpenableMimeTypes.picker)
    }

    @Test
    fun aliasesAreLowerCaseBareTypes() {
        OpenableMimeTypes.picker.forEach { type ->
            assertEquals(type.lowercase(), type)
            assertTrue(';' !in type && type.count { it == '/' } == 1, type)
        }
    }
}
