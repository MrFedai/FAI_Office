/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import org.faioffice.core.formats.OpenableMimeTypes
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Android manifest announces exactly the formats detection knows for "Open with" and "Share", and asks for no
 * storage permission (the merged manifest, with every library's additions, is checked by ci/android.sh).
 */
class AndroidManifestTest {
    private val manifest: Element by lazy {
        val file = File("../androidApp/src/main/AndroidManifest.xml")
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun Element.children(tag: String): List<Element> =
        getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    private fun Element.android(name: String): String = getAttributeNS(ANDROID, name)

    /** MIME types of the intent filters whose action is [action]. */
    private fun mimeTypesFor(action: String): List<String> =
        manifest
            .children("intent-filter")
            .filter { filter -> filter.children("action").any { it.android("name") == action } }
            .flatMap { filter -> filter.children("data").map { it.android("mimeType") } }
            .filter { it.isNotEmpty() }

    @Test
    fun viewAndShareFiltersAnnounceTheDetectedFormats() {
        val expected = OpenableMimeTypes.incoming.toSet()
        for (action in listOf(
            IncomingFiles.ACTION_VIEW,
            IncomingFiles.ACTION_SEND,
            IncomingFiles.ACTION_SEND_MULTIPLE,
        )) {
            val announced = mimeTypesFor(action)
            assertEquals(expected, announced.toSet(), action)
            assertEquals(announced.size, announced.toSet().size, "$action lists a type twice")
        }
    }

    @Test
    fun viewFiltersTakeContentUrisOnly() {
        val view =
            manifest.children("intent-filter").filter { filter ->
                filter.children("action").any { it.android("name") == IncomingFiles.ACTION_VIEW }
            }
        assertTrue(view.isNotEmpty())
        view.forEach { filter ->
            val schemes = filter.children("data").map { it.android("scheme") }.filter { it.isNotEmpty() }
            assertEquals(listOf("content"), schemes)
        }
    }

    @Test
    fun noStoragePermission() {
        val permissions = manifest.children("uses-permission").map { it.android("name") }
        assertTrue(permissions.none { it.contains("STORAGE") || it.contains("READ_MEDIA") }, "$permissions")
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
