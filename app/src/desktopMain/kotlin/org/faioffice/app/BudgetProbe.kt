/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import java.io.File
import kotlin.system.exitProcess

private const val MILLIS_PER_SECOND = 1000L

/**
 * Budget measurement hook for ci/budget.sh (MASTER_SPEC section 7). Inert unless FAI_BUDGET_PROBE=<seconds> is set:
 * then it prints the time from process start to the second frame, waits <seconds> at rest, prints the process PSS
 * (Linux /proc) and exits.
 */
@Composable
internal fun BudgetProbe() {
    val settleSeconds = System.getenv("FAI_BUDGET_PROBE")?.toLongOrNull() ?: return
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        val start =
            ProcessHandle
                .current()
                .info()
                .startInstant()
                .map { it.toEpochMilli() }
        if (start.isPresent) println("fai_budget first_frame_ms ${System.currentTimeMillis() - start.get()}")
        delay(settleSeconds * MILLIS_PER_SECOND)
        pssKb()?.let { println("fai_budget pss_kb $it") }
        exitProcess(0)
    }
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
