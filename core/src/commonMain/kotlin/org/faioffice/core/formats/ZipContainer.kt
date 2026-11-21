/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.core.formats

import org.faioffice.core.files.RandomAccessReader
import org.faioffice.core.files.readBytes

/** One central directory entry the detector cares about. */
internal class ZipEntry(
    val name: String,
    val method: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long,
)

/**
 * What the central directory says: the entries detection looks for ([wanted] names only), how many entries were
 * read, and whether the directory is inconsistent ([damaged]).
 */
internal class ZipDirectory(
    val entries: Map<String, ZipEntry>,
    val entryCount: Int,
    val damaged: Boolean,
)

/**
 * Bounded ZIP reading for detection: the end-of-central-directory record from the tail, at most [MAX_DIRECTORY_BYTES]
 * of the central directory and [MAX_ENTRIES] entries, and single small parts through [readPart]. Nothing else of the
 * file is touched.
 */
internal object ZipContainer {
    const val MAX_ENTRIES = 4096
    const val MAX_DIRECTORY_BYTES = 512 * 1024
    const val MAX_PART_COMPRESSED = 256 * 1024
    const val MAX_PART_OUTPUT = 64 * 1024

    /** The central directory, keeping only the [wanted] entry names; null when no end record is found. */
    suspend fun directory(
        reader: RandomAccessReader,
        wanted: Set<String>,
    ): ZipDirectory? {
        val end = findEnd(reader) ?: return null
        val inconsistent =
            end.directoryOffset < 0 || end.directorySize < 0 || end.entryCount < 0 ||
                end.directoryOffset + end.directorySize > end.recordOffset
        if (inconsistent) return ZipDirectory(emptyMap(), 0, damaged = true)
        val toRead = minOf(end.directorySize, MAX_DIRECTORY_BYTES.toLong()).toInt()
        val bytes = reader.readBytes(end.directoryOffset, toRead)
        return parseDirectory(bytes, readAll = toRead.toLong() == end.directorySize, end.entryCount, wanted)
    }

    /**
     * The bytes of [entry], at most [maxOutput] (stored or deflated). Null when the part cannot be read: unknown
     * method, bad local header, damaged deflate data. [InflateResult.limitReached] tells a cut part.
     */
    suspend fun readPart(
        reader: RandomAccessReader,
        entry: ZipEntry,
        maxOutput: Int = MAX_PART_OUTPUT,
    ): InflateResult? {
        if (entry.localHeaderOffset < 0 || entry.compressedSize < 0) return null
        val local = reader.readBytes(entry.localHeaderOffset, LOCAL_HEADER)
        if (local.size < LOCAL_HEADER || local.u32(0) != LOCAL_SIG) return null
        val dataOffset = entry.localHeaderOffset + LOCAL_HEADER + local.u16(26) + local.u16(28)
        return when (entry.method) {
            METHOD_STORED -> {
                val want = minOf(entry.compressedSize, maxOutput.toLong())
                val bytes = reader.readBytes(dataOffset, want.toInt())
                if (bytes.size.toLong() < want) {
                    null
                } else {
                    InflateResult(bytes, want == entry.compressedSize, limitReached = want < entry.compressedSize)
                }
            }

            METHOD_DEFLATED -> {
                val compressed =
                    reader.readBytes(dataOffset, minOf(entry.compressedSize, MAX_PART_COMPRESSED.toLong()).toInt())
                try {
                    inflate(compressed, maxOutput)
                } catch (_: MalformedDataException) {
                    null
                }
            }

            else -> {
                null
            }
        }
    }

    /** Name and stored content of the first local header, for a ZIP whose directory is missing (truncated file). */
    fun firstLocalEntry(head: ByteArray): Pair<String, ByteArray?>? {
        if (head.size < LOCAL_HEADER || head.u32(0) != LOCAL_SIG) return null
        val nameLength = head.u16(26)
        val dataOffset = LOCAL_HEADER + nameLength + head.u16(28)
        if (LOCAL_HEADER + nameLength > head.size) return null
        val name = head.copyOfRange(LOCAL_HEADER, LOCAL_HEADER + nameLength).decodeToString()
        val size = head.u32(18)
        val stored =
            if (head.u16(8) == METHOD_STORED && dataOffset + size <= head.size) {
                head.copyOfRange(dataOffset, (dataOffset + size).toInt())
            } else {
                null
            }
        return name to stored
    }

    private class EndRecord(
        val recordOffset: Long,
        val entryCount: Long,
        val directorySize: Long,
        val directoryOffset: Long,
    )

    private suspend fun findEnd(reader: RandomAccessReader): EndRecord? {
        if (reader.size < END_RECORD) return null
        val tailStart = maxOf(0L, reader.size - END_RECORD - MAX_COMMENT)
        val tail = reader.readBytes(tailStart, (reader.size - tailStart).toInt())
        var at = tail.size - END_RECORD
        while (at >= 0 && tail.u32(at) != END_SIG) at--
        if (at < 0) return null
        val recordOffset = tailStart + at
        val count = tail.u16(at + 10).toLong()
        val size = tail.u32(at + 12)
        val offset = tail.u32(at + 16)
        val needs64 = count == 0xFFFFL || size == 0xFFFFFFFFL || offset == 0xFFFFFFFFL
        return if (needs64) {
            zip64End(reader, recordOffset) ?: EndRecord(recordOffset, count, size, offset)
        } else {
            EndRecord(recordOffset, count, size, offset)
        }
    }

    private suspend fun zip64End(
        reader: RandomAccessReader,
        recordOffset: Long,
    ): EndRecord? {
        if (recordOffset < ZIP64_LOCATOR) return null
        val locator = reader.readBytes(recordOffset - ZIP64_LOCATOR, ZIP64_LOCATOR)
        if (locator.u32(0) != ZIP64_LOCATOR_SIG) return null
        val at = locator.u64(8)
        if (at < 0 || at > recordOffset - ZIP64_END) return null
        val end = reader.readBytes(at, ZIP64_END)
        if (end.size < ZIP64_END || end.u32(0) != ZIP64_END_SIG) return null
        return EndRecord(at, end.u64(32), end.u64(40), end.u64(48))
    }

    private fun parseDirectory(
        bytes: ByteArray,
        readAll: Boolean,
        claimed: Long,
        wanted: Set<String>,
    ): ZipDirectory {
        val found = mutableMapOf<String, ZipEntry>()
        var at = 0
        var count = 0
        var broken = false
        while (count < MAX_ENTRIES && count < claimed) {
            if (at + DIR_HEADER > bytes.size) {
                broken = readAll
                break
            }
            if (bytes.u32(at) != DIR_SIG) {
                broken = true
                break
            }
            val nameLength = bytes.u16(at + 28)
            val extraLength = bytes.u16(at + 30)
            val next = at + DIR_HEADER + nameLength + extraLength + bytes.u16(at + 32)
            if (next > bytes.size) {
                broken = readAll
                break
            }
            val name = bytes.copyOfRange(at + DIR_HEADER, at + DIR_HEADER + nameLength).decodeToString()
            if (name in wanted) found[name] = entryAt(bytes, at, name, at + DIR_HEADER + nameLength, extraLength)
            at = next
            count++
        }
        return ZipDirectory(found, count, damaged = broken)
    }

    private fun entryAt(
        bytes: ByteArray,
        at: Int,
        name: String,
        extraAt: Int,
        extraLength: Int,
    ): ZipEntry {
        var uncompressed = bytes.u32(at + 24)
        var compressed = bytes.u32(at + 20)
        var offset = bytes.u32(at + 42)
        // ZIP64 extended information: the 8-byte values present in this order for each field set to 0xFFFFFFFF.
        var e = extraAt
        while (e + 4 <= extraAt + extraLength) {
            val id = bytes.u16(e)
            val size = bytes.u16(e + 2)
            if (id == 1) {
                var f = e + 4
                if (uncompressed == MAX_U32 && f + 8 <= e + 4 + size) uncompressed = bytes.u64(f).also { f += 8 }
                if (compressed == MAX_U32 && f + 8 <= e + 4 + size) compressed = bytes.u64(f).also { f += 8 }
                if (offset == MAX_U32 && f + 8 <= e + 4 + size) offset = bytes.u64(f)
                break
            }
            e += 4 + size
        }
        return ZipEntry(name, bytes.u16(at + 10), compressed, uncompressed, offset)
    }

    private const val LOCAL_SIG = 0x04034B50L
    private const val DIR_SIG = 0x02014B50L
    private const val END_SIG = 0x06054B50L
    private const val ZIP64_LOCATOR_SIG = 0x07064B50L
    private const val ZIP64_END_SIG = 0x06064B50L
    private const val LOCAL_HEADER = 30
    private const val DIR_HEADER = 46
    private const val END_RECORD = 22
    private const val ZIP64_LOCATOR = 20
    private const val ZIP64_END = 56
    private const val MAX_COMMENT = 0xFFFF
    private const val MAX_U32 = 0xFFFFFFFFL
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8
}

internal fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

/** Little-endian 64-bit value; a value above [Long.MAX_VALUE] comes back negative and is treated as invalid. */
internal fun ByteArray.u64(at: Int): Long = u32(at) or (u32(at + 4) shl 32)
