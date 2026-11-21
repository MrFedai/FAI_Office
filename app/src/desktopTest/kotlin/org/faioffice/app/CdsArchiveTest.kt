/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CdsArchiveTest {
    @Test
    fun dumpsOnlyWithoutArchiveAndOncePerClasspath() {
        assertTrue(shouldDump(sharing = false, classListPresent = true, writable = true, null, "cp1"))
        assertTrue(
            shouldDump(sharing = false, classListPresent = true, writable = true, "", "cp1"),
            "placeholder stamp",
        )
        assertTrue(shouldDump(sharing = false, classListPresent = true, writable = true, "cp0", "cp1"), "after update")
        assertFalse(shouldDump(sharing = true, classListPresent = true, writable = true, null, "cp1"), "archive mapped")
        assertFalse(
            shouldDump(sharing = false, classListPresent = true, writable = true, "cp1", "cp1"),
            "tried already",
        )
        assertFalse(
            shouldDump(sharing = false, classListPresent = false, writable = true, null, "cp1"),
            "no class list",
        )
        assertFalse(shouldDump(sharing = false, classListPresent = true, writable = false, null, "cp1"), "read-only")
    }

    @Test
    fun inertOutsideAnInstalledImage() {
        // ./gradlew :app:run and the tests get no fai.cds property (or an unexpanded $APPDIR): nothing starts.
        CdsArchive.prepareInBackground()
        CdsArchive.awaitPending(0)
    }

    @Test
    fun packageShipsClassListAndEmptyPlaceholders() {
        val dir = File(System.getProperty("user.dir"), "cds/common")
        val classList = File(dir, "fai.classlist").readLines()
        assertTrue(classList.size > 1000, "class list has ${classList.size} lines")
        assertTrue(classList.none { it.startsWith("#") || it.contains(" id: ") || it.contains('\r') })
        assertTrue(classList.contains("org/faioffice/app/MainKt"))
        assertTrue(File(dir, "fai.jsa").length() == 0L && File(dir, "fai.jsa.stamp").length() == 0L)
    }
}
