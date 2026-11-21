/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import org.faioffice.core.files.FileRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IncomingFilesTest {
    private val doc = "content://com.android.providers.downloads.documents/document/msf%3A42"
    private val mail = "content://com.google.android.gm.sapi/a%40b.c/message_attachment_external/1/2"

    private fun ref(uri: String) = FileRef(FileRef.Kind.ContentUri, uri)

    @Test
    fun openWithBecomesOneOpenWithRequest() {
        assertEquals(
            Incoming(listOf(ref(doc)), OpenOrigin.OpenWith),
            IncomingFiles.of(IncomingFiles.ACTION_VIEW, listOf(doc)),
        )
    }

    @Test
    fun sharesKeepOrderAndDropDuplicates() {
        val incoming = IncomingFiles.of(IncomingFiles.ACTION_SEND_MULTIPLE, listOf(mail, doc, mail, null))
        assertEquals(Incoming(listOf(ref(mail), ref(doc)), OpenOrigin.Share), incoming)
        assertEquals(OpenOrigin.Share, IncomingFiles.of(IncomingFiles.ACTION_SEND, listOf(doc))?.origin)
    }

    @Test
    fun onlyContentUrisAreAccepted() {
        val uris = listOf("file:///sdcard/Download/a.pdf", "https://example.org/a.pdf", "content://", "CONTENT://x/y")
        assertEquals(listOf(ref("CONTENT://x/y")), IncomingFiles.of(IncomingFiles.ACTION_SEND_MULTIPLE, uris)?.refs)
        assertNull(IncomingFiles.of(IncomingFiles.ACTION_VIEW, listOf("file:///sdcard/a.pdf")))
    }

    @Test
    fun otherActionsAndEmptyHandOversOpenNothing() {
        assertNull(IncomingFiles.of("android.intent.action.MAIN", listOf(doc)))
        assertNull(IncomingFiles.of(null, listOf(doc)))
        assertNull(IncomingFiles.of(IncomingFiles.ACTION_VIEW, emptyList()))
    }

    @Test
    fun largeSharesAreCut() {
        val uris = (1..250).map { "content://p/document/$it" }
        val refs = IncomingFiles.of(IncomingFiles.ACTION_SEND_MULTIPLE, uris)?.refs.orEmpty()
        assertEquals(IncomingFiles.MAX_FILES, refs.size)
        assertEquals(ref("content://p/document/1"), refs.first())
    }
}
