/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core.files

/**
 * Access grants that survive a restart (Android: persisted SAF URI permissions). The app takes a grant when a file
 * is kept in recent files or a folder is added, and releases it when the entry is removed; [sync] does both from the
 * wanted list. Platforms where access needs no grant (desktop, Web) use [NoPersistedGrants].
 *
 * Only [FileRef.Kind.ContentUri] references take grants; every other kind is ignored.
 */
interface PersistedGrants {
    /** How many grants the platform keeps for the app (Android 10: 128, Android 11 and later: 512). */
    val limit: Int

    /** The references the app holds a grant for now. */
    suspend fun held(): Set<FileRef>

    /** Takes a grant for [ref] (read and write when offered, read only otherwise); false when it cannot be taken. */
    suspend fun take(ref: FileRef): Boolean

    /** Releases the grant for [ref]; nothing happens when none is held. */
    suspend fun release(ref: FileRef)
}

/** No grants needed: every reference stays reachable as long as the file system allows. */
object NoPersistedGrants : PersistedGrants {
    override val limit: Int = Int.MAX_VALUE

    override suspend fun held(): Set<FileRef> = emptySet()

    override suspend fun take(ref: FileRef): Boolean = true

    override suspend fun release(ref: FileRef) = Unit
}

/** What [planGrants] decided: grants to take and grants to release. [dropped]: wanted but over the limit. */
data class GrantPlan(
    val take: List<FileRef>,
    val release: List<FileRef>,
    val dropped: List<FileRef>,
)

/** What [PersistedGrants.sync] did. [failed]: grants the platform refused (no persistable grant was offered). */
data class GrantSync(
    val taken: List<FileRef>,
    val released: List<FileRef>,
    val failed: List<FileRef>,
    val dropped: List<FileRef>,
)

/**
 * Decides which grants to hold. [wanted] is in priority order (the most important first); a reference inside a
 * wanted folder grant ([coveredBy]) needs no grant of its own; at most [limit] grants are kept, the rest of [wanted]
 * is [GrantPlan.dropped] (it shows "permission lost" after a restart). Held grants not kept are released.
 */
fun planGrants(
    held: Set<FileRef>,
    wanted: List<FileRef>,
    limit: Int,
): GrantPlan {
    val keep = LinkedHashSet<FileRef>()
    val dropped = mutableListOf<FileRef>()
    for (ref in wanted) {
        if (ref.kind != FileRef.Kind.ContentUri || ref in keep) continue
        if (keep.any { coveredBy(ref, it) }) continue
        if (keep.size >= limit) dropped += ref else keep += ref
    }
    return GrantPlan(
        take = keep.filter { it !in held },
        release = held.filter { it !in keep },
        dropped = dropped,
    )
}

/**
 * Whether the grant for [folder] already covers [ref]: an Android document URI built under a document tree
 * (`content://<authority>/tree/<tree id>/document/<document id>`) is reachable through the tree's grant.
 */
fun coveredBy(
    ref: FileRef,
    folder: FileRef,
): Boolean =
    ref.kind == FileRef.Kind.ContentUri &&
        folder.kind == FileRef.Kind.ContentUri &&
        TREE_SEGMENT in folder.location &&
        DOCUMENT_SEGMENT !in folder.location &&
        ref.location.startsWith(folder.location + DOCUMENT_SEGMENT)

/**
 * Brings the held grants in line with [wanted] (priority order, see [planGrants]): releases first, so the limit has
 * room, then takes. A grant the platform refuses is reported in [GrantSync.failed], not retried. With
 * [releaseUnwanted] false nothing is released (the caller is not sure [wanted] is complete, for example after a
 * failed store write).
 */
suspend fun PersistedGrants.sync(
    wanted: List<FileRef>,
    releaseUnwanted: Boolean = true,
): GrantSync {
    val plan = planGrants(held(), wanted, limit)
    val released = if (releaseUnwanted) plan.release else emptyList()
    released.forEach { release(it) }
    val (taken, failed) = plan.take.partition { take(it) }
    return GrantSync(taken, released, failed, plan.dropped)
}

private const val TREE_SEGMENT = "/tree/"
private const val DOCUMENT_SEGMENT = "/document/"
