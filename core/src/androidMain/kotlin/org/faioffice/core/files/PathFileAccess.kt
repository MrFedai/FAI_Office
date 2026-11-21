/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * [FileAccess] for [FileRef.Kind.Path] references on Android: the app-private folders (files dir, cache) where the
 * library store and the safe-save journal live. Plain `java.nio` (API 26+), no Android classes, so the shared
 * contract tests run it on the host JVM. [AndroidFileAccess] hands every path reference to it.
 */
class PathFileAccess : FileAccess {
    override suspend fun info(ref: FileRef): FileResult<FileInfo> = io(ref) { infoOf(it) }

    override suspend fun list(folder: FileRef): FileResult<List<FileInfo>> =
        io(folder) { path ->
            if (!Files.isDirectory(path)) {
                throw if (Files.exists(path)) NotDirectoryException("$path") else NoSuchFileException("$path")
            }
            val context = currentCoroutineContext()
            Files.newDirectoryStream(path).use { children ->
                children.mapNotNull { child ->
                    context.ensureActive()
                    try {
                        infoOf(child)
                    } catch (_: NoSuchFileException) {
                        null // removed while listing
                    }
                }
            }
        }

    override suspend fun <T> readRandom(
        ref: FileRef,
        block: suspend (RandomAccessReader) -> T,
    ): FileResult<T> {
        val channel =
            when (val opened = io(ref) { open(it, StandardOpenOption.READ) }) {
                is FileResult.Success -> opened.value
                is FileResult.Failure -> return opened
            }
        return channel.closeAfter {
            fileResult { block(ChannelReader(ref, channel, io(ref) { channel.size() }.getOrThrow())) }
        }
    }

    override suspend fun <T> readStream(
        ref: FileRef,
        block: suspend (ByteSource) -> T,
    ): FileResult<T> = readRandom(ref) { block(it.asByteSource()) }

    @SafeSaveOnly
    override val rawWrites: RawFileWrites = Writes()

    @SafeSaveOnly
    private inner class Writes : RawFileWrites {
        override val atomicReplace: Boolean = true

        override suspend fun createSibling(
            target: FileRef,
            nameHint: String,
        ): FileResult<FileRef> =
            io(target, writing = true) { path ->
                if (Files.isDirectory(path)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
                refOf(Files.createTempFile(path.parent ?: throw NoSuchFileException("$path"), "$nameHint.", ".tmp"))
            }

        override suspend fun createIn(
            folder: FileRef,
            nameHint: String,
        ): FileResult<FileRef> =
            io(folder, writing = true) { path ->
                if (!Files.isDirectory(path)) {
                    if (Files.exists(path)) throw FileAccessException(FileError.WrongKind(EntryKind.Folder))
                    throw NoSuchFileException("$path")
                }
                refOf(Files.createTempFile(path, "$nameHint.", ".tmp"))
            }

        override suspend fun <T> write(
            ref: FileRef,
            block: suspend (ByteSink) -> T,
        ): FileResult<T> {
            val channel =
                when (
                    val opened =
                        io(ref, writing = true) {
                            open(it, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                        }
                ) {
                    is FileResult.Success -> opened.value
                    is FileResult.Failure -> return opened
                }
            return channel.closeAfter { fileResult { block(ChannelSink(ref, channel)) } }
        }

        override suspend fun replace(
            source: FileRef,
            target: FileRef,
        ): FileResult<Unit> =
            io(target, writing = true) { path ->
                if (Files.isDirectory(path)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
                Files.move(pathOf(source), path, StandardCopyOption.ATOMIC_MOVE)
                Unit
            }

        override suspend fun delete(ref: FileRef): FileResult<Unit> =
            io(ref, writing = true) { path ->
                if (Files.isDirectory(path)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
                Files.delete(path)
            }
    }

    private class ChannelReader(
        private val ref: FileRef,
        private val channel: FileChannel,
        override val size: Long,
    ) : RandomAccessReader {
        override suspend fun readAt(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            require(position >= 0 && offset >= 0 && length >= 0 && offset + length <= buffer.size)
            if (length == 0) return 0
            return io(ref) { channel.read(ByteBuffer.wrap(buffer, offset, length), position) }.getOrThrow()
        }
    }

    private class ChannelSink(
        private val ref: FileRef,
        private val channel: FileChannel,
    ) : ByteSink {
        override suspend fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) {
            io(ref, writing = true) {
                val data = ByteBuffer.wrap(buffer, offset, length)
                while (data.hasRemaining()) channel.write(data)
            }.getOrThrow()
        }

        override suspend fun flush() {
            io(ref, writing = true) { channel.force(true) }.getOrThrow()
        }
    }

    companion object {
        /** Reference of an app-private path (absolute, normalised). */
        fun refOf(path: Path): FileRef = FileRef(FileRef.Kind.Path, path.toAbsolutePath().normalize().toString())

        /** The path behind a [FileRef.Kind.Path] reference; [FileAccessException] for any other kind. */
        fun pathOf(ref: FileRef): Path {
            if (ref.kind != FileRef.Kind.Path) throw FileAccessException(FileError.NotFound)
            return try {
                Paths.get(ref.location)
            } catch (e: InvalidPathException) {
                throw FileAccessException(FileError.IoError("invalid path: ${e.reason}"), e)
            }
        }

        private fun infoOf(path: Path): FileInfo {
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
            val folder = attributes.isDirectory
            return FileInfo(
                ref = refOf(path),
                name = path.fileName?.toString() ?: path.toString(),
                kind = if (folder) EntryKind.Folder else EntryKind.File,
                size = if (folder) null else attributes.size(),
                modifiedEpochMillis = attributes.lastModifiedTime().toMillis(),
                mimeType = null,
                writable = Files.isWritable(path),
            )
        }

        private fun open(
            path: Path,
            vararg options: StandardOpenOption,
        ): FileChannel {
            if (Files.isDirectory(path)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
            return FileChannel.open(path, *options)
        }

        private suspend fun <T> io(
            ref: FileRef,
            writing: Boolean = false,
            block: suspend (Path) -> T,
        ): FileResult<T> =
            withContext(Dispatchers.IO) {
                fileResult {
                    try {
                        block(pathOf(ref))
                    } catch (e: IOException) {
                        throw FileAccessException(pathErrorOf(e, writing), e)
                    } catch (e: SecurityException) {
                        throw FileAccessException(FileError.PermissionLost, e)
                    }
                }
            }
    }
}

/** Maps a `java.io` / `java.nio` exception on Android to a [FileError]; [writing] says whether data changes. */
internal fun pathErrorOf(
    e: IOException,
    writing: Boolean,
): FileError {
    val text = e.message.orEmpty()
    return when {
        e is NoSuchFileException || (e is java.io.FileNotFoundException && "ENOENT" in text) -> FileError.NotFound
        e is FileAlreadyExistsException -> FileError.AlreadyExists
        e is NotDirectoryException -> FileError.WrongKind(EntryKind.Folder)
        "ENOSPC" in text || text.contains("no space left", ignoreCase = true) -> FileError.NoSpace
        e is AccessDeniedException || "EACCES" in text -> if (writing) FileError.ReadOnly else FileError.PermissionLost
        "EROFS" in text || text.contains("read-only file system", ignoreCase = true) -> FileError.ReadOnly
        else -> FileError.IoError(text.ifEmpty { e::class.simpleName.orEmpty() })
    }
}

/** One sequential pass over a positioned reader. */
internal fun RandomAccessReader.asByteSource(): ByteSource {
    var position = 0L
    return object : ByteSource {
        override suspend fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = readAt(position, buffer, offset, length).also { if (it > 0) position += it }
    }
}

/** Runs [block], then closes the stream or channel off the main thread; closing a read handle cannot lose data. */
internal suspend fun <T> java.io.Closeable.closeAfter(block: suspend () -> T): T =
    try {
        block()
    } finally {
        withContext(Dispatchers.IO) {
            try {
                close()
            } catch (_: IOException) {
                // A write handle was flushed (and verified) by safe save before it is closed.
            }
        }
    }
