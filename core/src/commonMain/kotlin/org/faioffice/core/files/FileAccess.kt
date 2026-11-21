/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

/**
 * Platform-neutral file access: metadata, folder listing, bounded random-access and streamed reads.
 *
 * Contract, shared by every implementation and checked by the common contract tests:
 * * Every call is `suspend`, main-safe (blocking I/O runs off the caller's thread) and may be called from several
 *   coroutines at once. Failures come back as [FileResult.Failure] with a [FileError]; platform exceptions never leak.
 * * Cancellation is cooperative: a cancelled caller gets `CancellationException` (never a [FileError]); [list] checks
 *   for it between entries, reads between chunks.
 * * Read handles live only inside their block and are closed when it returns or throws; they must not escape it nor
 *   be used from two coroutines at once. A [FileAccessException] thrown by a handle ends the block and becomes the
 *   call's [FileResult.Failure]; any other exception from the block propagates unchanged.
 * * Writing is not part of this interface: documents are written only by the safe-save layer, through
 *   [rawWrites] (see [RawFileWrites]).
 */
interface FileAccess {
    /** Metadata of a file or folder. */
    suspend fun info(ref: FileRef): FileResult<FileInfo>

    /** Direct children of [folder] (files and folders, not recursive), in no particular order. */
    suspend fun list(folder: FileRef): FileResult<List<FileInfo>>

    /**
     * Opens [ref] for positioned reads, for example the head of a file and its ZIP central directory, without loading
     * the whole file. A platform whose provider cannot seek copies the file to app-private storage first.
     */
    suspend fun <T> readRandom(
        ref: FileRef,
        block: suspend (RandomAccessReader) -> T,
    ): FileResult<T>

    /** Opens [ref] for one sequential pass from the start. */
    suspend fun <T> readStream(
        ref: FileRef,
        block: suspend (ByteSource) -> T,
    ): FileResult<T>

    /** Low-level write operations for the safe-save layer; null where the platform cannot write in place (Web). */
    @SafeSaveOnly
    val rawWrites: RawFileWrites?
}

/** Positioned reads on one open file; see [FileAccess.readRandom]. */
interface RandomAccessReader {
    /** File size in bytes when the file was opened. */
    val size: Long

    /**
     * Reads up to [length] bytes at [position] into [buffer] from [offset]. Returns the byte count, at least 1 when
     * [length] > 0 and [position] < [size], or -1 at the end of the file. Throws [FileAccessException].
     */
    suspend fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int = 0,
        length: Int = buffer.size - offset,
    ): Int
}

/** One sequential pass over a file; see [FileAccess.readStream]. */
interface ByteSource {
    /** Reads up to [length] bytes into [buffer]; the byte count (at least 1 when [length] > 0) or -1 at the end. */
    suspend fun read(
        buffer: ByteArray,
        offset: Int = 0,
        length: Int = buffer.size - offset,
    ): Int
}

/** Reads [length] bytes at [position], fewer only at the end of the file. Bounded: the caller picks [length]. */
suspend fun RandomAccessReader.readBytes(
    position: Long,
    length: Int,
): ByteArray {
    require(position >= 0 && length >= 0) { "negative position or length" }
    val available = (size - position).coerceIn(0, length.toLong()).toInt()
    val out = ByteArray(available)
    var done = 0
    while (done < available) {
        val n = readAt(position + done, out, done, available - done)
        if (n < 0) return out.copyOf(done)
        done += n
    }
    return out
}

/** Reads the rest of the stream, at most [maxBytes]; a longer stream is cut at [maxBytes]. */
suspend fun ByteSource.readBytes(maxBytes: Int): ByteArray {
    require(maxBytes >= 0) { "negative limit" }
    var out = ByteArray(minOf(maxBytes, INITIAL_BUFFER))
    var done = 0
    while (done < maxBytes) {
        if (done == out.size) out = out.copyOf(minOf(maxBytes, out.size * 2))
        val n = read(out, done, out.size - done)
        if (n < 0) break
        done += n
    }
    return if (done == out.size) out else out.copyOf(done)
}

private const val INITIAL_BUFFER = 8 * 1024
