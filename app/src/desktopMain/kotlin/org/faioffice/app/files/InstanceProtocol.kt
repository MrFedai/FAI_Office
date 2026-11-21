/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * What a second launch sends the running app over the local socket ([SingleInstance]): the absolute paths of its
 * files (already resolved against its own working folder), possibly none (then the running window only comes to
 * the front). One request per connection, answered with one [ACCEPTED] byte once the paths are queued.
 *
 * Request: the magic `FAIO`, a version byte, the path count (int), then each path as its UTF-8 length (int) and
 * bytes. Bounded on both ends: at most [IncomingFiles.MAX_FILES] paths of at most [MAX_PATH_BYTES] each; anything
 * else is rejected with [IOException] and no answer.
 */
object InstanceProtocol {
    const val VERSION: Int = 1

    /** The answer byte: the request was read and its files queued. */
    const val ACCEPTED: Int = 1

    /** Windows allows 32 767 UTF-16 units; three UTF-8 bytes each is the worst case. */
    const val MAX_PATH_BYTES: Int = 32_767 * 3

    private val MAGIC = byteArrayOf('F'.code.toByte(), 'A'.code.toByte(), 'I'.code.toByte(), 'O'.code.toByte())

    fun write(
        paths: List<String>,
        out: OutputStream,
    ) {
        require(paths.size <= IncomingFiles.MAX_FILES) { "too many paths: ${paths.size}" }
        val data = DataOutputStream(out)
        data.write(MAGIC)
        data.writeByte(VERSION)
        data.writeInt(paths.size)
        for (path in paths) {
            val bytes = path.encodeToByteArray()
            require(bytes.size <= MAX_PATH_BYTES) { "path too long: ${bytes.size} bytes" }
            data.writeInt(bytes.size)
            data.write(bytes)
        }
        data.flush()
    }

    fun read(input: InputStream): List<String> {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
            if (!magic.contentEquals(MAGIC)) throw IOException("not a FAI Office request")
            val version = data.readUnsignedByte()
            if (version != VERSION) throw IOException("unsupported request version $version")
            val count = data.readInt()
            if (count !in 0..IncomingFiles.MAX_FILES) throw IOException("bad path count $count")
            return List(count) {
                val length = data.readInt()
                if (length !in 1..MAX_PATH_BYTES) throw IOException("bad path length $length")
                ByteArray(length).also { data.readFully(it) }.decodeToString(throwOnInvalidSequence = true)
            }
        } catch (e: EOFException) {
            throw IOException("truncated request", e)
        } catch (e: CharacterCodingException) {
            throw IOException("path is not UTF-8", e)
        }
    }
}
