/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import org.faioffice.core.files.FileRef

/** Files another app handed over: the references to open and how they came. */
data class Incoming(
    val refs: List<FileRef>,
    val origin: OpenOrigin,
)

/**
 * Turns what the system hands over (Android: an intent's action, its data URI and its `EXTRA_STREAM` / clip data
 * URIs) into open requests, without Android types so it is tested on the JVM. Only `content:` URIs are accepted: a
 * `file:` URI would need the storage permission the app does not have (and other apps may not send one since
 * Android 7). Duplicates are dropped, order kept, at most [MAX_FILES] per hand-over.
 */
object IncomingFiles {
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"

    /** Upper bound of files taken from one share; a larger selection is cut, not rejected. */
    const val MAX_FILES = 100

    /** The hand-over in [action] with [uris] (data URI first, then streams); null when nothing can be opened. */
    fun of(
        action: String?,
        uris: List<String?>,
    ): Incoming? {
        val origin =
            when (action) {
                ACTION_VIEW -> OpenOrigin.OpenWith
                ACTION_SEND, ACTION_SEND_MULTIPLE -> OpenOrigin.Share
                else -> null
            }
        val refs =
            uris
                .filterNotNull()
                .map { it.trim() }
                .filter { it.startsWith(CONTENT_SCHEME, ignoreCase = true) && it.length > CONTENT_SCHEME.length }
                .distinct()
                .take(MAX_FILES)
                .map { FileRef(FileRef.Kind.ContentUri, it) }
        return if (origin == null || refs.isEmpty()) null else Incoming(refs, origin)
    }

    private const val CONTENT_SCHEME = "content://"
}
