/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.formats

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InflateTest {
    @Test
    fun dynamicHuffmanBlock() {
        val result = inflate(bytesOfHex(DOCX_CONTENT_TYPES_DEFLATED), 64 * 1024)
        assertTrue(result.complete)
        assertEquals(671, result.bytes.size)
        assertTrue("wordprocessingml.document.main+xml" in result.bytes.decodeToString())
    }

    @Test
    fun fixedHuffmanWithMatchesRoundTrips() {
        val data =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa bbbbbb ç ".encodeToByteArray() +
                ByteArray(1000) + ByteArray(300) { it.toByte() }
        val result = inflate(deflateFixed(data), 64 * 1024)
        assertTrue(result.complete)
        assertContentEquals(data, result.bytes)
    }

    @Test
    fun storedBlock() {
        val stored =
            byteArrayOf(1, 3, 0, 0xFC.toByte(), 0xFF.toByte(), 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertContentEquals("abc".encodeToByteArray(), inflate(stored, 100).bytes)
    }

    @Test
    fun outputLimitStopsABomb() {
        val bomb = FixedDeflater().literal(0)
        repeat(40_000) { bomb.repeatLast(258) } // about 10 MB of zeros from 65 KB
        val result = inflate(bomb.finish(), 1000)
        assertTrue(result.limitReached)
        assertFalse(result.complete)
        assertEquals(1000, result.bytes.size)
    }

    @Test
    fun malformedDataThrows() {
        val good = deflateFixed("merhaba dünya".encodeToByteArray())
        assertFailsWith<MalformedDataException> { inflate(good.copyOf(good.size / 2), 100) }
        assertFailsWith<MalformedDataException> { inflate(byteArrayOf(0x07), 100) } // block type 3
        // A match with nothing before it to copy.
        assertFailsWith<MalformedDataException> { inflate(FixedDeflater().repeatLast(10).finish(), 100) }
        assertFailsWith<MalformedDataException> { inflate(byteArrayOf(1, 3, 0, 0, 0), 100) } // length check fails
        assertFailsWith<MalformedDataException> { inflate(ByteArray(0), 100) }
    }

    companion object {
        /** A DOCX `[Content_Types].xml` (671 bytes) compressed by zlib level 9, raw: one dynamic Huffman block. */
        const val DOCX_CONTENT_TYPES_DEFLATED =
            "ad92cd4ec3301084ef3c85e56b953870400825e9819f23f4501ec0b2378985ffe4754bf3f66c9a5224548a907ab47766" +
                "bed1daf572e72cdb4242137cc3afcb8a33f02a68e3fb86bfad9f8b3bce304bafa50d1e1a3e02f2657b55afc708c8c8ec" +
                "b1e143cef15e08540338896588e069d285e464a663ea4594ea5df6206eaaea56a8e033f85ce42983b7f523747263337b" +
                "dad1f55c248145ce1e66e1c46ab88cd11a2533cdc5d6eb1f94e24028c9b9d7e060222e48c0c549c234f91d70f0bdd266" +
                "92d1c05632e517e948253e42d24207b571e42ccfc79ce819bace2838faa7b4988202445ab9b3e571e2a4f18bbf7a601e" +
                "2de0e55bccb9e7f0e45da510915e33c1ff0b7c3dd7e42e081d2165f34d14fbffd57e02"
    }
}
