@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class NotepadStateTest {
    private val holders = mutableListOf<ViewModelStore>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun store(file: File = File(context.cacheDir, "session-${UUID.randomUUID()}.json")) = SessionStore(file)

    private suspend fun create(repository: FakeDocuments = FakeDocuments(), store: SessionStore = store(), savedState: SavedStateHandle = SavedStateHandle(), startWithDocument: Boolean = true): NotepadViewModel {
        val vm = withContext(Dispatchers.Main) {
            NotepadViewModel(repository, store, savedState).also { vm ->
                holders.add(ViewModelStore().apply { put("notepad", vm) })
            }
        }
        waitFor { !vm.loading }
        withContext(Dispatchers.Main) { if (startWithDocument && vm.documents.isEmpty()) vm.newDocument() }
        return vm
    }

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) {
        while (!withContext(Dispatchers.Main) { condition() }) delay(10)
    }

    @After
    fun clearViewModels() = runBlocking {
        withContext(Dispatchers.Main) { holders.forEach { it.clear() } }
    }

    @Test
    fun switchingTabsKeepsIndependentTextSelectionScrollAndUndo() = runBlocking {
        val vm = create()
        withContext(Dispatchers.Main) {
            val first = vm.active!!
            first.editor.edit { append("first document"); selection = TextRange(3, 8) }
            first.scroll.scrollTo(32)
            vm.newDocument()
            val second = vm.active!!
            second.editor.edit { append("second") }
            vm.select(first.id)
            assertEquals("first document", vm.active!!.editor.text.toString())
            assertEquals(TextRange(3, 8), first.editor.selection)
            assertEquals(32, first.scroll.value)
            first.editor.undoState.undo()
            assertEquals("", first.editor.text.toString())
            assertEquals("second", second.editor.text.toString())
            first.editor.undoState.redo()
            assertEquals("first document", first.editor.text.toString())
        }
    }

    @Test
    fun reopeningSameUriSelectsExistingDirtyTab() = runBlocking {
        val repo = FakeDocuments().apply { put("file:one", "original") }
        val vm = create(repo)
        vm.openUri("file:one").join()
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append(" edited") }
            vm.newDocument()
        }
        vm.openUri("file:one").join()
        withContext(Dispatchers.Main) {
            assertEquals(2, vm.documents.size)
            assertEquals("original edited", vm.active!!.editor.text.toString())
        }
    }

    @Test
    fun closingUsesCancelDiscardAndNeighborRules() = runBlocking {
        val vm = create()
        withContext(Dispatchers.Main) {
            val first = vm.active!!
            first.editor.edit { append("unsaved") }
            vm.newDocument()
            val second = vm.active!!
            vm.newDocument()
            val third = vm.active!!
            vm.requestClose(first.id)
            assertEquals(first.id, vm.closeConfirmation)
            vm.cancelClose()
            assertEquals(3, vm.documents.size)
            vm.requestClose(first.id)
            vm.discardClose()
            assertEquals(third.id, vm.activeId)
            vm.select(second.id)
            vm.requestClose(second.id)
            assertEquals(third.id, vm.activeId)
            vm.requestClose(third.id)
            assertTrue(vm.documents.isEmpty())
            assertNull(vm.active)
            assertEquals("", vm.activeId)
        }
    }

    @Test
    fun saveResultStaysWithOriginalTabAndLaterEditsStayDirty() = runBlocking {
        val repo = FakeDocuments().apply { put("file:one", "original") }
        val vm = create(repo)
        vm.openUri("file:one").join()
        val gate = CompletableDeferred<Unit>()
        repo.writeGate = gate
        lateinit var first: DocumentSession
        withContext(Dispatchers.Main) {
            first = vm.active!!
            first.editor.edit { append(" saved") }
            vm.requestClose(first.id)
            vm.saveBeforeClose()
        }
        repo.writeStarted.await()
        withContext(Dispatchers.Main) {
            first.editor.edit { append(" later") }
            vm.newDocument()
        }
        gate.complete(Unit)
        waitFor { !first.saving }
        withContext(Dispatchers.Main) {
            assertEquals("original saved", repo.files.getValue("file:one").text)
            assertEquals("original saved", first.savedText)
            assertTrue(first.dirty)
            assertTrue(vm.documents.contains(first))
            assertNotEquals(first.id, vm.activeId)
        }
    }

    @Test
    fun failedSaveAndCancelledSaveAsNeverCloseDrafts() = runBlocking {
        val repo = FakeDocuments().apply { put("file:one", "original") }
        val vm = create(repo)
        vm.openUri("file:one").join()
        repo.failWrite = true
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append(" draft") }
            vm.requestClose(vm.activeId)
            vm.saveBeforeClose()
        }
        waitFor { vm.errorMessage == R.string.error_save }
        withContext(Dispatchers.Main) {
            assertTrue(vm.active!!.dirty)
            assertEquals(1, vm.documents.size)
            vm.requestSaveAs(closeAfter = true)
            vm.pickerLaunched()
            vm.pickerResult(null)
            assertEquals(1, vm.documents.size)
            assertFalse(vm.pickerPending)
        }
    }

    @Test
    fun successfulSaveBeforeCloseClosesTabAndUndoBackToSavedClearsDirty() = runBlocking {
        val repo = FakeDocuments().apply { put("file:one", "original") }
        val vm = create(repo)
        vm.openUri("file:one").join()
        val id = withContext(Dispatchers.Main) {
            val doc = vm.active!!
            doc.editor.edit { append(" transient") }
            assertTrue(doc.dirty)
            doc.editor.undoState.undo()
            assertFalse(doc.dirty)
            doc.editor.edit { append(" saved") }
            vm.requestClose(doc.id)
            vm.saveBeforeClose()
            doc.id
        }
        waitFor { vm.documents.none { it.id == id } }
        assertEquals("original saved", repo.files.getValue("file:one").text)
        withContext(Dispatchers.Main) {
            assertTrue(vm.documents.isEmpty())
            assertEquals(listOf(RecentFile("file:one", "one")), vm.recentFiles)
        }
    }

    @Test
    fun externalChangesRequireExplicitOverwrite() = runBlocking {
        val repo = FakeDocuments().apply { put("file:one", "original") }
        val vm = create(repo)
        vm.openUri("file:one").join()
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append(" mine") }
            repo.put("file:one", "external")
            vm.save()
        }
        waitFor { vm.conflict != null }
        assertEquals("external", repo.files.getValue("file:one").text)
        withContext(Dispatchers.Main) { vm.overwriteConflict() }
        waitFor { !vm.active!!.saving }
        assertEquals("original mine", repo.files.getValue("file:one").text)
    }

    @Test
    fun saveAsPickerTargetsItsDocumentAfterTabSwitch() = runBlocking {
        val saved = SavedStateHandle()
        val repo = FakeDocuments()
        val vm = create(repo, savedState = saved)
        val id = withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append("first") }
            val id = vm.activeId
            vm.requestSaveAs()
            vm.pickerLaunched()
            vm.newDocument()
            vm.pickerResult("file:saved")
            id
        }
        waitFor { vm.documents.first { it.id == id }.uri != null }
        withContext(Dispatchers.Main) {
            assertEquals("first", repo.files.getValue("file:saved").text)
            assertNotEquals(id, vm.activeId)
            assertEquals("", vm.active!!.editor.text.toString())
        }
    }

    @Test
    fun recoveryRestoresOrderedTabsDraftBaselinesSelectionScrollAndZoom() = runBlocking {
        val sessions = store()
        val repo = FakeDocuments().apply { put("file:one", "original") }
        val vm = create(repo, sessions)
        vm.openUri("file:one").join()
        val snapshot = withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append(" recovered"); selection = TextRange(2, 6) }
            vm.active!!.scroll.scrollTo(30)
            vm.newDocument()
            vm.active!!.editor.edit { append("untitled draft") }
            vm.changeZoom(140)
            vm.select(vm.documents.first().id)
            vm.snapshot()
        }
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(snapshot) { written.complete(it) }
        assertTrue(written.await())
        val recovered = create(repo, sessions)
        withContext(Dispatchers.Main) {
            assertEquals(snapshot, recovered.snapshot())
            assertTrue(recovered.active!!.dirty)
            assertEquals("original", repo.files.getValue("file:one").text)
            assertFalse(recovered.active!!.editor.undoState.canUndo)
        }
    }

    @Test
    fun recoverySkipsEmptyTabsAndSelectsTheFirstSurvivingDocument() = runBlocking {
        val sessions = store()
        val first = DocumentSnapshot(text = "First content")
        val empty = DocumentSnapshot()
        val savedEmpty = DocumentSnapshot(uri = "file:empty", name = "empty.txt")
        val whitespace = DocumentSnapshot(text = " \n")
        val last = DocumentSnapshot(text = "Last content")
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(SessionSnapshot(listOf(first, empty, savedEmpty, whitespace, last), empty.id)) { written.complete(it) }
        assertTrue(written.await())
        val recovered = create(store = sessions)
        withContext(Dispatchers.Main) {
            assertEquals(listOf(first.id, whitespace.id, last.id), recovered.documents.map { it.id })
            assertEquals(first.id, recovered.activeId)
            assertNull(recovered.errorMessage)
            // Newly created blank tabs stay open during the current session.
            recovered.newDocument()
            assertEquals(4, recovered.documents.size)
            assertEquals("", recovered.active!!.editor.text.toString())
        }
    }

    @Test
    fun restoringOnlyEmptyTabsLeavesTheWorkspaceEmpty() = runBlocking {
        val sessions = store()
        val empty = DocumentSnapshot()
        val second = DocumentSnapshot()
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(SessionSnapshot(listOf(empty, second), second.id)) { written.complete(it) }
        assertTrue(written.await())
        val recovered = create(store = sessions, startWithDocument = false)
        withContext(Dispatchers.Main) {
            assertTrue(recovered.documents.isEmpty())
            assertEquals("", recovered.activeId)
            assertNull(recovered.active)
        }
    }

    @Test
    fun viewPreferencesAndHorizontalScrollRestoreWithCompatibleDefaults() = runBlocking {
        val file = File(context.cacheDir, "view-${UUID.randomUUID()}.json")
        val sessions = store(file)
        val document = DocumentSnapshot(text = "A document with content", horizontalScroll = 17)
        val snapshot = SessionSnapshot(listOf(document), document.id, wordWrap = false, showStatusBar = false)
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(snapshot) { written.complete(it) }
        assertTrue(written.await())
        val oldJson = org.json.JSONObject(file.readText()).apply {
            put("nextUntitled", 1000)
            getJSONArray("documents").getJSONObject(0).put("untitledNumber", 999)
            remove("wordWrap")
            remove("showStatusBar")
            remove("recentFiles")
            getJSONArray("documents").getJSONObject(0).remove("horizontalScroll")
        }
        val oldFile = File(context.cacheDir, "old-view-${UUID.randomUUID()}.json").apply { writeText(oldJson.toString()) }
        val recovered = create(store = sessions)
        val legacy = create(store = store(oldFile))
        withContext(Dispatchers.Main) {
            assertEquals(snapshot, recovered.snapshot())
            assertFalse(recovered.wordWrap)
            assertFalse(recovered.showStatusBar)
            assertEquals(17, recovered.active!!.horizontalScroll.value)
            recovered.toggleWordWrap()
            recovered.toggleStatusBar()
            assertTrue(recovered.snapshot().wordWrap)
            assertTrue(recovered.snapshot().showStatusBar)
            assertTrue(legacy.wordWrap)
            assertTrue(legacy.showStatusBar)
            assertEquals(0, legacy.active!!.horizontalScroll.value)
            assertEquals(document.id, legacy.activeId)
            assertEquals(document.text, legacy.active!!.editor.text.toString())
            assertTrue(legacy.recentFiles.isEmpty())
        }
    }

    @Test
    fun appearancePreferencesPersistAndOlderSessionsUseDefaults() = runBlocking {
        val file = File(context.cacheDir, "appearance-${UUID.randomUUID()}.json")
        val sessions = store(file)
        val vm = create(store = sessions)
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append("A draft to recover") }
            vm.changeAppTheme(AppTheme.Dark)
            vm.changeFontStyle(NoteFontStyle.SansSerif)
        }
        withTimeout(5_000) {
            while (sessions.read()?.fontStyle != NoteFontStyle.SansSerif) delay(10)
        }
        val recovered = create(store = store(file), savedState = SavedStateHandle(mapOf("settingsOpen" to true)))
        withContext(Dispatchers.Main) {
            assertEquals(AppTheme.Dark, recovered.appTheme)
            assertEquals(NoteFontStyle.SansSerif, recovered.fontStyle)
            assertEquals("A draft to recover", recovered.active!!.editor.text.toString())
            assertTrue(recovered.settingsOpen)
            recovered.showSettings(false)
            assertFalse(recovered.settingsOpen)
        }
        val oldJson = org.json.JSONObject(file.readText()).apply {
            remove("appTheme")
            remove("fontStyle")
        }
        val oldFile = File(context.cacheDir, "old-appearance-${UUID.randomUUID()}.json").apply { writeText(oldJson.toString()) }
        val legacy = create(store = store(oldFile))
        withContext(Dispatchers.Main) {
            assertEquals(AppTheme.Auto, legacy.appTheme)
            assertEquals(NoteFontStyle.Monospace, legacy.fontStyle)
        }
    }

    @Test
    fun missingUriAndInvalidFilesNeverReplaceWorkingDrafts() = runBlocking {
        val repo = FakeDocuments()
        val vm = create(repo)
        withContext(Dispatchers.Main) { vm.active!!.editor.edit { append("keep this") } }
        vm.openUri("file:missing").join()
        withContext(Dispatchers.Main) {
            assertEquals("keep this", vm.active!!.editor.text.toString())
            assertEquals(R.string.error_open, vm.errorMessage)
        }
        repo.invalid = true
        vm.openUri("file:binary").join()
        withContext(Dispatchers.Main) {
            assertEquals("keep this", vm.active!!.editor.text.toString())
            assertEquals(R.string.error_encoding, vm.errorMessage)
        }
    }

    @Test
    fun corruptRecoveryIsPreservedInsteadOfBeingOverwritten() = runBlocking {
        val file = File(context.cacheDir, "corrupt-${UUID.randomUUID()}.json").apply { writeText("corrupt original") }
        val sessions = store(file)
        val vm = create(store = sessions)
        withContext(Dispatchers.Main) { assertEquals(R.string.error_restore, vm.errorMessage) }
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(withContext(Dispatchers.Main) { vm.snapshot() }) { written.complete(it) }
        assertFalse(written.await())
        assertEquals("corrupt original", file.readText())
    }

    @Test
    fun missingOriginalAfterRestartRetainsDraftAndAllowsSaveAs() = runBlocking {
        val sessions = store()
        val document = DocumentSnapshot(
            name = "missing.txt", uri = "file:missing", text = "Recovered changes", savedText = "Original",
            diskFingerprint = "original fingerprint",
        )
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(SessionSnapshot(listOf(document), document.id)) { written.complete(it) }
        assertTrue(written.await())
        val repo = FakeDocuments()
        val vm = create(repo, sessions)
        waitFor { vm.errorMessage == R.string.error_access }
        withContext(Dispatchers.Main) {
            assertEquals("Recovered changes", vm.active!!.editor.text.toString())
            assertTrue(vm.active!!.dirty)
            vm.requestSaveAs()
            vm.pickerLaunched()
            vm.pickerResult("file:recovered")
        }
        waitFor { vm.active!!.uri == "file:recovered" }
        assertEquals("Recovered changes", repo.files.getValue("file:recovered").text)
    }

    @Test
    fun pendingPickerResultAfterRestartStillTargetsItsOriginalDocument() = runBlocking {
        val sessions = store()
        val original = DocumentSnapshot(text = "Original picker target")
        val selected = DocumentSnapshot(text = "Selected tab")
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(SessionSnapshot(listOf(original, selected), selected.id)) { written.complete(it) }
        assertTrue(written.await())
        val savedState = SavedStateHandle(mapOf(
            "pickerKind" to PickerKind.SaveAs.name,
            "pickerTargetId" to original.id,
            "pickerCloseAfter" to false,
        ))
        val repo = FakeDocuments()
        val vm = create(repo, sessions, savedState)
        withContext(Dispatchers.Main) { vm.pickerResult("file:original") }
        waitFor { vm.documents.first().uri != null }
        withContext(Dispatchers.Main) {
            assertEquals("Original picker target", repo.files.getValue("file:original").text)
            assertEquals(selected.id, vm.activeId)
            assertEquals("Selected tab", vm.active!!.editor.text.toString())
        }
    }

    @Test
    fun interruptedAtomicWriteRestoresBackupSession() = runBlocking {
        val file = File(context.cacheDir, "interrupted-${UUID.randomUUID()}.json")
        val sessions = store(file)
        val vm = create(store = sessions)
        val written = CompletableDeferred<Boolean>()
        val snapshot = withContext(Dispatchers.Main) { vm.snapshot() }
        sessions.checkpoint(snapshot) { written.complete(it) }
        assertTrue(written.await())
        file.copyTo(File(file.path + ".bak"))
        file.writeText("incomplete write")
        assertEquals(snapshot, sessions.read())
    }

    @Test
    fun freshWorkspaceAndClosedLastTabPersistWithoutDocuments() = runBlocking {
        val sessions = store()
        val vm = create(store = sessions, startWithDocument = false)
        withContext(Dispatchers.Main) {
            assertTrue(vm.documents.isEmpty())
            assertNull(vm.active)
            assertEquals("", vm.snapshot().activeId)
            vm.newDocument()
            vm.requestClose(vm.activeId)
            assertTrue(vm.documents.isEmpty())
        }
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(withContext(Dispatchers.Main) { vm.snapshot() }) { written.complete(it) }
        assertTrue(written.await())
        val recovered = create(store = sessions, startWithDocument = false)
        withContext(Dispatchers.Main) {
            assertTrue(recovered.documents.isEmpty())
            assertNull(recovered.active)
        }
    }

    @Test
    fun recentFilesAreOrderedDeduplicatedAndLimitedToFive() = runBlocking {
        val repo = FakeDocuments().apply { (1..6).forEach { put("file:$it.md", "Text $it") } }
        val vm = create(repo, startWithDocument = false)
        (1..6).forEach { vm.openUri("file:$it.md").join() }
        withContext(Dispatchers.Main) {
            assertEquals((6 downTo 2).map { "file:$it.md" }, vm.recentFiles.map { it.uri })
            vm.active!!.editor.edit { append(" unsaved edits") }
        }
        vm.openUri("file:3.md").join()
        withContext(Dispatchers.Main) {
            assertEquals(listOf(3, 6, 5, 4, 2).map { "file:$it.md" }, vm.recentFiles.map { it.uri })
            assertEquals(6, vm.documents.size)
            assertEquals("3.md", vm.recentFiles.first().name)
        }
    }

    @Test
    fun savedFilesAndSaveAsEnterHistoryButDraftsAndFailuresDoNot() = runBlocking {
        val repo = FakeDocuments()
        val vm = create(repo)
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append("Draft") }
            vm.requestSaveAs()
            vm.pickerResult(null)
            assertTrue(vm.recentFiles.isEmpty())
            repo.failWrite = true
            vm.requestSaveAs()
            vm.pickerResult("file:failed.txt")
        }
        waitFor { vm.errorMessage == R.string.error_save }
        withContext(Dispatchers.Main) {
            assertTrue(vm.recentFiles.isEmpty())
            repo.failWrite = false
            vm.requestSaveAs()
            vm.pickerResult("file:first.txt")
        }
        waitFor { vm.active?.uri == "file:first.txt" && vm.active?.saving == false }
        withContext(Dispatchers.Main) {
            vm.requestSaveAs()
            vm.pickerResult("file:second.txt")
        }
        waitFor { vm.active?.uri == "file:second.txt" && vm.active?.saving == false }
        vm.openUri("file:first.txt").join()
        withContext(Dispatchers.Main) {
            vm.save()
        }
        waitFor { vm.active?.saving == false }
        withContext(Dispatchers.Main) {
            assertEquals(listOf("file:first.txt", "file:second.txt"), vm.recentFiles.map { it.uri })
        }
    }

    @Test
    fun historyRestoresWithoutReopeningFilesOrStoringTheirContents() = runBlocking {
        val file = File(context.cacheDir, "recent-${UUID.randomUUID()}.json")
        val sessions = store(file)
        val repo = FakeDocuments().apply { put("file:notes.txt", "Private file contents") }
        val vm = create(repo, sessions, startWithDocument = false)
        vm.openUri("file:notes.txt").join()
        val snapshot = withContext(Dispatchers.Main) {
            vm.requestClose(vm.activeId)
            vm.snapshot()
        }
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(snapshot) { written.complete(it) }
        assertTrue(written.await())
        assertFalse(file.readText().contains("Private file contents"))
        val recovered = create(store = store(file), startWithDocument = false)
        withContext(Dispatchers.Main) {
            assertEquals(snapshot, recovered.snapshot())
            assertTrue(recovered.documents.isEmpty())
            assertNull(recovered.errorMessage)
        }
        recovered.openUri("file:notes.txt").join()
        withContext(Dispatchers.Main) {
            assertTrue(recovered.documents.isEmpty())
            assertEquals(snapshot.recentFiles, recovered.recentFiles)
            assertEquals(R.string.error_open, recovered.errorMessage)
        }
    }

    @Test
    fun failedOrUnsupportedOpenFromWelcomeDoesNotCreateTabsOrHistory() = runBlocking {
        val repo = FakeDocuments()
        val vm = create(repo, startWithDocument = false)
        withContext(Dispatchers.Main) {
            vm.requestOpen()
            assertTrue(vm.pickerPending)
            vm.pickerResult(null)
            assertFalse(vm.pickerPending)
        }
        vm.openUri("file:missing").join()
        repo.invalid = true
        vm.openUri("file:unsupported").join()
        withContext(Dispatchers.Main) {
            assertTrue(vm.documents.isEmpty())
            assertTrue(vm.recentFiles.isEmpty())
            assertEquals(R.string.error_encoding, vm.errorMessage)
        }
    }
}

internal class FakeDocuments : DocumentRepository {
    val files = mutableMapOf<String, LoadedDocument>()
    var failWrite = false
    var invalid = false
    var writeGate: CompletableDeferred<Unit>? = null
    val writeStarted = CompletableDeferred<Unit>()

    fun put(uri: String, text: String) {
        files[uri] = LoadedDocument(uri.substringAfter(':'), text, TextFormat(), TextCodec.fingerprint(text.toByteArray()))
    }

    override suspend fun read(uri: String): LoadedDocument {
        if (invalid) throw UnsupportedTextException()
        return files[uri] ?: throw IOException("Unavailable")
    }

    override suspend fun write(uri: String, text: String, format: TextFormat, expectedFingerprint: String?): LoadedDocument {
        writeStarted.complete(Unit)
        writeGate?.await()
        if (failWrite) throw IOException("Write failed")
        if (expectedFingerprint != null && files[uri]?.fingerprint != expectedFingerprint) throw FileChangedException()
        put(uri, text)
        return files.getValue(uri)
    }
}
