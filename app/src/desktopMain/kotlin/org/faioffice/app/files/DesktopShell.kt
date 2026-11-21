/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import java.nio.file.Path

/** The desktop systems the shell hand-offs differ on. */
enum class DesktopOs {
    Windows,
    MacOs,
    Linux,
    ;

    companion object {
        fun of(osName: String): DesktopOs =
            when {
                osName.startsWith("Windows", ignoreCase = true) -> Windows
                osName.startsWith("Mac", ignoreCase = true) -> MacOs
                else -> Linux
            }

        val current: DesktopOs by lazy { of(System.getProperty("os.name").orEmpty()) }
    }
}

/**
 * The processes behind "Show in folder" and "Open in another app" (the system's own file manager and app chooser;
 * a local process, no network). Pure: the commands are tested, [DesktopFiles] starts them.
 */
object DesktopShell {
    /**
     * Opens the folder of [file] with [file] selected: Explorer `/select,` on Windows, Finder `open -R` on macOS,
     * the freedesktop `FileManager1.ShowItems` D-Bus call on Linux (Files, Dolphin, Nemo, Thunar…).
     */
    fun showInFolder(
        os: DesktopOs,
        file: Path,
    ): List<String> =
        when (os) {
            // Explorer reads "/select," and the path as separate words; ProcessBuilder quotes the path.
            DesktopOs.Windows -> {
                listOf("explorer.exe", "/select,", file.toString())
            }

            DesktopOs.MacOs -> {
                listOf("open", "-R", file.toString())
            }

            DesktopOs.Linux -> {
                listOf(
                    "dbus-send",
                    "--session",
                    "--print-reply",
                    "--dest=org.freedesktop.FileManager1",
                    "--type=method_call",
                    "/org/freedesktop/FileManager1",
                    "org.freedesktop.FileManager1.ShowItems",
                    "array:string:${file.toUri()}",
                    "string:",
                )
            }
        }

    /**
     * The system's "Open with" chooser for [file] on Windows (`OpenWith.exe`, Windows 8 and later, from
     * [systemRoot]); null elsewhere, where the desktop opens the file with its default app instead (Linux and macOS
     * have no common chooser).
     */
    fun openWith(
        os: DesktopOs,
        file: Path,
        systemRoot: String?,
    ): List<String>? =
        if (os == DesktopOs.Windows) {
            val root = systemRoot?.takeIf { it.isNotBlank() } ?: "C:\\Windows"
            listOf("$root\\System32\\OpenWith.exe", file.toString())
        } else {
            null
        }
}
