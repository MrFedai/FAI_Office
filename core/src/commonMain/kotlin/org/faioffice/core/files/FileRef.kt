/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

/**
 * Identifies a file or folder independently of the platform: the app keeps references, never raw paths.
 *
 * [location] is opaque to everything except the [FileAccess] implementation that made it: an absolute path on
 * desktop, a content URI on Android, a session handle id on the Web, a key in the in-memory fake. The display name,
 * size and type always come from [FileAccess.info], never from parsing [location] (a content URI has no file name).
 *
 * [serialize] / [parse] give a stable string for recent files and settings; whether the referenced file is still
 * reachable after a restart depends on [Kind.survivesRestart] and on the platform grant.
 */
data class FileRef(
    val kind: Kind,
    val location: String,
) {
    init {
        require(location.isNotEmpty()) { "empty file reference" }
    }

    /** Which implementation owns the reference. [tag] is the serialised prefix and must never change. */
    enum class Kind(
        val tag: String,
        val survivesRestart: Boolean,
    ) {
        /** Absolute file system path (Windows, Linux, macOS; `java.nio`). */
        Path("path", survivesRestart = true),

        /** Android Storage Access Framework URI, reachable after restart while its persisted grant is held. */
        ContentUri("content", survivesRestart = true),

        /** Web file picked through a file input; the handle lives only as long as the page. */
        WebSession("web", survivesRestart = false),

        /** Key in [InMemoryFileAccess]; tests only. */
        Memory("mem", survivesRestart = false),
    }

    /** Stable string form: `<tag>:<location>`, for example `path:C:\Belgeler\Çizelge.xlsx`. */
    fun serialize(): String = "${kind.tag}:$location"

    override fun toString(): String = serialize()

    companion object {
        /** Reads a [serialize] result back; null for an unknown tag or an empty location. */
        fun parse(serialized: String): FileRef? {
            val colon = serialized.indexOf(':')
            if (colon <= 0 || colon == serialized.lastIndex) return null
            val tag = serialized.substring(0, colon)
            val kind = Kind.entries.firstOrNull { it.tag == tag } ?: return null
            return FileRef(kind, serialized.substring(colon + 1))
        }
    }
}

/** What a reference points at. The document format (PDF, DOCX, ...) is format detection's job, not this one. */
enum class EntryKind { File, Folder }

/**
 * Metadata of one file or folder, as the platform reports it.
 *
 * @property name display name, from the platform (SAF `DISPLAY_NAME`, the last path segment, the picked file name).
 * @property size byte count; null for folders and when the provider does not report it.
 * @property modifiedEpochMillis last modification time in milliseconds since 1970-01-01 UTC; null when unknown.
 * @property mimeType MIME type from the platform when it has one; a hint only, detection reads the content.
 * @property writable whether the platform says the content can be replaced (via the safe-save layer).
 */
data class FileInfo(
    val ref: FileRef,
    val name: String,
    val kind: EntryKind,
    val size: Long?,
    val modifiedEpochMillis: Long?,
    val mimeType: String?,
    val writable: Boolean,
)
