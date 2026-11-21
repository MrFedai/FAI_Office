/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared contract on real files in a temporary folder. */
class DesktopFileAccessTest : FileAccessContract() {
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    override fun fixture(): FileAccessContract.Fixture =
        object : FileAccessContract.Fixture {
            val dir: Path = Files.createTempDirectory("fai-files-")
            override val access: FileAccess = DesktopFileAccess()
            override val root: FileRef = DesktopFileAccess.refOf(dir)

            override fun file(
                path: String,
                bytes: ByteArray,
            ): FileRef {
                val file = dir.resolve(path)
                file.parent.createDirectories()
                file.writeBytes(bytes)
                return DesktopFileAccess.refOf(file)
            }

            override fun folder(path: String): FileRef = DesktopFileAccess.refOf(dir.resolve(path).createDirectories())

            override fun missing(path: String): FileRef = DesktopFileAccess.refOf(dir.resolve(path))

            override fun makeReadOnly(ref: FileRef): Boolean {
                val path = DesktopFileAccess.pathOf(ref)
                path.toFile().setWritable(false)
                return !Files.isWritable(path)
            }

            override fun close() {
                dir.toFile().walk().forEach { it.setWritable(true) }
                dir.deleteRecursively()
            }
        }

    @Test
    fun platformExceptionsMapToTypedErrors() {
        val windowsLock =
            FileSystemException(
                "C:\\a.docx",
                null,
                "The process cannot access the file because it is being used by another process",
            )
        assertEquals(FileError.Locked, errorOf(windowsLock, writing = false))
        assertEquals(
            FileError.NoSpace,
            errorOf(FileSystemException("/a", null, "No space left on device"), writing = true),
        )
        assertEquals(FileError.NoSpace, errorOf(IOException("There is not enough space on the disk"), writing = true))
        assertEquals(FileError.ReadOnly, errorOf(AccessDeniedException("/a"), writing = true))
        assertEquals(FileError.PermissionLost, errorOf(AccessDeniedException("/a"), writing = false))
        assertEquals(
            FileError.ReadOnly,
            errorOf(FileSystemException("/a", null, "Read-only file system"), writing = true),
        )
        assertEquals(
            FileError.IoError("Input/output error"),
            errorOf(IOException("Input/output error"), writing = false),
        )
    }
}
