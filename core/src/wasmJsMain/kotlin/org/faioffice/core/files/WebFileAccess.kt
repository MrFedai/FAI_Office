/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.CancellationException

/** A file picked through a Web file input, as far as [WebFileAccess] needs it; the browser side wraps a `File`. */
interface WebFileSource {
    val name: String
    val size: Long
    val mimeType: String?
    val lastModifiedEpochMillis: Long?

    /** Up to [length] bytes at [position] (fewer only at the end of the file); may throw on a browser error. */
    suspend fun read(
        position: Long,
        length: Int,
    ): ByteArray
}

/**
 * Minimal Web [FileAccess] (P3.6): files picked through `<input type="file">` are registered here and read through
 * their handle ([FileRef.Kind.WebSession] reference, valid only while the page lives). Folders, listing and writing
 * are not available on the Web yet and report [FileError.IoError] with [NOT_AVAILABLE]; [rawWrites] is null.
 * No File System Access API, no IndexedDB. Meant for the single browser thread.
 */
class WebFileAccess : FileAccess {
    private val sources = mutableMapOf<String, WebFileSource>()
    private var counter = 0

    /** Registers a picked file and returns its session reference. */
    fun register(source: WebFileSource): FileRef {
        val id = (++counter).toString()
        sources[id] = source
        return FileRef(FileRef.Kind.WebSession, id)
    }

    override suspend fun info(ref: FileRef): FileResult<FileInfo> =
        when (val source = sourceOf(ref)) {
            null -> {
                FileResult.Failure(FileError.NotFound)
            }

            else -> {
                FileResult.Success(
                    FileInfo(
                        ref,
                        source.name,
                        EntryKind.File,
                        source.size,
                        source.lastModifiedEpochMillis,
                        source.mimeType?.takeIf { it.isNotEmpty() },
                        writable = false,
                    ),
                )
            }
        }

    override suspend fun list(folder: FileRef): FileResult<List<FileInfo>> = FileResult.Failure(notAvailable())

    override suspend fun <T> readRandom(
        ref: FileRef,
        block: suspend (RandomAccessReader) -> T,
    ): FileResult<T> {
        val source = sourceOf(ref) ?: return FileResult.Failure(FileError.NotFound)
        return fileResult { block(Reader(source)) }
    }

    override suspend fun <T> readStream(
        ref: FileRef,
        block: suspend (ByteSource) -> T,
    ): FileResult<T> =
        readRandom(ref) { reader ->
            var position = 0L
            block(
                object : ByteSource {
                    override suspend fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = reader.readAt(position, buffer, offset, length).also { if (it > 0) position += it }
                },
            )
        }

    @SafeSaveOnly
    override val rawWrites: RawFileWrites? = null

    private fun sourceOf(ref: FileRef): WebFileSource? =
        if (ref.kind == FileRef.Kind.WebSession) sources[ref.location] else null

    private class Reader(
        private val source: WebFileSource,
    ) : RandomAccessReader {
        override val size: Long = source.size

        override suspend fun readAt(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            require(position >= 0 && offset >= 0 && length >= 0 && offset + length <= buffer.size)
            if (length == 0) return 0
            if (position >= size) return -1
            val wanted = minOf(length.toLong(), size - position).toInt()
            val bytes =
                try {
                    source.read(position, wanted)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FileAccessException) {
                    throw e
                } catch (e: Throwable) {
                    throw FileAccessException(FileError.IoError(e.message ?: "read failed"), e)
                }
            if (bytes.isEmpty()) throw FileAccessException(FileError.IoError("file changed while reading"))
            bytes.copyInto(buffer, offset, 0, minOf(bytes.size, wanted))
            return minOf(bytes.size, wanted)
        }
    }

    companion object {
        /** Detail of the errors for what the minimal Web build cannot do (saving, folders, listing). */
        const val NOT_AVAILABLE = "not available on Web yet"

        /** The error for saving or a folder on the Web. */
        fun notAvailable(): FileError = FileError.IoError(NOT_AVAILABLE)
    }
}

/** Session-only recent files of the Web build: memory only, newest first, newest [limit] kept, gone with the page. */
class SessionRecents(
    private val limit: Int = 100,
) {
    private val refs = mutableListOf<FileRef>()

    /** Newest first. */
    val recent: List<FileRef> get() = refs.toList()

    fun add(ref: FileRef) {
        refs.remove(ref)
        refs.add(0, ref)
        while (refs.size > limit) refs.removeAt(refs.lastIndex)
    }

    fun clear() = refs.clear()
}
