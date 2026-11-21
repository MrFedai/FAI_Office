/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.SafeSave
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.ModuleFamily
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Store behaviour shared by the in-memory fake and the real desktop file system; restart = a new [LibraryStore]. */
abstract class LibraryStoreContract {
    interface Fixture {
        val access: FileAccess
        val appFolder: FileRef
        val storeFile: FileRef
        val damagedFile: FileRef
        val journal: FileRef

        /** A document the access can see. */
        fun document(name: String): FileRef

        fun missing(name: String): FileRef

        /** Puts [bytes] where the store file is. */
        fun writeStore(bytes: ByteArray)

        fun readStore(): ByteArray?

        fun readDamaged(): ByteArray?

        fun close()
    }

    abstract fun fixture(): Fixture

    private class Clock {
        var now = 1_000L

        fun tick(): Long = ++now
    }

    private fun run(block: suspend Env.() -> Unit): TestResult =
        runTest {
            val f = fixture()
            try {
                Env(f).block()
            } finally {
                f.close()
            }
        }

    private class Env(
        val f: Fixture,
    ) {
        val clock = Clock()

        /** A new store on the same folder, as after an app restart or Android process death. */
        fun store(): LibraryStore =
            LibraryStore(f.access, SafeSave(f.access, f.journal), f.storeFile, f.damagedFile, clock::tick)

        suspend fun LibraryStore.open(
            ref: FileRef,
            format: FileFormat = FileFormat.Docx,
        ) = recordOpened(ref, "ad", format, 10L)
    }

    @Test
    fun newestOpenedFirstAndReopenKeepsOneEntry() =
        run {
            val s = store()
            val a = f.document("a.docx")
            val b = f.document("b.docx")
            val c = f.document("c.docx")
            s.open(a)
            s.open(b)
            s.open(c)
            assertEquals(listOf(c, b, a), s.recents().map { it.ref })
            s.recordOpened(a, "yeni ad", FileFormat.Pdf, 99L)
            val recents = s.recents()
            assertEquals(listOf(a, c, b), recents.map { it.ref })
            assertEquals("yeni ad", recents[0].name)
            assertEquals(ModuleFamily.Pdf, recents[0].family)
            assertEquals(99L, recents[0].size)
            assertTrue(recents[0].lastOpenedEpochMillis > recents[1].lastOpenedEpochMillis)
        }

    @Test
    fun unpinnedLimitDropsOldestAndPinnedStay() =
        run {
            val s = store()
            val pinned = f.document("pinned.docx")
            s.open(pinned)
            s.setPinned(pinned, true)
            val refs = (1..LibraryStore.MAX_UNPINNED).map { f.document("d$it.docx") }
            refs.forEach { s.open(it) }
            assertEquals(LibraryStore.MAX_UNPINNED + 1, s.recents().size)
            val extra = f.document("extra.docx")
            s.open(extra)
            val recents = s.recents()
            assertEquals(LibraryStore.MAX_UNPINNED + 1, recents.size)
            assertEquals(extra, recents.first().ref)
            assertTrue(pinned in recents.map { it.ref })
            assertFalse(refs.first() in recents.map { it.ref })
            assertTrue(refs[1] in recents.map { it.ref })
        }

    @Test
    fun pinUnpinRemoveAndClear() =
        run {
            val s = store()
            val a = f.document("a.docx")
            val b = f.document("b.docx")
            s.open(a)
            s.open(b)
            s.setPinned(a, true)
            assertEquals(listOf(false, true), s.recents().map { it.pinned })
            s.open(a)
            assertTrue(s.recents().first { it.ref == a }.pinned, "reopen keeps the pin")
            s.setPinned(a, false)
            assertFalse(s.recents().first { it.ref == a }.pinned)
            s.remove(a)
            assertEquals(listOf(b), s.recents().map { it.ref })
            s.setPinned(b, true)
            s.clearRecents()
            assertEquals(emptyList(), s.recents())
        }

    @Test
    fun switchOffClearsAndStopsRecording() =
        run {
            val s = store()
            val a = f.document("a.docx")
            s.open(a)
            s.addFolder(f.appFolder, "Klasör")
            assertTrue(s.setRememberRecent(false))
            assertEquals(emptyList(), s.recents())
            assertFalse(s.rememberRecent())
            s.open(a)
            assertEquals(emptyList(), s.recents())
            assertFalse(store().rememberRecent(), "switch survives a restart")
            assertEquals(1, store().folders().size, "folders are kept")
            s.setRememberRecent(true)
            s.open(a)
            assertEquals(1, store().recents().size)
        }

    @Test
    fun restartReadsTheSameList() =
        run {
            val s = store()
            val a = f.document("a.docx")
            val b = f.document("Çizelge\tüç.xlsx")
            assertTrue(s.recordOpened(a, "Rapor ığüşöç.docx", FileFormat.Docx, null))
            assertTrue(s.recordOpened(b, "Çizelge\tüç\nsatır.xlsx", FileFormat.Xlsx, 1234L))
            s.setPinned(a, true)
            assertTrue(s.addFolder(f.appFolder, "Belgeler"))
            val again = store()
            assertEquals(s.recents(), again.recents())
            assertEquals(listOf("Çizelge\tüç\nsatır.xlsx", "Rapor ığüşöç.docx"), again.recents().map { it.name })
            assertEquals(s.folders(), again.folders())
            assertTrue(again.removeFolder(f.appFolder))
            assertEquals(emptyList(), store().folders())
        }

    @Test
    fun migratesFromVersion1() =
        run {
            val a = f.document("a.docx")
            val v1 =
                "FAI-LIBRARY 1\n" +
                    "recent\t${a.serialize()}\tEski.docx\tDocx\t5\t777\n" +
                    "recent\tpath:/x/y.pdf\tY.pdf\tPdf\t-\t700\n"
            f.writeStore(v1.encodeToByteArray())
            val s = store()
            val recents = s.recents()
            assertEquals(listOf("Eski.docx", "Y.pdf"), recents.map { it.name })
            assertEquals(listOf(false, false), recents.map { it.pinned })
            assertEquals(5L, recents[0].size)
            assertNull(recents[1].size)
            assertTrue(s.rememberRecent())
            assertEquals(emptyList(), s.folders())
            assertNull(f.readDamaged())
            s.setPinned(a, true)
            val text = assertNotNull(f.readStore()).decodeToString()
            assertTrue(text.startsWith("FAI-LIBRARY 2\n"), text)
            assertTrue(store().recents().first().pinned)
        }

    @Test
    fun damagedStoreStartsEmptyAndIsKeptAside() =
        run {
            val junk = "bu bir kitaplık değil\u0000\u0001".encodeToByteArray()
            f.writeStore(junk)
            val s = store()
            assertEquals(emptyList(), s.recents())
            assertContentEquals(junk, f.readDamaged())
            val a = f.document("a.docx")
            assertTrue(s.open(a))
            assertEquals(listOf(a), store().recents().map { it.ref })
            assertContentEquals(junk, f.readDamaged())
        }

    @Test
    fun newerVersionAndCutOffRecordAreDamaged() =
        run {
            f.writeStore("FAI-LIBRARY 99\nremember\t1\n".encodeToByteArray())
            assertEquals(emptyList(), store().recents())
            assertNotNull(f.readDamaged())
        }

    @Test
    fun missingOrEmptyStoreIsEmptyLibrary() =
        run {
            assertEquals(emptyList(), store().recents())
            assertNull(f.readDamaged())
        }

    @Test
    fun stateAvailableAndMissing() =
        run {
            val s = store()
            val here = f.document("a.docx")
            val gone = f.missing("gone.docx")
            s.open(here)
            s.open(gone)
            val states = s.recentsWithState().associate { it.entry.ref to it.state }
            assertEquals(EntryState.Available, states[here])
            assertEquals(EntryState.Missing, states[gone])
        }
}
