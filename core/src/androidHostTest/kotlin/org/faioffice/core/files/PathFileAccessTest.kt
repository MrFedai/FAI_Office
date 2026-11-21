/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** Recursively deletes a temporary test folder (API 26 `java.nio`, no Kotlin path extensions needed). */
private fun Path.deleteTree() {
    toFile().walkBottomUp().forEach {
        it.setWritable(true)
        it.delete()
    }
}

/** The shared contract on the Android path implementation (app-private storage), run on the host JVM. */
class PathFileAccessTest : FileAccessContract() {
    override fun fixture(): FileAccessContract.Fixture =
        object : FileAccessContract.Fixture {
            val dir: Path = Files.createTempDirectory("fai-android-files-")
            override val access: FileAccess = PathFileAccess()
            override val root: FileRef = PathFileAccess.refOf(dir)

            override fun file(
                path: String,
                bytes: ByteArray,
            ): FileRef {
                val file = dir.resolve(path)
                Files.createDirectories(file.parent)
                Files.write(file, bytes)
                return PathFileAccess.refOf(file)
            }

            override fun folder(path: String): FileRef = PathFileAccess.refOf(Files.createDirectories(dir.resolve(path)))

            override fun missing(path: String): FileRef = PathFileAccess.refOf(dir.resolve(path))

            override fun makeReadOnly(ref: FileRef): Boolean {
                val path = PathFileAccess.pathOf(ref)
                path.toFile().setWritable(false)
                return !Files.isWritable(path)
            }

            override fun close() = dir.deleteTree()
        }

    @Test
    fun androidExceptionsMapToTypedErrors() {
        assertEquals(FileError.NotFound, pathErrorOf(NoSuchFileException("/a"), writing = false))
        assertEquals(
            FileError.NotFound,
            pathErrorOf(FileNotFoundException("/a: open failed: ENOENT (No such file or directory)"), writing = false),
        )
        assertEquals(
            FileError.NoSpace,
            pathErrorOf(IOException("write failed: ENOSPC (No space left on device)"), writing = true),
        )
        assertEquals(FileError.ReadOnly, pathErrorOf(AccessDeniedException("/a"), writing = true))
        assertEquals(
            FileError.PermissionLost,
            pathErrorOf(FileNotFoundException("/a: open failed: EACCES (Permission denied)"), writing = false),
        )
        assertEquals(FileError.ReadOnly, pathErrorOf(IOException("open failed: EROFS (Read-only file system)"), true))
        assertEquals(FileError.IoError("EIO"), pathErrorOf(IOException("EIO"), writing = false))
    }

    @Test
    fun grantLimitFollowsTheAndroidVersion() {
        assertEquals(128, persistedGrantLimit(29))
        assertEquals(512, persistedGrantLimit(30))
        assertEquals(512, persistedGrantLimit(36))
    }
}

/**
 * Safe save as the Android app runs it on app-private files: [AndroidFileAccess] has no atomic replace (content
 * URIs), so the library store and every other file take the backup path, with the journal in app-private storage.
 */
class PathSafeSaveTest : SafeSaveContract() {
    override val contentUri: Boolean = true

    override fun fixture(): Fixture =
        object : Fixture {
            val dir: Path = Files.createTempDirectory("fai-android-save-")
            val documents: Path = Files.createDirectories(dir.resolve("Belgeler Öğüş"))
            override val inner: FileAccess = PathFileAccess()
            override val folder: FileRef = PathFileAccess.refOf(documents)
            override val journal: FileRef = PathFileAccess.refOf(Files.createDirectories(dir.resolve("files/safe-save")))

            override fun file(
                name: String,
                bytes: ByteArray,
            ): FileRef = PathFileAccess.refOf(Files.write(documents.resolve(name), bytes))

            override fun missing(name: String): FileRef = PathFileAccess.refOf(documents.resolve(name))

            override fun close() = dir.deleteTree()
        }
}
