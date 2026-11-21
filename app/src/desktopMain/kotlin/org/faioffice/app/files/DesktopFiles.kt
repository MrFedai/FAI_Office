/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.faioffice.core.files.DesktopFileAccess
import org.faioffice.core.files.FileRef
import org.faioffice.core.files.NoPersistedGrants
import org.faioffice.core.files.SafeSave
import org.faioffice.core.library.LibraryStore
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.concurrent.thread
import kotlin.time.TimeSource

/**
 * The user's app data folders: [data] for the library and the safe-save journal (Windows `%APPDATA%`, roaming),
 * [local] for the single-instance lock and socket (Windows `%LOCALAPPDATA%`, never roams). macOS and Linux use one
 * folder for both (`~/Library/Application Support`, the XDG data folder). The system property `fai.data.dir`
 * replaces both (tests, a portable run).
 */
data class DesktopDirs(
    val data: Path,
    val local: Path,
) {
    companion object {
        const val PROPERTY = "fai.data.dir"
        private const val WINDOWS_NAME = "FAI Office"
        private const val UNIX_NAME = "fai-office"

        fun of(
            os: DesktopOs,
            env: (String) -> String?,
            home: String,
            override: String?,
        ): DesktopDirs {
            if (!override.isNullOrBlank()) return Path.of(override).let { DesktopDirs(it, it) }

            fun dir(
                variable: String,
                fallback: String,
            ): Path = Path.of(env(variable)?.takeIf { it.isNotBlank() } ?: fallback)
            return when (os) {
                DesktopOs.Windows -> {
                    DesktopDirs(
                        dir("APPDATA", "$home\\AppData\\Roaming").resolve(WINDOWS_NAME),
                        dir("LOCALAPPDATA", "$home\\AppData\\Local").resolve(WINDOWS_NAME),
                    )
                }

                DesktopOs.MacOs -> {
                    Path.of(home, "Library", "Application Support", WINDOWS_NAME).let { DesktopDirs(it, it) }
                }

                DesktopOs.Linux -> {
                    dir("XDG_DATA_HOME", "$home/.local/share").resolve(UNIX_NAME).let { DesktopDirs(it, it) }
                }
            }
        }
    }
}

/**
 * The file layer of the desktop app (Windows first, Linux and macOS unchanged): `java.nio` access, the library in
 * the user's app data folder, the [FileHub] the screens collect, and the hand-offs. Files arrive from the command
 * line (Explorer "Open with", a desktop entry, a terminal), a second launch ([SingleInstance]), a drop on the window
 * ([DesktopDrop]) and the open dialog. Main.kt [start]s it before the window exists and [attach]es the window.
 *
 * Every file that reaches the hub leaves a line on stderr (prefix `FaiFiles:`): where it came from, its detected
 * format, size and time, never its name or path. Run `fai-office.exe` from a terminal, or `./gradlew :app:run`, to
 * read them.
 */
class DesktopFiles private constructor(
    dirs: DesktopDirs,
    private val os: DesktopOs,
    private val log: (String) -> Unit,
) : FileActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var window: Frame? = null

    /** Keeps the single-instance lock and socket for the process lifetime. */
    @Volatile
    private var server: SingleInstance.Server? = null

    val access = DesktopFileAccess()
    val library: FileLibrary
    val hub: FileHub
    val services: FileServices

    init {
        val libraryDir = dirs.data.resolve("library")
        val journal = dirs.data.resolve("safe-save")
        try {
            Files.createDirectories(libraryDir)
            Files.createDirectories(journal)
        } catch (e: IOException) {
            // The library then reports its write failures itself; opening files does not depend on it.
            log("app data folder not writable (${e::class.simpleName})")
        }
        library =
            FileLibrary(
                LibraryStore(
                    access,
                    SafeSave(access, DesktopFileAccess.refOf(journal)),
                    DesktopFileAccess.refOf(libraryDir.resolve("library.txt")),
                    DesktopFileAccess.refOf(libraryDir.resolve("library-damaged.txt")),
                    System::currentTimeMillis,
                ),
                NoPersistedGrants,
            )
        hub = FileHub(access, scope, library, log)
        services = FileServices(access, hub, this, library)
    }

    /** The main window: parent of the dialogs, brought forward by a second launch. */
    fun attach(window: Frame) {
        this.window = window
    }

    fun detach(window: Frame) {
        if (this.window === window) this.window = null
    }

    /** Queues desktop files ([OpenOrigin.Desktop]); [how] names the way they came, for the log only. */
    fun receive(
        paths: List<Path>,
        how: String,
    ) {
        if (paths.isEmpty()) return
        log("incoming ${OpenOrigin.Desktop.name} ($how): ${paths.size} file(s)")
        hub.open(paths.map(DesktopFileAccess::refOf), OpenOrigin.Desktop)
    }

    /** A drop on the window; false when it carried no files. */
    fun drop(transferable: Transferable): Boolean {
        val paths = DesktopDrop.files(transferable)
        receive(paths, "drop")
        return paths.isNotEmpty()
    }

    /** Restores and raises the main window (a second launch asked for it). */
    fun bringToFront() {
        EventQueue.invokeLater {
            val frame = window ?: return@invokeLater
            if (frame.extendedState and Frame.ICONIFIED != 0) {
                frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
            }
            frame.isVisible = true
            frame.toFront()
            // Windows lets a background process only flash its taskbar button; staying on top for a moment raises
            // the window for real. The second launch was the user's own action, so this is not focus stealing.
            if (!frame.isAlwaysOnTop) {
                frame.isAlwaysOnTop = true
                frame.isAlwaysOnTop = false
            }
            frame.requestFocus()
        }
    }

    override val canAddFolder: Boolean = true

    override val canShowInFolder: Boolean = true

    // Set by the screens in the interface language; the JDK dialogs have no title of their own on Windows.
    @Volatile
    private var openTitle: String? = null

    @Volatile
    private var folderTitle: String? = null

    override fun setDialogTitles(
        openFiles: String,
        addFolder: String,
    ) {
        openTitle = openFiles
        folderTitle = addFolder
    }

    override fun openFiles(): Boolean {
        val owner = window ?: return false
        EventQueue.invokeLater {
            // The system open dialog through AWT (the Windows common file dialog); every file, detection decides.
            val dialog = FileDialog(owner, openTitle.orEmpty()).apply { isMultipleMode = true }
            dialog.isVisible = true
            val picked = dialog.files.orEmpty().map(File::toPath)
            dialog.dispose()
            if (picked.isNotEmpty()) {
                log("incoming ${OpenOrigin.Picker.name}: ${picked.size} file(s)")
                hub.open(picked.map(DesktopFileAccess::refOf), OpenOrigin.Picker)
            }
        }
        return true
    }

    override fun addFolder(): Boolean {
        val owner = window ?: return false
        EventQueue.invokeLater {
            val folder = chooseFolder(owner) ?: return@invokeLater
            log("incoming folder")
            hub.addFolder(DesktopFileAccess.refOf(folder))
        }
        return true
    }

    override fun openInOtherApp(
        ref: FileRef,
        mimeType: String?,
    ): Boolean {
        val path = pathOf(ref) ?: return false
        background("open in other app") {
            val chooser = DesktopShell.openWith(os, path, System.getenv("SystemRoot"))
            if (chooser == null || !launch(chooser, waitForSuccess = false)) desktopOpen(path)
        }
        return true
    }

    override fun showInFolder(ref: FileRef): Boolean {
        val path = pathOf(ref) ?: return false
        background("show in folder") {
            // Explorer answers 1 even when it worked; only the D-Bus call reports a missing file manager.
            val shown = launch(DesktopShell.showInFolder(os, path), waitForSuccess = os == DesktopOs.Linux)
            if (!shown) path.parent?.let(::desktopOpen)
        }
        return true
    }

    private fun chooseFolder(owner: Frame): Path? {
        if (os == DesktopOs.MacOs) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val dialog = FileDialog(owner, folderTitle.orEmpty()).apply { isVisible = true }
                val chosen =
                    dialog.files
                        .orEmpty()
                        .firstOrNull()
                        ?.toPath()
                dialog.dispose()
                return chosen
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        // AWT has no folder dialog on Windows and Linux: Swing's chooser in the system look (Windows look on
        // Windows, GTK on GNOME). Compose draws its own UI, so the look and feel only touches this dialog.
        systemLook
        val chooser =
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
                folderTitle?.let { dialogTitle = it }
            }
        val approved = chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION
        return if (approved) chooser.selectedFile?.toPath() else null
    }

    private val systemLook: Unit by lazy {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            log("system look and feel unavailable (${e::class.simpleName})")
        }
    }

    private fun pathOf(ref: FileRef): Path? =
        if (ref.kind != FileRef.Kind.Path) {
            null
        } else {
            try {
                DesktopFileAccess.pathOf(ref)
            } catch (_: Exception) {
                null
            }
        }

    /** Starts [command]; with [waitForSuccess] it must exit with 0 within a few seconds. */
    private fun launch(
        command: List<String>,
        waitForSuccess: Boolean,
    ): Boolean =
        try {
            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
            !waitForSuccess || (process.waitFor(LAUNCH_WAIT_S, TimeUnit.SECONDS) && process.exitValue() == 0)
        } catch (_: IOException) {
            false
        }

    private fun desktopOpen(path: Path) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop?.isSupported(Desktop.Action.OPEN) == true) desktop.open(path.toFile())
    }

    private fun background(
        what: String,
        block: () -> Unit,
    ) {
        thread(name = "fai-files-shell", isDaemon = true) {
            val start = TimeSource.Monotonic.markNow()
            val outcome =
                try {
                    block()
                    "started"
                } catch (e: Exception) {
                    "failed (${e::class.simpleName})"
                }
            log("$what: $outcome in ${start.elapsedNow().inWholeMilliseconds} ms")
        }
    }

    companion object {
        private const val LAUNCH_WAIT_S = 3L

        /** One stderr line per event; no names or paths (see the class comment). */
        fun stderr(line: String) {
            System.err.println("FaiFiles: $line")
        }

        /**
         * Starts the file layer for this launch of the app with its program [args]: becomes the running app (files
         * from [args] go to the hub) or hands them to the running one and returns null; the caller then exits.
         */
        fun start(
            args: Array<String>,
            log: (String) -> Unit = ::stderr,
        ): DesktopFiles? {
            val started = TimeSource.Monotonic.markNow()
            val os = DesktopOs.current
            val dirs =
                DesktopDirs.of(
                    os,
                    System::getenv,
                    System.getProperty("user.home").orEmpty(),
                    System.getProperty(DesktopDirs.PROPERTY),
                )
            val commandLine =
                ProcessHandle
                    .current()
                    .info()
                    .commandLine()
                    .orElse(null)
            val argv = DesktopCommandLine.programArgs(args.toList(), os == DesktopOs.Windows, commandLine)
            val paths = DesktopCommandLine.files(argv, Path.of("").toAbsolutePath())

            val ready = CompletableFuture<DesktopFiles>()
            val role =
                SingleInstance(dirs.local, log).claim(paths.map(Path::toString)) { request ->
                    val files = ready.get()
                    files.receive(request.mapNotNull(::pathOrNull), "second launch")
                    files.bringToFront()
                }
            val elapsed = started.elapsedNow().inWholeMilliseconds
            when (role) {
                is SingleInstance.Role.HandedOver -> {
                    log("handed ${paths.size} file(s) to the running app in $elapsed ms")
                    return null
                }

                is SingleInstance.Role.Primary -> {
                    log("single instance: running app (claimed in $elapsed ms)")
                }

                is SingleInstance.Role.Standalone -> {
                    log("single instance: unavailable, ${role.reason}")
                }
            }
            val files = DesktopFiles(dirs, os, log)
            files.server = (role as? SingleInstance.Role.Primary)?.server
            ready.complete(files)
            files.receive(paths, "command line")
            return files
        }

        private fun pathOrNull(location: String): Path? =
            try {
                Path.of(location)
            } catch (_: InvalidPathException) {
                null
            }
    }
}

/** The files of a drop on the window: the platform's file list, or `file:` URIs as text (some Linux desktops). */
object DesktopDrop {
    private val uriList: DataFlavor? =
        try {
            DataFlavor("text/uri-list;class=java.lang.String")
        } catch (_: ClassNotFoundException) {
            null
        }

    /** Whether [transferable] may carry files (decides whether the window accepts the drag). */
    fun accepts(transferable: Transferable): Boolean =
        transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
            (uriList != null && transferable.isDataFlavorSupported(uriList))

    fun files(transferable: Transferable): List<Path> =
        try {
            when {
                transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                    (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        .orEmpty()
                        .filterIsInstance<File>()
                        .map { it.toPath().toAbsolutePath().normalize() }
                        .distinct()
                        .take(IncomingFiles.MAX_FILES)
                }

                uriList != null && transferable.isDataFlavorSupported(uriList) -> {
                    val text = transferable.getTransferData(uriList) as? String
                    // RFC 2483: one URI per line (`#` lines are comments); only local files count.
                    val uris =
                        text
                            .orEmpty()
                            .lines()
                            .map { it.trim() }
                            .filter { it.startsWith("file:", true) }
                    DesktopCommandLine.files(listOf("--") + uris, Path.of("").toAbsolutePath())
                }

                else -> {
                    emptyList()
                }
            }
        } catch (_: Exception) {
            emptyList() // UnsupportedFlavorException, IOException: the drop source went away
        }
}
