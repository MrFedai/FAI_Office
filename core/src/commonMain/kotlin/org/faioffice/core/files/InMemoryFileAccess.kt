/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The operations [InMemoryFileAccess.faults] can fail. */
enum class FileOp { Info, List, Read, CreateSibling, CreateIn, Write, Flush, Replace, Delete }

/**
 * In-memory [FileAccess] with the same contract as the platform implementations, for tests and previews.
 *
 * Files and folders are addressed by `/`-separated paths (`/Belgeler/Rapor.docx`); references are
 * [FileRef.Kind.Memory]. Set [faults] to make any operation fail with a chosen [FileError] (permission lost, locked,
 * full disk, a crash in the middle of a save): it is asked before every operation, and before every
 * [ByteSink.flush] with [FileOp.Flush].
 */
class InMemoryFileAccess : FileAccess {
    private sealed interface Node {
        var modified: Long
    }

    private class FileNode(
        var bytes: ByteArray,
        override var modified: Long,
        val mimeType: String?,
        var readOnly: Boolean,
    ) : Node

    private class FolderNode(
        override var modified: Long,
    ) : Node

    private val mutex = Mutex()
    private val nodes = mutableMapOf<String, Node>(ROOT to FolderNode(0))
    private var clock = 0L
    private var siblingCounter = 0

    /** Returns the error an operation on a reference should fail with, or null to let it run. */
    var faults: (FileOp, FileRef) -> FileError? = { _, _ -> null }

    /** Reference of the folder or file at [path] (which need not exist). */
    fun ref(path: String): FileRef = FileRef(FileRef.Kind.Memory, normalize(path))

    /** Adds or replaces a file, creating missing parent folders. */
    fun addFile(
        path: String,
        bytes: ByteArray,
        mimeType: String? = null,
        readOnly: Boolean = false,
    ): FileRef {
        val key = normalize(path)
        addFolders(parentOf(key))
        nodes[key] = FileNode(bytes.copyOf(), tick(), mimeType, readOnly)
        return ref(key)
    }

    /** Adds a folder and its missing parents. */
    fun addFolder(path: String): FileRef {
        val key = normalize(path)
        addFolders(key)
        return ref(key)
    }

    /** Marks the file at [ref] read-only (writes fail with [FileError.ReadOnly]) or writable again. */
    fun setReadOnly(
        ref: FileRef,
        readOnly: Boolean = true,
    ) {
        (nodes[ref.location] as? FileNode ?: error("no file at $ref")).readOnly = readOnly
    }

    /** Current bytes of a file, or null when there is no file at [ref]. */
    fun contents(ref: FileRef): ByteArray? = (nodes[ref.location] as? FileNode)?.bytes?.copyOf()

    /** Every path currently present, folders included; for checking that no temporary file was left behind. */
    fun paths(): Set<String> = nodes.keys.toSet()

    override suspend fun info(ref: FileRef): FileResult<FileInfo> =
        locked(FileOp.Info, ref) { key ->
            infoOf(key, nodes[key] ?: fail(FileError.NotFound))
        }

    override suspend fun list(folder: FileRef): FileResult<List<FileInfo>> =
        locked(FileOp.List, folder) { key ->
            when (nodes[key]) {
                null -> fail(FileError.NotFound)
                is FileNode -> fail(FileError.WrongKind(EntryKind.Folder))
                is FolderNode -> Unit
            }
            val prefix = if (key == ROOT) ROOT else "$key/"
            nodes.entries
                .filter { (path, _) ->
                    path != ROOT && path.startsWith(prefix) && '/' !in path.substring(prefix.length)
                }.map { (path, node) ->
                    currentCoroutineContext().ensureActive()
                    infoOf(path, node)
                }
        }

    override suspend fun <T> readRandom(
        ref: FileRef,
        block: suspend (RandomAccessReader) -> T,
    ): FileResult<T> {
        val bytes =
            when (val opened = snapshot(ref)) {
                is FileResult.Success -> opened.value
                is FileResult.Failure -> return opened
            }
        return fileResult {
            block(
                object : RandomAccessReader {
                    override val size: Long = bytes.size.toLong()

                    override suspend fun readAt(
                        position: Long,
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        require(position >= 0 && offset >= 0 && length >= 0 && offset + length <= buffer.size)
                        currentCoroutineContext().ensureActive()
                        if (length == 0) return 0
                        if (position >= bytes.size) return -1
                        val n = minOf(length.toLong(), bytes.size - position).toInt()
                        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + n)
                        return n
                    }
                },
            )
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
            locked(FileOp.CreateSibling, target) { key ->
                if (nodes[key] is FolderNode) fail(FileError.WrongKind(EntryKind.File))
                val parent = parentOf(key)
                if (nodes[parent] !is FolderNode) fail(FileError.NotFound)
                var path: String
                do {
                    path = (if (parent == ROOT) "" else parent) + "/$nameHint.${++siblingCounter}"
                } while (path in nodes)
                nodes[path] = FileNode(ByteArray(0), tick(), mimeType = null, readOnly = false)
                ref(path)
            }

        override suspend fun createIn(
            folder: FileRef,
            nameHint: String,
        ): FileResult<FileRef> =
            locked(FileOp.CreateIn, folder) { key ->
                when (nodes[key]) {
                    null -> fail(FileError.NotFound)
                    is FileNode -> fail(FileError.WrongKind(EntryKind.Folder))
                    is FolderNode -> Unit
                }
                var path: String
                do {
                    path = (if (key == ROOT) "" else key) + "/$nameHint.${++siblingCounter}"
                } while (path in nodes)
                nodes[path] = FileNode(ByteArray(0), tick(), mimeType = null, readOnly = false)
                ref(path)
            }

        override suspend fun <T> write(
            ref: FileRef,
            block: suspend (ByteSink) -> T,
        ): FileResult<T> {
            val opened =
                locked(FileOp.Write, ref) { key ->
                    val node = writableFile(key)
                    node.bytes = ByteArray(0)
                    node.modified = tick()
                    node
                }
            val node =
                when (opened) {
                    is FileResult.Success -> opened.value
                    is FileResult.Failure -> return opened
                }
            return fileResult { block(Sink(ref, node)) }
        }

        override suspend fun replace(
            source: FileRef,
            target: FileRef,
        ): FileResult<Unit> =
            locked(FileOp.Replace, target) { key ->
                faults(FileOp.Replace, source)?.let { fail(it) }
                val node = nodes[source.location] as? FileNode ?: fail(FileError.NotFound)
                (nodes[key] as? FileNode)?.let { if (it.readOnly) fail(FileError.ReadOnly) }
                if (nodes[key] is FolderNode) fail(FileError.WrongKind(EntryKind.File))
                nodes.remove(source.location)
                nodes[key] = node.also { it.modified = tick() }
            }

        override suspend fun delete(ref: FileRef): FileResult<Unit> =
            locked(FileOp.Delete, ref) { key ->
                val node = nodes[key] ?: fail(FileError.NotFound)
                if (node is FolderNode) fail(FileError.WrongKind(EntryKind.File))
                nodes.remove(key)
            }
    }

    private inner class Sink(
        private val ref: FileRef,
        private val node: FileNode,
    ) : ByteSink {
        // Written bytes reach the file at once; a crash before flush is simulated with a Flush fault.
        override suspend fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) {
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                node.bytes += buffer.copyOfRange(offset, offset + length)
                node.modified = tick()
            }
        }

        override suspend fun flush() {
            faults(FileOp.Flush, ref)?.let { throw FileAccessException(it) }
        }
    }

    private suspend fun snapshot(ref: FileRef): FileResult<ByteArray> =
        locked(FileOp.Read, ref) { key ->
            when (val node = nodes[key]) {
                null -> fail(FileError.NotFound)
                is FolderNode -> fail(FileError.WrongKind(EntryKind.File))
                is FileNode -> node.bytes.copyOf()
            }
        }

    private fun writableFile(key: String): FileNode =
        when (val node = nodes[key]) {
            null -> fail(FileError.NotFound)
            is FolderNode -> fail(FileError.WrongKind(EntryKind.File))
            is FileNode -> if (node.readOnly) fail(FileError.ReadOnly) else node
        }

    private suspend inline fun <T> locked(
        op: FileOp,
        ref: FileRef,
        block: (String) -> T,
    ): FileResult<T> {
        currentCoroutineContext().ensureActive()
        if (ref.kind != FileRef.Kind.Memory) return FileResult.Failure(FileError.NotFound)
        faults(op, ref)?.let { return FileResult.Failure(it) }
        return mutex.withLock { fileResult { block(ref.location) } }
    }

    private fun infoOf(
        key: String,
        node: Node,
    ): FileInfo =
        when (node) {
            is FileNode -> {
                FileInfo(
                    ref = ref(key),
                    name = nameOf(key),
                    kind = EntryKind.File,
                    size = node.bytes.size.toLong(),
                    modifiedEpochMillis = node.modified,
                    mimeType = node.mimeType,
                    writable = !node.readOnly,
                )
            }

            is FolderNode -> {
                FileInfo(ref(key), nameOf(key), EntryKind.Folder, size = null, node.modified, null, writable = true)
            }
        }

    private fun addFolders(key: String) {
        if (key == ROOT) return
        addFolders(parentOf(key))
        when (nodes[key]) {
            null -> nodes[key] = FolderNode(tick())
            is FileNode -> error("$key is a file")
            is FolderNode -> Unit
        }
    }

    private fun tick(): Long = ++clock

    private companion object {
        const val ROOT = "/"

        fun normalize(path: String): String = "/" + path.split('/').filter { it.isNotEmpty() }.joinToString("/")

        fun parentOf(key: String): String = key.substringBeforeLast('/').ifEmpty { ROOT }

        fun nameOf(key: String): String = key.substringAfterLast('/')

        fun fail(error: FileError): Nothing = throw FileAccessException(error)
    }
}
