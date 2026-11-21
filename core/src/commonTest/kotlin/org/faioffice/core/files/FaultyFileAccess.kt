/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

/**
 * Wraps any [FileAccess] (the in-memory fake, real desktop files) and fails chosen operations: [faults] is asked
 * before every call and before every chunk read or written and every flush. With [atomicReplace] false it behaves
 * like an Android content URI provider (no atomic replace; safe save takes the backup path); with [writable] false
 * like the Web (no raw writes at all).
 */
@OptIn(SafeSaveOnly::class)
class FaultyFileAccess(
    private val inner: FileAccess,
    private val atomicReplace: Boolean = true,
    writable: Boolean = true,
) : FileAccess {
    var faults: (FileOp, FileRef) -> FileError? = { _, _ -> null }

    private fun check(
        op: FileOp,
        ref: FileRef,
    ) {
        faults(op, ref)?.let { throw FileAccessException(it) }
    }

    private inline fun <T> guarded(
        op: FileOp,
        ref: FileRef,
        block: () -> FileResult<T>,
    ): FileResult<T> = faults(op, ref)?.let { FileResult.Failure(it) } ?: block()

    override suspend fun info(ref: FileRef): FileResult<FileInfo> = guarded(FileOp.Info, ref) { inner.info(ref) }

    override suspend fun list(folder: FileRef): FileResult<List<FileInfo>> =
        guarded(FileOp.List, folder) {
            inner.list(folder)
        }

    override suspend fun <T> readRandom(
        ref: FileRef,
        block: suspend (RandomAccessReader) -> T,
    ): FileResult<T> =
        guarded(FileOp.Read, ref) {
            inner.readRandom(ref) { reader ->
                block(
                    object : RandomAccessReader {
                        override val size: Long = reader.size

                        override suspend fun readAt(
                            position: Long,
                            buffer: ByteArray,
                            offset: Int,
                            length: Int,
                        ): Int {
                            check(FileOp.Read, ref)
                            return reader.readAt(position, buffer, offset, length)
                        }
                    },
                )
            }
        }

    override suspend fun <T> readStream(
        ref: FileRef,
        block: suspend (ByteSource) -> T,
    ): FileResult<T> =
        guarded(FileOp.Read, ref) {
            inner.readStream(ref) { source ->
                block(
                    object : ByteSource {
                        override suspend fun read(
                            buffer: ByteArray,
                            offset: Int,
                            length: Int,
                        ): Int {
                            check(FileOp.Read, ref)
                            return source.read(buffer, offset, length)
                        }
                    },
                )
            }
        }

    @SafeSaveOnly
    override val rawWrites: RawFileWrites? = if (writable) inner.rawWrites?.let { Writes(it) } else null

    @SafeSaveOnly
    private inner class Writes(
        private val raw: RawFileWrites,
    ) : RawFileWrites {
        override val atomicReplace: Boolean = this@FaultyFileAccess.atomicReplace && raw.atomicReplace

        override suspend fun createSibling(
            target: FileRef,
            nameHint: String,
        ): FileResult<FileRef> = guarded(FileOp.CreateSibling, target) { raw.createSibling(target, nameHint) }

        override suspend fun createIn(
            folder: FileRef,
            nameHint: String,
        ): FileResult<FileRef> = guarded(FileOp.CreateIn, folder) { raw.createIn(folder, nameHint) }

        override suspend fun <T> write(
            ref: FileRef,
            block: suspend (ByteSink) -> T,
        ): FileResult<T> =
            guarded(FileOp.Write, ref) {
                raw.write(ref) { sink ->
                    block(
                        object : ByteSink {
                            override suspend fun write(
                                buffer: ByteArray,
                                offset: Int,
                                length: Int,
                            ) {
                                check(FileOp.Write, ref)
                                sink.write(buffer, offset, length)
                            }

                            override suspend fun flush() {
                                check(FileOp.Flush, ref)
                                sink.flush()
                            }
                        },
                    )
                }
            }

        override suspend fun replace(
            source: FileRef,
            target: FileRef,
        ): FileResult<Unit> {
            if (!atomicReplace) return FileResult.Failure(FileError.IoError("atomic replace not supported"))
            return guarded(FileOp.Replace, target) { raw.replace(source, target) }
        }

        override suspend fun delete(ref: FileRef): FileResult<Unit> = guarded(FileOp.Delete, ref) { raw.delete(ref) }
    }
}

/**
 * A power cut after [limit] operations: from then on every operation fails, so nothing after the cut reaches the
 * files (cleanup included), as on a real crash. [ops] records the operations that ran, in order.
 */
class Crash(
    private val limit: Int = Int.MAX_VALUE,
) {
    val ops = mutableListOf<Pair<FileOp, FileRef>>()
    var crashed = false
        private set

    fun fault(
        op: FileOp,
        ref: FileRef,
    ): FileError? {
        if (crashed || ops.size >= limit) {
            crashed = true
            return FileError.IoError("power cut")
        }
        ops += op to ref
        return null
    }
}
