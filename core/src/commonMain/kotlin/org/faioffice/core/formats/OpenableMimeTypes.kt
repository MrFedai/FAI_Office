/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.formats

import org.faioffice.core.text.lowercase

/**
 * MIME types the platform layers announce. The content decides the format afterwards ([FormatDetector]); these
 * lists only choose which files the system offers to the app.
 */
object OpenableMimeTypes {
    /** Types other providers commonly report for formats detection knows, beside the registered ones. */
    val aliases: List<String> =
        listOf(
            "application/x-pdf",
            "text/rtf",
            "application/x-rtf",
            "text/comma-separated-values",
            "text/tab-separated-values",
            "application/csv",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/x-gzip",
            "text/xml",
            "application/json",
            "text/markdown",
            "text/x-markdown",
        )

    /**
     * "Open with" and "Share to" filters (Android intent filters, a desktop installer's associations): the registered
     * type of every detected format plus [aliases], in lower case (Android matches intent filter types case
     * sensitively and normalises the sender's type to lower case). No wildcard, so the app is not offered for
     * unrelated files.
     */
    val incoming: List<String> =
        (FileFormat.entries.mapNotNull { it.mimeType } + aliases).map { it.lowercase(INVARIANT) }.distinct()

    /**
     * The open-document picker: [incoming] plus `text` (source code and other text has no single type) and
     * `application/octet-stream`, which providers report for files whose type they do not know (often ODF and
     * archives); detection reads the content anyway.
     */
    val picker: List<String> = incoming + listOf("text/*", "application/octet-stream")
}
