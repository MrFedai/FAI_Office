/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.i18n

import kotlinx.coroutines.runBlocking
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.app_name
import org.faioffice.app.resources.destination_archive
import org.faioffice.app.resources.destination_documents
import org.faioffice.app.resources.destination_file_converter
import org.faioffice.app.resources.destination_home
import org.faioffice.app.resources.destination_pdf
import org.faioffice.app.resources.destination_presentations
import org.faioffice.app.resources.destination_recent_files
import org.faioffice.app.resources.destination_settings
import org.faioffice.app.resources.destination_spreadsheets
import org.faioffice.app.resources.language_english
import org.faioffice.app.resources.language_system
import org.faioffice.app.resources.language_turkish
import org.faioffice.app.resources.theme_dark
import org.faioffice.app.resources.theme_light
import org.faioffice.app.resources.theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every string key resolves in English and Turkish while the language switches EN -> TR -> System. */
class LanguageTest {
    private val keys: Map<String, StringResource> =
        mapOf(
            "app_name" to Res.string.app_name,
            "destination_home" to Res.string.destination_home,
            "destination_recent_files" to Res.string.destination_recent_files,
            "destination_documents" to Res.string.destination_documents,
            "destination_presentations" to Res.string.destination_presentations,
            "destination_spreadsheets" to Res.string.destination_spreadsheets,
            "destination_pdf" to Res.string.destination_pdf,
            "destination_file_converter" to Res.string.destination_file_converter,
            "destination_archive" to Res.string.destination_archive,
            "destination_settings" to Res.string.destination_settings,
            "language_system" to Res.string.language_system,
            "language_english" to Res.string.language_english,
            "language_turkish" to Res.string.language_turkish,
            "theme_system" to Res.string.theme_system,
            "theme_light" to Res.string.theme_light,
            "theme_dark" to Res.string.theme_dark,
        )
    private val systemLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(systemLocale)

    private fun resolveAll(locale: Locale): Map<String, String> {
        Locale.setDefault(locale)
        return runBlocking { keys.mapValues { (_, res) -> getString(res) } }
    }

    @Test
    fun everyKeyResolvesInBothLanguages() {
        val en = resolveAll(Locale.forLanguageTag(AppLanguage.English.tag!!))
        val tr = resolveAll(Locale.forLanguageTag(AppLanguage.Turkish.tag!!))
        assertEquals(keys.keys, en.keys)
        assertEquals(keys.keys, tr.keys)
        assertTrue((en + tr).values.all { it.isNotBlank() })
        assertEquals("Settings", en["destination_settings"])
        assertEquals("Ayarlar", tr["destination_settings"])
        assertEquals("Dosya dönüştürücü", tr["destination_file_converter"])
    }

    @Test
    fun switchingEnglishTurkishSystemFollowsTheLanguage() {
        assertEquals("Home", resolveAll(Locale.forLanguageTag("en"))["destination_home"])
        assertEquals("Ana sayfa", resolveAll(Locale.forLanguageTag("tr"))["destination_home"])
        // System: the platform default is restored, as LocalAppLocale does for a null tag.
        assertEquals("Home", resolveAll(Locale.forLanguageTag("en-US"))["destination_home"])
    }

    @Test
    fun systemLanguageHasNoOverrideTag() {
        assertEquals(null, AppLanguage.System.tag)
    }
}
