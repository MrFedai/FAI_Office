/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

/**
 * Marks the raw write primitives. Only the safe-save layer (and the [FileAccess] implementations themselves) opt in;
 * everywhere else using them is a compile error, so a document can only be written through safe save.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Documents are written only through the safe-save layer; raw writes can lose the user's file.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class SafeSaveOnly

/**
 * The primitives the safe-save layer builds on: write a temporary file next to the target, flush it to disk,
 * verify it with [FileAccess.readRandom] / [FileAccess.readStream], then [replace]. Where [atomicReplace] is false
 * (Android content URIs) safe save backs the original up, writes the target in truncate mode with [write] and
 * verifies before dropping the backup. None of these keep a backup themselves; that policy is safe save's.
 */
@SafeSaveOnly
interface RawFileWrites {
    /** Whether [replace] swaps the target in one atomic step (desktop file systems, the fake). */
    val atomicReplace: Boolean

    /**
     * Creates a new empty file in the same folder as [target] (so [replace] can be atomic), named from [nameHint]
     * with a suffix the implementation picks to keep it unique. [FileError.WrongKind] when [target] is a folder.
     */
    suspend fun createSibling(
        target: FileRef,
        nameHint: String,
    ): FileResult<FileRef>

    /**
     * Truncates the existing file [ref] and lets [block] write it from the start. Data is durable only after
     * [ByteSink.flush]; the sink is closed when the block ends. [FileError.NotFound] when [ref] does not exist.
     */
    suspend fun <T> write(
        ref: FileRef,
        block: suspend (ByteSink) -> T,
    ): FileResult<T>

    /**
     * Replaces [target] with [source] (a file from [createSibling] for [target]); [source] no longer exists
     * afterwards. Atomic when [atomicReplace]; otherwise not supported ([FileError.IoError]).
     */
    suspend fun replace(
        source: FileRef,
        target: FileRef,
    ): FileResult<Unit>

    /** Deletes the file [ref] (temporary or backup files). [FileError.NotFound] when it does not exist. */
    suspend fun delete(ref: FileRef): FileResult<Unit>
}

/** Sequential writes to one open file; see [RawFileWrites.write]. Throws [FileAccessException]. */
interface ByteSink {
    suspend fun write(
        buffer: ByteArray,
        offset: Int = 0,
        length: Int = buffer.size - offset,
    )

    /** Pushes everything written so far to the storage device (fsync); returns when it is durable. */
    suspend fun flush()
}
