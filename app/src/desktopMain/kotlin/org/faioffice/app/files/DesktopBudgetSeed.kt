/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import org.faioffice.core.files.DesktopFileAccess
import org.faioffice.core.files.FileRef
import org.faioffice.core.library.LibrarySeed
import org.faioffice.core.library.LibraryStore
import org.faioffice.core.library.SeedResult
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The budget seed on the desktop (ci/budget.sh, PHASE3_PLAN P3.9). `FAI_BUDGET_SEED=<folder>` makes the app keep
 * everything of its own in that folder instead of the user's app data folder: the library and journal under
 * `data/` ([dirs], the single-instance lock too), the seeded files and the two added folders under `files/`. The
 * user's real library is never read or touched. The first launch with an empty folder writes the seed ([apply]),
 * every later one finds it there and only reads.
 */
object DesktopBudgetSeed {
    const val ENV = "FAI_BUDGET_SEED"

    /** The seed folder named by [ENV]; null when the variable is unset or blank. */
    fun folder(env: (String) -> String?): Path? = env(ENV)?.takeIf { it.isNotBlank() }?.let { Path.of(it) }

    /** App data folders inside the seed [folder]. */
    fun dirs(folder: Path): DesktopDirs = folder.resolve("data").let { DesktopDirs(it, it) }

    /**
     * Seeds [store] from the files under [folder]`/files`: two real folders with most of the files, a third folder
     * outside the library with the rest, the missing ones never created. Files are created only when the store
     * took the seed, so later launches write nothing.
     */
    suspend fun apply(
        store: LibraryStore,
        folder: Path,
        now: Long,
    ): SeedResult {
        val root = folder.resolve("files").toAbsolutePath().normalize()
        val folders = LibrarySeed.folderNames.map { root.resolve(it) }
        val elsewhere = root.resolve(ELSEWHERE)
        val files = LibrarySeed.files()

        fun pathOf(file: LibrarySeed.File): Path = (file.folder?.let(folders::get) ?: elsewhere).resolve(file.name)
        val result =
            store.seed(
                LibrarySeed.recents(files, now) { DesktopFileAccess.refOf(pathOf(it)) },
                LibrarySeed.folders(now) { DesktopFileAccess.refOf(folders[it]) },
            ) { ref -> ref.kind == FileRef.Kind.Path && isUnder(ref.location, root) }
        if (result.outcome == SeedResult.Outcome.Seeded) {
            try {
                (folders + elsewhere).forEach { Files.createDirectories(it) }
                files.filterNot { it.missing }.forEach { Files.write(pathOf(it), ByteArray(it.size.toInt())) }
            } catch (e: IOException) {
                // The entries are written; their files then show as missing, still a full Home to measure.
                DesktopFiles.stderr("budget seed: files not written (${e::class.simpleName})")
            }
        }
        return result
    }

    private fun isUnder(
        location: String,
        root: Path,
    ): Boolean =
        try {
            Path.of(location).startsWith(root)
        } catch (_: InvalidPathException) {
            false
        }

    private const val ELSEWHERE = "İndirilenler"
}
