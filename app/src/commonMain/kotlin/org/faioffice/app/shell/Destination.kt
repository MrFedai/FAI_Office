/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import org.faioffice.app.resources.Res
import org.faioffice.app.resources.description_archive
import org.faioffice.app.resources.description_documents
import org.faioffice.app.resources.description_file_converter
import org.faioffice.app.resources.description_home
import org.faioffice.app.resources.description_pdf
import org.faioffice.app.resources.description_presentations
import org.faioffice.app.resources.description_recent_files
import org.faioffice.app.resources.description_settings
import org.faioffice.app.resources.description_spreadsheets
import org.faioffice.app.resources.destination_archive
import org.faioffice.app.resources.destination_documents
import org.faioffice.app.resources.destination_file_converter
import org.faioffice.app.resources.destination_home
import org.faioffice.app.resources.destination_pdf
import org.faioffice.app.resources.destination_presentations
import org.faioffice.app.resources.destination_recent_files
import org.faioffice.app.resources.destination_settings
import org.faioffice.app.resources.destination_spreadsheets
import org.faioffice.app.resources.nav_short_archive
import org.faioffice.app.resources.nav_short_documents
import org.faioffice.app.resources.nav_short_file_converter
import org.faioffice.app.resources.nav_short_home
import org.faioffice.app.resources.nav_short_pdf
import org.faioffice.app.resources.nav_short_presentations
import org.faioffice.app.resources.nav_short_recent_files
import org.faioffice.app.resources.nav_short_settings
import org.faioffice.app.resources.nav_short_spreadsheets
import org.jetbrains.compose.resources.StringResource

/**
 * The nine destinations of MASTER_SPEC section 10 in spec order (side navigation order, Settings last).
 * [title] is the full name (top bar, side navigation), [shortLabel] fits the rail and the phone bottom bar.
 */
enum class Destination(
    val title: StringResource,
    val shortLabel: StringResource,
    val description: StringResource,
) {
    Home(Res.string.destination_home, Res.string.nav_short_home, Res.string.description_home),
    RecentFiles(
        Res.string.destination_recent_files,
        Res.string.nav_short_recent_files,
        Res.string.description_recent_files,
    ),
    Documents(Res.string.destination_documents, Res.string.nav_short_documents, Res.string.description_documents),
    Presentations(
        Res.string.destination_presentations,
        Res.string.nav_short_presentations,
        Res.string.description_presentations,
    ),
    Spreadsheets(
        Res.string.destination_spreadsheets,
        Res.string.nav_short_spreadsheets,
        Res.string.description_spreadsheets,
    ),
    Pdf(Res.string.destination_pdf, Res.string.nav_short_pdf, Res.string.description_pdf),
    FileConverter(
        Res.string.destination_file_converter,
        Res.string.nav_short_file_converter,
        Res.string.description_file_converter,
    ),
    Archive(Res.string.destination_archive, Res.string.nav_short_archive, Res.string.description_archive),
    Settings(Res.string.destination_settings, Res.string.nav_short_settings, Res.string.description_settings),
    ;

    /**
     * Bottom bar entry shown as selected on the phone: the destination itself when it has an entry, Home for the
     * destinations opened from the Home file-type tiles, none for Settings (top bar icon).
     */
    val phoneTab: Destination?
        get() =
            when (this) {
                in PhoneTabs -> this
                Settings -> null
                else -> Home
            }

    companion object {
        /** Phone bottom bar, at most five entries (DESIGN.md section 6). */
        val PhoneTabs: List<Destination> = listOf(Home, RecentFiles, Documents, Pdf, FileConverter)

        /** File-type tiles on Home; the phone reaches Presentations, Spreadsheets and Archive only through these. */
        val HomeTiles: List<Destination> = listOf(Documents, Spreadsheets, Presentations, Pdf, Archive)
    }
}
