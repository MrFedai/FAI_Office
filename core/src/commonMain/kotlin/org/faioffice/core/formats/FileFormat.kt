/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.formats

/** The app module that opens a format (MASTER_SPEC section 3: one app, modules loaded on demand). */
enum class ModuleFamily { Documents, Spreadsheets, Presentations, Pdf, Archive, Viewer, Unknown }

/**
 * Every format detection can name. [extensions] are lower case without the dot, the first one canonical; [mimeType]
 * is the registered type, used only to stand in for a missing extension.
 */
enum class FileFormat(
    val family: ModuleFamily,
    val mimeType: String?,
    vararg val extensions: String,
) {
    Pdf(ModuleFamily.Pdf, "application/pdf", "pdf"),

    Docx(ModuleFamily.Documents, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    Docm(ModuleFamily.Documents, "application/vnd.ms-word.document.macroEnabled.12", "docm"),
    Dotx(ModuleFamily.Documents, "application/vnd.openxmlformats-officedocument.wordprocessingml.template", "dotx"),
    Dotm(ModuleFamily.Documents, "application/vnd.ms-word.template.macroEnabled.12", "dotm"),
    Odt(ModuleFamily.Documents, "application/vnd.oasis.opendocument.text", "odt"),
    Ott(ModuleFamily.Documents, "application/vnd.oasis.opendocument.text-template", "ott"),
    Doc(ModuleFamily.Documents, "application/msword", "doc", "dot"),
    Rtf(ModuleFamily.Documents, "application/rtf", "rtf"),

    Xlsx(ModuleFamily.Spreadsheets, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    Xlsm(ModuleFamily.Spreadsheets, "application/vnd.ms-excel.sheet.macroEnabled.12", "xlsm"),
    Xltx(ModuleFamily.Spreadsheets, "application/vnd.openxmlformats-officedocument.spreadsheetml.template", "xltx"),
    Xltm(ModuleFamily.Spreadsheets, "application/vnd.ms-excel.template.macroEnabled.12", "xltm"),
    Ods(ModuleFamily.Spreadsheets, "application/vnd.oasis.opendocument.spreadsheet", "ods"),
    Ots(ModuleFamily.Spreadsheets, "application/vnd.oasis.opendocument.spreadsheet-template", "ots"),
    Xls(ModuleFamily.Spreadsheets, "application/vnd.ms-excel", "xls", "xlt"),
    Csv(ModuleFamily.Spreadsheets, "text/csv", "csv", "tsv"),

    Pptx(
        ModuleFamily.Presentations,
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "pptx",
    ),
    Pptm(ModuleFamily.Presentations, "application/vnd.ms-powerpoint.presentation.macroEnabled.12", "pptm"),
    Potx(ModuleFamily.Presentations, "application/vnd.openxmlformats-officedocument.presentationml.template", "potx"),
    Potm(ModuleFamily.Presentations, "application/vnd.ms-powerpoint.template.macroEnabled.12", "potm"),
    Ppsx(ModuleFamily.Presentations, "application/vnd.openxmlformats-officedocument.presentationml.slideshow", "ppsx"),
    Ppsm(ModuleFamily.Presentations, "application/vnd.ms-powerpoint.slideshow.macroEnabled.12", "ppsm"),
    Odp(ModuleFamily.Presentations, "application/vnd.oasis.opendocument.presentation", "odp"),
    Otp(ModuleFamily.Presentations, "application/vnd.oasis.opendocument.presentation-template", "otp"),
    Ppt(ModuleFamily.Presentations, "application/vnd.ms-powerpoint", "ppt", "pot", "pps"),

    Zip(ModuleFamily.Archive, "application/zip", "zip"),
    SevenZip(ModuleFamily.Archive, "application/x-7z-compressed", "7z"),
    Rar(ModuleFamily.Archive, "application/vnd.rar", "rar"),
    Tar(ModuleFamily.Archive, "application/x-tar", "tar"),
    Gzip(ModuleFamily.Archive, "application/gzip", "gz", "tgz"),
    Bzip2(ModuleFamily.Archive, "application/x-bzip2", "bz2", "tbz2"),
    Xz(ModuleFamily.Archive, "application/x-xz", "xz", "txz"),

    Png(ModuleFamily.Viewer, "image/png", "png"),
    Jpeg(ModuleFamily.Viewer, "image/jpeg", "jpg", "jpeg", "jpe", "jfif"),
    Gif(ModuleFamily.Viewer, "image/gif", "gif"),
    Webp(ModuleFamily.Viewer, "image/webp", "webp"),
    Bmp(ModuleFamily.Viewer, "image/bmp", "bmp", "dib"),
    Tiff(ModuleFamily.Viewer, "image/tiff", "tif", "tiff"),
    Svg(ModuleFamily.Viewer, "image/svg+xml", "svg"),
    Html(ModuleFamily.Viewer, "text/html", "html", "htm", "xhtml"),
    Xml(ModuleFamily.Viewer, "application/xml", "xml"),
    Epub(ModuleFamily.Viewer, "application/epub+zip", "epub"),
    PlainText(ModuleFamily.Viewer, "text/plain", "txt", "text", "log"),
    SourceCode(
        ModuleFamily.Viewer,
        null,
        "kt",
        "kts",
        "java",
        "py",
        "js",
        "mjs",
        "ts",
        "c",
        "h",
        "cpp",
        "hpp",
        "cc",
        "cs",
        "go",
        "rs",
        "swift",
        "sh",
        "bat",
        "ps1",
        "json",
        "yaml",
        "yml",
        "toml",
        "ini",
        "css",
        "sql",
        "md",
        "gradle",
        "properties",
    ),

    /** An encrypted OOXML package (OLE2 `EncryptedPackage`) whose name gives no hint whether it is DOCX, XLSX, PPTX. */
    EncryptedOoxml(ModuleFamily.Unknown, null),

    /** Zero bytes. */
    Empty(ModuleFamily.Unknown, null),

    /** Binary content no rule recognises. */
    Unknown(ModuleFamily.Unknown, null),
    ;

    val canonicalExtension: String? get() = extensions.firstOrNull()

    companion object {
        /** The format whose extension list holds [extension] (lower case, no dot); null when none does. */
        fun byExtension(extension: String): FileFormat? = entries.firstOrNull { extension in it.extensions }

        /** The format registered for [mimeType], ignoring parameters such as `; charset=`; null when none is. */
        fun byMimeType(mimeType: String): FileFormat? {
            val bare = mimeType.substringBefore(';').trim().lowercase()
            return entries.firstOrNull { it.mimeType?.lowercase() == bare }
        }
    }
}

/**
 * Compatibility levels of MASTER_SPEC section 5, plus level 0. A format moves up only when the corpus tests for the
 * next level pass.
 */
enum class CompatLevel(
    val level: Int,
) {
    /** Detected and routed to its module; nothing more is promised. */
    Recognised(0),

    /** Open and view. */
    View(1),

    /** Edit and save. */
    Edit(2),

    /** Formatting and structure preserved with high fidelity. */
    Fidelity(3),
}

/** The compatibility matrix (MASTER_SPEC section 5), format level: the single source of truth for each format. */
object CompatibilityMatrix {
    /** Every format detection can name, except the non-formats ([FileFormat.Empty], [FileFormat.Unknown]). */
    val levels: Map<FileFormat, CompatLevel> =
        FileFormat.entries
            .filter { it != FileFormat.Empty && it != FileFormat.Unknown }
            .associateWith { CompatLevel.Recognised }

    /** The level of [format]; null for the non-formats. */
    fun levelOf(format: FileFormat): CompatLevel? = levels[format]
}
