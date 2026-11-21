/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.faioffice.core.files.EntryKind
import org.faioffice.core.files.FileError
import org.faioffice.core.files.FileOp
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.InMemoryFileAccess
import org.faioffice.core.files.PersistedGrants
import org.faioffice.core.files.SafeSave
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.library.LibraryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileHubTest {
    private val pdf = "%PDF-1.7\n1 0 obj <<>> endobj\ntrailer <<>>\n%%EOF\n".encodeToByteArray()

    private class FakeGrants(
        override val limit: Int = 10,
        val refused: Set<FileRef> = emptySet(),
    ) : PersistedGrants {
        val grants = LinkedHashSet<FileRef>()

        override suspend fun held(): Set<FileRef> = grants.toSet()

        override suspend fun take(ref: FileRef): Boolean = (ref !in refused).also { if (it) grants += ref }

        override suspend fun release(ref: FileRef) {
            grants -= ref
        }
    }

    private class Env(
        scope: TestScope,
        val fake: InMemoryFileAccess = InMemoryFileAccess(),
        val grants: FakeGrants = FakeGrants(),
    ) {
        val logs = mutableListOf<String>()
        val store =
            LibraryStore(
                fake,
                SafeSave(fake, fake.addFolder("/uygulama/kayıt")),
                fake.ref("/uygulama/kitaplık"),
                fake.ref("/uygulama/kitaplık.bozuk"),
            ) { 1L }
        val library = FileLibrary(store, grants)
        val hub = FileHub(fake, scope.backgroundScope, library) { logs += it }
    }

    private fun content(id: String) = FileRef(FileRef.Kind.ContentUri, "content://sağlayıcı/document/$id")

    @Test
    fun openedFilesArriveInOrderWithTheirFormat() =
        runTest {
            val env = Env(this)
            val report = env.fake.addFile("/Belgeler/Rapor Şubat.pdf", pdf)
            val note = env.fake.addFile("/Belgeler/not", "merhaba dünya".encodeToByteArray(), mimeType = "text/plain")
            env.hub.open(listOf(report, note), OpenOrigin.Share)
            val events =
                env.hub.events
                    .take(2)
                    .toList()
                    .map { assertIs<FileEvent.Opened>(it).request }
            assertEquals(listOf(report, note), events.map { it.ref })
            assertEquals(FileFormat.Pdf, events[0].detection?.format)
            assertEquals("Rapor Şubat.pdf", events[0].info?.name)
            assertEquals(FileFormat.PlainText, events[1].detection?.format)
            assertTrue(events.all { it.origin == OpenOrigin.Share && it.error == null })
            assertTrue(env.logs.first().startsWith("open Share: Pdf, ${pdf.size} bytes in "))
        }

    @Test
    fun unreadableFilesArriveWithTheirError() =
        runTest {
            val env = Env(this)
            val locked = env.fake.addFile("/kilitli.pdf", pdf)
            env.fake.faults = { op, ref -> if (op == FileOp.Info && ref == locked) FileError.PermissionLost else null }
            env.hub.open(listOf(env.fake.ref("/yok.pdf"), locked, env.fake.addFolder("/klasör")), OpenOrigin.OpenWith)
            val requests =
                env.hub.events
                    .take(3)
                    .toList()
                    .map { assertIs<FileEvent.Opened>(it).request }
            assertEquals(FileError.NotFound, requests[0].error)
            assertEquals(FileError.PermissionLost, requests[1].error)
            assertEquals(FileError.WrongKind(EntryKind.File), requests[2].error)
            assertTrue(requests.all { it.detection == null })
        }

    @Test
    fun eventsWaitForTheirCollector() =
        runTest {
            val env = Env(this)
            env.hub.open(listOf(env.fake.addFile("/erken.pdf", pdf)), OpenOrigin.OpenWith)
            testScheduler.advanceUntilIdle()
            val event = assertIs<FileEvent.Opened>(env.hub.events.first())
            assertEquals(FileFormat.Pdf, event.request.detection?.format)
        }

    @Test
    fun addedFolderIsListedTimedAndKept() =
        runTest {
            val env = Env(this)
            val folder = env.fake.addFolder("/Belgeler Öğüş")
            repeat(5) { env.fake.addFile("/Belgeler Öğüş/dosya $it.txt", byteArrayOf(1)) }
            env.hub.addFolder(folder)
            val added = assertIs<FileEvent.FolderAdded>(env.hub.events.first())
            assertEquals("Belgeler Öğüş", added.name)
            assertEquals(5, added.listing.entries)
            assertTrue(added.grantKept)
            assertEquals(listOf(folder), env.store.folders().map { it.ref })
            assertTrue(env.logs.any { it.startsWith("list 5 entries in ") })
            assertEquals(1, env.hub.measureFolders().size)

            env.hub.addFolder(env.fake.ref("/yok"))
            assertEquals(FileError.NotFound, assertIs<FileEvent.FolderFailed>(env.hub.events.first()).error)
        }

    @Test
    fun libraryTakesAndReleasesGrants() =
        runTest {
            val tree = content("ağaç")
            val shared = content("paylaşılan")
            val env = Env(this, grants = FakeGrants(refused = setOf(shared)))
            assertTrue(env.library.addFolder(tree, "Ağaç"))
            assertEquals(setOf(tree), env.grants.grants)

            val info = env.fake.addFile("/a.pdf", pdf).let { env.hub.resolve(it, OpenOrigin.Picker) }
            val picked = info.copy(ref = content("seçilen"))
            assertTrue(env.library.keepRecent(picked))
            assertEquals(setOf(tree, picked.ref), env.grants.grants)

            // A shared file has no persistable grant: it stays in recents, its grant just is not held.
            env.library.keepRecent(info.copy(ref = shared))
            assertFalse(shared in env.grants.grants)

            env.library.remove(picked.ref)
            assertEquals(setOf(tree), env.grants.grants)
            env.library.removeFolder(tree)
            assertTrue(env.grants.grants.isEmpty())
        }

    @Test
    fun grantLimitDropsTheOldestUnpinned() =
        runTest {
            val env = Env(this, grants = FakeGrants(limit = 2))
            val (first, second, third) = listOf(content("1"), content("2"), content("3"))
            var now = 0L
            val journal = env.fake.addFolder("/k")
            val store =
                LibraryStore(env.fake, SafeSave(env.fake, journal), env.fake.ref("/s"), env.fake.ref("/d")) { ++now }
            val library = FileLibrary(store, env.grants)
            val base = env.hub.resolve(env.fake.addFile("/a.pdf", pdf), OpenOrigin.Picker)
            listOf(first, second, third).forEach { library.keepRecent(base.copy(ref = it)) }
            assertEquals(setOf(second, third), env.grants.grants)
            library.setPinned(first, true)
            assertEquals(setOf(first, third), env.grants.grants)
            library.clearRecents()
            assertTrue(env.grants.grants.isEmpty())
        }

    @Test
    fun requestWithoutDetectionIsNotKept() =
        runTest {
            val env = Env(this)
            val missing = env.hub.resolve(env.fake.ref("/yok.pdf"), OpenOrigin.Picker)
            assertNull(missing.info)
            assertFalse(env.library.keepRecent(missing))
            assertTrue(env.store.recents().isEmpty())
        }
}
