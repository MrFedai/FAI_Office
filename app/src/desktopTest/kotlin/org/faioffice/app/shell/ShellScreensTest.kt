/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.shell

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.faioffice.app.App
import org.faioffice.app.i18n.AppLanguage
import org.faioffice.app.theme.ThemeMode
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every destination opens its screen without files: navigates to each of the nine on the desktop (1920 x 950,
 * English) and the phone layout (390 x 844, Turkish) and checks the title and the primary action on screen. File
 * opening works since P3.8, so the actions are enabled; only File Converter's stays disabled (Phase 6, no fake
 * function). Settings shows its sections and has no primary action.
 */
class ShellScreensTest {
    private val systemLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(systemLocale)

    private class Expected(
        val title: String,
        val action: String?,
    )

    private val english =
        mapOf(
            Destination.Home to Expected("Start with a file", "Open file"),
            Destination.RecentFiles to Expected("No recent files yet", "Open file"),
            Destination.Documents to Expected("No documents yet", "Open document"),
            Destination.Presentations to Expected("No presentations yet", "Open presentation"),
            Destination.Spreadsheets to Expected("No spreadsheets yet", "Open spreadsheet"),
            Destination.Pdf to Expected("No PDF files yet", "Open PDF"),
            Destination.FileConverter to Expected("Nothing to convert yet", "Choose file"),
            Destination.Archive to Expected("No archives yet", "Open archive"),
            Destination.Settings to Expected("Added folders", null),
        )

    private val turkish =
        mapOf(
            Destination.Home to Expected("Bir dosyayla başlayın", "Dosya aç"),
            Destination.RecentFiles to Expected("Henüz son dosya yok", "Dosya aç"),
            Destination.Documents to Expected("Henüz belge yok", "Belge aç"),
            Destination.Presentations to Expected("Henüz sunum yok", "Sunum aç"),
            Destination.Spreadsheets to Expected("Henüz tablo yok", "Tablo aç"),
            Destination.Pdf to Expected("Henüz PDF yok", "PDF aç"),
            Destination.FileConverter to Expected("Dönüştürülecek dosya yok", "Dosya seç"),
            Destination.Archive to Expected("Henüz arşiv yok", "Arşiv aç"),
            Destination.Settings to Expected("Eklenen klasörler", null),
        )

    /** All semantics nodes (merged tree) of the scene. */
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()

        fun walk(node: SemanticsNode) {
            out += node
            node.children.forEach(::walk)
        }
        semanticsOwners.forEach { walk(it.rootSemanticsNode) }
        return out
    }

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun checkAll(
        width: Int,
        height: Int,
        language: AppLanguage,
        expected: Map<Destination, Expected>,
    ) {
        val nav = ShellNavigator()
        val scene = ImageComposeScene(width, height, Density(1f)) { App(ThemeMode.Light, language, navigator = nav) }
        try {
            scene.render()
            for (d in Destination.entries) {
                nav.navigate(d)
                Snapshot.sendApplyNotifications()
                scene.render()
                assertEquals(d, nav.current)
                val nodes = scene.nodes()
                val want = expected.getValue(d)
                assertTrue(
                    nodes.any { want.title in it.texts() },
                    "$d at ${width}x$height: title '${want.title}' not on screen",
                )
                val actions = nodes.filter { it.config.getOrNull(SemanticsProperties.Role) != null }
                val action = actions.firstOrNull { node -> node.texts().any { it == want.action } }
                if (want.action == null) {
                    assertNull(action, "$d has no primary action")
                } else {
                    assertNotNull(action, "$d at ${width}x$height: action '${want.action}' not on screen")
                    val disabled = action.config.contains(SemanticsProperties.Disabled)
                    if (d == Destination.FileConverter) {
                        assertTrue(disabled, "$d: '${want.action}' must be disabled until it works")
                    } else {
                        assertTrue(!disabled, "$d: '${want.action}' must work")
                    }
                }
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun desktopOpensEveryEmptyState() = checkAll(1920, 950, AppLanguage.English, english)

    @Test
    fun phoneOpensEveryEmptyState() = checkAll(390, 844, AppLanguage.Turkish, turkish)
}
