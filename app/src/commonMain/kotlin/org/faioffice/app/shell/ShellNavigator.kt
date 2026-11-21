/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Small state based back stack of [Destination]s, no navigation library. The first entry ([root], Home) never
 * leaves the stack: going back there returns false so the platform handles it (Android closes the app).
 * Each destination is on the stack at most once; navigating to the root clears the stack.
 */
@Stable
class ShellNavigator(
    val root: Destination = Destination.Home,
) {
    private val stack = mutableStateListOf(root)

    /** Destination on top of the stack, shown by the shell. */
    val current: Destination get() = stack.last()

    /** True when [back] has an entry to return to. */
    val canGoBack: Boolean get() = stack.size > 1

    /** Back stack, bottom (root) first. */
    val entries: List<Destination> get() = stack.toList()

    /** Progress 0..1 of a predictive back gesture in flight (Android); 0 when none. */
    var backProgress: Float by mutableFloatStateOf(0f)

    /** Shows [destination]; no-op when it is already shown. */
    fun navigate(destination: Destination) {
        if (destination == current) return
        if (destination == root) {
            stack.removeRange(1, stack.size)
            return
        }
        stack.remove(destination)
        stack.add(destination)
    }

    /** Returns to the previous destination; false at the root (the caller lets the system handle back). */
    fun back(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

/** Navigator that survives recomposition of the caller. */
@Composable
fun rememberShellNavigator(root: Destination = Destination.Home): ShellNavigator = remember { ShellNavigator(root) }
