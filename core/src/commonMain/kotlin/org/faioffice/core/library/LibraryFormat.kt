/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import org.faioffice.core.files.FileRef
import org.faioffice.core.formats.FileFormat

/**
 * Text form of the store file: UTF-8, one record per line, fields separated by a tab, `\`-escaped (`\\`, `\t`,
 * `\n`, `\r`). The first line is `FAI-LIBRARY <version>`.
 *
 * Version 2 (current): `remember<TAB>0|1`, `recent<TAB>ref<TAB>name<TAB>format<TAB>size|-<TAB>opened<TAB>pinned(0|1)`
 * (newest first), `folder<TAB>ref<TAB>name<TAB>added`.
 * Version 1: only `recent` lines, without the `pinned` field; no switch, no folders (remembering on, nothing pinned).
 */
internal object LibraryFormat {
    const val VERSION = 2
    private const val MAGIC = "FAI-LIBRARY"

    fun encode(data: LibraryData): ByteArray {
        val out = StringBuilder()
        out
            .append(MAGIC)
            .append(' ')
            .append(VERSION)
            .append('\n')
        out.append("remember\t").append(if (data.rememberRecent) '1' else '0').append('\n')
        for (e in data.recents) {
            out
                .append(
                    listOf(
                        "recent",
                        escape(e.ref.serialize()),
                        escape(e.name),
                        e.format.name,
                        e.size?.toString() ?: "-",
                        e.lastOpenedEpochMillis.toString(),
                        if (e.pinned) "1" else "0",
                    ).joinToString("\t"),
                ).append('\n')
        }
        for (f in data.folders) {
            val fields = listOf("folder", escape(f.ref.serialize()), escape(f.name), f.addedEpochMillis.toString())
            out.append(fields.joinToString("\t")).append('\n')
        }
        return out.toString().encodeToByteArray()
    }

    /** The data, or null when the bytes are not a readable store of a known version. */
    fun decode(bytes: ByteArray): LibraryData? {
        val text =
            try {
                bytes.decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                return null
            }
        val lines = text.split('\n').filter { it.isNotEmpty() }
        val head = lines.firstOrNull()?.split(' ') ?: return null
        if (head.size != 2 || head[0] != MAGIC) return null
        val version = head[1].toIntOrNull() ?: return null
        if (version !in 1..VERSION) return null
        var remember = true
        val recents = mutableListOf<RecentEntry>()
        val folders = mutableListOf<LibraryFolder>()
        for (line in lines.drop(1)) {
            val f = line.split('\t')
            when (f[0]) {
                "remember" -> remember = (f.getOrNull(1) ?: return null) == "1"
                "recent" -> recents += recent(f, version) ?: return null
                "folder" -> folders += folder(f) ?: return null
                else -> return null
            }
        }
        return LibraryData(remember, recents, folders)
    }

    private fun recent(
        f: List<String>,
        version: Int,
    ): RecentEntry? {
        if (f.size != if (version >= 2) 7 else 6) return null
        val ref = FileRef.parse(unescape(f[1]) ?: return null) ?: return null
        val size = if (f[4] == "-") null else f[4].toLongOrNull() ?: return null
        return RecentEntry(
            ref = ref,
            name = unescape(f[2]) ?: return null,
            format = FileFormat.entries.firstOrNull { it.name == f[3] } ?: FileFormat.Unknown,
            size = size,
            lastOpenedEpochMillis = f[5].toLongOrNull() ?: return null,
            pinned = version >= 2 && f[6] == "1",
        )
    }

    private fun folder(f: List<String>): LibraryFolder? {
        if (f.size != 4) return null
        return LibraryFolder(
            ref = FileRef.parse(unescape(f[1]) ?: return null) ?: return null,
            name = unescape(f[2]) ?: return null,
            addedEpochMillis = f[3].toLongOrNull() ?: return null,
        )
    }

    private fun escape(s: String): String =
        buildString {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(c)
                }
            }
        }

    private fun unescape(s: String): String? {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i++]
            if (c != '\\') {
                out.append(c)
                continue
            }
            out.append(
                when (s.getOrNull(i++)) {
                    '\\' -> '\\'
                    't' -> '\t'
                    'n' -> '\n'
                    'r' -> '\r'
                    else -> return null
                },
            )
        }
        return out.toString()
    }
}
