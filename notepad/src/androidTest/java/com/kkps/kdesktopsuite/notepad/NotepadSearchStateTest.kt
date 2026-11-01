@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NotepadSearchStateTest {
    private val holders = mutableListOf<ViewModelStore>()
    private val workspaces = mutableListOf<NotepadWorkspace>()

    private suspend fun create(savedState: SavedStateHandle = SavedStateHandle(), workspace: NotepadWorkspace? = null,
        newWindow: Boolean = false): NotepadViewModel {
        val vm = withContext(Dispatchers.Main) {
            val shared = workspace ?: NotepadWorkspace(SessionStore(File(
                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "search-${UUID.randomUUID()}.json")))
                .also { workspaces.add(it) }
            NotepadViewModel(FakeDocuments(), shared, savedState, newWindow).also {
                holders.add(ViewModelStore().apply { put("notepad", it) })
            }
        }
        await { !vm.loading }
        withContext(Dispatchers.Main) { vm.newDocument() }
        return vm
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(10_000) {
        while (!withContext(Dispatchers.Main) { Snapshot.sendApplyNotifications(); condition() }) delay(10)
    }

    private suspend fun find(vm: NotepadViewModel, text: String, query: String = "cat", replacement: String = "dog") {
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { replace(0, length, text); selection = TextRange(0) }
            vm.search.query.edit { replace(0, length, query) }
            vm.search.replacement.edit { replace(0, length, replacement) }
            vm.search.open(replace = true)
        }
        await { vm.search.results != null && (vm.search.results!!.matches.isEmpty() || vm.search.currentIndex == 0) }
    }

    @After
    fun close() = runBlocking {
        withContext(Dispatchers.Main) { holders.forEach { it.clear() }; workspaces.forEach { it.close() } }
    }

    @Test
    fun replaceAllUsesOriginalMatchesAndOneIndependentUndoStep() = runBlocking {
        val vm = create()
        find(vm, "cat cat CAT", replacement = "catcat")
        withContext(Dispatchers.Main) {
            val doc = vm.active!!
            vm.search.replaceAll()
            assertEquals("catcat catcat catcat", doc.editor.text.toString())
            assertEquals(TextRange(20), doc.editor.selection)
            assertEquals(3, vm.search.replacementCount)
            doc.editor.undoState.undo()
            assertEquals("cat cat CAT", doc.editor.text.toString())
            doc.editor.undoState.redo()
            assertEquals("catcat catcat catcat", doc.editor.text.toString())
            vm.newDocument()
            assertFalse(vm.active!!.editor.undoState.canUndo)
        }
    }

    @Test
    fun deletionAndIdenticalReplacementDoNotCreateSpuriousUndoOrDirtyChanges() = runBlocking {
        val vm = create()
        find(vm, "cat cat", replacement = "cat")
        withContext(Dispatchers.Main) {
            val doc = vm.active!!
            doc.savedText = doc.editor.text.toString()
            doc.editor.undoState.clearHistory()
            vm.search.replaceAll()
            assertEquals("cat cat", doc.editor.text.toString())
            assertFalse(doc.dirty)
            assertFalse(doc.editor.undoState.canUndo)
            assertEquals(TextRange(7), doc.editor.selection)
            vm.search.replacement.edit { replace(0, length, "") }
            vm.search.replaceAll()
            assertEquals(" ", doc.editor.text.toString())
            assertEquals(TextRange(1), doc.editor.selection)
            doc.editor.undoState.undo()
            assertEquals("cat cat", doc.editor.text.toString())
            doc.editor.undoState.redo()
            assertEquals(" ", doc.editor.text.toString())
        }
    }

    @Test
    fun replaceRequiresExactSelectionAndAdvancesFromInsertedEnd() = runBlocking {
        val vm = create()
        find(vm, "cat cat", replacement = "catcat")
        withContext(Dispatchers.Main) {
            val doc = vm.active!!
            doc.editor.edit { selection = TextRange(1) }
            vm.search.replace()
            assertEquals("cat cat", doc.editor.text.toString())
            assertEquals(TextRange(4, 7), doc.editor.selection)
            vm.search.replace()
            assertEquals("cat catcat", doc.editor.text.toString())
        }
        await { vm.search.results != null && vm.search.currentIndex == 0 }
        withContext(Dispatchers.Main) { assertEquals(TextRange(0, 3), vm.active!!.editor.selection) }
    }

    @Test
    fun readOnlyAndStaleScansCannotMutateDocuments() = runBlocking {
        val vm = create()
        find(vm, "cat cat")
        withContext(Dispatchers.Main) {
            val doc = vm.active!!
            doc.readOnly = true
            vm.search.replace()
            vm.search.replaceAll()
            assertEquals("cat cat", doc.editor.text.toString())
            vm.search.navigate()
            assertEquals(TextRange(4, 7), doc.editor.selection)
            doc.readOnly = false
            doc.editor.edit { replace(0, length, "new text"); selection = TextRange(2) }
            assertNull(vm.search.results)
            vm.search.replaceAll()
            vm.search.query.edit { replace(0, length, "new") }
            vm.newDocument()
            vm.active!!.editor.edit { append("untouched") }
        }
        await { vm.search.results?.input?.text == "untouched" }
        withContext(Dispatchers.Main) {
            assertEquals("new text", vm.documents.first().editor.text.toString())
            assertEquals("untouched", vm.active!!.editor.text.toString())
            assertTrue(vm.search.results!!.matches.isEmpty())
        }
    }

    @Test
    fun queuedReplacementRechecksReadOnlyBeforeApplyingTheScan() = runBlocking {
        val vm = create()
        find(vm, "cat cat")
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append(" cat") }
            assertNull(vm.search.results)
            vm.search.replaceAll()
            vm.active!!.readOnly = true
        }
        await { vm.search.results?.matches?.size == 3 }
        withContext(Dispatchers.Main) { assertEquals("cat cat cat", vm.active!!.editor.text.toString()) }
    }

    @Test
    fun supersededLargeScansOnlyPublishCurrentTextAndOptions() = runBlocking {
        val vm = create()
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { append("a".repeat(300_000)) }
            vm.search.query.edit { append("a") }
            vm.search.open()
        }
        withContext(Dispatchers.Main) { Snapshot.sendApplyNotifications() }
        withContext(Dispatchers.Main) {
            vm.active!!.editor.edit { replace(0, length, "Aa") }
            vm.search.matchCase = true
            vm.search.query.edit { replace(0, length, "A") }
        }
        await { vm.search.results?.input?.text == "Aa" }
        withContext(Dispatchers.Main) {
            assertTrue(vm.search.results!!.input.matchCase)
            assertEquals(listOf(LiteralMatch(0, 1)), vm.search.results!!.matches)
        }
    }

    @Test
    fun tabSwitchAndTransferPreserveDestinationSelectionAndWindowSearchState() = runBlocking {
        val first = create()
        find(first, "cat cat")
        val moving = first.active!!
        withContext(Dispatchers.Main) {
            first.newDocument()
            first.active!!.editor.edit { append("cat cat"); selection = TextRange(5) }
            first.active!!.scroll.scrollTo(36)
            first.active!!.horizontalScroll.scrollTo(24)
        }
        await { first.search.results?.input?.document === first.active }
        withContext(Dispatchers.Main) {
            assertEquals(TextRange(5), first.active!!.editor.selection)
            assertEquals(36, first.active!!.scroll.value)
            assertEquals(24, first.active!!.horizontalScroll.value)
        }
        val second = create(workspace = first.workspace, newWindow = true)
        find(second, "dog", query = "dog")
        withContext(Dispatchers.Main) {
            moving.editor.edit { selection = TextRange(1, 2) }
            moving.scroll.scrollTo(48)
            val token = first.workspace.beginTransfer(first.window, moving.id)!!.token
            assertTrue(first.workspace.move(token, second.window, 0))
        }
        await { second.search.results?.input?.document === moving }
        withContext(Dispatchers.Main) {
            assertEquals("dog", second.search.query.text.toString())
            assertEquals("cat", first.search.query.text.toString())
            assertEquals(TextRange(1, 2), moving.editor.selection)
            assertEquals(48, moving.scroll.value)
            second.search.matchCase = true
            second.search.close()
            assertFalse(first.search.matchCase)
            assertTrue(first.search.visible)
        }
    }

    @Test
    fun savedUiStateRestoresWithoutEnteringDocumentRecovery() = runBlocking {
        val saved = SavedStateHandle()
        val vm = create(saved)
        find(vm, "cat", replacement = "dog")
        withContext(Dispatchers.Main) { vm.search.matchCase = true }
        await { saved.get<Boolean>("searchMatchCase") == true && saved.get<String>("searchReplacement") == "dog" }
        val restored = create(SavedStateHandle(mapOf(
            "searchQuery" to saved.get<String>("searchQuery"),
            "searchReplacement" to saved.get<String>("searchReplacement"),
            "searchVisible" to true, "searchExpanded" to true, "searchMatchCase" to true,
        )), workspace = vm.workspace)
        withContext(Dispatchers.Main) {
            assertEquals("cat", restored.search.query.text.toString())
            assertEquals("dog", restored.search.replacement.text.toString())
            assertTrue(restored.search.matchCase)
            assertTrue(restored.search.visible)
            assertTrue(restored.search.expanded)
            assertFalse(vm.snapshot().toString().contains("searchQuery"))
        }
    }

    @Test
    fun finalCloseHidesPanelAndNewWindowsStartWithoutSearchHistory() = runBlocking {
        val vm = create()
        find(vm, "cat")
        withContext(Dispatchers.Main) {
            vm.active!!.savedText = vm.active!!.editor.text.toString()
            vm.requestClose(vm.activeId)
        }
        await { !vm.search.visible }
        val second = create(workspace = vm.workspace, newWindow = true)
        withContext(Dispatchers.Main) {
            assertEquals("", second.search.query.text.toString())
            assertFalse(second.search.visible)
            assertFalse(second.search.matchCase)
        }
    }
}
