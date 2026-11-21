/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.shell

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.faioffice.app.App
import org.faioffice.app.files.FilesModel
import org.faioffice.app.files.OpenRequest
import org.faioffice.app.files.SampleFiles
import org.faioffice.app.i18n.AppLanguage
import org.faioffice.app.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * File screen screenshots (P3.8) into build/bakis/: every file screen and information-page state with the sample
 * library ([SampleFiles]: real-length Turkish and English names, pinned, missing and access-lost entries, two added
 * folders) as <theme>_<size>_<screen>_<lang>.png, in both themes and both languages at 390x844 and 1920x950; the
 * empty lists come from an empty library. Times are shown in Istanbul time so the shots do not depend on the machine.
 */
class FileShotTest {
    private val outDir = File(System.getProperty("bakis.dir") ?: "build/bakis")
    private val themes = listOf("acik" to ThemeMode.Light, "koyu" to ThemeMode.Dark)
    private val languages = listOf("en" to AppLanguage.English, "tr" to AppLanguage.Turkish)
    private val sizes = listOf(390 to 844, 1920 to 950)
    private val systemLocale: Locale = Locale.getDefault()
    private val systemZone: TimeZone = TimeZone.getDefault()

    @BeforeTest
    fun istanbulTime() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))

    @AfterTest
    fun restore() {
        Locale.setDefault(systemLocale)
        TimeZone.setDefault(systemZone)
    }

    /** One screen: what to show and how the model and navigator are set up. */
    private class Screen(
        val name: String,
        val destination: Destination,
        val empty: Boolean = false,
        val opened: ((SampleFiles) -> OpenRequest)? = null,
    )

    private val screens =
        listOf(
            Screen("home_files", Destination.Home),
            Screen("recent_files_list", Destination.RecentFiles),
            Screen("documents_list", Destination.Documents),
            Screen("spreadsheets_list", Destination.Spreadsheets),
            Screen("presentations_list", Destination.Presentations),
            Screen("pdf_list", Destination.Pdf),
            Screen("archive_list", Destination.Archive),
            Screen("settings_files", Destination.Settings),
            Screen("recent_files_empty", Destination.RecentFiles, empty = true),
            Screen("documents_empty", Destination.Documents, empty = true),
            Screen("settings_empty", Destination.Settings, empty = true),
            Screen("file_info", Destination.Pdf) { it.resolve("manual") },
            Screen("file_info_missing", Destination.RecentFiles) { it.resolve("zip") },
            Screen("file_info_permission", Destination.RecentFiles) { it.resolve("csv") },
            Screen("file_info_damaged", Destination.Pdf) { it.resolve("spec") },
            Screen("file_info_protected", Destination.Documents) { it.protectedRequest("report") },
        )

    private fun render(
        file: File,
        size: Pair<Int, Int>,
        mode: ThemeMode,
        language: AppLanguage,
        screen: Screen,
    ) {
        val sample = SampleFiles(populated = !screen.empty)
        val model: FilesModel = sample.model()
        val nav = ShellNavigator().apply { navigate(screen.destination) }
        screen.opened?.let { open ->
            val request = open(sample)
            model.opened = request
            nav.openFile(request.ref)
        }
        val scene =
            ImageComposeScene(
                size.first,
                size.second,
                Density(1f),
            ) { App(mode, language, navigator = nav, files = model) }
        try {
            scene.render()
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)
            file.writeBytes(checkNotNull(png).bytes)
        } finally {
            scene.close()
        }
        assertTrue(file.length() > 0, "empty shot: $file")
    }

    @Test
    fun writesFileScreenShots() {
        outDir.mkdirs()
        for ((themeName, mode) in themes) {
            for ((tag, language) in languages) {
                for ((w, h) in sizes) {
                    for (screen in screens) {
                        render(
                            File(outDir, "${themeName}_${w}x${h}_${screen.name}_$tag.png"),
                            w to h,
                            mode,
                            language,
                            screen,
                        )
                    }
                }
            }
        }
    }
}
