/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.core.formats

import org.faioffice.core.files.RandomAccessReader
import org.faioffice.core.files.readBytes

/** Directory entry names of an OLE2 compound file, and whether the structure was broken before the end. */
internal class CompoundDirectory(
    val names: Set<String>,
    val damaged: Boolean,
)

/**
 * Bounded OLE2 (MS-CFB) reading for detection: the 512-byte header, the FAT sectors on the directory chain and at
 * most [MAX_DIRECTORY_SECTORS] directory sectors. Stream contents are never read.
 */
internal object CompoundFile {
    const val MAX_DIRECTORY_SECTORS = 32
    private const val MAX_DIFAT_SECTORS = 64

    val SIGNATURE: ByteArray = bytesOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)

    suspend fun directory(reader: RandomAccessReader): CompoundDirectory {
        val header = reader.readBytes(0, HEADER)
        val shift = if (header.size == HEADER) header.u16(0x1E) else 0
        if (shift != 9 && shift != 12) return CompoundDirectory(emptySet(), damaged = true)
        val names = mutableSetOf<String>()
        val damaged =
            try {
                Walker(reader, header, 1 shl shift).collect(names)
                false
            } catch (_: MalformedDataException) {
                true
            }
        return CompoundDirectory(names, damaged)
    }

    private class Walker(
        private val reader: RandomAccessReader,
        private val header: ByteArray,
        private val sectorSize: Int,
    ) {
        private val perSector = sectorSize / 4

        suspend fun collect(names: MutableSet<String>) {
            var sector = header.u32(0x30)
            var visited = 0
            while (sector != END_OF_CHAIN) {
                if (visited++ == MAX_DIRECTORY_SECTORS) return
                val bytes = sector(sector)
                for (at in 0 until sectorSize step ENTRY) {
                    val nameBytes = bytes.u16(at + 0x40)
                    val type = bytes[at + 0x42].toInt()
                    if (type != 0 && nameBytes in 2..64) names += utf16(bytes, at, nameBytes / 2 - 1)
                }
                sector = fatEntry(sector)
            }
        }

        private suspend fun sector(index: Long): ByteArray {
            if (index >= MAX_REGULAR) throw MalformedDataException("special sector in chain")
            val offset = (index + 1) * sectorSize
            val bytes = reader.readBytes(offset, sectorSize)
            if (bytes.size < sectorSize) throw MalformedDataException("sector past end of file")
            return bytes
        }

        private suspend fun fatEntry(index: Long): Long {
            val fatSector = difatEntry(index / perSector)
            val bytes = sector(fatSector)
            return bytes.u32(((index % perSector) * 4).toInt())
        }

        private suspend fun difatEntry(index: Long): Long {
            if (index < HEADER_DIFAT) return header.u32(0x4C + (index * 4).toInt())
            var remaining = index - HEADER_DIFAT
            var difat = header.u32(0x44)
            var hops = 0
            while (remaining >= perSector - 1) {
                if (hops++ == MAX_DIFAT_SECTORS) throw MalformedDataException("DIFAT chain too long")
                difat = sector(difat).u32(sectorSize - 4)
                remaining -= perSector - 1
            }
            return sector(difat).u32((remaining * 4).toInt())
        }
    }

    private fun utf16(
        bytes: ByteArray,
        at: Int,
        chars: Int,
    ): String = CharArray(chars) { i -> bytes.u16(at + i * 2).toChar() }.concatToString()

    private const val HEADER = 512
    private const val ENTRY = 128
    private const val HEADER_DIFAT = 109
    private const val END_OF_CHAIN = 0xFFFFFFFEL
    private const val MAX_REGULAR = 0xFFFFFFFAL
}

internal fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
