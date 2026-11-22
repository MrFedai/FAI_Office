/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import android.net.Uri
import org.faioffice.core.files.FileRef
import org.faioffice.core.library.LibrarySeed
import org.faioffice.core.library.LibraryStore
import org.faioffice.core.library.SeedResult

/**
 * The budget seed on Android (ci/budget.sh, PHASE3_PLAN P3.9; [AndroidFiles.EXTRA_BUDGET_SEED]). The release APK is
 * not debuggable, so nothing can be pushed into the app's files from outside: the app writes the seed itself into
 * its own library, and [LibraryStore.seed] refuses a library holding anything of the user's.
 *
 * Without the user picking files there are no grants, so the entries point at shared storage documents under
 * `FAI-budget-seed/` that the app holds no grant for: the real documents provider answers each state check with a
 * permission denial, and Home shows them as "access lost", the state of entries whose grant went away. The checks
 * still cross into the provider like real ones; listing the two folders fails the same way.
 */
internal object AndroidBudgetSeed {
    private const val AUTHORITY = "content://com.android.externalstorage.documents"
    private const val ROOT = "primary:FAI-budget-seed"
    private const val ELSEWHERE = "Download"

    private val documentPrefix = "$AUTHORITY/document/" + Uri.encode(ROOT)
    private val treePrefix = "$AUTHORITY/tree/" + Uri.encode(ROOT)

    suspend fun apply(
        store: LibraryStore,
        now: Long,
    ): SeedResult =
        store.seed(
            LibrarySeed.recents(LibrarySeed.files(), now) { file ->
                val folder = file.folder?.let(LibrarySeed.folderNames::get) ?: ELSEWHERE
                ref("$AUTHORITY/document/" + Uri.encode("$ROOT/$folder/${file.name}"))
            },
            LibrarySeed.folders(now) { ref("$AUTHORITY/tree/" + Uri.encode("$ROOT/${LibrarySeed.folderNames[it]}")) },
        ) { it.kind == FileRef.Kind.ContentUri && isSeed(it.location) }

    private fun isSeed(location: String): Boolean =
        location.startsWith(documentPrefix) || location.startsWith(treePrefix)

    private fun ref(location: String) = FileRef(FileRef.Kind.ContentUri, location)
}
