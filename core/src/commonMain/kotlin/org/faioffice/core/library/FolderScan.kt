/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.faioffice.core.files.EntryKind
import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileInfo
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.FileResult
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.INVARIANT
import org.faioffice.core.formats.ModuleFamily
import org.faioffice.core.text.lowercase

/** Limits of one folder scan. [maxDepth] counts folder levels below the scanned folder (0 = its direct files). */
data class ScanLimits(
    val maxDepth: Int = 3,
    val maxFiles: Int = 2000,
)

/** A scanned file with the format its extension names ([FileFormat.Unknown] without one; no content detection). */
data class ScannedFile(
    val info: FileInfo,
    val format: FileFormat,
)

/**
 * Result of [scanFolder]. [truncated]: the file limit stopped the scan. [skippedFolders]: subfolders that could not
 * be listed (permission, vanished) or lay below the depth limit.
 */
data class FolderScan(
    val files: List<ScannedFile>,
    val truncated: Boolean,
    val skippedFolders: Int,
)

/**
 * Lists the files below [folder] breadth first, for the category screens: bounded by [limits], optionally only files
 * of [family] (by extension), results in memory only. Cancellation is honoured between folders and entries. A
 * [folder] that cannot be listed is a failure; problems below it only count in [FolderScan.skippedFolders].
 */
suspend fun FileAccess.scanFolder(
    folder: FileRef,
    family: ModuleFamily? = null,
    limits: ScanLimits = ScanLimits(),
): FileResult<FolderScan> {
    val found = mutableListOf<ScannedFile>()
    var skipped = 0
    var truncated = false
    var level = listOf(folder)
    var depth = 0
    while (level.isNotEmpty() && !truncated) {
        val next = mutableListOf<FileRef>()
        for (dir in level) {
            currentCoroutineContext().ensureActive()
            val children =
                when (val listed = list(dir)) {
                    is FileResult.Success -> {
                        listed.value
                    }

                    is FileResult.Failure -> {
                        if (dir == folder) return listed
                        skipped++
                        continue
                    }
                }
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (child.kind == EntryKind.Folder) {
                    if (depth < limits.maxDepth) next += child.ref else skipped++
                    continue
                }
                val format = formatOf(child.name)
                if (family != null && format.family != family) continue
                if (found.size >= limits.maxFiles) {
                    truncated = true
                    break
                }
                found += ScannedFile(child, format)
            }
            if (truncated) break
        }
        level = next
        depth++
    }
    return FileResult.Success(FolderScan(found, truncated, skipped))
}

private fun formatOf(name: String): FileFormat {
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return FileFormat.Unknown
    return FileFormat.byExtension(name.substring(dot + 1).lowercase(INVARIANT)) ?: FileFormat.Unknown
}
