/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

@file:OptIn(ExperimentalWasmJsInterop::class)

package org.faioffice.app.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.SessionRecents
import org.faioffice.core.files.WebFileAccess
import org.faioffice.core.files.WebFileSource
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * The file layer of the minimal Web build (P3.6): [openFiles] shows a hidden `<input type="file" multiple>`, the
 * picked files are registered with [access] (read lazily, slice by slice) and queued on [hub] as
 * [OpenOrigin.Picker]. Recent files are session-only ([recents]). Folders and "open in another app" are not
 * available here; there is no File System Access API, no IndexedDB and no network call.
 */
class WebFiles(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : FileActions {
    val access = WebFileAccess()
    val recents = SessionRecents()
    val hub = FileHub(access, scope)
    val services = FileServices(access, hub, this, library = null)

    override val canAddFolder: Boolean = false

    override fun openFiles(): Boolean =
        try {
            pickFiles { files -> onPicked(files) }
            true
        } catch (e: Throwable) {
            false
        }

    override fun addFolder(): Boolean = false

    override fun openInOtherApp(
        ref: FileRef,
        mimeType: String?,
    ): Boolean = false

    /** Registers the files of a `FileList` and hands them to the hub; also the entry point for scripted checks. */
    internal fun onPicked(files: JsAny) {
        val refs = (0 until fileCount(files)).map { access.register(BrowserFile(fileAt(files, it))) }
        refs.forEach { recents.add(it) }
        hub.open(refs, OpenOrigin.Picker)
    }
}

private class BrowserFile(
    private val file: JsAny,
) : WebFileSource {
    override val name: String = fileName(file)
    override val size: Long = fileSize(file).toLong()
    override val mimeType: String? = fileType(file).ifEmpty { null }
    override val lastModifiedEpochMillis: Long? = fileModified(file).toLong()

    override suspend fun read(
        position: Long,
        length: Int,
    ): ByteArray {
        val buffer = sliceBuffer(file, position.toDouble(), (position + length).toDouble()).await<JsAny>()
        return ByteArray(bufferLength(buffer)) { byteAt(buffer, it) }
    }
}

private fun pickFiles(onFiles: (JsAny) -> Unit): Unit =
    js(
        """{
  const input = document.createElement('input');
  input.type = 'file';
  input.multiple = true;
  input.style.display = 'none';
  input.addEventListener('change', () => { onFiles(input.files); input.remove(); });
  input.addEventListener('cancel', () => input.remove());
  document.body.appendChild(input);
  input.click();
}""",
    )

private fun fileCount(files: JsAny): Int = js("files.length")

private fun fileAt(
    files: JsAny,
    index: Int,
): JsAny = js("files[index]")

private fun fileName(file: JsAny): String = js("file.name")

private fun fileSize(file: JsAny): Double = js("file.size")

private fun fileType(file: JsAny): String = js("file.type")

private fun fileModified(file: JsAny): Double = js("file.lastModified")

private fun sliceBuffer(
    file: JsAny,
    from: Double,
    to: Double,
): Promise<JsAny> = js("file.slice(from, to).arrayBuffer().then(b => new Uint8Array(b))")

private fun bufferLength(bytes: JsAny): Int = js("bytes.length")

private fun byteAt(
    bytes: JsAny,
    index: Int,
): Byte = js("(bytes[index] << 24) >> 24")
