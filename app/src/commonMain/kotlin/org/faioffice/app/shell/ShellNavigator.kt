/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.faioffice.core.files.FileRef

/**
 * Small state based back stack of [Destination]s, no navigation library. The first entry ([root], Home) never
 * leaves the stack: going back there returns false so the platform handles it (Android closes the app).
 * Each destination is on the stack at most once; navigating to the root clears the stack.
 *
 * Above the destinations sits at most one open file ([openedFile], the file information page of P3.8): back closes
 * it first, choosing a destination closes it too. The whole state is saved with [Saver] (Android: it survives process
 * death; the page then reads the file again from its reference).
 */
@Stable
class ShellNavigator(
    val root: Destination = Destination.Home,
) {
    private val stack = mutableStateListOf(root)

    /** Destination on top of the stack, shown by the shell. */
    val current: Destination get() = stack.last()

    /** File whose information page is shown over [current]; null when none is open. */
    var openedFile: FileRef? by mutableStateOf(null)
        private set

    /** True when [back] has an entry to return to (an open file or a destination below the top). */
    val canGoBack: Boolean get() = openedFile != null || stack.size > 1

    /** Back stack, bottom (root) first. */
    val entries: List<Destination> get() = stack.toList()

    /** Progress 0..1 of a predictive back gesture in flight (Android); 0 when none. */
    var backProgress: Float by mutableFloatStateOf(0f)

    /** Shows [destination] (closing an open file); no-op when it is already shown. */
    fun navigate(destination: Destination) {
        openedFile = null
        if (destination == current) return
        if (destination == root) {
            stack.removeRange(1, stack.size)
            return
        }
        stack.remove(destination)
        stack.add(destination)
    }

    /** Shows the information page of [ref] over the current destination. */
    fun openFile(ref: FileRef) {
        openedFile = ref
    }

    /** Closes the open file, back to [current]. */
    fun closeFile() {
        openedFile = null
    }

    /** Closes the open file or returns to the previous destination; false at the root (the system handles back). */
    fun back(): Boolean {
        if (openedFile != null) {
            openedFile = null
            return true
        }
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    companion object {
        /** Saved form: the destination names bottom first, then the open file's serialised reference or "". */
        val Saver: Saver<ShellNavigator, List<String>> =
            Saver(
                save = { nav -> nav.stack.map { it.name } + (nav.openedFile?.serialize() ?: "") },
                restore = { saved -> restore(saved) },
            )

        private fun restore(saved: List<String>): ShellNavigator? {
            val destinations =
                saved.dropLast(1).mapNotNull { name ->
                    Destination.entries.firstOrNull { it.name == name }
                }
            val root = destinations.firstOrNull() ?: return null
            return ShellNavigator(root).apply {
                destinations.drop(1).forEach { stack.add(it) }
                openedFile = saved.lastOrNull()?.let(FileRef::parse)
            }
        }
    }
}

/** Navigator that survives recomposition, configuration changes and (Android) process death. */
@Composable
fun rememberShellNavigator(root: Destination = Destination.Home): ShellNavigator =
    rememberSaveable(saver = ShellNavigator.Saver) { ShellNavigator(root) }
