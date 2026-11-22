/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import kotlinx.coroutines.test.runTest
import org.faioffice.core.files.DesktopFileAccess
import org.faioffice.core.files.SafeSave
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.library.EntryState
import org.faioffice.core.library.LibraryStore
import org.faioffice.core.library.SeedResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class DesktopBudgetSeedTest {
    private val seed: Path = Files.createTempDirectory("fai-seed")
    private val access = DesktopFileAccess()

    @AfterTest
    fun cleanUp() = seed.deleteRecursively()

    private fun store(data: Path = DesktopBudgetSeed.dirs(seed).data): LibraryStore {
        val library = data.resolve("library").also { Files.createDirectories(it) }
        val journal = data.resolve("safe-save").also { Files.createDirectories(it) }
        return LibraryStore(
            access,
            SafeSave(access, DesktopFileAccess.refOf(journal)),
            DesktopFileAccess.refOf(library.resolve("library.txt")),
            DesktopFileAccess.refOf(library.resolve("library-damaged.txt")),
        ) { NOW }
    }

    @Test
    fun variableNamesTheSeedFolderOnly() {
        assertNull(DesktopBudgetSeed.folder { null })
        assertNull(DesktopBudgetSeed.folder { " " })
        assertEquals(seed, DesktopBudgetSeed.folder { if (it == "FAI_BUDGET_SEED") seed.toString() else null })
        assertEquals(seed.resolve("data"), DesktopBudgetSeed.dirs(seed).data)
        assertEquals(seed.resolve("data"), DesktopBudgetSeed.dirs(seed).local)
    }

    @Test
    fun seedsRealFilesAndFoldersOnceThenOnlyReads() =
        runTest {
            val result = DesktopBudgetSeed.apply(store(), seed, NOW)
            assertEquals(SeedResult(SeedResult.Outcome.Seeded, 100, 2), result)

            val reread = store()
            val states = reread.recentsWithState().groupingBy { it.state }.eachCount()
            assertEquals(mapOf(EntryState.Available to 90, EntryState.Missing to 10), states)
            val folders = reread.folders().map { DesktopFileAccess.pathOf(it.ref) }
            assertEquals(2, folders.size)
            assertTrue(folders.all { it.isDirectory() && it.listDirectoryEntries().size >= 3 })

            val stored = seed.resolve("data/library/library.txt").readBytes()
            val files = Files.walk(seed.resolve("files")).use { it.count() }
            assertEquals(SeedResult.Outcome.AlreadySeeded, DesktopBudgetSeed.apply(store(), seed, NOW + 1).outcome)
            assertTrue(stored.contentEquals(seed.resolve("data/library/library.txt").readBytes()))
            assertEquals(files, Files.walk(seed.resolve("files")).use { it.count() })
        }

    @Test
    fun aLibraryWithTheUsersFilesIsLeftAlone() =
        runTest {
            val own = Files.write(seed.resolve("mektup.docx"), byteArrayOf(1))
            store().recordOpened(DesktopFileAccess.refOf(own), "mektup.docx", FileFormat.Docx, 1)

            val result = DesktopBudgetSeed.apply(store(), seed, NOW)
            assertEquals(SeedResult(SeedResult.Outcome.Refused, 1, 0), result)
            assertEquals(listOf(DesktopFileAccess.refOf(own)), store().recents().map { it.ref })
            assertTrue(Files.notExists(seed.resolve("files")))
        }

    private companion object {
        const val NOW = 1_795_000_000_000L
    }
}
