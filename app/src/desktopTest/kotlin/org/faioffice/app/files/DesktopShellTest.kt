/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopShellTest {
    private val file: Path = Path.of("Belgeler", "Şubat raporu.docx").toAbsolutePath()

    @Test
    fun showInFolderUsesEachSystemsFileManager() {
        assertEquals(
            listOf("explorer.exe", "/select,", file.toString()),
            DesktopShell.showInFolder(DesktopOs.Windows, file),
        )
        assertEquals(listOf("open", "-R", file.toString()), DesktopShell.showInFolder(DesktopOs.MacOs, file))
        val linux = DesktopShell.showInFolder(DesktopOs.Linux, file)
        assertEquals("dbus-send", linux.first())
        assertTrue("org.freedesktop.FileManager1.ShowItems" in linux)
        assertTrue("array:string:${file.toUri()}" in linux)
    }

    @Test
    fun openWithIsTheWindowsChooserOnly() {
        assertEquals(
            listOf("D:\\Win\\System32\\OpenWith.exe", file.toString()),
            DesktopShell.openWith(DesktopOs.Windows, file, "D:\\Win"),
        )
        assertEquals(
            "C:\\Windows\\System32\\OpenWith.exe",
            DesktopShell.openWith(DesktopOs.Windows, file, null)?.first(),
        )
        assertNull(DesktopShell.openWith(DesktopOs.Linux, file, null))
        assertNull(DesktopShell.openWith(DesktopOs.MacOs, file, null))
    }

    @Test
    fun osNames() {
        assertEquals(DesktopOs.Windows, DesktopOs.of("Windows 11"))
        assertEquals(DesktopOs.MacOs, DesktopOs.of("Mac OS X"))
        assertEquals(DesktopOs.Linux, DesktopOs.of("Linux"))
    }

    @Test
    fun appDataFolders() {
        val env =
            mapOf(
                "APPDATA" to "C:\\Users\\ayşe\\AppData\\Roaming",
                "LOCALAPPDATA" to "C:\\Users\\ayşe\\AppData\\Local",
                "XDG_DATA_HOME" to "/home/ayşe/.veri",
            )
        val windows = DesktopDirs.of(DesktopOs.Windows, env::get, "C:\\Users\\ayşe", null)
        assertEquals(Path.of("C:\\Users\\ayşe\\AppData\\Roaming").resolve("FAI Office"), windows.data)
        assertEquals(Path.of("C:\\Users\\ayşe\\AppData\\Local").resolve("FAI Office"), windows.local)
        val linux = DesktopDirs.of(DesktopOs.Linux, env::get, "/home/ayşe", null)
        assertEquals(Path.of("/home/ayşe/.veri/fai-office"), linux.data)
        assertEquals(linux.data, linux.local)
        assertEquals(
            Path.of("/home/ayşe/.local/share/fai-office"),
            DesktopDirs.of(DesktopOs.Linux, { null }, "/home/ayşe", null).data,
        )
        assertEquals(Path.of("/tmp/fai"), DesktopDirs.of(DesktopOs.Windows, env::get, "/home", "/tmp/fai").local)
    }

    private class FileList(
        private val files: List<File>,
    ) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return files
        }
    }

    private class UriList(
        private val text: String,
    ) : Transferable {
        private val flavor = DataFlavor("text/uri-list;class=java.lang.String")

        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(flavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == this.flavor

        override fun getTransferData(flavor: DataFlavor): Any = text
    }

    @Test
    fun droppedFileListsAndUriListsBecomePaths() {
        val a = file.toFile()
        val b = file.resolveSibling("tablo.xlsx").toFile()
        val list = FileList(listOf(a, b, a))
        assertTrue(DesktopDrop.accepts(list))
        assertEquals(listOf(a.toPath(), b.toPath()), DesktopDrop.files(list))

        val uris = UriList("# yorum\r\n${file.toUri()}\r\nhttps://example.org/x.pdf\r\n")
        assertTrue(DesktopDrop.accepts(uris))
        assertEquals(listOf(file), DesktopDrop.files(uris))

        val text = StringSelection("düz metin")
        assertFalse(DesktopDrop.accepts(text))
        assertEquals(emptyList(), DesktopDrop.files(text))
    }
}
