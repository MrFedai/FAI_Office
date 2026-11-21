/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.FileNotFoundException

/**
 * Metadata and folder listing through the Storage Access Framework. Document URIs (picked with
 * `ACTION_OPEN_DOCUMENT`, or inside a tree from `ACTION_OPEN_DOCUMENT_TREE`) are queried with the
 * [DocumentsContract] columns; other content URIs (shared or opened with "Open with" from any app) with
 * [OpenableColumns]. Display name, size and type always come from the provider. Blocking: callers run it on an I/O
 * dispatcher and map the exceptions ([AndroidFileAccess]).
 */
internal class SafDocuments(
    private val context: Context,
) {
    private val resolver: ContentResolver = context.contentResolver

    fun info(
        ref: FileRef,
        uri: Uri,
    ): FileInfo {
        val document = documentUriOf(uri) ?: return openableInfo(ref, uri)
        val writeGranted = granted(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return resolver.query(document, DOCUMENT_COLUMNS, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) throw FileNotFoundException("no row for $document")
            documentRow(cursor, ref, writeGranted, fallbackName = uri.lastPathSegment)
        } ?: throw FileNotFoundException("no cursor for $document")
    }

    /** Direct children of a document tree or of a folder inside one. */
    suspend fun list(uri: Uri): List<FileInfo> {
        if (!DocumentsContract.isTreeUri(uri)) throw FileAccessException(FileError.WrongKind(EntryKind.Folder))
        val parent = documentUriOf(uri) ?: throw FileNotFoundException("$uri")
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parentId)
        val writeGranted = granted(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val context = currentCoroutineContext()
        val rows =
            resolver.query(children, CHILD_COLUMNS, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
                buildList {
                    while (cursor.moveToNext()) {
                        context.ensureActive()
                        val id = cursor.stringAt(idColumn) ?: continue
                        val child = FileRef(FileRef.Kind.ContentUri, DocumentsContract.buildDocumentUriUsingTree(uri, id).toString())
                        add(documentRow(cursor, child, writeGranted, fallbackName = id))
                    }
                }
            }
        if (rows != null) return rows
        // No cursor: the provider does not list this document, usually because it is a file.
        if (info(FileRef(FileRef.Kind.ContentUri, uri.toString()), uri).kind == EntryKind.File) {
            throw FileAccessException(FileError.WrongKind(EntryKind.Folder))
        }
        throw FileNotFoundException("$uri")
    }

    /** Whether [uri] names a folder (a tree root or a folder document); opening one for reading is a wrong kind. */
    fun isFolder(uri: Uri): Boolean {
        if (DocumentsContract.isTreeUri(uri) && !DocumentsContract.isDocumentUri(context, uri)) return true
        val document = documentUriOf(uri) ?: return false
        return resolver.query(document, arrayOf(Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == Document.MIME_TYPE_DIR
        } ?: false
    }

    /**
     * The document URI that metadata queries go to: [uri] itself for a document, the tree's root document for a tree
     * URI from `ACTION_OPEN_DOCUMENT_TREE`, null for content URIs of other providers.
     */
    fun documentUriOf(uri: Uri): Uri? =
        when {
            DocumentsContract.isDocumentUri(context, uri) -> uri
            DocumentsContract.isTreeUri(uri) -> DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            else -> null
        }

    /** Whether the app holds [flag] (read or write URI permission) for [uri], temporary or persisted. */
    fun granted(
        uri: Uri,
        flag: Int,
    ): Boolean = context.checkUriPermission(uri, Process.myPid(), Process.myUid(), flag) == PackageManager.PERMISSION_GRANTED

    private fun openableInfo(
        ref: FileRef,
        uri: Uri,
    ): FileInfo {
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, OPENABLE_COLUMNS, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.stringAt(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                size = cursor.longAt(cursor.getColumnIndex(OpenableColumns.SIZE))
            }
        }
        if (size == null) {
            // Some providers answer no query; opening proves the file exists and gives its length when it has one.
            size = resolver.openAssetFileDescriptor(uri, "r")?.use { fd -> fd.length.takeIf { it >= 0 } }
        }
        return FileInfo(
            ref = ref,
            name = name ?: uri.lastPathSegment ?: uri.toString(),
            kind = EntryKind.File,
            size = size,
            modifiedEpochMillis = null,
            mimeType = resolver.getType(uri),
            writable = granted(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        )
    }

    private fun documentRow(
        cursor: Cursor,
        ref: FileRef,
        writeGranted: Boolean,
        fallbackName: String?,
    ): FileInfo {
        val mime = cursor.stringAt(cursor.getColumnIndex(Document.COLUMN_MIME_TYPE))
        val folder = mime == Document.MIME_TYPE_DIR
        val flags = cursor.longAt(cursor.getColumnIndex(Document.COLUMN_FLAGS))?.toInt() ?: 0
        return FileInfo(
            ref = ref,
            name = cursor.stringAt(cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)) ?: fallbackName ?: ref.location,
            kind = if (folder) EntryKind.Folder else EntryKind.File,
            size = if (folder) null else cursor.longAt(cursor.getColumnIndex(Document.COLUMN_SIZE)),
            modifiedEpochMillis = cursor.longAt(cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED))?.takeIf { it > 0 },
            mimeType = if (folder) null else mime,
            writable = !folder && writeGranted && (flags and Document.FLAG_SUPPORTS_WRITE) != 0,
        )
    }

    private companion object {
        val DOCUMENT_COLUMNS =
            arrayOf(
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_FLAGS,
            )
        val CHILD_COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID) + DOCUMENT_COLUMNS
        val OPENABLE_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        fun Cursor.stringAt(column: Int): String? = if (column < 0 || isNull(column)) null else getString(column)

        fun Cursor.longAt(column: Int): Long? = if (column < 0 || isNull(column)) null else getLong(column)
    }
}
