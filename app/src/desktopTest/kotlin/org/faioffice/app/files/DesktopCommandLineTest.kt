/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopCommandLineTest {
    private val cwd: Path = Path.of("/home/kullanıcı/Belgeler").toAbsolutePath()

    private fun files(vararg args: String): List<Path> = DesktopCommandLine.files(args.toList(), cwd)

    @Test
    fun absoluteAndRelativePathsResolveAgainstTheWorkingFolder() {
        assertEquals(
            listOf(cwd.resolve("Şubat raporu.docx"), Path.of("/tmp/tablo.xlsx").toAbsolutePath(), cwd.resolve("x.pdf")),
            files("Şubat raporu.docx", "/tmp/tablo.xlsx", "alt/../x.pdf"),
        )
    }

    @Test
    fun fileUrisAreDecodedAndOtherSchemesDropped() {
        val target = Path.of(System.getProperty("java.io.tmpdir"), "Çizelge Ağustos.ods").toAbsolutePath()
        val uri = target.toUri().toASCIIString()
        assertEquals(true, "%C3%87" in uri, uri)
        assertEquals(
            listOf(target),
            files(uri, "https://example.org/a.pdf", "content://x/y"),
        )
    }

    @Test
    fun optionsAreSkippedUntilDoubleDash() {
        assertEquals(
            listOf(cwd.resolve("a.txt"), cwd.resolve("-tire.txt")),
            files("-psn_0_12345", "--verbose", "a.txt", "--", "-tire.txt"),
        )
    }

    @Test
    fun blanksAndDuplicatesAreDroppedAndTheCountIsCapped() {
        assertEquals(listOf(cwd.resolve("a.txt")), files("", "  ", "a.txt", "./a.txt", "a.txt"))
        val many = (1..IncomingFiles.MAX_FILES + 20).map { "f$it.txt" }
        val result = DesktopCommandLine.files(many, cwd)
        assertEquals(IncomingFiles.MAX_FILES, result.size)
        assertEquals(cwd.resolve("f1.txt"), result.first())
    }

    @Test
    fun windowsCommandLineSplitsLikeTheCRuntime() {
        assertEquals(
            listOf("C:\\Program Files\\FAI Office\\fai-office.exe", "C:\\Belgeler\\Şubat raporu.docx"),
            DesktopCommandLine.splitWindows(
                "\"C:\\Program Files\\FAI Office\\fai-office.exe\" \"C:\\Belgeler\\Şubat raporu.docx\"",
            ),
        )
        assertEquals(listOf("app.exe", "a", "b c", "d"), DesktopCommandLine.splitWindows("app.exe a \"b c\"   d "))
        // Backslashes: literal unless before a quote; 2n+1 before a quote give a literal quote.
        assertEquals(
            listOf("app", "C:\\yol\\", "a\\\\b", "x\"y", "q\"z"),
            DesktopCommandLine.splitWindows("app \"C:\\yol\\\\\" a\\\\b x\\\"y \"q\"\"z\""),
        )
        assertEquals(listOf("app", ""), DesktopCommandLine.splitWindows("app \"\""))
        assertEquals(emptyList(), DesktopCommandLine.splitWindows("   "))
    }

    @Test
    fun windowsArgumentsComeFromTheUnicodeCommandLineWhenTheyMatch() {
        val ansi = listOf("C:\\Belgeler\\?ubat.docx")
        val line = "\"C:\\FAI\\fai-office.exe\" \"C:\\Belgeler\\Şubat.docx\""
        assertEquals(listOf("C:\\Belgeler\\Şubat.docx"), DesktopCommandLine.programArgs(ansi, true, line))
        // A java.exe command line carries JVM options too: the counts differ, the arguments stay.
        val java = "java.exe -cp app.jar org.faioffice.app.MainKt \"C:\\Belgeler\\Şubat.docx\""
        assertEquals(ansi, DesktopCommandLine.programArgs(ansi, true, java))
        assertEquals(ansi, DesktopCommandLine.programArgs(ansi, false, line))
        assertEquals(ansi, DesktopCommandLine.programArgs(ansi, true, null))
    }
}
