/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.core.formats

/** Malformed or truncated container data; caught by the container reader, never leaves the detector. */
internal class MalformedDataException(
    message: String,
) : Exception(message)

/**
 * Outcome of [inflate]: the bytes produced, whether the final block ended ([complete]) and whether the output limit
 * stopped decoding first ([limitReached]).
 */
internal class InflateResult(
    val bytes: ByteArray,
    val complete: Boolean,
    val limitReached: Boolean,
)

/**
 * Raw DEFLATE (RFC 1951) decoder for small ZIP parts, after zlib's `puff`. Produces at most [maxOutput] bytes and then
 * stops, so a zip bomb part costs no more than the limit. Throws [MalformedDataException] on invalid data or when
 * [input] ends before the final block.
 */
internal fun inflate(
    input: ByteArray,
    maxOutput: Int,
): InflateResult = Inflater(input, maxOutput).run()

private class Huffman(
    lengths: IntArray,
    offset: Int,
    n: Int,
) {
    val count = IntArray(MAX_BITS + 1)
    val symbol = IntArray(n)

    init {
        for (i in 0 until n) count[lengths[offset + i]]++
        var left = 1
        for (len in 1..MAX_BITS) {
            left = (left shl 1) - count[len]
            if (left < 0) throw MalformedDataException("over-subscribed Huffman code")
        }
        val offs = IntArray(MAX_BITS + 1)
        for (len in 1 until MAX_BITS) offs[len + 1] = offs[len] + count[len]
        for (i in 0 until n) {
            val len = lengths[offset + i]
            if (len != 0) symbol[offs[len]++] = i
        }
    }
}

private class LimitReached : Exception()

private class Inflater(
    private val input: ByteArray,
    private val maxOutput: Int,
) {
    private var pos = 0
    private var bitBuf = 0
    private var bitCount = 0
    private var out = ByteArray(minOf(maxOutput, INITIAL_OUTPUT))
    private var outCount = 0

    fun run(): InflateResult =
        try {
            var last: Boolean
            do {
                last = bits(1) == 1
                when (bits(2)) {
                    0 -> stored()
                    1 -> codes(FIXED_LENGTH, FIXED_DISTANCE)
                    2 -> dynamic()
                    else -> throw MalformedDataException("invalid block type")
                }
            } while (!last)
            InflateResult(out.copyOf(outCount), complete = true, limitReached = false)
        } catch (_: LimitReached) {
            InflateResult(out.copyOf(outCount), complete = false, limitReached = true)
        }

    private fun bits(need: Int): Int {
        var value = bitBuf
        while (bitCount < need) {
            if (pos >= input.size) throw MalformedDataException("deflate data ends early")
            value = value or ((input[pos++].toInt() and 0xFF) shl bitCount)
            bitCount += 8
        }
        bitBuf = value ushr need
        bitCount -= need
        return value and ((1 shl need) - 1)
    }

    private fun put(b: Byte) {
        if (outCount == maxOutput) throw LimitReached()
        if (outCount == out.size) out = out.copyOf(minOf(maxOutput, out.size * 2))
        out[outCount++] = b
    }

    private fun stored() {
        bitBuf = 0
        bitCount = 0
        if (pos + 4 > input.size) throw MalformedDataException("stored block header ends early")
        val len = (input[pos].toInt() and 0xFF) or ((input[pos + 1].toInt() and 0xFF) shl 8)
        val nlen = (input[pos + 2].toInt() and 0xFF) or ((input[pos + 3].toInt() and 0xFF) shl 8)
        pos += 4
        if (len != nlen.inv() and 0xFFFF) throw MalformedDataException("stored block length mismatch")
        if (pos + len > input.size) throw MalformedDataException("stored block ends early")
        for (i in 0 until len) put(input[pos + i])
        pos += len
    }

    private fun decode(h: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..MAX_BITS) {
            code = code or bits(1)
            val count = h.count[len]
            if (code - count < first) return h.symbol[index + (code - first)]
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        throw MalformedDataException("invalid Huffman code")
    }

    private fun codes(
        lengthCode: Huffman,
        distanceCode: Huffman,
    ) {
        while (true) {
            val symbol = decode(lengthCode)
            if (symbol == END_OF_BLOCK) return
            if (symbol < END_OF_BLOCK) {
                put(symbol.toByte())
                continue
            }
            val li = symbol - 257
            if (li >= LENGTH_BASE.size) throw MalformedDataException("invalid length symbol")
            val length = LENGTH_BASE[li] + bits(LENGTH_EXTRA[li])
            val di = decode(distanceCode)
            if (di >= DIST_BASE.size) throw MalformedDataException("invalid distance symbol")
            val dist = DIST_BASE[di] + bits(DIST_EXTRA[di])
            if (dist > outCount) throw MalformedDataException("distance too far back")
            repeat(length) { put(out[outCount - dist]) }
        }
    }

    private fun dynamic() {
        val nlen = bits(5) + 257
        val ndist = bits(5) + 1
        val ncode = bits(4) + 4
        if (nlen > 286 || ndist > 30) throw MalformedDataException("bad code counts")
        val lengths = IntArray(286 + 30)
        for (i in 0 until ncode) lengths[CODE_ORDER[i]] = bits(3)
        val lencode = Huffman(lengths, 0, 19)
        var index = 0
        while (index < nlen + ndist) {
            val symbol = decode(lencode)
            if (symbol < 16) {
                lengths[index++] = symbol
                continue
            }
            val (value, repeat) =
                when (symbol) {
                    16 -> {
                        if (index == 0) throw MalformedDataException("repeat with no first length")
                        lengths[index - 1] to 3 + bits(2)
                    }

                    17 -> {
                        0 to 3 + bits(3)
                    }

                    else -> {
                        0 to 11 + bits(7)
                    }
                }
            if (index + repeat > nlen + ndist) throw MalformedDataException("too many lengths")
            repeat(repeat) { lengths[index++] = value }
        }
        if (lengths[END_OF_BLOCK] == 0) throw MalformedDataException("no end-of-block code")
        codes(Huffman(lengths, 0, nlen), Huffman(lengths.copyOfRange(nlen, nlen + ndist), 0, ndist))
    }

    companion object {
        val FIXED_LENGTH: Huffman =
            Huffman(
                IntArray(288) { i ->
                    if (i < 144) {
                        8
                    } else if (i < 256) {
                        9
                    } else if (i < 280) {
                        7
                    } else {
                        8
                    }
                },
                0,
                288,
            )
        val FIXED_DISTANCE: Huffman = Huffman(IntArray(30) { 5 }, 0, 30)
    }
}

private const val MAX_BITS = 15
private const val END_OF_BLOCK = 256
private const val INITIAL_OUTPUT = 4096
private val CODE_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
private val LENGTH_BASE =
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
private val LENGTH_EXTRA =
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
private val DIST_BASE =
    intArrayOf(
        1,
        2,
        3,
        4,
        5,
        7,
        9,
        13,
        17,
        25,
        33,
        49,
        65,
        97,
        129,
        193,
        257,
        385,
        513,
        769,
        1025,
        1537,
        2049,
        3073,
        4097,
        6145,
        8193,
        12289,
        16385,
        24577,
    )
private val DIST_EXTRA =
    intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
