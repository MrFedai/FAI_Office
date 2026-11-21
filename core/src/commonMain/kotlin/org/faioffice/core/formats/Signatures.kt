/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.core.formats

/** Fixed-signature formats, recognised from the head of the file alone. */
internal object Signatures {
    private class Rule(
        val format: FileFormat,
        val test: (ByteArray) -> Boolean,
    )

    private fun at(
        offset: Int,
        vararg bytes: Int,
    ): (ByteArray) -> Boolean = { head -> head.matches(offset, bytes) }

    private fun at(
        offset: Int,
        ascii: String,
    ): (ByteArray) -> Boolean = at(offset, *IntArray(ascii.length) { ascii[it].code })

    private val RULES: List<Rule> =
        listOf(
            Rule(FileFormat.Rtf, at(0, "{\\rtf")),
            Rule(FileFormat.SevenZip, at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)),
            Rule(FileFormat.Rar, at(0, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)),
            Rule(FileFormat.Rar, at(0, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)),
            Rule(FileFormat.Xz, at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)),
            Rule(FileFormat.Gzip, at(0, 0x1F, 0x8B, 0x08)),
            Rule(FileFormat.Bzip2) {
                it.matches(0, intArrayOf(0x42, 0x5A, 0x68)) && it.size > 3 &&
                    it[3] in '1'.code.toByte()..'9'.code.toByte()
            },
            Rule(FileFormat.Png, at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
            Rule(FileFormat.Jpeg, at(0, 0xFF, 0xD8, 0xFF)),
            Rule(FileFormat.Gif, at(0, "GIF87a")),
            Rule(FileFormat.Gif, at(0, "GIF89a")),
            Rule(FileFormat.Webp) { at(0, "RIFF")(it) && at(8, "WEBP")(it) },
            Rule(FileFormat.Tiff, at(0, 0x49, 0x49, 0x2A, 0x00)),
            Rule(FileFormat.Tiff, at(0, 0x4D, 0x4D, 0x00, 0x2A)),
            Rule(FileFormat.Bmp) { at(0, "BM")(it) && it.size >= 18 && it.u32(14) in BMP_INFO_SIZES },
            Rule(FileFormat.Tar, at(257, "ustar")),
        )

    private val BMP_INFO_SIZES = setOf(12L, 40L, 52L, 56L, 64L, 108L, 124L)

    fun match(head: ByteArray): FileFormat? = RULES.firstOrNull { it.test(head) }?.format
}

internal fun ByteArray.matches(
    offset: Int,
    bytes: IntArray,
): Boolean = size >= offset + bytes.size && bytes.indices.all { this[offset + it] == bytes[it].toByte() }

internal fun ByteArray.matches(
    offset: Int,
    bytes: ByteArray,
): Boolean = size >= offset + bytes.size && bytes.indices.all { this[offset + it] == bytes[it] }

/** `%PDF-` within the first [limit] bytes (readers accept leading junk there). */
internal fun ByteArray.hasPdfMarker(limit: Int = 1024): Boolean = indexOf(PDF_MARKER, 0, minOf(size, limit)) >= 0

internal fun ByteArray.indexOf(
    needle: ByteArray,
    from: Int = 0,
    until: Int = size,
): Int {
    for (i in from..until - needle.size) if (matches(i, needle)) return i
    return -1
}

private val PDF_MARKER = "%PDF-".encodeToByteArray()

/** Text versus binary and markup sniffing on the head of a file. */
internal object TextSniffer {
    /**
     * [FileFormat.PlainText], [FileFormat.Html], [FileFormat.Xml] or [FileFormat.Svg] for text, null for binary.
     * [cut] says the sample stops before the end of the file (a multi-byte sequence may be split there).
     */
    fun classify(
        head: ByteArray,
        cut: Boolean,
    ): FileFormat? {
        val text = decode(head, cut) ?: return null
        return markup(text)
    }

    private fun decode(
        head: ByteArray,
        cut: Boolean,
    ): String? =
        when {
            head.matches(0, intArrayOf(0xEF, 0xBB, 0xBF)) -> head.decodeToString(3, head.size)
            head.matches(0, intArrayOf(0xFF, 0xFE)) -> utf16(head, littleEndian = true)
            head.matches(0, intArrayOf(0xFE, 0xFF)) -> utf16(head, littleEndian = false)
            head.any { it == 0.toByte() } -> null
            isUtf8(head, cut) -> head.decodeToString()
            controlRatio(head) <= MAX_CONTROL_RATIO -> latin1(head)
            else -> null
        }

    private fun utf16(
        head: ByteArray,
        littleEndian: Boolean,
    ): String {
        val chars = (head.size - 2) / 2
        return CharArray(chars) { i ->
            val a = head[2 + i * 2].toInt() and 0xFF
            val b = head[3 + i * 2].toInt() and 0xFF
            (if (littleEndian) a or (b shl 8) else (a shl 8) or b).toChar()
        }.concatToString()
    }

    private fun latin1(head: ByteArray): String =
        CharArray(head.size) {
            (head[it].toInt() and 0xFF).toChar()
        }.concatToString()

    /** Share of C0 control bytes other than tab, line breaks, form feed and escape: high in binary data. */
    private fun controlRatio(head: ByteArray): Double {
        val controls = head.count { b -> b in 0..0x1F && b.toInt() !in TEXT_CONTROLS }
        return controls.toDouble() / head.size
    }

    /** Strict UTF-8 (no overlongs, no surrogates); a sequence split by the end of a [cut] sample is accepted. */
    fun isUtf8(
        bytes: ByteArray,
        cut: Boolean,
    ): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val (extra, min) =
                when {
                    b < 0x80 -> 0 to 0
                    b in 0xC2..0xDF -> 1 to 0x80
                    b in 0xE0..0xEF -> 2 to 0x800
                    b in 0xF0..0xF4 -> 3 to 0x10000
                    else -> return false
                }
            if (i + extra >= bytes.size && extra > 0) return cut && continuationsOk(bytes, i + 1, bytes.size)
            var code = b and (0x3F shr extra)
            for (k in 1..extra) {
                val c = bytes[i + k].toInt() and 0xFF
                if (c and 0xC0 != 0x80) return false
                code = (code shl 6) or (c and 0x3F)
            }
            if (code < min || code > 0x10FFFF || code in 0xD800..0xDFFF) return false
            i += extra + 1
        }
        return true
    }

    private fun continuationsOk(
        bytes: ByteArray,
        from: Int,
        until: Int,
    ): Boolean = (from until until).all { bytes[it].toInt() and 0xC0 == 0x80 }

    private fun markup(text: String): FileFormat {
        val start = text.trimStart('', ' ', '\t', '\r', '\n')
        if (!start.startsWith("<")) return FileFormat.PlainText
        val t = start.take(MARKUP_WINDOW).lowercase()
        return when {
            t.startsWith("<!doctype html") || "<html" in t -> FileFormat.Html
            "<svg" in t -> FileFormat.Svg
            t.startsWith("<?xml") -> FileFormat.Xml
            "<head" in t || "<body" in t -> FileFormat.Html
            t.length > 1 && t[1].isLetter() && ("</" in t || "/>" in t) -> FileFormat.Xml
            else -> FileFormat.PlainText
        }
    }

    private val TEXT_CONTROLS = setOf(0x09, 0x0A, 0x0C, 0x0D, 0x1B)
    private const val MAX_CONTROL_RATIO = 0.02
    private const val MARKUP_WINDOW = 2048
}
