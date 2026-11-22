/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import kotlinx.coroutines.test.runTest
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.InMemoryFileAccess
import org.faioffice.core.files.SafeSave
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.ModuleFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibrarySeedTest {
    private val fake = InMemoryFileAccess()

    private fun store() =
        LibraryStore(fake, SafeSave(fake, fake.addFolder("/uygulama/kayıt")), fake.ref("/uygulama/k.txt"), fake.ref("/uygulama/k.bad")) {
            NOW
        }

    private val seedRecents = LibrarySeed.recents(LibrarySeed.files(), NOW) { fake.ref("/tohum/${it.name}") }
    private val seedFolders = LibrarySeed.folders(NOW) { fake.ref("/tohum/klasör-$it") }

    private fun isSeed(ref: FileRef) = ref.location.startsWith("/tohum/")

    @Test
    fun seedHasAHundredVariedRecentsAndTwoFolders() {
        val files = LibrarySeed.files()
        assertEquals(100, files.size)
        assertEquals(100, files.map { it.name }.toSet().size)
        assertEquals(2, LibrarySeed.folderNames.size)
        assertEquals(3, files.count { it.pinned })
        assertEquals(10, files.count { it.missing })
        assertEquals(setOf(0, 1, null), files.map { it.folder }.toSet())
        val families = files.map { it.format.family }.toSet()
        assertTrue(families.containsAll(ModuleFamily.entries - ModuleFamily.Unknown), "families: $families")
        val lengths = files.map { it.name.length }
        assertTrue(lengths.min() < 10 && lengths.max() > 60, "name lengths ${lengths.min()}..${lengths.max()}")
        assertTrue(files.all { it.name.endsWith("." + it.format.canonicalExtension) && it.size > 0 })
        val times = seedRecents.map { it.lastOpenedEpochMillis }
        assertEquals(times.sortedDescending(), times, "newest first")
        assertTrue(seedFolders.all { it.addedEpochMillis < times.last() })
    }

    @Test
    fun emptyLibraryIsSeededOnceThenOnlyRead() =
        runTest {
            val store = store()
            val first = store.seed(seedRecents, seedFolders, ::isSeed)
            assertEquals(SeedResult(SeedResult.Outcome.Seeded, 100, 2), first)
            assertEquals("fai_budget seed 100 2 seeded", first.line)
            val written = fake.contents(fake.ref("/uygulama/k.txt"))!!

            val again = store().seed(seedRecents, seedFolders, ::isSeed)
            assertEquals(SeedResult(SeedResult.Outcome.AlreadySeeded, 100, 2), again)
            assertTrue(written.contentEquals(fake.contents(fake.ref("/uygulama/k.txt"))!!))
            val reread = store()
            assertEquals(seedRecents, reread.recents())
            assertEquals(seedFolders, reread.folders())
        }

    @Test
    fun userLibraryIsNeverReplaced() =
        runTest {
            val store = store()
            val own = fake.addFile("/Belgeler/mektup.docx", byteArrayOf(1))
            store.recordOpened(own, "mektup.docx", FileFormat.Docx, 1)
            val before = fake.contents(fake.ref("/uygulama/k.txt"))!!

            assertEquals(SeedResult(SeedResult.Outcome.Refused, 1, 0), store().seed(seedRecents, seedFolders, ::isSeed))
            assertTrue(before.contentEquals(fake.contents(fake.ref("/uygulama/k.txt"))!!))
            assertEquals(listOf(own), store().recents().map { it.ref })
        }

    @Test
    fun userFolderOrRecentsSwitchedOffAlsoRefuse() =
        runTest {
            val withFolder = store()
            withFolder.addFolder(fake.addFolder("/Belgeler"), "Belgeler")
            assertEquals(SeedResult.Outcome.Refused, store().seed(seedRecents, seedFolders, ::isSeed).outcome)

            val fresh = InMemoryFileAccess()
            val off = LibraryStore(fresh, SafeSave(fresh, fresh.addFolder("/j")), fresh.ref("/s.txt"), fresh.ref("/s.bad")) { NOW }
            off.setRememberRecent(false)
            assertEquals(SeedResult(SeedResult.Outcome.Refused, 0, 0), off.seed(seedRecents, seedFolders) { true })
            assertTrue(off.recents().isEmpty())
        }

    private companion object {
        const val NOW = 1_795_000_000_000L
    }
}
