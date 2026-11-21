/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.faioffice.app.i18n.LocalAppLocale
import org.faioffice.app.resources.Res
import org.faioffice.app.resources.compat_phase_4
import org.faioffice.app.resources.compat_phase_5
import org.faioffice.app.resources.compat_phase_6
import org.faioffice.app.resources.compat_unsupported
import org.faioffice.app.resources.family_archive
import org.faioffice.app.resources.family_documents
import org.faioffice.app.resources.family_pdf
import org.faioffice.app.resources.family_presentations
import org.faioffice.app.resources.family_spreadsheets
import org.faioffice.app.resources.family_unknown
import org.faioffice.app.resources.family_viewer
import org.faioffice.app.resources.format_empty
import org.faioffice.app.resources.format_encrypted_ooxml
import org.faioffice.app.resources.format_unknown
import org.faioffice.app.resources.location_android
import org.faioffice.app.resources.location_web
import org.faioffice.app.resources.size_bytes
import org.faioffice.app.resources.size_gb
import org.faioffice.app.resources.size_kb
import org.faioffice.app.resources.size_mb
import org.faioffice.app.theme.FaiTheme
import org.faioffice.core.files.FileRef
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.ModuleFamily
import org.faioffice.core.text.uppercase
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Language part of the current locale tag ("tr-TR" -> "tr"). */
@Composable
fun currentLanguage(): String = LocalAppLocale.current.substringBefore('-').substringBefore('_')

private const val KIB = 1024L
private const val TEN = 10

/** Byte count as the user reads it: "820 B", "412 KB", "3,8 MB" (Turkish decimal comma), "1.2 GB". */
@Composable
fun sizeText(bytes: Long?): String? {
    if (bytes == null) return null
    val comma = currentLanguage() == "tr"
    val units = listOf(Res.string.size_bytes, Res.string.size_kb, Res.string.size_mb, Res.string.size_gb)
    var value = bytes.toDouble()
    var unit = 0
    while (value >= KIB && unit < units.lastIndex) {
        value /= KIB
        unit++
    }
    val number =
        if (unit == 0 || value >= TEN) {
            value.toLong().toString()
        } else {
            val tenths = (value * TEN).toLong()
            "${tenths / TEN}${if (comma) ',' else '.'}${tenths % TEN}"
        }
    return stringResource(units[unit], number)
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_DAY = 1440L
private const val MINUTES_PER_HOUR = 60L

/** Civil date (proleptic Gregorian) of a day count since 1970-01-01 (H. Hinnant's days-from-civil inverse). */
@Suppress("MagicNumber")
internal fun civilDate(days: Long): Triple<Int, Int, Int> {
    val z = days + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    return Triple(year, month, day)
}

/**
 * Local date of [epochMillis]: "21.11.2026" in Turkish, ISO "2026-11-21" otherwise (no month names to translate,
 * never ambiguous); [withTime] adds "14:05". Set in IBM Plex Mono by the caller (DESIGN.md section 4).
 */
fun dateText(
    epochMillis: Long,
    language: String,
    withTime: Boolean = false,
    offsetMillis: Long = localOffsetMillis(epochMillis),
): String {
    val minutes = (epochMillis + offsetMillis).floorDiv(MILLIS_PER_MINUTE)
    val days = minutes.floorDiv(MINUTES_PER_DAY)
    val (y, m, d) = civilDate(days)
    val dayMinutes = minutes - days * MINUTES_PER_DAY

    fun two(n: Long) = n.toString().padStart(2, '0')
    val date =
        if (language == "tr") "${two(d.toLong())}.${two(m.toLong())}.$y" else "$y-${two(m.toLong())}-${two(d.toLong())}"
    return if (withTime) "$date ${two(dayMinutes / MINUTES_PER_HOUR)}:${two(dayMinutes % MINUTES_PER_HOUR)}" else date
}

/** Name of a module family ("Document", "Spreadsheet" ...). */
val ModuleFamily.label: StringResource
    get() =
        when (this) {
            ModuleFamily.Documents -> Res.string.family_documents
            ModuleFamily.Spreadsheets -> Res.string.family_spreadsheets
            ModuleFamily.Presentations -> Res.string.family_presentations
            ModuleFamily.Pdf -> Res.string.family_pdf
            ModuleFamily.Archive -> Res.string.family_archive
            ModuleFamily.Viewer -> Res.string.family_viewer
            ModuleFamily.Unknown -> Res.string.family_unknown
        }

/** Short badge text: the canonical extension in capitals ("DOCX"), "?" for content without a format. */
val FileFormat.badge: String
    get() = canonicalExtension?.uppercase(IDENTIFIER_LANGUAGE)?.take(BADGE_CHARS) ?: "?"

private const val BADGE_CHARS = 4

/** Extensions are identifiers: language-neutral capitals, never Turkish dotted İ. */
private const val IDENTIFIER_LANGUAGE = "en"

/** Format name for the information page: the extension ("DOCX"), or a word for the non-formats. */
@Composable
fun formatName(format: FileFormat): String =
    when (format) {
        FileFormat.EncryptedOoxml -> stringResource(Res.string.format_encrypted_ooxml)
        FileFormat.Empty -> stringResource(Res.string.format_empty)
        FileFormat.Unknown -> stringResource(Res.string.format_unknown)
        else -> format.badge
    }

private val imageFormats =
    setOf(
        FileFormat.Png,
        FileFormat.Jpeg,
        FileFormat.Gif,
        FileFormat.Webp,
        FileFormat.Bmp,
        FileFormat.Tiff,
        FileFormat.Svg,
    )

/** Meaning colour of a format (DESIGN.md section 3): its family's file colour; text formats read as documents. */
@Composable
fun FileFormat.color(): Color {
    val c = FaiTheme.colors
    return when (family) {
        ModuleFamily.Documents -> c.fileDocument
        ModuleFamily.Spreadsheets -> c.fileSpreadsheet
        ModuleFamily.Presentations -> c.filePresentation
        ModuleFamily.Pdf -> c.filePdf
        ModuleFamily.Archive -> c.fileArchive
        ModuleFamily.Viewer -> if (this in imageFormats) c.fileImage else c.fileDocument
        ModuleFamily.Unknown -> c.muted
    }
}

/**
 * Compatibility sentence (MASTER_SPEC section 5): every detected format is level 0 "recognised" in Phase 3, and the
 * phase that brings its viewer follows from the family (PDF 4, viewer and archive 5, office formats 6).
 */
fun compatibilityText(format: FileFormat): StringResource =
    when (format.family) {
        ModuleFamily.Pdf -> Res.string.compat_phase_4
        ModuleFamily.Viewer, ModuleFamily.Archive -> Res.string.compat_phase_5
        ModuleFamily.Documents, ModuleFamily.Spreadsheets, ModuleFamily.Presentations -> Res.string.compat_phase_6
        ModuleFamily.Unknown -> Res.string.compat_unsupported
    }

/** Folder part of a reference, split into segments; empty when the reference carries no readable folder. */
fun folderSegments(ref: FileRef): List<String> =
    when (ref.kind) {
        FileRef.Kind.Path, FileRef.Kind.Memory -> {
            ref.location
                .split('/', '\\')
                .filter { it.isNotEmpty() }
                .dropLast(1)
        }

        FileRef.Kind.ContentUri -> {
            // SAF document ids of the system providers read "primary:Download/Kılavuzlar/x.pdf" once decoded.
            val id = percentDecode(ref.location.substringAfterLast('/'))
            val path = id.substringAfter(':', "")
            path.split('/').filter { it.isNotEmpty() }.dropLast(1)
        }

        FileRef.Kind.WebSession -> {
            emptyList()
        }
    }

private const val BREADCRUMB_SEGMENTS = 3
private const val BREADCRUMB = " › "

/** Location for a list row: the last folders as a breadcrumb ("Belgeler › Muhasebe › 2026"). */
@Composable
fun shortLocation(ref: FileRef): String {
    val segments = folderSegments(ref)
    return if (segments.isEmpty()) {
        locationFallback(ref)
    } else {
        segments.takeLast(BREADCRUMB_SEGMENTS).joinToString(BREADCRUMB)
    }
}

/** Full location for the information page: the folder path as the platform writes it. */
@Composable
fun fullLocation(ref: FileRef): String =
    when (ref.kind) {
        FileRef.Kind.Path, FileRef.Kind.Memory -> {
            val cut = maxOf(ref.location.lastIndexOf('/'), ref.location.lastIndexOf('\\'))
            if (cut > 0) ref.location.substring(0, cut) else ref.location
        }

        else -> {
            folderSegments(ref).joinToString(BREADCRUMB).ifEmpty { locationFallback(ref) }
        }
    }

/** Where an added folder is: its own path (desktop) or its folders below the storage root (Android tree URI). */
@Composable
fun folderLocation(ref: FileRef): String =
    when (ref.kind) {
        FileRef.Kind.Path, FileRef.Kind.Memory -> {
            ref.location
        }

        FileRef.Kind.ContentUri -> {
            val id = percentDecode(ref.location.substringAfterLast('/'))
            id.substringAfter(':', "").split('/').filter { it.isNotEmpty() }.joinToString(BREADCRUMB).ifEmpty {
                locationFallback(ref)
            }
        }

        FileRef.Kind.WebSession -> {
            locationFallback(ref)
        }
    }

@Composable
private fun locationFallback(ref: FileRef): String =
    when (ref.kind) {
        FileRef.Kind.WebSession -> stringResource(Res.string.location_web)
        FileRef.Kind.ContentUri -> stringResource(Res.string.location_android)
        else -> ref.location
    }

private const val HEX = 16

/** Decodes %XX escapes as UTF-8; malformed escapes stay as they are. */
internal fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val out = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val hex = if (c == '%' && i + 2 <= text.lastIndex) text.substring(i + 1, i + 3) else null
        val byte = hex?.toIntOrNull(HEX)
        if (byte != null) {
            out += byte.toByte()
            i += 3
        } else {
            c.toString().encodeToByteArray().forEach { out += it }
            i++
        }
    }
    return out.toByteArray().decodeToString()
}
