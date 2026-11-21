/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.formats

import org.faioffice.core.files.EntryKind
import org.faioffice.core.files.FileAccess
import org.faioffice.core.files.FileError
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.FileResult
import org.faioffice.core.files.RandomAccessReader
import org.faioffice.core.files.readBytes

/**
 * What a file is.
 *
 * @property format the format the content shows; the name only breaks ties the content leaves open.
 * @property damaged a truncated or malformed container (missing ZIP directory, broken OLE2 chain, PDF without `%%EOF`).
 * @property passwordProtected the content is encrypted (OOXML inside an OLE2 `EncryptedPackage`).
 * @property extensionMismatch the name's extension belongs to another known format.
 */
data class FormatDetection(
    val format: FileFormat,
    val damaged: Boolean = false,
    val passwordProtected: Boolean = false,
    val extensionMismatch: Boolean = false,
) {
    val family: ModuleFamily get() = format.family

    /** Level in the compatibility matrix; null for [FileFormat.Empty] and [FileFormat.Unknown]. */
    val level: CompatLevel? get() = CompatibilityMatrix.levelOf(format)
}

/**
 * Content-based format detection through [FileAccess]: reads the head of the file and, for containers, the ZIP
 * central directory or the OLE2 directory, each within fixed limits; never the whole file. File errors come back as
 * [FileResult.Failure]; damaged content is a successful detection with [FormatDetection.damaged].
 */
class FormatDetector(
    private val files: FileAccess,
) {
    suspend fun detect(ref: FileRef): FileResult<FormatDetection> {
        val info =
            when (val result = files.info(ref)) {
                is FileResult.Success -> result.value
                is FileResult.Failure -> return result
            }
        if (info.kind != EntryKind.File) return FileResult.Failure(FileError.WrongKind(EntryKind.File))
        return files.readRandom(ref) { detectFormat(it, info.name, info.mimeType) }
    }
}

/**
 * Detects the format of the open file [reader]. [name] (its extension) and [mimeType] are tie-breakers only: for text
 * (CSV, code), and for containers whose content is ambiguous or damaged. The MIME type stands in for a missing extension.
 */
suspend fun detectFormat(
    reader: RandomAccessReader,
    name: String?,
    mimeType: String? = null,
): FormatDetection {
    val nameExtension = extensionOf(name)
    val extension = nameExtension ?: mimeType?.let { FileFormat.byMimeType(it)?.canonicalExtension }
    val guess = if (reader.size == 0L) Guess(FileFormat.Empty) else sniff(reader, extension)
    return FormatDetection(guess.format, guess.damaged, guess.passwordProtected, mismatch(nameExtension, guess.format))
}

internal data class Guess(
    val format: FileFormat,
    val damaged: Boolean = false,
    val passwordProtected: Boolean = false,
)

private suspend fun sniff(
    reader: RandomAccessReader,
    extension: String?,
): Guess {
    val head = reader.readBytes(0, HEAD_BYTES)
    val simple = Signatures.match(head)
    return when {
        head.matches(0, CompoundFile.SIGNATURE) -> compound(reader, extension)
        head.matches(0, ZIP_LOCAL) || head.matches(0, ZIP_EMPTY) -> ContainerRules.zip(reader, head, extension)
        head.hasPdfMarker() -> pdf(reader)
        simple != null -> Guess(simple)
        else -> text(head, cut = reader.size > head.size, extension)
    }
}

private suspend fun pdf(reader: RandomAccessReader): Guess {
    val tailStart = maxOf(0L, reader.size - PDF_TAIL)
    val tail = reader.readBytes(tailStart, (reader.size - tailStart).toInt())
    return Guess(FileFormat.Pdf, damaged = tail.indexOf(PDF_EOF) < 0)
}

private suspend fun compound(
    reader: RandomAccessReader,
    extension: String?,
): Guess {
    val directory = CompoundFile.directory(reader)
    val names = directory.names.mapTo(mutableSetOf()) { it.lowercase() }
    val damaged = directory.damaged
    return when {
        "encryptedpackage" in names && "encryptioninfo" in names -> {
            Guess(byExtension(extension, OOXML) ?: FileFormat.EncryptedOoxml, damaged, passwordProtected = true)
        }

        "worddocument" in names -> {
            Guess(FileFormat.Doc, damaged)
        }

        "workbook" in names || "book" in names -> {
            Guess(FileFormat.Xls, damaged)
        }

        "powerpoint document" in names -> {
            Guess(FileFormat.Ppt, damaged)
        }

        damaged -> {
            Guess(byExtension(extension, LEGACY) ?: FileFormat.Unknown, damaged = true)
        }

        else -> {
            Guess(FileFormat.Unknown)
        }
    }
}

private fun text(
    head: ByteArray,
    cut: Boolean,
    extension: String?,
): Guess {
    val kind = TextSniffer.classify(head, cut) ?: return Guess(FileFormat.Unknown)
    if (kind != FileFormat.PlainText) return Guess(kind)
    return Guess(byExtension(extension, TEXT_BY_EXTENSION) ?: FileFormat.PlainText)
}

/** ZIP based formats: OOXML by `[Content_Types].xml`, ODF and EPUB by `mimetype`, otherwise a ZIP archive. */
private object ContainerRules {
    suspend fun zip(
        reader: RandomAccessReader,
        head: ByteArray,
        extension: String?,
    ): Guess {
        val directory =
            ZipContainer.directory(reader, setOf(CONTENT_TYPES, MIMETYPE)) ?: return truncated(head, extension)
        val byMimetype = directory.entries[MIMETYPE]?.let { mimetype(reader, it) }
        val contentTypes = directory.entries[CONTENT_TYPES]
        val guess =
            when {
                byMimetype != null -> byMimetype
                contentTypes != null -> ooxml(reader, contentTypes, extension)
                directory.damaged -> Guess(byExtension(extension, ZIP_BASED) ?: FileFormat.Zip, damaged = true)
                else -> Guess(FileFormat.Zip)
            }
        return if (directory.damaged) guess.copy(damaged = true) else guess
    }

    /** No end-of-central-directory record: a cut-off file. The first local entry may still name an ODF type. */
    private fun truncated(
        head: ByteArray,
        extension: String?,
    ): Guess {
        val first = ZipContainer.firstLocalEntry(head)
        val odf = first?.takeIf { it.first == MIMETYPE }?.second?.let { MIMETYPES[it.decodeToString().trim()] }
        return Guess(odf ?: byExtension(extension, ZIP_BASED) ?: FileFormat.Zip, damaged = true)
    }

    private suspend fun mimetype(
        reader: RandomAccessReader,
        entry: ZipEntry,
    ): Guess? {
        val part = ZipContainer.readPart(reader, entry, MIMETYPE_LIMIT) ?: return null
        return MIMETYPES[part.bytes.decodeToString().trim()]?.let { Guess(it) }
    }

    private suspend fun ooxml(
        reader: RandomAccessReader,
        entry: ZipEntry,
        extension: String?,
    ): Guess {
        val part = ZipContainer.readPart(reader, entry)
        val format = part?.let { mainFormat(it.bytes.decodeToString()) }
        return when {
            // Found even if the part was cut at the output limit (a zip bomb part still names its type first).
            format != null -> Guess(format)

            part == null || !part.complete -> Guess(byExtension(extension, OOXML) ?: FileFormat.Zip, damaged = true)

            // A valid package of another kind (XPS, XLSB, ...): shown as an archive.
            else -> Guess(FileFormat.Zip)
        }
    }

    /** The format whose main part content type appears in a `ContentType="..."` attribute. */
    private fun mainFormat(xml: String): FileFormat? {
        var at = xml.indexOf(CONTENT_TYPE_ATTR)
        while (at >= 0) {
            val quote = at + CONTENT_TYPE_ATTR.length
            if (quote < xml.length && (xml[quote] == '"' || xml[quote] == '\'')) {
                val end = xml.indexOf(xml[quote], quote + 1)
                if (end < 0) return null
                OOXML_MAIN[xml.substring(quote + 1, end).trim()]?.let { return it }
            }
            at = xml.indexOf(CONTENT_TYPE_ATTR, at + 1)
        }
        return null
    }

    private const val CONTENT_TYPES = "[Content_Types].xml"
    private const val MIMETYPE = "mimetype"
    private const val CONTENT_TYPE_ATTR = "ContentType="
    private const val MIMETYPE_LIMIT = 256
}

/** Lower-case extension of [name] without the dot; null when there is none. */
internal fun extensionOf(name: String?): String? =
    name
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.takeIf { '.' in it }
        ?.substringAfterLast('.')
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }

private fun byExtension(
    extension: String?,
    among: Set<FileFormat>,
): FileFormat? = extension?.let { FileFormat.byExtension(it) }?.takeIf { it in among }

private fun mismatch(
    extension: String?,
    format: FileFormat,
): Boolean =
    extension != null &&
        format != FileFormat.Empty &&
        extension !in format.extensions &&
        FileFormat.byExtension(extension) != null

private val OOXML_MAIN: Map<String, FileFormat> =
    mapOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml" to FileFormat.Docx,
        "application/vnd.ms-word.document.macroEnabled.main+xml" to FileFormat.Docm,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml" to FileFormat.Dotx,
        "application/vnd.ms-word.template.macroEnabledTemplate.main+xml" to FileFormat.Dotm,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml" to FileFormat.Xlsx,
        "application/vnd.ms-excel.sheet.macroEnabled.main+xml" to FileFormat.Xlsm,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml" to FileFormat.Xltx,
        "application/vnd.ms-excel.template.macroEnabled.main+xml" to FileFormat.Xltm,
        "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml" to FileFormat.Pptx,
        "application/vnd.ms-powerpoint.presentation.macroEnabled.main+xml" to FileFormat.Pptm,
        "application/vnd.openxmlformats-officedocument.presentationml.template.main+xml" to FileFormat.Potx,
        "application/vnd.ms-powerpoint.template.macroEnabled.main+xml" to FileFormat.Potm,
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml" to FileFormat.Ppsx,
        "application/vnd.ms-powerpoint.slideshow.macroEnabled.main+xml" to FileFormat.Ppsm,
    )

private val MIMETYPES: Map<String, FileFormat> =
    listOf(
        FileFormat.Odt,
        FileFormat.Ott,
        FileFormat.Ods,
        FileFormat.Ots,
        FileFormat.Odp,
        FileFormat.Otp,
        FileFormat.Epub,
    ).mapNotNull { format -> format.mimeType?.let { it to format } }
        .toMap()

private val OOXML: Set<FileFormat> = OOXML_MAIN.values.toSet()
private val ZIP_BASED: Set<FileFormat> = OOXML + MIMETYPES.values + FileFormat.Zip
private val LEGACY: Set<FileFormat> = setOf(FileFormat.Doc, FileFormat.Xls, FileFormat.Ppt)
private val TEXT_BY_EXTENSION: Set<FileFormat> = setOf(FileFormat.Csv, FileFormat.SourceCode)

private val ZIP_LOCAL = "PK\u0003\u0004".encodeToByteArray()
private val ZIP_EMPTY = "PK\u0005\u0006".encodeToByteArray()
private val PDF_EOF = "%%EOF".encodeToByteArray()
private const val HEAD_BYTES = 8 * 1024
private const val PDF_TAIL = 1024
