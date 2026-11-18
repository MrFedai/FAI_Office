/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.text

/** Languages whose dotted and dotless i differ from the invariant rules (Turkish, Azerbaijani). */
private val dottedILanguages = setOf("tr", "az")

private fun usesDottedI(language: String): Boolean =
    language.substringBefore('-').substringBefore('_').lowercase() in dottedILanguages

/** Upper case by the rules of [language] (BCP 47 tag or bare code): in tr and az, i becomes İ and ı becomes I. */
fun String.uppercase(language: String): String =
    if (usesDottedI(language)) {
        map {
            when (it) {
                'i' -> "İ"
                'ı' -> "I"
                else -> it.uppercase()
            }
        }.joinToString("")
    } else {
        uppercase()
    }

/** Lower case by the rules of [language]: in tr and az, I becomes ı and İ becomes i. */
fun String.lowercase(language: String): String =
    if (usesDottedI(language)) {
        map {
            when (it) {
                'I' -> "ı"
                'İ' -> "i"
                else -> it.lowercase()
            }
        }.joinToString("")
    } else {
        lowercase()
    }
