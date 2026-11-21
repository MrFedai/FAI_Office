/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persisted Storage Access Framework URI permissions: what keeps a picked file or an added folder reachable after a
 * restart. A grant can be persisted only when the system offered it (`ACTION_OPEN_DOCUMENT`,
 * `ACTION_OPEN_DOCUMENT_TREE`); files shared or opened with "Open with" usually carry a temporary grant, so
 * [take] returns false for them. The platform keeps at most [limit] grants per app.
 */
class SafPersistedGrants(
    context: Context,
) : PersistedGrants {
    private val resolver: ContentResolver = (context.applicationContext ?: context).contentResolver

    override val limit: Int = persistedGrantLimit(Build.VERSION.SDK_INT)

    override suspend fun held(): Set<FileRef> =
        withContext(Dispatchers.IO) {
            val held = resolver.persistedUriPermissions
            held.mapTo(LinkedHashSet()) { FileRef(FileRef.Kind.ContentUri, it.uri.toString()) }
        }

    override suspend fun take(ref: FileRef): Boolean {
        if (ref.kind != FileRef.Kind.ContentUri) return false
        val uri = Uri.parse(ref.location)
        return withContext(Dispatchers.IO) { persist(uri, READ_WRITE) || persist(uri, READ) }
    }

    override suspend fun release(ref: FileRef) {
        if (ref.kind != FileRef.Kind.ContentUri) return
        val uri = Uri.parse(ref.location)
        withContext(Dispatchers.IO) {
            val held = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return@withContext
            val flags = (if (held.isReadPermission) READ else 0) or (if (held.isWritePermission) WRITE else 0)
            try {
                resolver.releasePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Already gone (revoked by the user or the provider): nothing left to release.
            }
        }
    }

    private fun persist(
        uri: Uri,
        flags: Int,
    ): Boolean =
        try {
            resolver.takePersistableUriPermission(uri, flags)
            true
        } catch (_: SecurityException) {
            false // not offered as persistable with these flags, or the platform limit is reached
        }

    private companion object {
        const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
        const val WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val READ_WRITE = READ or WRITE
    }
}

/** Persisted URI permissions the platform keeps per app: 128 up to Android 10, 512 from Android 11 (API 30). */
fun persistedGrantLimit(sdkInt: Int): Int = if (sdkInt >= ANDROID_11) GRANTS_ANDROID_11 else GRANTS_ANDROID_10

private const val ANDROID_11 = 30
private const val GRANTS_ANDROID_11 = 512
private const val GRANTS_ANDROID_10 = 128
