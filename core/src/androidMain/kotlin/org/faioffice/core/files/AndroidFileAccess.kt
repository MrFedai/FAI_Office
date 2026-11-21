/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.SyncFailedException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * [FileAccess] on Android: [FileRef.Kind.ContentUri] references through the Storage Access Framework (documents
 * picked with `ACTION_OPEN_DOCUMENT`, folders from `ACTION_OPEN_DOCUMENT_TREE` listed through [DocumentsContract],
 * files shared or opened with "Open with" from other apps) and [FileRef.Kind.Path] references for app-private storage
 * ([PathFileAccess]: library store, safe-save journal and backups). No storage permission: access to a content URI
 * comes only from the grant that came with it (temporary, or persisted by [SafPersistedGrants]); a revoked grant is
 * [FileError.PermissionLost].
 *
 * Writes: content URIs have no atomic replace, so [RawFileWrites.atomicReplace] is false and safe save takes its
 * backup path (backup in app-private storage, truncate write, verify). A provider that cannot seek (a pipe) is copied
 * to the cache folder for [readRandom].
 */
class AndroidFileAccess(
    context: Context,
    private val paths: PathFileAccess = PathFileAccess(),
) : FileAccess {
    private val appContext: Context = context.applicationContext ?: context
    private val resolver: ContentResolver = appContext.contentResolver
    private val documents = SafDocuments(appContext)
    private val cacheDir: File = appContext.cacheDir

    override suspend fun info(ref: FileRef): FileResult<FileInfo> =
        when (ref.kind) {
            FileRef.Kind.Path -> paths.info(ref)
            else -> saf(ref) { uri -> documents.info(ref, uri) }
        }

    override suspend fun list(folder: FileRef): FileResult<List<FileInfo>> =
        when (folder.kind) {
            FileRef.Kind.Path -> paths.list(folder)
            else -> saf(folder) { uri -> documents.list(uri) }
        }

    override suspend fun <T> readRandom(
        ref: FileRef,
        block: suspend (RandomAccessReader) -> T,
    ): FileResult<T> =
        if (ref.kind == FileRef.Kind.Path) {
            paths.readRandom(ref, block)
        } else {
            saf(ref) { uri -> openForRead(uri) }.andThen { fd ->
                fd.closeAfter {
                    if (fd.statSize >= 0) {
                        val channel = FileInputStream(fd.fileDescriptor).channel
                        fileResult { block(FdReader(ref, channel, fd.statSize)) }
                    } else {
                        readCopy(ref, fd, block)
                    }
                }
            }
        }

    override suspend fun <T> readStream(
        ref: FileRef,
        block: suspend (ByteSource) -> T,
    ): FileResult<T> =
        if (ref.kind == FileRef.Kind.Path) {
            paths.readStream(ref, block)
        } else {
            saf(ref) { uri -> openStream(uri) }.andThen { stream ->
                stream.closeAfter { fileResult { block(StreamSource(ref, stream)) } }
            }
        }

    @SafeSaveOnly
    override val rawWrites: RawFileWrites = Writes(paths.rawWrites)

    @SafeSaveOnly
    private inner class Writes(
        private val pathWrites: RawFileWrites,
    ) : RawFileWrites {
        override val atomicReplace: Boolean = false

        override suspend fun createSibling(
            target: FileRef,
            nameHint: String,
        ): FileResult<FileRef> =
            if (target.kind == FileRef.Kind.Path) {
                pathWrites.createSibling(target, nameHint)
            } else {
                FileResult.Failure(FileError.IoError("a content URI has no sibling files"))
            }

        override suspend fun createIn(
            folder: FileRef,
            nameHint: String,
        ): FileResult<FileRef> {
            if (folder.kind == FileRef.Kind.Path) return pathWrites.createIn(folder, nameHint)
            return saf(folder, writing = true) { uri ->
                if (!DocumentsContract.isTreeUri(uri)) throw FileAccessException(FileError.WrongKind(EntryKind.Folder))
                val parent = documents.documentUriOf(uri) ?: throw FileNotFoundException("$uri")
                val name = "$nameHint.${System.nanoTime()}.tmp"
                val created =
                    DocumentsContract.createDocument(resolver, parent, OCTET_STREAM, name)
                        ?: throw IOException("the provider created no document")
                FileRef(FileRef.Kind.ContentUri, created.toString())
            }
        }

        override suspend fun <T> write(
            ref: FileRef,
            block: suspend (ByteSink) -> T,
        ): FileResult<T> =
            if (ref.kind == FileRef.Kind.Path) {
                pathWrites.write(ref, block)
            } else {
                saf(ref, writing = true) { uri -> openForWrite(uri) }.andThen { fd ->
                    fd.closeAfter { fileResult { block(FdSink(ref, fd)) } }
                }
            }

        override suspend fun replace(
            source: FileRef,
            target: FileRef,
        ): FileResult<Unit> =
            if (source.kind == FileRef.Kind.Path && target.kind == FileRef.Kind.Path) {
                pathWrites.replace(source, target)
            } else {
                FileResult.Failure(FileError.IoError("atomic replace not supported for content URIs"))
            }

        override suspend fun delete(ref: FileRef): FileResult<Unit> {
            if (ref.kind == FileRef.Kind.Path) return pathWrites.delete(ref)
            return saf(ref, writing = true) { uri ->
                if (documents.documentUriOf(uri) != null) {
                    if (!DocumentsContract.deleteDocument(resolver, uri)) throw IOException("not deleted")
                } else if (resolver.delete(uri, null, null) == 0) {
                    throw FileNotFoundException("$uri")
                }
            }
        }
    }

    private fun openForRead(uri: Uri): ParcelFileDescriptor {
        if (documents.isFolder(uri)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
        return resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("$uri")
    }

    private fun openStream(uri: Uri): InputStream {
        if (documents.isFolder(uri)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
        return resolver.openInputStream(uri) ?: throw FileNotFoundException("$uri")
    }

    private fun openForWrite(uri: Uri): ParcelFileDescriptor {
        if (documents.isFolder(uri)) throw FileAccessException(FileError.WrongKind(EntryKind.File))
        // "wt": write from the start and truncate; safe save verifies the result by reading it back.
        return resolver.openFileDescriptor(uri, "wt") ?: throw FileNotFoundException("$uri")
    }

    /** A provider that streams through a pipe cannot seek: copy to the cache folder, read there, delete the copy. */
    private suspend fun <T> readCopy(
        ref: FileRef,
        fd: ParcelFileDescriptor,
        block: suspend (RandomAccessReader) -> T,
    ): FileResult<T> =
        saf(ref) { copyToCache(fd) }.andThen { file ->
            try {
                paths.readRandom(PathFileAccess.refOf(file.toPath()), block)
            } finally {
                withContext(Dispatchers.IO) { file.delete() }
            }
        }

    private suspend fun copyToCache(fd: ParcelFileDescriptor): File {
        val file = File.createTempFile("fai-read-", ".tmp", cacheDir)
        val context = currentCoroutineContext()
        FileInputStream(fd.fileDescriptor).use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    context.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
            }
        }
        return file
    }

    /** Runs blocking [block] on [Dispatchers.IO] with [ref]'s URI and maps every platform exception. */
    private suspend fun <T> saf(
        ref: FileRef,
        writing: Boolean = false,
        block: suspend (Uri) -> T,
    ): FileResult<T> {
        if (ref.kind != FileRef.Kind.ContentUri) return FileResult.Failure(FileError.NotFound)
        val uri = Uri.parse(ref.location)
        return withContext(Dispatchers.IO) {
            fileResult {
                val (error, cause) =
                    try {
                        return@fileResult block(uri)
                    } catch (e: SecurityException) {
                        securityErrorOf(uri, writing) to e
                    } catch (e: FileNotFoundException) {
                        notFoundErrorOf(e, writing) to e
                    } catch (e: IllegalArgumentException) {
                        // Providers report an unknown or deleted document ID this way.
                        FileError.NotFound to e
                    } catch (e: UnsupportedOperationException) {
                        (if (writing) FileError.ReadOnly else FileError.IoError(e.message.orEmpty())) to e
                    } catch (e: IOException) {
                        pathErrorOf(e, writing) to e
                    }
                throw FileAccessException(error, cause)
            }
        }
    }

    /** A refused write while reading is still granted means a read-only grant, not a lost one. */
    private fun securityErrorOf(
        uri: Uri,
        writing: Boolean,
    ): FileError =
        if (writing && documents.granted(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)) {
            FileError.ReadOnly
        } else {
            FileError.PermissionLost
        }

    private fun notFoundErrorOf(
        e: FileNotFoundException,
        writing: Boolean,
    ): FileError {
        val text = e.message.orEmpty()
        return when {
            "EACCES" in text || text.contains("permission", ignoreCase = true) -> {
                if (writing) FileError.ReadOnly else FileError.PermissionLost
            }

            "EROFS" in text || text.contains("read-only", ignoreCase = true) -> FileError.ReadOnly
            else -> FileError.NotFound
        }
    }

    private inner class FdReader(
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
            return saf(ref) { channel.read(ByteBuffer.wrap(buffer, offset, length), position) }.getOrThrow()
        }
    }

    private inner class StreamSource(
        private val ref: FileRef,
        private val stream: InputStream,
    ) : ByteSource {
        override suspend fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (length == 0) return 0
            return saf(ref) { stream.read(buffer, offset, length) }.getOrThrow()
        }
    }

    private inner class FdSink(
        private val ref: FileRef,
        private val fd: ParcelFileDescriptor,
    ) : ByteSink {
        private val stream = FileOutputStream(fd.fileDescriptor)

        override suspend fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) {
            saf(ref, writing = true) { stream.write(buffer, offset, length) }.getOrThrow()
        }

        override suspend fun flush() {
            saf(ref, writing = true) {
                stream.flush()
                try {
                    fd.fileDescriptor.sync()
                } catch (_: SyncFailedException) {
                    // A pipe to a cloud provider cannot fsync; the provider commits on close and safe save verifies.
                }
            }.getOrThrow()
        }
    }

    private companion object {
        inline fun <A, B> FileResult<A>.andThen(next: (A) -> FileResult<B>): FileResult<B> =
            when (this) {
                is FileResult.Success -> next(value)
                is FileResult.Failure -> this
            }

        const val COPY_BUFFER = 64 * 1024
        const val OCTET_STREAM = "application/octet-stream"
    }
}
