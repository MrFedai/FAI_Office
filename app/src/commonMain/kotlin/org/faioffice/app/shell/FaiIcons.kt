/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// Icon path data from Lucide (https://lucide.dev), ISC License:
// Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of Feather (MIT). All other
// copyright (c) for Lucide are held by Lucide Contributors 2022.
// Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby
// granted, provided that the above copyright notice and this permission notice appear in all copies.
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING
// ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL,
// DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
// WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE
// USE OR PERFORMANCE OF THIS SOFTWARE.

package org.faioffice.app.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import org.faioffice.app.theme.FaiSize

/** Lucide grid: 24 x 24 viewport, stroke width 2, round caps and joins. */
private const val LUCIDE_VIEWPORT = 24f
private const val LUCIDE_STROKE = 2f

/** Circle of radius [r] around ([cx], [cy]) as path data (Lucide `<circle>`). */
private fun circle(
    cx: Float,
    cy: Float,
    r: Float,
): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

/**
 * Stroke-only icon. The stroke colour is a placeholder: [FaiIcon] always tints with a theme colour
 * (`ColorFilter.tint`), so no fixed colour reaches the screen.
 */
private fun lucide(
    name: String,
    vararg paths: String,
): ImageVector =
    ImageVector
        .Builder(name, FaiSize.icon, FaiSize.icon, LUCIDE_VIEWPORT, LUCIDE_VIEWPORT)
        .apply {
            for (path in paths) {
                addPath(
                    pathData = addPathNodes(path),
                    fill = null,
                    stroke = SolidColor(Color.Black), // hardcode-ok: placeholder, FaiIcon tints it
                    strokeLineWidth = LUCIDE_STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

/** Local vector icons of the shell (Lucide, ISC). */
object FaiIcons {
    val house: ImageVector by lazy {
        lucide(
            "house",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5" +
                "a2 2 0 0 1-2-2z",
        )
    }

    val clock: ImageVector by lazy { lucide("clock", circle(12f, 12f, 10f), "M12 6v6l4 2") }

    val fileText: ImageVector by lazy {
        lucide(
            "file-text",
            "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z",
            "M14 2v4a2 2 0 0 0 2 2h4",
            "M10 9H8",
            "M16 13H8",
            "M16 17H8",
        )
    }

    val presentation: ImageVector by lazy {
        lucide("presentation", "M2 3h20", "M21 3v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V3", "m7 21 5-5 5 5")
    }

    val sheet: ImageVector by lazy {
        lucide(
            "sheet",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M3 9h18",
            "M3 15h18",
            "M9 9v12",
            "M15 9v12",
        )
    }

    val bookOpen: ImageVector by lazy {
        lucide(
            "book-open",
            "M12 7v14",
            "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6" +
                "a3 3 0 0 0-3 3 3 3 0 0 0-3-3z",
        )
    }

    val arrowLeftRight: ImageVector by lazy {
        lucide("arrow-left-right", "M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4")
    }

    val archive: ImageVector by lazy {
        lucide(
            "archive",
            "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8",
            "M10 12h4",
        )
    }

    val folderOpen: ImageVector by lazy {
        lucide(
            "folder-open",
            "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5" +
                "a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2",
        )
    }

    val settings: ImageVector by lazy {
        lucide(
            "settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73" +
                "l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73" +
                "l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44" +
                "a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39" +
                "a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73" +
                "l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            circle(12f, 12f, 3f),
        )
    }

    val sun: ImageVector by lazy {
        lucide(
            "sun",
            circle(12f, 12f, 4f),
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41",
        )
    }

    val moon: ImageVector by lazy { lucide("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z") }

    val monitor: ImageVector by lazy {
        lucide(
            "monitor",
            "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M8 21h8",
            "M12 17v4",
        )
    }

    val panelLeftClose: ImageVector by lazy {
        lucide(
            "panel-left-close",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M9 3v18",
            "m16 15-3-3 3-3",
        )
    }

    val panelLeftOpen: ImageVector by lazy {
        lucide(
            "panel-left-open",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M9 3v18",
            "m14 9 3 3-3 3",
        )
    }

    private const val FOLDER =
        "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13" +
            "a2 2 0 0 0 2 2Z"

    val folder: ImageVector by lazy { lucide("folder", FOLDER) }

    val folderPlus: ImageVector by lazy { lucide("folder-plus", "M12 10v6", "M9 13h6", FOLDER) }

    val arrowLeft: ImageVector by lazy { lucide("arrow-left", "m12 19-7-7 7-7", "M19 12H5") }

    val arrowUp: ImageVector by lazy { lucide("arrow-up", "m5 12 7-7 7 7", "M12 19V5") }

    val arrowDown: ImageVector by lazy { lucide("arrow-down", "M12 5v14", "m19 12-7 7-7-7") }

    val chevronRight: ImageVector by lazy { lucide("chevron-right", "m9 18 6-6-6-6") }

    val x: ImageVector by lazy { lucide("x", "M18 6 6 18", "m6 6 12 12") }

    val pin: ImageVector by lazy {
        lucide(
            "pin",
            "M12 17v5",
            "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76" +
                "a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4" +
                "a1 1 0 0 1 1 1z",
        )
    }

    val externalLink: ImageVector by lazy {
        lucide(
            "external-link",
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
        )
    }

    val trash: ImageVector by lazy {
        lucide(
            "trash-2",
            "M3 6h18",
            "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
            "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
        )
    }

    val lock: ImageVector by lazy {
        lucide(
            "lock",
            "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2Z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }

    val circleAlert: ImageVector by lazy { lucide("circle-alert", circle(12f, 12f, 10f), "M12 8v4", "M12 16h.01") }

    val triangleAlert: ImageVector by lazy {
        lucide(
            "triangle-alert",
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01",
        )
    }

    val circleCheck: ImageVector by lazy { lucide("circle-check", circle(12f, 12f, 10f), "m9 12 2 2 4-4") }
}

/** Navigation icon of each destination. */
val Destination.icon: ImageVector
    get() =
        when (this) {
            Destination.Home -> FaiIcons.house
            Destination.RecentFiles -> FaiIcons.clock
            Destination.Documents -> FaiIcons.fileText
            Destination.Presentations -> FaiIcons.presentation
            Destination.Spreadsheets -> FaiIcons.sheet
            Destination.Pdf -> FaiIcons.bookOpen
            Destination.FileConverter -> FaiIcons.arrowLeftRight
            Destination.Archive -> FaiIcons.archive
            Destination.Settings -> FaiIcons.settings
        }
