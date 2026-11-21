/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.faioffice.core.files.AndroidFileAccess
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.PathFileAccess
import org.faioffice.core.files.SafPersistedGrants
import org.faioffice.core.files.SafeSave
import org.faioffice.core.formats.OpenableMimeTypes
import org.faioffice.core.library.LibraryStore
import java.io.File

/**
 * The file layer of the Android app, one per process so it outlives activity re-creation: SAF access, persisted
 * grants, the library store in the app's files dir and the [FileHub] the screens collect. The activity forwards its
 * intents ([onIntent], from `onCreate` and `onNewIntent`) and picker results, and attaches itself as the [Host] that
 * launches the system dialogs. Times of opens and folder listings go to logcat under [LOG_TAG].
 */
class AndroidFiles private constructor(
    context: Context,
) : FileActions {
    /** What only an activity can do: start the SAF pickers (activity result API) and other activities. */
    interface Host {
        /** The activity's own component, left out of the "Open in another app" chooser. */
        val component: ComponentName

        fun pickDocuments(mimeTypes: Array<String>)

        fun pickFolder()

        fun start(intent: Intent)
    }

    private val appContext: Context = context.applicationContext ?: context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var host: Host? = null

    val access = AndroidFileAccess(appContext)
    val library: FileLibrary
    val hub: FileHub
    val services: FileServices

    init {
        val files = appContext.filesDir
        val journal = File(files, "safe-save").apply { mkdirs() }
        // Content URIs make safe save take its backup path, which writes existing files only: create both empty
        // (an empty store is an empty library).
        val store = File(files, "library/library.txt").apply { createEmpty() }
        val damaged = File(files, "library/library-damaged.txt").apply { createEmpty() }
        library =
            FileLibrary(
                LibraryStore(
                    access,
                    SafeSave(access, PathFileAccess.refOf(journal.toPath())),
                    PathFileAccess.refOf(store.toPath()),
                    PathFileAccess.refOf(damaged.toPath()),
                    System::currentTimeMillis,
                ),
                SafPersistedGrants(appContext),
            )
        hub = FileHub(access, scope, library) { Log.i(LOG_TAG, it) }
        services = FileServices(access, hub, this, library)
    }

    fun attach(host: Host) {
        this.host = host
    }

    fun detach(host: Host) {
        if (this.host === host) this.host = null
    }

    /**
     * An intent the activity received: "Open with" (`ACTION_VIEW`) and "Share" (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`)
     * go to the hub; the launcher intent and one restored from recents open nothing. [EXTRA_MEASURE_FOLDERS] lists
     * every library folder once and logs the times: `adb shell am start -n
     * org.faioffice/org.faioffice.android.MainActivity --ez org.faioffice.extra.MEASURE_FOLDERS true`.
     */
    fun onIntent(intent: Intent?) {
        if (intent == null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        if (intent.getBooleanExtra(EXTRA_MEASURE_FOLDERS, false)) scope.launch { hub.measureFolders() }
        val incoming = IncomingFiles.of(intent.action, urisOf(intent)) ?: return
        Log.i(LOG_TAG, "incoming ${incoming.origin.name}: ${incoming.refs.size} file(s)")
        hub.open(incoming.refs, incoming.origin)
    }

    /** Result of the open-documents picker. */
    fun onDocumentsPicked(uris: List<Uri>) {
        hub.open(uris.map { FileRef(FileRef.Kind.ContentUri, it.toString()) }, OpenOrigin.Picker)
    }

    /** Result of the choose-folder picker; null when the user backed out. */
    fun onFolderPicked(uri: Uri?) {
        if (uri != null) hub.addFolder(FileRef(FileRef.Kind.ContentUri, uri.toString()))
    }

    override val canAddFolder: Boolean = true

    override fun openFiles(): Boolean = withHost { pickDocuments(OpenableMimeTypes.picker.toTypedArray()) }

    override fun addFolder(): Boolean = withHost { pickFolder() }

    override fun openInOtherApp(
        ref: FileRef,
        mimeType: String?,
    ): Boolean {
        if (ref.kind != FileRef.Kind.ContentUri) return false
        val uri = Uri.parse(ref.location)
        val type = mimeType ?: appContext.contentResolver.getType(uri) ?: OCTET_STREAM
        val view =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The chooser passes the read grant (and only that) on to the app the user picks; never this app itself.
        return withHost {
            val chooser =
                Intent
                    .createChooser(view, null)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(component))
            start(chooser)
        }
    }

    private fun withHost(action: Host.() -> Unit): Boolean {
        val current = host ?: return false
        return try {
            current.action()
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(LOG_TAG, "no activity for the file dialog", e)
            false
        }
    }

    companion object {
        /** Logcat tag of the file layer: `adb logcat -s FaiFiles`. */
        const val LOG_TAG = "FaiFiles"

        /** Boolean extra: list every library folder once and log the times (`am start ... --ez <this> true`). */
        const val EXTRA_MEASURE_FOLDERS = "org.faioffice.extra.MEASURE_FOLDERS"

        private const val OCTET_STREAM = "application/octet-stream"

        @Volatile
        private var instance: AndroidFiles? = null

        fun get(context: Context): AndroidFiles =
            instance ?: synchronized(this) { instance ?: AndroidFiles(context).also { instance = it } }

        private fun File.createEmpty() {
            parentFile?.mkdirs()
            createNewFile()
        }

        /** The intent's data URI, then its `EXTRA_STREAM` URI(s), then its clip data URIs (where grants travel). */
        private fun urisOf(intent: Intent): List<String?> =
            buildList {
                add(intent.data?.toString())
                streamsOf(intent).forEach { add(it.toString()) }
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri?.toString())
                }
            }

        private fun streamsOf(intent: Intent): List<Uri> =
            when (intent.action) {
                Intent.ACTION_SEND -> listOfNotNull(parcelable(intent))
                Intent.ACTION_SEND_MULTIPLE -> parcelables(intent).orEmpty()
                else -> emptyList()
            }

        @Suppress("DEPRECATION") // the typed overload exists from API 33; minSdk is 29
        private fun parcelable(intent: Intent): Uri? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            }

        @Suppress("DEPRECATION") // the typed overload exists from API 33; minSdk is 29
        private fun parcelables(intent: Intent): List<Uri>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.filterIsInstance<Uri>()
            }
    }
}
