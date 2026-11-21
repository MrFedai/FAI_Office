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

    /** One field as a v1 writer stored it: `\`-escaped like every version (LibraryFormat). */
    private fun v1Field(s: String): String =
        s
            .replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

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
            assertTrue(recents[0].lastOpenedEpochMillis > recents[1].lastOpenedEpochMillis, "reopened entry is newest")
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
            assertTrue(pinned in recents.map { it.ref }, "pinned entry is never dropped")
            assertFalse(refs.first() in recents.map { it.ref }, "oldest unpinned entry is dropped")
            assertTrue(refs[1] in recents.map { it.ref }, "second oldest unpinned entry stays")
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
            assertFalse(s.recents().first { it.ref == a }.pinned, "unpinned")
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
            assertTrue(s.setRememberRecent(false), "switch off reaches the disk")
            assertEquals(emptyList(), s.recents())
            assertFalse(s.rememberRecent(), "switch is off")
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
            assertTrue(s.recordOpened(a, "Rapor ığüşöç.docx", FileFormat.Docx, null), "first record saved")
            assertTrue(s.recordOpened(b, "Çizelge\tüç\nsatır.xlsx", FileFormat.Xlsx, 1234L), "second record saved")
            s.setPinned(a, true)
            assertTrue(s.addFolder(f.appFolder, "Belgeler"), "folder saved")
            val again = store()
            assertEquals(s.recents(), again.recents())
            assertEquals(listOf("Çizelge\tüç\nsatır.xlsx", "Rapor ığüşöç.docx"), again.recents().map { it.name })
            assertEquals(s.folders(), again.folders())
            assertTrue(again.removeFolder(f.appFolder), "folder removal saved after a restart")
            assertEquals(emptyList(), store().folders())
        }

    @Test
    fun migratesFromVersion1() =
        run {
            val a = f.document("a.docx")
            // Fields are `\`-escaped in v1 too. Written raw, a Windows path (`C:\Users\...`: `\U` is no escape) made
            // the whole store unreadable, so it was set aside as damaged; the third line keeps that case on Linux.
            val win = FileRef(FileRef.Kind.Path, "D:\\new\\tablo\\Çizelge.xlsx")
            val v1 =
                "FAI-LIBRARY 1\n" +
                    "recent\t${v1Field(a.serialize())}\tEski.docx\tDocx\t5\t777\n" +
                    "recent\tpath:/x/y.pdf\tY.pdf\tPdf\t-\t700\n" +
                    "recent\t${v1Field(win.serialize())}\tÇizelge.xlsx\tXlsx\t9\t600\n"
            f.writeStore(v1.encodeToByteArray())
            val s = store()
            val recents = s.recents()
            assertNull(f.readDamaged(), "a v1 store is read, not set aside as damaged")
            assertEquals(listOf("Eski.docx", "Y.pdf", "Çizelge.xlsx"), recents.map { it.name }, "v1 entries in order")
            assertEquals(listOf(a, FileRef(FileRef.Kind.Path, "/x/y.pdf"), win), recents.map { it.ref }, "v1 refs")
            assertEquals(listOf(false, false, false), recents.map { it.pinned }, "v1 has no pins")
            assertEquals(5L, recents[0].size, "v1 size")
            assertNull(recents[1].size, "v1 unknown size")
            assertTrue(s.rememberRecent(), "v1 means remembering is on")
            assertEquals(emptyList(), s.folders(), "v1 has no folders")
            assertTrue(s.setPinned(a, true), "pin after migration reaches the disk")
            assertTrue(s.setPinned(win, true), "pin of the Windows path reaches the disk")
            val text = assertNotNull(f.readStore(), "store written").decodeToString()
            assertTrue(text.startsWith("FAI-LIBRARY 2\n"), text)
            val again = store().recents()
            assertEquals(listOf(true, false, true), again.map { it.pinned }, "pins survive a restart:\n$text")
            assertEquals(recents.map { it.ref }, again.map { it.ref }, "refs survive the v2 rewrite:\n$text")
        }

    @Test
    fun windowsPathsSurviveARestart() =
        run {
            // Linux and the in-memory fake see the Windows forms too: drive letter, UNC, escape-like `\n` / `\t`.
            val refs =
                listOf(
                    "C:\\Users\\Öğüş\\Belgeler\\Rapor ığüşöç.docx",
                    "D:\\new\\tablo\\rapor.pdf",
                    "\\\\sunucu\\pay\\Çizelge.xlsx",
                    "C:\\a\\",
                ).map { FileRef(FileRef.Kind.Path, it) }
            refs.forEach { assertEquals(it, FileRef.parse(it.serialize()), "FileRef round trip of $it") }
            val s = store()
            refs.forEach { assertTrue(s.recordOpened(it, "ad", FileFormat.Docx, 1L), "record of $it saved") }
            val folder = FileRef(FileRef.Kind.Path, "E:\\Belgeler Öğüş")
            assertTrue(s.addFolder(folder, "Belgeler"), "Windows folder saved")
            val again = store()
            assertNull(f.readDamaged(), "store with Windows paths is not damaged")
            assertEquals(refs.reversed(), again.recents().map { it.ref }, "Windows paths survive a restart")
            assertEquals(listOf(folder), again.folders().map { it.ref }, "Windows folder survives a restart")
            // A reference made again from the saved string (as the platform layer does) finds the same entry.
            val pin = assertNotNull(FileRef.parse(refs[1].serialize()))
            assertTrue(again.setPinned(pin, true), "pin saved")
            assertTrue(store().recents().first { it.ref == refs[1] }.pinned, "pin of a Windows path survives")
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
            assertTrue(s.open(a), "store replacing a damaged one is written")
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
