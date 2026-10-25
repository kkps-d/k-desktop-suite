@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class TabWorkspaceTest {
    private val workspaces = mutableListOf<NotepadWorkspace>()
    private fun store() = SessionStore(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "windows-${UUID.randomUUID()}.json"))

    private suspend fun workspace(store: SessionStore = store()): NotepadWorkspace {
        val workspace = withContext(Dispatchers.Main) { NotepadWorkspace(store).also { workspaces.add(it) } }
        workspace.ready.await()
        withContext(Dispatchers.Main) {
            if (workspace.windows.first().documents.isEmpty()) workspace.addBlank(workspace.windows.first())
        }
        return workspace
    }

    @After
    fun close() = runBlocking { withContext(Dispatchers.Main) { workspaces.forEach { it.close() } } }

    @Test
    fun tabDragUsesPlatformGlobalFlagsAndOnlyAnOpaqueToken() = runBlocking {
        val workspace = workspace()
        withContext(Dispatchers.Main) {
            val notepad = NotepadViewModel(FakeDocuments(), workspace, SavedStateHandle())
            val data = TabWindows.dragData(null, notepad, notepad.activeId)!!
            val expected = if (android.os.Build.VERSION.SDK_INT >= 35) android.view.View.DRAG_FLAG_GLOBAL_SAME_APPLICATION
                else android.view.View.DRAG_FLAG_GLOBAL
            assertEquals(expected, data.flags)
            assertEquals(workspace.dragging!!.token, data.clipData.getItemAt(0).text.toString())
            assertNull(data.localState)
            workspace.cancelTransfer(workspace.dragging!!.token)
        }
    }

    @Test
    fun transferKeepsTheLiveEditorAndIndependentUndoHistory() = runBlocking {
        val workspace = workspace()
        withContext(Dispatchers.Main) {
            val source = workspace.acquireWindow(null, false)
            val doc = source.documents.single()
            doc.editor.edit { append("moving draft"); selection = TextRange(2, 7) }
            doc.scroll.scrollTo(32)
            doc.horizontalScroll.scrollTo(24)
            workspace.addBlank(source)
            val destination = workspace.acquireWindow(null, true)
            source.errorDocumentId = doc.id
            source.errorMessage = R.string.error_access
            val token = workspace.beginTransfer(source, doc.id)!!.token
            assertTrue(workspace.move(token, destination, 0))
            assertSame(doc, destination.documents.first())
            assertEquals(TextRange(2, 7), doc.editor.selection)
            assertEquals(32, doc.scroll.value)
            assertEquals(24, doc.horizontalScroll.value)
            assertEquals(doc.id, destination.activeId)
            assertEquals(R.string.error_access, destination.errorMessage)
            assertEquals(doc.id, destination.errorDocumentId)
            assertNull(source.errorDocumentId)
            assertFalse(workspace.move(token, source, 0))
            doc.editor.undoState.undo()
            assertEquals("", doc.editor.text.toString())
            doc.editor.undoState.redo()
            assertEquals("moving draft", doc.editor.text.toString())
            assertEquals(1, workspace.windows.sumOf { it.documents.count { candidate -> candidate.id == doc.id } })
        }
    }

    @Test
    fun endingAndCancellingDragsNeverChangeOrderOrSelection() = runBlocking {
        val workspace = workspace()
        withContext(Dispatchers.Main) {
            val window = workspace.acquireWindow(null, false)
            workspace.addBlank(window)
            workspace.addBlank(window)
            val before = window.documents.toList()
            val active = window.activeId
            val transfer = workspace.beginTransfer(window, before.first().id)!!
            assertEquals(listOf(before[1], before[2], before[0]), previewTabOrder(before, before[0], 2))
            workspace.endDrag()
            assertEquals(before, window.documents)
            assertEquals(active, window.activeId)
            // SystemUI can acknowledge the launch after the drag-ended event.
            assertNotNull(workspace.transfer(transfer.token))
            workspace.cancelTransfer(transfer.token)
            assertFalse(workspace.move(transfer.token, window, 2))
            assertTrue(workspace.reorder(window, before.first().id, 2))
            assertEquals(listOf(before[1], before[2], before[0]), window.documents)
            assertTrue(workspace.reorder(window, before.first().id, 0))
            assertEquals(before, window.documents)
        }
    }

    @Test
    fun movingLastTabClosesSourceButCannotDetachToNewWindow() = runBlocking {
        val workspace = workspace()
        withContext(Dispatchers.Main) {
            val source = workspace.acquireWindow(null, false)
            val doc = source.documents.single()
            val destination = workspace.acquireWindow(null, true)
            val token = workspace.beginTransfer(source, doc.id)!!.token
            assertFalse(workspace.move(token, destination, 0, newWindow = true))
            assertFalse(source.closed)
            assertTrue(workspace.move(token, destination, 0))
            assertTrue(source.closed)
            assertTrue(source.documents.isEmpty())
            assertSame(doc, destination.documents.first())
        }
    }

    @Test
    fun busySourceDestinationAndClosedDestinationRejectTransfers() = runBlocking {
        val workspace = workspace()
        withContext(Dispatchers.Main) {
            val source = workspace.acquireWindow(null, false)
            val doc = source.documents.single()
            val destination = workspace.acquireWindow(null, true)
            doc.saving = true
            assertNull(workspace.beginTransfer(source, doc.id))
            doc.saving = false
            source.blocked = { true }
            assertNull(workspace.beginTransfer(source, doc.id))
            source.blocked = { false }
            val token = workspace.beginTransfer(source, doc.id)!!.token
            destination.blocked = { true }
            assertFalse(workspace.move(token, destination, 0))
            destination.blocked = { false }
            workspace.abandonWindow(destination)
            assertFalse(workspace.move(token, destination, 0))
            assertSame(doc, source.documents.single())
            assertFalse(workspace.move("invalid", source, 0))
        }
    }

    @Test
    fun activeSourceSelectsRightNeighborAndInactiveSourceKeepsSelection() = runBlocking {
        val workspace = workspace()
        withContext(Dispatchers.Main) {
            val source = workspace.acquireWindow(null, false)
            workspace.addBlank(source)
            workspace.addBlank(source)
            val original = source.documents.toList()
            source.select(original[1].id)
            val destination = workspace.acquireWindow(null, true)
            assertTrue(workspace.move(workspace.beginTransfer(source, original[1].id)!!.token, destination, 0))
            assertEquals(original[2].id, source.activeId)
            assertTrue(workspace.move(workspace.beginTransfer(source, original[0].id)!!.token, destination, 0))
            assertEquals(original[2].id, source.activeId)
        }
    }

    @Test
    fun pickerAndSaveAsChecksApplyAcrossWindows() = runBlocking {
        val workspace = workspace()
        val repository = FakeDocuments().apply { put("file:shared", "original") }
        val first = withContext(Dispatchers.Main) { NotepadViewModel(repository, workspace, SavedStateHandle()) }
        first.openUri("file:shared").join()
        val second = withContext(Dispatchers.Main) { NotepadViewModel(repository, workspace, SavedStateHandle(), newWindow = true) }
        second.openUri("file:shared").join()
        withContext(Dispatchers.Main) {
            assertTrue(second.documents.isEmpty())
            assertEquals(first.recentFiles, second.recentFiles)
            second.newDocument()
            assertEquals(1, workspace.windows.sumOf { it.documents.count { doc -> doc.uri == "file:shared" } })
            assertEquals(first.documents.single().id, first.activeId)
            second.requestSaveAs()
            assertFalse(second.canTransfer(second.activeId))
            second.pickerResult("file:shared")
        }
        withContext(Dispatchers.Main) {
            assertEquals(R.string.error_already_open, second.errorMessage)
            assertNull(second.active!!.uri)
        }
    }

    @Test
    fun inFlightSaveAsReservesItsUriAcrossWindows() = runBlocking {
        val workspace = workspace()
        val repository = FakeDocuments().apply { writeGate = CompletableDeferred() }
        val first = withContext(Dispatchers.Main) { NotepadViewModel(repository, workspace, SavedStateHandle()) }
        val second = withContext(Dispatchers.Main) { NotepadViewModel(repository, workspace, SavedStateHandle(), newWindow = true) }
        withContext(Dispatchers.Main) {
            second.newDocument()
            first.requestSaveAs()
            first.pickerResult("file:new")
        }
        repository.writeStarted.await()
        withContext(Dispatchers.Main) {
            second.requestSaveAs()
            second.pickerResult("file:new")
            assertEquals(R.string.error_already_open, second.errorMessage)
            assertNull(second.active!!.uri)
            repository.writeGate!!.complete(Unit)
            Unit
        }
    }

    @Test
    fun aggregateRecoveryMergesWindowsOnceAndRetainsOrderAndPreferences() = runBlocking {
        val sessions = store()
        val workspace = workspace(sessions)
        val expected = withContext(Dispatchers.Main) {
            val first = workspace.acquireWindow(null, false)
            first.documents.single().editor.edit { append("first") }
            workspace.addBlank(first)
            first.documents.last().editor.edit { append("second") }
            workspace.reorder(first, first.documents.last().id, 0)
            val second = workspace.acquireWindow(null, true)
            workspace.addBlank(second)
            second.documents.single().editor.edit { append("third") }
            workspace.focus(second)
            workspace.appTheme = AppTheme.Dark
            workspace.zoom = 140
            assertSame(first, workspace.acquireWindow(first.id, false))
            workspace.snapshot()
        }
        val written = CompletableDeferred<Boolean>()
        sessions.checkpoint(expected) { written.complete(it) }
        assertTrue(written.await())
        withContext(Dispatchers.Main) { workspace.close() }
        val recovered = workspace(sessions)
        withContext(Dispatchers.Main) {
            val merged = recovered.acquireWindow("old-window-id", false)
            assertSame(merged, recovered.acquireWindow("another-old-window-id", false))
            assertEquals(1, recovered.windows.size)
            assertEquals(expected, recovered.snapshot())
        }
    }
}
