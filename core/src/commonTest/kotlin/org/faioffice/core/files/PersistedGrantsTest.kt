/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.test.runTest
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.library.LibraryFolder
import org.faioffice.core.library.RecentEntry
import org.faioffice.core.library.wantedGrants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistedGrantsTest {
    private fun doc(id: String) = FileRef(FileRef.Kind.ContentUri, "content://com.android.externalstorage.documents/document/$id")

    private val tree = FileRef(FileRef.Kind.ContentUri, "content://com.android.externalstorage.documents/tree/primary%3ABelgeler")

    private fun inTree(id: String) = FileRef(FileRef.Kind.ContentUri, "${tree.location}/document/primary%3ABelgeler%2F$id")

    /** Grants in memory with a platform limit; refs in [refused] have no persistable grant on offer. */
    private class FakeGrants(
        override val limit: Int,
        val refused: Set<FileRef> = emptySet(),
    ) : PersistedGrants {
        val grants = LinkedHashSet<FileRef>()

        override suspend fun held(): Set<FileRef> = grants.toSet()

        override suspend fun take(ref: FileRef): Boolean {
            if (ref in refused || grants.size >= limit) return false
            grants += ref
            return true
        }

        override suspend fun release(ref: FileRef) {
            grants -= ref
        }
    }

    @Test
    fun planTakesMissingAndReleasesUnwanted() {
        val plan = planGrants(held = setOf(doc("a"), doc("old")), wanted = listOf(doc("a"), doc("b")), limit = 10)
        assertEquals(listOf(doc("b")), plan.take)
        assertEquals(listOf(doc("old")), plan.release)
        assertEquals(emptyList(), plan.dropped)
    }

    @Test
    fun limitKeepsTheMostImportantAndDropsTheTail() {
        val wanted = (1..5).map { doc("$it") }
        val plan = planGrants(held = setOf(doc("5")), wanted = wanted, limit = 3)
        assertEquals(listOf(doc("1"), doc("2"), doc("3")), plan.take)
        assertEquals(listOf(doc("5")), plan.release)
        assertEquals(listOf(doc("4"), doc("5")), plan.dropped)
    }

    @Test
    fun filesInsideAWantedTreeNeedNoGrantOfTheirOwn() {
        val plan = planGrants(held = setOf(inTree("a.pdf")), wanted = listOf(tree, inTree("a.pdf"), doc("x")), limit = 2)
        assertEquals(listOf(tree, doc("x")), plan.take)
        assertEquals(listOf(inTree("a.pdf")), plan.release)
        assertTrue(coveredBy(inTree("a.pdf"), tree))
        assertFalse(coveredBy(doc("a"), tree))
        assertFalse(coveredBy(inTree("a.pdf"), inTree("b")))
        assertFalse(coveredBy(FileRef(FileRef.Kind.ContentUri, tree.location + "2/document/x"), tree))
    }

    @Test
    fun onlyContentUrisTakeGrantsAndDuplicatesCountOnce() {
        val path = FileRef(FileRef.Kind.Path, "/home/u/a.txt")
        val plan = planGrants(held = emptySet(), wanted = listOf(path, doc("a"), doc("a")), limit = 1)
        assertEquals(listOf(doc("a")), plan.take)
        assertEquals(emptyList(), plan.dropped)
    }

    @Test
    fun syncReleasesBeforeTakingSoAFullTableHasRoom() =
        runTest {
            val grants = FakeGrants(limit = 2)
            grants.grants += listOf(doc("old1"), doc("old2"))
            val result = grants.sync(listOf(doc("a"), doc("b"), doc("c")))
            assertEquals(setOf(doc("a"), doc("b")), grants.held())
            assertEquals(listOf(doc("a"), doc("b")), result.taken)
            assertEquals(listOf(doc("old1"), doc("old2")), result.released)
            assertEquals(listOf(doc("c")), result.dropped)
        }

    @Test
    fun refusedGrantIsReportedNotRetried() =
        runTest {
            val shared = doc("shared-by-mail")
            val grants = FakeGrants(limit = 5, refused = setOf(shared))
            val result = grants.sync(listOf(shared, doc("a")))
            assertEquals(listOf(shared), result.failed)
            assertEquals(setOf(doc("a")), grants.held())
            assertEquals(listOf(shared), grants.sync(listOf(shared, doc("a"))).failed)
        }

    @Test
    fun removingEverythingReleasesEverything() =
        runTest {
            val grants = FakeGrants(limit = 5)
            grants.sync(listOf(tree, doc("a")))
            val result = grants.sync(emptyList())
            assertEquals(setOf(tree, doc("a")), result.released.toSet())
            assertTrue(grants.held().isEmpty())
        }

    @Test
    fun libraryOrderIsFoldersThenPinnedThenNewest() {
        fun recent(
            id: String,
            opened: Long,
            pinned: Boolean = false,
        ) = RecentEntry(doc(id), "$id.pdf", FileFormat.Pdf, 1, opened, pinned)
        val recents = listOf(recent("new", 30), recent("pin", 10, pinned = true), recent("mid", 20))
        val folders =
            listOf(
                LibraryFolder(doc("f2"), "İkinci", addedEpochMillis = 5),
                LibraryFolder(doc("f1"), "Birinci", addedEpochMillis = 1),
            )
        assertEquals(
            listOf(doc("f1"), doc("f2"), doc("pin"), doc("new"), doc("mid")),
            wantedGrants(recents, folders),
        )
    }

    @Test
    fun noGrantsPlatformAcceptsEverything() =
        runTest {
            assertTrue(NoPersistedGrants.take(doc("a")))
            assertTrue(NoPersistedGrants.held().isEmpty())
        }
}
