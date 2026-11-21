/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FileRefTest {
    @Test
    fun serialisedFormRoundTrips() {
        val refs =
            listOf(
                FileRef(FileRef.Kind.Path, "C:\\Belgeler\\Çizelge: ilk.xlsx"),
                FileRef(FileRef.Kind.Path, "/home/kişi/İş/rapor.docx"),
                FileRef(
                    FileRef.Kind.ContentUri,
                    "content://com.android.externalstorage.documents/document/primary%3AA.pdf",
                ),
                FileRef(FileRef.Kind.WebSession, "3f2a"),
            )
        for (ref in refs) assertEquals(ref, FileRef.parse(ref.serialize()))
        assertEquals("path:/a", FileRef(FileRef.Kind.Path, "/a").serialize())
    }

    @Test
    fun malformedFormsAreRejected() {
        for (bad in listOf("", ":x", "path:", "nope:/a", "/plain/path")) assertNull(FileRef.parse(bad), bad)
    }

    @Test
    fun webReferencesDoNotSurviveRestart() {
        assertFalse(FileRef.Kind.WebSession.survivesRestart)
    }
}
