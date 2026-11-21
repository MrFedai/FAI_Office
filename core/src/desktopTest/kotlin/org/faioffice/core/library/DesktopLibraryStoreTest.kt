/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.library

import org.faioffice.core.files.DesktopFileAccess
import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileRef
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/** Real files in a temporary folder: the restart round trip on the desktop file system. */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class DesktopLibraryStoreTest : LibraryStoreContract() {
    override fun fixture(): Fixture =
        object : Fixture {
            val dir: Path = Files.createTempDirectory("fai-library-")
            val docs: Path = dir.resolve("Belgeler Öğüş").createDirectories()
            val app: Path = dir.resolve("uygulama").createDirectories()
            override val access: FileAccess = DesktopFileAccess()
            override val appFolder: FileRef = DesktopFileAccess.refOf(app)
            override val storeFile: FileRef = DesktopFileAccess.refOf(app.resolve("kitaplik.txt"))
            override val damagedFile: FileRef = DesktopFileAccess.refOf(app.resolve("kitaplik.damaged"))
            override val journal: FileRef = DesktopFileAccess.refOf(app.resolve("kayıt").createDirectories())

            override fun document(name: String): FileRef =
                DesktopFileAccess.refOf(docs.resolve(name.replace('\t', '_')).also { it.writeBytes(byteArrayOf(1)) })

            override fun missing(name: String): FileRef = DesktopFileAccess.refOf(docs.resolve(name))

            override fun writeStore(bytes: ByteArray) = app.resolve("kitaplik.txt").writeBytes(bytes)

            override fun readStore(): ByteArray? = app.resolve("kitaplik.txt").takeIf { it.exists() }?.readBytes()

            override fun readDamaged(): ByteArray? = app.resolve("kitaplik.damaged").takeIf { it.exists() }?.readBytes()

            override fun close() {
                dir.deleteRecursively()
            }
        }
}
