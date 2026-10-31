package com.kkps.kdesktopsuite.paint

import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class PaintWindowSessionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings get() = (context.applicationContext as PaintApplication).settings
    private val images get() = ImageRepository(context)
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(15_000) { while (!condition()) delay(20) }
    private suspend fun main(action: suspend () -> Unit) = withContext(Dispatchers.Main) { action() }

    private inner class Session(val root: File) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val models = mutableListOf<PaintViewModel>()
        val legacy = PaintRecovery(File(root, "legacy"))
        fun coordinator() = PaintWindows(File(root, "windows"), legacy)
        val windows = coordinator()
        suspend fun acquire(
            coordinator: PaintWindows = windows, id: String? = null, resume: Boolean = false,
            newWindow: Boolean = coordinator.windows.isNotEmpty(), behavior: PaintStartupBehavior = PaintStartupBehavior.ReopenLastEdit,
        ): PaintWindow = withContext(Dispatchers.Main) { coordinator.acquire(id, resume, newWindow, behavior) }
        suspend fun model(window: PaintWindow, coordinator: PaintWindows = windows): PaintViewModel = withContext(Dispatchers.Main) {
            PaintViewModel(images, window.recovery, settings, SavedStateHandle(), scope,
                windows = coordinator, window = window).also { models.add(it) }
        }.also { waitFor { !it.state.value.loading || it.state.value.dialog == PaintDialog.ReopenLastEdit } }
        suspend fun stop() {
            main { models.forEach { it.viewModelScope.cancel() } }
            scope.coroutineContext.job.cancelAndJoin()
        }
        suspend fun checkpoint(window: PaintWindow, color: Int, coordinator: PaintWindows = windows) {
            main {
                coordinator.checkpoint(window, RecoverySnapshot(Raster.blank(4, 3, color), ToolOptions(), null,
                    null, Viewport(), null, "Untitled", "image/png", null, true,
                    window.recovery.nextSequence()))
            }
        }
    }

    private suspend fun session(block: suspend Session.() -> Unit) {
        val original = settings.preferences.first()
        val root = File(context.cacheDir, "windows-test-${UUID.randomUUID()}")
        val session = Session(root)
        try {
            settings.save(original.copy(defaultCanvasWidth = 32, defaultCanvasHeight = 24))
            session.block()
        } finally {
            session.stop()
            settings.save(original)
            root.deleteRecursively()
        }
    }

    @Test fun windowsKeepIndependentEditsPreviewsUndoAndRecovery() = runBlocking { session {
        val first = acquire()
        val second = acquire()
        val a = model(first)
        val b = model(second)
        main { a.stroke(listOf(Point(10f, 10f))); b.tool(Tool.Text); b.tap(Point(2f, 2f)) }
        waitFor { a.state.value.dirty && !a.state.value.busy }
        main { b.textChanged("other window", 2, 5, true); a.undo() }
        assertFalse(a.state.value.dirty)
        assertEquals("other window", b.state.value.preview?.text)
        main { a.redo(); a.flush(); b.flush() }
        waitFor { first.hasDraft && second.hasDraft }
        val restored = coordinator()
        val restoredFirst = acquire(restored, first.id, resume = true)
        val restoredSecond = acquire(restored, second.id, resume = true)
        val ar = model(restoredFirst, restored)
        val br = model(restoredSecond, restored)
        assertEquals(BLACK, ar.state.value.raster.pixel(10, 10))
        assertEquals("other window", br.state.value.preview?.text)
        assertEquals(2, br.state.value.preview?.textSelectionStart)
        assertEquals(5, br.state.value.preview?.textSelectionEnd)
        assertFalse(ar.state.value.canUndo)
        assertNull(ar.state.value.preview)
    } }

    @Test fun closeCancelAndDiscardAffectOnlyOneWindowAndLateFlushCannotRestoreIt() = runBlocking { session {
        val first = acquire()
        val second = acquire()
        val a = model(first)
        val b = model(second)
        main { a.stroke(listOf(Point(10f, 10f))) }
        waitFor { !a.state.value.busy && a.state.value.dirty }
        main {
            a.closeImage()
            assertEquals(PaintDialog.Unsaved, a.state.value.dialog)
            a.cancelReplace()
            assertFalse(first.closed)
            a.flush()
            a.closeImage()
            a.discard()
        }
        waitFor { a.state.value.closed }
        main { a.flush(); b.flush() }
        assertTrue(first.closed)
        assertFalse(second.closed)
        assertFalse(b.state.value.closed)
        val restored = coordinator()
        acquire(restored, second.id, resume = true)
        assertFalse(restored.windows.containsKey(first.id))
        assertTrue(restored.windows.containsKey(second.id))
    } }

    @Test fun cancelledSaveAndNewerEditsKeepClosingWindowOpen() = runBlocking { session {
        val window = acquire()
        val model = model(window)
        main { model.stroke(listOf(Point(10f, 10f))) }
        waitFor { model.state.value.dirty && !model.state.value.busy }
        main {
            model.closeImage(); model.saveThenContinue(); model.saveFormat("image/png"); model.pickerResult(null)
        }
        assertFalse(model.state.value.closed)
        assertFalse(model.state.value.saving)
        val target = images.stage(Raster.blank(32, 24), "image/png", 100).file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.images", target).toString()
        main {
            model.closeImage(); model.saveThenContinue(); model.saveFormat("image/png"); model.pickerResult(uri)
            model.stroke(listOf(Point(20f, 20f)))
        }
        waitFor { !model.state.value.saving && !model.state.value.busy }
        assertTrue(model.state.value.dirty)
        assertFalse(window.closed)
        assertFalse(model.state.value.closed)
        main { model.closeImage(); model.saveThenContinue() }
        waitFor { model.state.value.closed }
        assertTrue(window.closed)
    } }

    @Test fun failedSaveKeepsClosingDraftAndRecovery() = runBlocking { session {
        val window = acquire()
        val model = model(window)
        main { model.stroke(listOf(Point(10f, 10f))) }
        waitFor { model.state.value.dirty && !model.state.value.busy }
        main {
            model.closeImage(); model.saveThenContinue(); model.saveFormat("image/png")
            model.pickerResult("content://missing.paint.provider/image.png")
        }
        waitFor { !model.state.value.saving }
        assertFalse(window.closed)
        assertTrue(model.state.value.dirty)
        assertTrue(model.state.value.message.orEmpty().startsWith("Save failed"))
        main { model.flush() }
        waitFor { window.hasDraft }
        assertTrue(requireNotNull(window.recovery.read()).dirty)
    } }

    @Test fun openingOwnedFilePreservesCurrentDraftAndSaveAsRejectsItsUri() = runBlocking { session {
        val owner = acquire()
        val source = acquire()
        val a = model(owner)
        val b = model(source)
        val uri = "content://images/already-open.png"
        main { owner.uri = uri; b.stroke(listOf(Point(10f, 10f))) }
        waitFor { b.state.value.dirty && !b.state.value.busy }
        main { b.openRecent(uri) }
        assertNull(b.state.value.dialog)
        assertTrue(b.state.value.dirty)
        assertNull(b.state.value.uri)
        main { b.save(true); b.saveFormat("image/png"); b.pickerResult(uri) }
        assertFalse(b.state.value.saving)
        assertNull(b.state.value.uri)
        assertTrue(b.state.value.message.orEmpty().contains("already open"))
        assertFalse(a.state.value.dirty)
    } }

    @Test fun concurrentOpenReservesFileAndFailedLoadReleasesReservation() = runBlocking { session {
        val first = acquire()
        val second = acquire()
        val a = model(first)
        val b = model(second)
        val target = images.stage(Raster.blank(7, 5, BLACK), "image/png", 100).file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.images", target).toString()
        main { a.openRecent(uri); b.openRecent(uri) }
        waitFor { !a.state.value.busy }
        assertEquals(uri, a.state.value.uri)
        assertNull(b.state.value.uri)
        assertEquals(32, b.state.value.raster.width)
        val missing = "content://missing.paint.provider/image.png"
        main { b.openRecent(missing) }
        waitFor { !b.state.value.busy }
        main { assertTrue(windows.reserve(first, missing)); first.reservedUris.clear() }
    } }

    @Test fun cancelledOpenPickerAndCancelledReplacementPreserveDocumentAndReleaseClaims() = runBlocking { session {
        val first = acquire()
        val second = acquire()
        val a = model(first)
        main { a.stroke(listOf(Point(10f, 10f))) }
        waitFor { a.state.value.dirty && !a.state.value.busy }
        main {
            a.open()
            assertNull(a.state.value.dialog)
            a.pickerResult(null)
            a.open()
            a.pickerResult("content://images/next.png")
            assertEquals(PaintDialog.Unsaved, a.state.value.dialog)
            a.cancelReplace()
            assertTrue(windows.reserve(second, "content://images/next.png"))
        }
        assertTrue(a.state.value.dirty)
        assertEquals(BLACK, a.state.value.raster.pixel(10, 10))
    } }

    @Test fun migratesLegacyOnceAndRestoresEveryWindowWithStableIds() = runBlocking { session {
        legacy.write(RecoverySnapshot(Raster.blank(4, 3, BLACK), ToolOptions(), null, null, Viewport(),
            null, "Legacy", "image/png", null, true))
        val first = acquire()
        assertTrue(first.hasDraft)
        assertEquals("Legacy", first.recovery.read()?.name)
        val second = acquire()
        checkpoint(second, WHITE)
        val restored = coordinator()
        acquire(restored, second.id, resume = true, behavior = PaintStartupBehavior.NewImage)
        assertEquals(setOf(first.id, second.id), restored.windows.keys)
        main { restored.close(requireNotNull(restored.windows[first.id])); restored.close(requireNotNull(restored.windows[second.id])) }
        val empty = coordinator()
        val fresh = acquire(empty, newWindow = false)
        assertFalse(fresh.hasDraft)
        assertEquals(1, empty.windows.size)
        assertNotNull(legacy.read())
    } }

    @Test fun startupAskAppliesOnceAndDecliningPreservesExplicitlyCreatedWindow() = runBlocking { session {
        val first = acquire()
        val second = acquire()
        checkpoint(first, BLACK); checkpoint(second, WHITE)
        val restored = coordinator()
        val selected = acquire(restored, newWindow = false, behavior = PaintStartupBehavior.AskEveryTime)
        val a = model(selected, restored)
        assertEquals(PaintDialog.ReopenLastEdit, a.state.value.dialog)
        val explicit = acquire(restored, newWindow = true)
        val b = model(explicit, restored)
        assertNull(b.state.value.dialog)
        main { a.resolveStartup(false) }
        waitFor { !a.state.value.loading }
        assertEquals(setOf(selected.id, explicit.id), restored.windows.keys)
        assertFalse(a.state.value.dirty)
        assertEquals(32, a.state.value.raster.width)
    } }

    @Test fun reopeningAskRestoresAllAndNewImageReplacesPreviousSession() = runBlocking { session {
        settings.update { it.copy(startupBehavior = PaintStartupBehavior.AskEveryTime) }
        val first = acquire()
        val second = acquire()
        checkpoint(first, BLACK); checkpoint(second, WHITE)
        val restored = coordinator()
        val selected = acquire(restored, newWindow = false, behavior = PaintStartupBehavior.AskEveryTime)
        val a = model(selected, restored)
        main { a.resolveStartup(true) }
        waitFor { !a.state.value.loading }
        assertEquals(setOf(first.id, second.id), restored.windows.keys)
        assertEquals(4, a.state.value.raster.width)
        val other = restored.windows.values.first { it !== selected }
        val b = model(other, restored)
        assertNull(b.state.value.dialog)
        assertFalse(b.state.value.loading)
        val fresh = coordinator()
        val blank = acquire(fresh, newWindow = false, behavior = PaintStartupBehavior.NewImage)
        assertEquals(setOf(blank.id), fresh.windows.keys)
        assertFalse(blank.hasDraft)
        assertFalse(blank.id in setOf(first.id, second.id))
    } }

    @Test fun corruptWindowDoesNotOverwriteRecoveryOrBlockHealthyWindow() = runBlocking { session {
        val first = acquire()
        val second = acquire()
        checkpoint(first, BLACK); checkpoint(second, WHITE)
        val brokenManifest = File(root, "windows/${first.id}/current.json")
        brokenManifest.writeText("broken")
        val restored = coordinator()
        val broken = acquire(restored, first.id, resume = true)
        val healthy = acquire(restored, second.id, resume = true)
        val a = model(broken, restored)
        val b = model(healthy, restored)
        main { a.flush() }
        assertTrue(a.state.value.message.orEmpty().contains("preserved"))
        assertEquals("broken", brokenManifest.readText())
        assertEquals(4, b.state.value.raster.width)
        assertEquals(WHITE, b.state.value.raster.pixel(0, 0))
        main { a.closeImage() }
        waitFor { a.state.value.closed }
        assertEquals("broken", brokenManifest.readText())
    } }

    @Test fun simultaneousSettingsUpdatesPreserveUnrelatedValuesAndRecentFiles() = runBlocking { session {
        coroutineScope {
            launch { settings.update { it.copy(appearance = "Dark") } }
            launch { settings.update { it.copy(jpegQuality = 72) } }
            launch { settings.update { it.copy(recent = (listOf("one") + it.recent).distinct()) } }
            launch { settings.update { it.copy(recent = (listOf("two") + it.recent).distinct()) } }
        }
        val preferences = settings.preferences.first()
        assertEquals("Dark", preferences.appearance)
        assertEquals(72, preferences.jpegQuality)
        assertTrue(preferences.recent.containsAll(listOf("one", "two")))
    } }

    @Test fun failedLaunchKeepsRecoveryAndAllowsRetry() = runBlocking { session {
        val window = acquire()
        checkpoint(window, BLACK)
        val failing = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { throw IllegalStateException("Test launch failure") }
        }
        main {
            repeat(2) {
                try { windows.show(failing, window); fail("Expected launch failure") }
                catch (error: IllegalStateException) { assertEquals("Test launch failure", error.message) }
                assertFalse(window.launching)
                assertFalse(window.closed)
            }
        }
        val restored = coordinator()
        val retained = acquire(restored, window.id, resume = true)
        assertEquals(BLACK, retained.recovery.read()?.raster?.pixel(0, 0))
    } }

    @Test fun failedSessionIndexWriteKeepsWindowOpen() = runBlocking { session {
        val window = acquire()
        val model = model(window)
        val pending = File(root, "windows/session.json.new")
        assertTrue(pending.mkdir())
        val blocker = File(pending, "blocker").apply { writeText("Prevent AtomicFile from opening its staging file") }
        try {
            main { model.closeImage() }
            waitFor { !model.state.value.busy }
            assertFalse(model.state.value.closed)
            assertFalse(window.closed)
            assertSame(window, windows.windows[window.id])
            assertTrue(model.state.value.message.orEmpty().isNotEmpty())
        } finally {
            assertTrue(blocker.delete())
            assertTrue(pending.delete())
        }
    } }

    @Test fun outOfOrderCheckpointCannotRevertPixelsOrFileOwnershipAndLastFocusRestores() = runBlocking { session {
        val first = acquire()
        val second = acquire()
        val older = RecoverySnapshot(Raster.blank(4, 3, WHITE), ToolOptions(), null, null, Viewport(),
            "content://images/old.png", "Old", "image/png", null, false, second.recovery.nextSequence())
        val newer = older.copy(raster = Raster.blank(4, 3, BLACK), uri = "content://images/new.png",
            name = "New", sequence = second.recovery.nextSequence())
        main {
            windows.checkpoint(second, newer)
            second.uri = "content://images/not-checkpointed.png"
            windows.checkpoint(second, older)
            windows.focus(second)
        }
        val restored = coordinator()
        val selected = acquire(restored, newWindow = false)
        assertEquals(second.id, selected.id)
        assertEquals(newer.uri, selected.uri)
        assertEquals(BLACK, selected.recovery.read()?.raster?.pixel(0, 0))
        assertTrue(first.id in restored.windows)
    } }

    @Test fun anotherWindowCanOpenWhileAnImageCheckpointIsBlocked() = runBlocking { session {
        val original = acquire()
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val gate = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val blocked = PaintWindow(original.id, PaintRecovery(File(root, "windows/${original.id}"), dispatcher))
        try {
            main { windows.windows[original.id] = blocked }
            executor.submit { entered.countDown(); gate.await() }
            entered.await()
            val checkpoint = scope.launch(Dispatchers.Main) {
                windows.checkpoint(blocked, RecoverySnapshot(Raster.blank(4, 3), ToolOptions(), null, null,
                    Viewport(), null, "Untitled", "image/png", null, false, blocked.recovery.nextSequence()))
            }
            main { yield() }
            val other = withTimeout(5_000) { acquire() }
            assertNotEquals(blocked.id, other.id)
            gate.countDown()
            checkpoint.join()
            assertTrue(blocked.hasDraft)
        } finally {
            gate.countDown()
            dispatcher.close()
        }
    } }

    @Test fun recoveredTasksWaitForTheSingleStartupDecision() = runBlocking { session {
        val first = acquire()
        val second = acquire()
        checkpoint(first, BLACK); checkpoint(second, WHITE)
        val restored = coordinator()
        val selected = acquire(restored, newWindow = false, behavior = PaintStartupBehavior.AskEveryTime)
        val owner = model(selected, restored)
        val otherId = restored.windows.keys.first { it != selected.id }
        val waiting = async(Dispatchers.Main) { restored.acquire(otherId, true, false, PaintStartupBehavior.AskEveryTime) }
        main { yield() }
        assertFalse(waiting.isCompleted)
        main { owner.resolveStartup(false) }
        val discarded = withTimeout(5_000) { waiting.await() }
        waitFor { !owner.state.value.loading }
        assertTrue(discarded.closed)
        assertFalse(otherId in restored.windows)
        assertEquals(setOf(selected.id), restored.windows.keys)
    } }
}
