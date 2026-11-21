/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.core.formats

import org.faioffice.core.files.InMemoryFileAccess
import org.faioffice.core.files.getOrThrow

/** Little-endian byte writer for building fixtures. */
class Out {
    private var buf = ByteArray(256)
    var size = 0
        private set

    fun u8(v: Int) = apply { put(v.toByte()) }

    fun u16(v: Int) = apply { u8(v and 0xFF).u8((v ushr 8) and 0xFF) }

    fun u32(v: Long) = apply { u16((v and 0xFFFF).toInt()).u16(((v ushr 16) and 0xFFFF).toInt()) }

    fun u64(v: Long) = apply { u32(v and 0xFFFFFFFFL).u32(v ushr 32) }

    fun bytes(b: ByteArray) = apply { b.forEach { put(it) } }

    fun ascii(s: String) = bytes(s.encodeToByteArray())

    fun zeros(n: Int) = apply { repeat(n) { put(0) } }

    fun toByteArray(): ByteArray = buf.copyOf(size)

    private fun put(b: Byte) {
        if (size == buf.size) buf = buf.copyOf(buf.size * 2)
        buf[size++] = b
    }
}

/** Raw DEFLATE writer using only the fixed Huffman code; runs of one byte become distance-1 matches. */
class FixedDeflater {
    private val out = Out()
    private var bitBuf = 0
    private var bitCount = 0

    init {
        bits(1, 1) // final block
        bits(1, 2) // fixed Huffman
    }

    fun literal(v: Int) =
        apply {
            when {
                v < 144 -> huff(0x30 + v, 8)
                else -> huff(0x190 + v - 144, 9)
            }
        }

    /** A match of [length] (3..258) at distance 1. */
    fun repeatLast(length: Int) =
        apply {
            val li = LENGTH_BASE.indexOfLast { it <= length }
            val symbol = 257 + li
            if (symbol < 280) huff(symbol - 256, 7) else huff(0xC0 + symbol - 280, 8)
            bits(length - LENGTH_BASE[li], LENGTH_EXTRA[li])
            huff(0, 5) // distance code 0 = 1
        }

    fun data(bytes: ByteArray) =
        apply {
            var i = 0
            while (i < bytes.size) {
                literal(bytes[i].toInt() and 0xFF)
                var run = 0
                while (i + 1 + run < bytes.size && run < 258 && bytes[i + 1 + run] == bytes[i]) run++
                if (run >= 3) repeatLast(run) else run = 0
                i += 1 + run
            }
        }

    fun finish(): ByteArray {
        huff(0, 7) // end of block
        if (bitCount > 0) out.u8(bitBuf and 0xFF)
        return out.toByteArray()
    }

    private fun bits(
        value: Int,
        n: Int,
    ) {
        for (k in 0 until n) {
            bitBuf = bitBuf or (((value ushr k) and 1) shl bitCount)
            if (++bitCount == 8) {
                out.u8(bitBuf)
                bitBuf = 0
                bitCount = 0
            }
        }
    }

    private fun huff(
        code: Int,
        length: Int,
    ) {
        for (k in length - 1 downTo 0) bits((code ushr k) and 1, 1)
    }

    private companion object {
        val LENGTH_BASE =
            intArrayOf(
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                13,
                15,
                17,
                19,
                23,
                27,
                31,
                35,
                43,
                51,
                59,
                67,
                83,
                99,
                115,
                131,
                163,
                195,
                227,
                258,
            )
        val LENGTH_EXTRA =
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    }
}

fun deflateFixed(bytes: ByteArray): ByteArray = FixedDeflater().data(bytes).finish()

/** ZIP writer: local headers, central directory, end record (CRCs are zero; detection does not check them). */
class ZipBuilder {
    private class Item(
        val name: String,
        val method: Int,
        val data: ByteArray,
        val uncompressedSize: Long,
    )

    private val items = mutableListOf<Item>()

    fun stored(
        name: String,
        bytes: ByteArray,
    ) = apply { items += Item(name, 0, bytes, bytes.size.toLong()) }

    fun deflated(
        name: String,
        bytes: ByteArray,
    ) = apply { items += Item(name, 8, deflateFixed(bytes), bytes.size.toLong()) }

    fun raw(
        name: String,
        method: Int,
        data: ByteArray,
        uncompressedSize: Long,
    ) = apply { items += Item(name, method, data, uncompressedSize) }

    fun build(): ByteArray {
        val out = Out()
        val offsets = mutableListOf<Long>()
        for (item in items) {
            offsets += out.size.toLong()
            val name = item.name.encodeToByteArray()
            out
                .u32(0x04034B50)
                .u16(20)
                .u16(0)
                .u16(item.method)
                .u16(0)
                .u16(0)
                .u32(0)
            out
                .u32(
                    item.data.size.toLong(),
                ).u32(item.uncompressedSize)
                .u16(name.size)
                .u16(0)
                .bytes(name)
                .bytes(item.data)
        }
        val directoryOffset = out.size.toLong()
        items.forEachIndexed { i, item ->
            val name = item.name.encodeToByteArray()
            out
                .u32(0x02014B50)
                .u16(20)
                .u16(20)
                .u16(0)
                .u16(item.method)
                .u16(0)
                .u16(0)
                .u32(0)
            out
                .u32(item.data.size.toLong())
                .u32(item.uncompressedSize)
                .u16(name.size)
                .u16(0)
                .u16(0)
            out
                .u16(0)
                .u16(0)
                .u32(0)
                .u32(offsets[i])
                .bytes(name)
        }
        val directorySize = out.size - directoryOffset
        out
            .u32(0x06054B50)
            .u16(0)
            .u16(0)
            .u16(items.size)
            .u16(items.size)
        out.u32(directorySize).u32(directoryOffset).u16(0)
        return out.toByteArray()
    }
}

fun contentTypes(mainType: String): ByteArray =
    (
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/main.xml" ContentType="$mainType"/></Types>"""
    ).encodeToByteArray()

/** A minimal OOXML package whose main part has [mainType]; [deflate] compresses `[Content_Types].xml`. */
fun ooxml(
    mainType: String,
    deflate: Boolean = true,
): ByteArray {
    val zip = ZipBuilder()
    if (deflate) {
        zip.deflated(
            "[Content_Types].xml",
            contentTypes(mainType),
        )
    } else {
        zip.stored("[Content_Types].xml", contentTypes(mainType))
    }
    return zip
        .deflated(
            "_rels/.rels",
            "<Relationships/>".encodeToByteArray(),
        ).deflated("main.xml", "<doc/>".encodeToByteArray())
        .build()
}

/** A minimal ODF or EPUB package: stored `mimetype` first, then a deflated content part. */
fun odf(mimetype: String): ByteArray =
    ZipBuilder()
        .stored("mimetype", mimetype.encodeToByteArray())
        .deflated("content.xml", "<office:document-content/>".encodeToByteArray())
        .build()

/** An OLE2 compound file (version 3, 512-byte sectors) whose directory holds the root and [streams]. */
fun compoundFile(
    streams: List<String>,
    sectorShift: Int = 9,
): ByteArray {
    val sectorSize = 1 shl sectorShift
    val entries = listOf("Root Entry") + streams
    val dirSectors = (entries.size * 128 + sectorSize - 1) / sectorSize
    val out = Out()
    out
        .bytes(CompoundFile.SIGNATURE)
        .zeros(16)
        .u16(0x3E)
        .u16(if (sectorShift == 9) 3 else 4)
        .u16(0xFFFE)
    out
        .u16(sectorShift)
        .u16(6)
        .zeros(6)
        .u32(0)
        .u32(1)
        .u32(1)
        .u32(0)
        .u32(4096)
    out
        .u32(0xFFFFFFFEL)
        .u32(0)
        .u32(0xFFFFFFFEL)
        .u32(0)
    out.u32(0)
    repeat(108) { out.u32(0xFFFFFFFFL) }
    out.zeros(sectorSize - 512)
    // Sector 0: FAT.
    out.u32(0xFFFFFFFDL)
    for (s in 1..dirSectors) out.u32(if (s == dirSectors) 0xFFFFFFFEL else (s + 1).toLong())
    repeat(sectorSize / 4 - 1 - dirSectors) { out.u32(0xFFFFFFFFL) }
    // Directory sectors.
    for (i in 0 until dirSectors * sectorSize / 128) {
        val name = entries.getOrNull(i)
        if (name == null) {
            out.zeros(128)
            continue
        }
        name.forEach { out.u16(it.code) }
        out.zeros(64 - name.length * 2)
        out.u16((name.length + 1) * 2).u8(if (i == 0) 5 else 2).u8(1)
        out
            .u32(0xFFFFFFFFL)
            .u32(0xFFFFFFFFL)
            .u32(0xFFFFFFFFL)
            .zeros(16 + 4 + 16)
            .u32(0xFFFFFFFEL)
            .u64(0)
    }
    return out.toByteArray()
}

/** Detection through the in-memory file access, as the app runs it. */
suspend fun detectBytes(
    name: String,
    bytes: ByteArray,
    mimeType: String? = null,
): FormatDetection {
    val fake = InMemoryFileAccess()
    val ref = fake.addFile("/test/$name", bytes, mimeType)
    return FormatDetector(fake).detect(ref).getOrThrow()
}

fun bytesOfHex(hex: String): ByteArray =
    ByteArray(hex.length / 2) {
        hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
