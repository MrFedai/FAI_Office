/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

/**
 * Why a file operation failed. Implementations map every platform exception to one of these; nothing else leaks.
 * Cancellation is not an error: a cancelled coroutine gets its `CancellationException` as usual.
 */
sealed interface FileError {
    /** The file or folder does not exist (any more). */
    data object NotFound : FileError

    /** Access was denied or the grant is gone (Android grant revoked, folder permissions changed). */
    data object PermissionLost : FileError

    /** Another program holds the file open without sharing (Windows sharing violation). */
    data object Locked : FileError

    /** The target cannot be written: read-only file, medium or provider. */
    data object ReadOnly : FileError

    /** A file was expected and a folder found, or the other way round; [expected] is what the operation needed. */
    data class WrongKind(
        val expected: EntryKind,
    ) : FileError

    /** A file that must be new already exists. */
    data object AlreadyExists : FileError

    /** The disk or provider is full. */
    data object NoSpace : FileError

    /** Damaged or unreadable data, broken medium, provider failure: any I/O error without a narrower type. */
    data class IoError(
        val detail: String,
    ) : FileError
}

/** Outcome of a file operation: a value or a typed [FileError]. */
sealed interface FileResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : FileResult<T>

    data class Failure(
        val error: FileError,
    ) : FileResult<Nothing>
}

fun <T> FileResult<T>.getOrNull(): T? = (this as? FileResult.Success)?.value

fun FileResult<*>.errorOrNull(): FileError? = (this as? FileResult.Failure)?.error

/** The value, or [FileAccessException] carrying the error (convenient inside a read or write block). */
fun <T> FileResult<T>.getOrThrow(): T =
    when (this) {
        is FileResult.Success -> value
        is FileResult.Failure -> throw FileAccessException(error)
    }

inline fun <T, R> FileResult<T>.map(transform: (T) -> R): FileResult<R> =
    when (this) {
        is FileResult.Success -> FileResult.Success(transform(value))
        is FileResult.Failure -> this
    }

/**
 * Thrown only by the handles passed to read and write blocks ([RandomAccessReader], [ByteSource], [ByteSink]);
 * the [FileAccess] call that opened the handle catches it and returns [FileResult.Failure] with [error].
 */
class FileAccessException(
    val error: FileError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

/** Runs [block] and turns a [FileAccessException] from it into a [FileResult.Failure]; for implementations. */
internal inline fun <T> fileResult(block: () -> T): FileResult<T> =
    try {
        FileResult.Success(block())
    } catch (e: FileAccessException) {
        FileResult.Failure(e.error)
    }
