/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

/** How a journalled save protects the original; see [SafeSave]. */
internal enum class SaveMode(
    val tag: String,
) {
    /** Temporary sibling file, then atomic replace; the leftover is the temporary file. */
    Atomic("atomic"),

    /** Backup in app-private storage, then truncating write; the leftover is the backup. */
    Backup("backup"),
}

/**
 * One journal entry: a small text file in the journal folder that lives exactly as long as a save is in progress, so
 * the next start can find what a crash left behind. [size] and [sha256] describe the backup ([SaveMode.Backup] only).
 */
internal data class JournalEntry(
    val mode: SaveMode,
    val target: FileRef,
    val targetName: String,
    val leftover: FileRef,
    val size: Long? = null,
    val sha256: String? = null,
) {
    fun encode(): ByteArray =
        buildString {
            append(HEADER).append('\n')
            field("mode", mode.tag)
            field("target", target.serialize())
            field("name", targetName)
            field("leftover", leftover.serialize())
            size?.let { field("size", it.toString()) }
            sha256?.let { field("sha256", it) }
            append(END).append('\n')
        }.encodeToByteArray()

    private fun StringBuilder.field(
        key: String,
        value: String,
    ) {
        append(key).append('=').append(escape(value)).append('\n')
    }

    companion object {
        /** Name prefixes of the journal folder's files; recovery recognises them by these. */
        const val ENTRY_PREFIX = "fai-save-journal"
        const val BACKUP_PREFIX = "fai-save-backup"

        /** Marker in temporary file names next to the document, for example `Rapor.docx.fai-save.123.tmp`. */
        const val TEMP_MARKER = "fai-save"

        /** Upper bound for reading an entry; real entries are a few hundred bytes. */
        const val MAX_BYTES = 64 * 1024

        private const val HEADER = "fai-office-save 1"
        private const val END = "end"

        /** The entry in [bytes], or null when it is incomplete (a crash while writing it) or not an entry. */
        fun decode(bytes: ByteArray): JournalEntry? {
            val lines = bytes.decodeToString().split('\n')
            if (lines.firstOrNull() != HEADER) return null
            // A complete entry ends with "end" and a line break; anything shorter was cut off by a crash.
            val endIndex = lines.lastIndex - 1
            if (endIndex < 1 || lines[endIndex] != END || lines.last().isNotEmpty()) return null
            val fields =
                lines.subList(1, endIndex).associate { line ->
                    val eq = line.indexOf('=')
                    if (eq <= 0) return null
                    line.substring(0, eq) to (unescape(line.substring(eq + 1)) ?: return null)
                }
            val mode = SaveMode.entries.firstOrNull { it.tag == fields["mode"] } ?: return null
            val entry =
                JournalEntry(
                    mode = mode,
                    target = fields["target"]?.let(FileRef::parse) ?: return null,
                    targetName = fields["name"] ?: return null,
                    leftover = fields["leftover"]?.let(FileRef::parse) ?: return null,
                    size = fields["size"]?.let { it.toLongOrNull() ?: return null },
                    sha256 = fields["sha256"],
                )
            if (mode == SaveMode.Backup && (entry.size == null || entry.sha256 == null)) return null
            return entry
        }

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

        private fun unescape(value: String): String? {
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i++]
                if (c != '\\') {
                    out.append(c)
                    continue
                }
                if (i == value.length) return null
                when (value[i++]) {
                    '\\' -> out.append('\\')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> return null
                }
            }
            return out.toString()
        }
    }
}
