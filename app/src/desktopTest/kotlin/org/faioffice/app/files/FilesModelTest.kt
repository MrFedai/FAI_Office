/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:Suppress("MagicNumber")

package org.faioffice.app.files

import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.runBlocking
import org.faioffice.app.screens.InfoState
import org.faioffice.app.shell.Destination
import org.faioffice.app.shell.ShellNavigator
import org.faioffice.core.formats.FileFormat
import org.faioffice.core.formats.ModuleFamily
import org.faioffice.core.library.EntryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The file screens' model over the real file channel (FileHub, FileLibrary, LibraryStore on the in-memory files). */
class FilesModelTest {
    @Test
    fun recentsCarryTheirStatePinnedAndNewestFirst() {
        val sample = SampleFiles()
        val model = sample.model()
        assertEquals(10, model.recents.size)
        assertEquals(sample.refs["budget"], model.recents.first().ref)
        assertTrue(model.recents.first().pinned)
        assertEquals(EntryState.Missing, model.recents.first { it.ref == sample.refs["zip"] }.state)
        assertEquals(EntryState.PermissionLost, model.recents.first { it.ref == sample.refs["csv"] }.state)
        assertEquals(listOf("Projeler", "İndirilenler"), model.folders.map { it.name })
    }

    @Test
    fun familyListsRecentsThenFolderFilesWithoutDuplicates() {
        val sample = SampleFiles()
        val model = sample.model()
        val pdf = model.family(ModuleFamily.Pdf)
        assertFalse(pdf.loading)
        val names = pdf.items.map { it.name }
        // Three recent PDFs first (newest first), then the folders' PDFs; the subfolder is read too.
        assertEquals(3, pdf.items.count { it.inRecents })
        assertTrue(pdf.items.take(3).all { it.inRecents })
        assertTrue("Teknik Şartname_Rev3.pdf" in names)
        assertTrue("Fatura_Kasım_2026.pdf" in names)
        assertEquals(
            names.size,
            pdf.items
                .map { it.ref }
                .toSet()
                .size,
        )
        assertEquals("Projeler", pdf.items.first { it.name == "Teknik Şartname_Rev3.pdf" }.folder)
        val presentations = model.family(ModuleFamily.Presentations).items.map { it.name }
        assertTrue("Eğitim Semineri — Yeni Başlayanlar İçin Tablo Kullanımı.odp" in presentations)
    }

    @Test
    fun anOpenedFileIsKeptInRecentsAndShown() {
        val sample = SampleFiles()
        val model = sample.model()
        val nav = ShellNavigator()
        val invoice = sample.refs.getValue("invoice")
        runBlocking { model.handle(FileEvent.Opened(sample.resolve("invoice")), nav) }
        assertEquals(invoice, nav.openedFile)
        assertEquals(invoice, model.opened?.ref)
        assertEquals(invoice, model.recents.first().ref)
        assertEquals(FileFormat.Pdf, model.recents.first().format)
        assertEquals(InfoState.Ok, InfoState.of(model.opened))
    }

    @Test
    fun infoStatesFollowTheReadResult() {
        val sample = SampleFiles()
        assertEquals(InfoState.Loading, InfoState.of(null))
        assertEquals(InfoState.Missing, InfoState.of(sample.resolve("zip")))
        assertEquals(InfoState.PermissionLost, InfoState.of(sample.resolve("csv")))
        assertEquals(InfoState.Damaged, InfoState.of(sample.resolve("spec")))
        assertEquals(InfoState.Protected, InfoState.of(sample.protectedRequest("report")))
        assertEquals(InfoState.Ok, InfoState.of(sample.resolve("manual")))
    }

    @Test
    fun pinRemoveClearAndSwitchReachTheStore() {
        val sample = SampleFiles()
        val model = sample.model()
        val deck = sample.refs.getValue("deck")
        model.setPinned(deck, true)
        assertTrue(model.recents.first { it.ref == deck }.pinned)
        model.remove(deck)
        assertFalse(model.inRecents(deck))
        assertFalse(runBlocking { sample.store.recents() }.any { it.ref == deck })
        model.removeFolder(sample.fake.ref("/Users/ayse/Belgeler/Projeler"))
        assertEquals(listOf("İndirilenler"), model.folders.map { it.name })
        model.clearRecents()
        assertTrue(model.recents.isEmpty())
        model.switchRememberRecent(false)
        assertFalse(model.rememberRecent)
    }

    @Test
    fun navigatorSavesItsStackAndOpenFile() {
        val sample = SampleFiles()
        val nav = ShellNavigator()
        nav.navigate(Destination.RecentFiles)
        nav.navigate(Destination.Pdf)
        nav.openFile(sample.refs.getValue("manual"))
        val saved = with(ShellNavigator.Saver) { SaverScope { true }.save(nav) }
        val restored = assertNotNull(saved?.let { ShellNavigator.Saver.restore(it) })
        assertEquals(nav.entries, restored.entries)
        assertEquals(sample.refs["manual"], restored.openedFile)
        assertTrue(restored.back(), "back closes the open file first")
        assertNull(restored.openedFile)
        assertEquals(Destination.Pdf, restored.current)
    }

    @Test
    fun aRestoredOpenFileIsReadAgain() {
        // Android process death: the navigator comes back with the file's reference, the model has nothing yet.
        val sample = SampleFiles()
        val model = sample.model()
        val manual = sample.refs.getValue("manual")
        assertNull(model.opened)
        runBlocking { model.ensureOpened(manual) }
        val request = assertNotNull(model.opened)
        assertEquals(manual, request.ref)
        assertEquals(13_526_630L, request.info?.size)
        assertEquals(FileFormat.Pdf, request.detection?.format)
    }

    @Test
    fun withoutALibraryRecentsLastForTheSession() {
        val sample = SampleFiles(populated = false)
        val web = FileServices(sample.access, sample.hub, NoFileActions, library = null)
        val model = FilesModel(web, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        sample.fake.addFile("/web/Not.pdf", SampleFiles.PDF)
        val request = runBlocking { sample.hub.resolve(sample.fake.ref("/web/Not.pdf"), OpenOrigin.Picker) }
        runBlocking { model.handle(FileEvent.Opened(request), ShellNavigator()) }
        assertFalse(model.keepsLibrary)
        assertEquals(listOf("Not.pdf"), model.recents.map { it.name })
        model.setPinned(request.ref, true)
        assertTrue(model.recents.single().pinned)
        model.clearRecents()
        assertTrue(model.recents.isEmpty())
    }
}
