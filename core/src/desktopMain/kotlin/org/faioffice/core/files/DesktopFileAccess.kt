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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * [FileAccess] on the desktop file system (`java.nio`; Windows, Linux, macOS). References are absolute, normalised
 * paths ([FileRef.Kind.Path]). Blocking calls run on [Dispatchers.IO]. P3.5 adds dialogs, drag and drop and the
 * Windows specifics on top; the mapping of platform exceptions to [FileError] lives here.
 */
class DesktopFileAccess : FileAccess {
    override suspend fun info(ref: FileRef): FileResult<FileInfo> = io(ref) { path -> infoOf(path) }

    override suspend fun list(folder: FileRef): FileResult<List<FileInfo>> =
        io(folder) { path ->
            if (!Files.isDirectory(path)) {
                throw if (Files.exists(
                        path,
                    )
                ) {
                    NotDirectoryException(path.toString())
                } else {
                    NoSuchFileException(path.toString())
                }
            }
            val context = currentCoroutineContext()
            Files.newDirectoryStream(path).use { children ->
                children.mapNotNull { child ->
                    context.ensureActive()
                    // A child removed while listing is skipped, not an error for the whole folder.
                    try {
                        infoOf(child)
                    } catch (_: NoSuchFileException) {
                        null
                    }
                }
            }
        }

    override suspend fun <T> readRandom(
        ref: FileRef,
        block: suspend (RandomAccessReader) -> T,
    ): FileResult<T> {
        val channel =
            when (val opened = io(ref) { path -> openForRead(path) }) {
                is FileResult.Success -> opened.value
                is FileResult.Failure -> return opened
            }
        return channel.useSuspending {
            fileResult {
                val size = io(ref) { channel.size() }.getOrThrow()
                block(ChannelReader(ref, channel, size))
            }
        }
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
                val folder = path.parent ?: throw NoSuchFileException(path.toString())
                refOf(Files.createTempFile(folder, "$nameHint.", ".tmp"))
            }

        override suspend fun createIn(
            folder: FileRef,
            nameHint: String,
        ): FileResult<FileRef> =
            io(folder, writing = true) { path ->
                if (!Files.isDirectory(path)) {
                    if (Files.exists(path)) throw FileAccessException(FileError.WrongKind(EntryKind.Folder))
                    throw NoSuchFileException(path.toString())
                }
                refOf(Files.createTempFile(path, "$nameHint.", ".tmp"))
            }

        override suspend fun <T> write(
            ref: FileRef,
            block: suspend (ByteSink) -> T,
        ): FileResult<T> {
            val channel =
                when (val opened = io(ref, writing = true) { path -> openForWrite(path) }) {
                    is FileResult.Success -> opened.value
                    is FileResult.Failure -> return opened
                }
            return channel.useSuspending { fileResult { block(ChannelSink(ref, channel)) } }
        }

        override suspend fun replace(
            source: FileRef,
            target: FileRef,
        ): FileResult<Unit> =
            io(target, writing = true) { path ->
                if (Files.isDirectory(path)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
                try {
                    Files.move(pathOf(source), path, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: AccessDeniedException) {
                    // Windows refuses to replace a file another program holds open with ERROR_ACCESS_DENIED; a
                    // writable file in a writable folder is then locked, not read-only.
                    if (isWritableFileInWritableFolder(path)) throw FileAccessException(FileError.Locked, e)
                    throw e
                }
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
        /** Reference of a desktop path (made absolute and normalised). */
        fun refOf(path: Path): FileRef = FileRef(FileRef.Kind.Path, path.toAbsolutePath().normalize().toString())

        /** The path behind a [FileRef.Kind.Path] reference; [FileAccessException] for any other kind. */
        fun pathOf(ref: FileRef): Path {
            if (ref.kind != FileRef.Kind.Path) throw FileAccessException(FileError.NotFound)
            return try {
                Path.of(ref.location)
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
                mimeType = if (folder) null else probeMime(path),
                writable = Files.isWritable(path),
            )
        }

        private fun isWritableFileInWritableFolder(path: Path): Boolean =
            Files.isRegularFile(path) && Files.isWritable(path) && path.parent?.let(Files::isWritable) == true

        private fun probeMime(path: Path): String? =
            try {
                Files.probeContentType(path)
            } catch (_: IOException) {
                null
            }

        private fun openForRead(path: Path): FileChannel {
            if (Files.isDirectory(path)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
            return FileChannel.open(path, StandardOpenOption.READ)
        }

        private fun openForWrite(path: Path): FileChannel {
            if (Files.isDirectory(path)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
            return FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        }

        /** Runs blocking [block] on [Dispatchers.IO] with [ref]'s path and maps every platform exception. */
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
                        throw FileAccessException(errorOf(e, writing), e)
                    } catch (e: SecurityException) {
                        throw FileAccessException(FileError.PermissionLost, e)
                    }
                }
            }

        private suspend fun <T> FileChannel.useSuspending(block: suspend () -> T): T =
            try {
                block()
            } finally {
                withContext(Dispatchers.IO) {
                    try {
                        close()
                    } catch (_: IOException) {
                        // Closing a read channel cannot lose data; a write channel was flushed by safe save.
                    }
                }
            }
    }
}

private val LOCKED_MESSAGES = listOf("being used by another process", "locked a portion of the file")
private val NO_SPACE_MESSAGES = listOf("no space left", "not enough space", "disk quota exceeded")

/** Maps a `java.io` / `java.nio` exception to a [FileError]; [writing] says whether the operation changes data. */
internal fun errorOf(
    e: IOException,
    writing: Boolean,
): FileError {
    val message = (e as? FileSystemException)?.reason ?: e.message
    val text = message.orEmpty()
    return when {
        e is NoSuchFileException -> FileError.NotFound
        e is FileAlreadyExistsException -> FileError.AlreadyExists
        e is NotDirectoryException -> FileError.WrongKind(EntryKind.Folder)
        e is AtomicMoveNotSupportedException -> FileError.IoError("atomic replace not supported here")
        LOCKED_MESSAGES.any { text.contains(it, ignoreCase = true) } -> FileError.Locked
        NO_SPACE_MESSAGES.any { text.contains(it, ignoreCase = true) } -> FileError.NoSpace
        e is AccessDeniedException -> if (writing) FileError.ReadOnly else FileError.PermissionLost
        text.contains("read-only file system", ignoreCase = true) -> FileError.ReadOnly
        else -> FileError.IoError(message ?: e::class.simpleName.orEmpty())
    }
}
