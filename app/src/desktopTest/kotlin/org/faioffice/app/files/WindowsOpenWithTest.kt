/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import org.faioffice.core.formats.FileFormat
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The MSI's "Open with" entries (app/msi/open-with.wxs, added to the installer by app/build.gradle.kts): every
 * detected format with a registered type is offered, per user, and the app never claims to be a default handler.
 */
class WindowsOpenWithTest {
    private val values: List<Element> by lazy {
        val root =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(File("msi/open-with.wxs"))
                .documentElement
        val nodes = root.getElementsByTagName("RegistryValue")
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val extensions: Set<String> =
        FileFormat.entries
            .filter { it.mimeType != null }
            .flatMap { it.extensions.toList() }
            .toSet()

    private fun Element.key(): String = getAttribute("Key")

    @Test
    fun everyDetectedFormatIsOfferedInOpenWith() {
        val progIds =
            values
                .filter { it.key().endsWith("\\OpenWithProgids") }
                .map { it.key().removePrefix("Software\\Classes\\.").removeSuffix("\\OpenWithProgids") }
        assertEquals(extensions, progIds.toSet())
        assertEquals(progIds.size, progIds.toSet().size, "duplicate extension")
        values.filter { it.key().endsWith("\\OpenWithProgids") }.forEach {
            assertEquals("FAIOffice.File", it.getAttribute("Name"))
        }
        val supported =
            values
                .filter { it.key() == "Software\\Classes\\Applications\\fai-office.exe\\SupportedTypes" }
                .map { it.getAttribute("Name").removePrefix(".") }
        assertEquals(extensions, supported.toSet())
    }

    @Test
    fun neverClaimsADefaultAndStaysPerUser() {
        assertTrue(values.isNotEmpty())
        values.forEach { value ->
            assertEquals("HKCU", value.getAttribute("Root"), value.key())
            // The default value of Software\Classes\.<ext> would make the app the handler of that type.
            assertTrue(!Regex("""Software\\Classes\\\.[^\\]+""").matches(value.key()), value.key())
            listOf("RegisteredApplications", "Capabilities", "UserChoice", "FileExts").forEach {
                assertTrue(it !in value.key(), value.key())
            }
            assertTrue(value.getAttribute("Value").isNotEmpty(), "empty value under ${value.key()}")
        }
        val keyPaths = values.filter { it.getAttribute("KeyPath") == "yes" }
        assertEquals(1, keyPaths.size)
    }

    @Test
    fun openCommandsPassTheFileToTheInstalledLauncher() {
        val commands = values.filter { it.key().endsWith("\\shell\\open\\command") }
        assertEquals(2, commands.size)
        commands.forEach {
            assertEquals("\"[INSTALLDIR]fai-office.exe\" \"%1\"", it.getAttribute("Value"))
        }
    }
}
