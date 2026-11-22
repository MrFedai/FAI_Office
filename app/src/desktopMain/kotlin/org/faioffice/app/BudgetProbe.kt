/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

private const val MILLIS_PER_SECOND = 1000L
private const val DEFAULT_HOLD_SECONDS = 30L
private const val CDS_WAIT_MS = 60_000L

/**
 * Budget measurement hook for ci/budget.sh (MASTER_SPEC section 7). Inert unless FAI_BUDGET_PROBE=<seconds> is set:
 * then it prints the time from process start to the second frame and waits <seconds> at rest (and for a running
 * AppCDS dump, see CdsArchive). Where Linux /proc
 * exists it prints the process PSS and exits. Elsewhere (Windows) nothing in the JVM reads the working set, so it
 * prints `fai_budget at_rest pid <pid>` and stays open FAI_BUDGET_HOLD seconds (default 30) for the caller to read
 * the process memory from outside (ci/budget.sh: PowerShell Get-Process), then exits.
 * FAI_BUDGET_OUT=<file> also appends every line to that file: a Windows GUI launcher started from WSL may have no
 * usable stdout. FAI_BUDGET_SEED=<folder> measures with the seeded library (DesktopBudgetSeed), whose line comes first.
 */
@Composable
internal fun BudgetProbe() {
    val settleSeconds = System.getenv("FAI_BUDGET_PROBE")?.toLongOrNull() ?: return
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        val process = ProcessHandle.current()
        val start = process.info().startInstant().map { it.toEpochMilli() }
        if (start.isPresent) probeLine("fai_budget first_frame_ms ${System.currentTimeMillis() - start.get()}")
        delay(settleSeconds * MILLIS_PER_SECOND)
        // First start of an install: let the AppCDS dump (CdsArchive) finish before the caller reads memory and
        // ends the process (taskkill on Windows), or the next start would still run without the archive.
        withContext(Dispatchers.IO) { CdsArchive.awaitPending(CDS_WAIT_MS) }
        val pss = pssKb()
        if (pss != null) {
            probeLine("fai_budget pss_kb $pss")
        } else {
            probeLine("fai_budget at_rest pid ${process.pid()}")
            delay((System.getenv("FAI_BUDGET_HOLD")?.toLongOrNull() ?: DEFAULT_HOLD_SECONDS) * MILLIS_PER_SECOND)
        }
        exitProcess(0)
    }
}

/** Prints a `fai_budget` line for ci/budget.sh: stdout, and FAI_BUDGET_OUT when set. */
internal fun probeLine(line: String) {
    println(line)
    System.getenv("FAI_BUDGET_OUT")?.let { File(it).appendText(line + "\n") }
}

/** Proportional set size of this process in KB, from /proc/self/smaps_rollup; null where that file does not exist. */
private fun pssKb(): Long? =
    File("/proc/self/smaps_rollup")
        .takeIf { it.canRead() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("Pss:") }
        ?.split(Regex("\\s+"))
        ?.getOrNull(1)
        ?.toLongOrNull()
