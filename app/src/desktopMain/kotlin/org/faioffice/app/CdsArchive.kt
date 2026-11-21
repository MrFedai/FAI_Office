/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * AppCDS archive of the installed desktop app (P2.8b, risk R7: JVM start). A class data sharing archive holds the
 * JDK, Kotlin and Compose classes the app loads up to its first frames, already parsed and verified; the JVM maps it
 * instead of loading those classes from the jars, which cuts the start to first frame by about 40 % and the RAM at
 * rest by about 40 MB (ci/budget.sh, 2026-11-21).
 *
 * JDK 17 accepts an archive only for the exact classpath (absolute jar paths, sizes, times) it was dumped with, so it
 * cannot be built in CI and shipped: it is dumped on the user's machine, once per install. The launcher always passes
 * `-XX:SharedArchiveFile=<app>/resources/fai.jsa` (app/build.gradle.kts); while that file is missing, empty (the
 * placeholder the package ships) or stale (after an update) the JVM silently runs without it. Then, after the first
 * frame, this starts the app launcher once more as a child with `_JAVA_OPTIONS=-Xshare:dump` (it dumps the archive
 * from the shipped class list, app/cds/common/fai.classlist, about 3 s of CPU, and exits without opening a window),
 * and moves the result into place when the child ends. The next start maps it.
 *
 * `fai.jsa.stamp` records the classpath the last attempt was made for, so a failed dump or an archive the JVM keeps
 * rejecting is not retried on every start, only after the next update. Inert outside an installed image (no
 * `fai.cds` property, e.g. `./gradlew :app:run`) and where the install folder is not writable.
 */
internal object CdsArchive {
    private const val WAIT_AT_EXIT_MS = 30_000L

    @Volatile private var worker: Thread? = null

    /** Starts the check (and the dump, if this install has no usable archive yet) on a background thread. */
    fun prepareInBackground() {
        val archive = System.getProperty("fai.cds")?.takeUnless { it.startsWith('$') }?.let(::File) ?: return
        val thread = Thread({ prepare(archive) }, "fai-cds")
        thread.isDaemon = true
        worker = thread
        // A user closing the window during the first start: let the dump finish and move into place.
        Runtime.getRuntime().addShutdownHook(Thread { thread.join(WAIT_AT_EXIT_MS) })
        thread.start()
    }

    /** Waits up to [millis] for the check and dump (ci/budget.sh warm-up: the app is killed from outside later). */
    fun awaitPending(millis: Long) {
        worker?.join(millis)
    }

    private fun prepare(archive: File) {
        val dir = archive.parentFile ?: return
        val classList = File(dir, "fai.classlist")
        val stamp = File(dir, "${archive.name}.stamp")
        val fingerprint = classpathFingerprint()
        val previous = runCatching { stamp.readText() }.getOrNull()
        if (!shouldDump(sharing(), classList.isFile, canCreateFiles(dir), previous, fingerprint)) return
        val launcher =
            ProcessHandle
                .current()
                .info()
                .command()
                .orElse(null) ?: return
        dir.listFiles { f -> f.name.startsWith("${archive.name}.") && f.name.endsWith(".tmp") }?.forEach { it.delete() }
        val tmp = File(dir, "${archive.name}.${ProcessHandle.current().pid()}.tmp")
        val ok =
            runCatching {
                // Relative paths: the child runs in the resources folder, so spaces in the install path need no
                // quoting inside _JAVA_OPTIONS (which the JVM reads after the launcher's options, so it wins).
                val pb = ProcessBuilder(launcher).directory(dir)
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
                // The jpackage launcher hands its JVM options to its own second stage through _JPACKAGE_* variables;
                // inherited, they would make the child start without the app classpath.
                pb.environment().keys.removeIf { it.startsWith("_JPACKAGE") }
                pb.environment()["_JAVA_OPTIONS"] =
                    "-Xshare:dump -XX:SharedClassListFile=${classList.name} -XX:SharedArchiveFile=${tmp.name}"
                pb.start().waitFor() == 0 && tmp.length() > 0
            }.getOrDefault(false)
        if (ok) {
            // The JVM writes the archive read-only; Windows cannot replace or uninstall a read-only file.
            tmp.setWritable(true)
            archive.setWritable(true)
            runCatching { Files.move(tmp.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        tmp.delete()
        runCatching { stamp.writeText(fingerprint) }
    }

    /** File.canWrite on a Windows folder only reads the read-only flag, not the access rights: try it. */
    private fun canCreateFiles(dir: File): Boolean =
        runCatching { File.createTempFile("fai", ".probe", dir).delete() }.getOrDefault(false)

    /** True when the JVM mapped a CDS archive; the packaged runtime has no default one, so it is ours. */
    private fun sharing(): Boolean = System.getProperty("java.vm.info").orEmpty().contains("sharing")

    /** What the JVM checks an archive against: every classpath entry with its size and modification time. */
    private fun classpathFingerprint(): String =
        System
            .getProperty("java.class.path")
            .orEmpty()
            .split(File.pathSeparatorChar)
            .filter { it.isNotEmpty() }
            .joinToString("\n") { File(it).let { f -> "${f.absolutePath} ${f.length()} ${f.lastModified()}" } }
}

/** Dump decision, kept pure for the test: no archive in use, inputs present, and not already tried for this classpath. */
internal fun shouldDump(
    sharing: Boolean,
    classListPresent: Boolean,
    writable: Boolean,
    previousFingerprint: String?,
    fingerprint: String,
): Boolean = !sharing && classListPresent && writable && previousFingerprint != fingerprint

/** Starts [CdsArchive.prepareInBackground] once the window has drawn its first frames, so it never delays them. */
@Composable
internal fun CdsArchiveAfterFirstFrame() {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        CdsArchive.prepareInBackground()
    }
}
