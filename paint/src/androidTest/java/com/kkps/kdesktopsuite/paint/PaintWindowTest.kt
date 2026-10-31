package com.kkps.kdesktopsuite.paint

import android.content.Intent
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import androidx.lifecycle.viewModelScope
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class PaintWindowTest {
    @get:Rule val compose = createAndroidComposeRule<PaintActivity>()
    private val initialHost by lazy { compose.activity }
    private var hostWindow: PaintWindow? = null
    private val host get() = hostWindow?.activity ?: initialHost
    private val workspace get() = (initialHost.application as PaintApplication).windows

    @After fun finishHost() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (!host.isDestroyed) host.finishAndRemoveTask()
        }
    }

    private fun ready(activity: PaintActivity = host): PaintViewModel {
        compose.waitUntil(15_000) { runCatching { !activity.paintModel.state.value.loading }.getOrDefault(false) }
        if (hostWindow == null && activity === initialHost) {
            compose.runOnIdle { hostWindow = workspace.windows.values.first { it.activity === activity } }
        }
        return activity.paintModel
    }

    private fun newWindow(action: () -> Unit): PaintWindow {
        var before = emptySet<String>()
        compose.runOnIdle { before = workspace.windows.keys.toSet(); action() }
        compose.waitUntil(15_000) { workspace.windows.values.any { it.id !in before && it.activity != null } }
        return workspace.windows.values.first { it.id !in before && it.activity != null }.also { ready(requireNotNull(it.activity)) }
    }

    private fun close(window: PaintWindow) {
        val model = requireNotNull(window.activity).paintModel
        compose.runOnIdle {
            model.closeImage()
            if (model.state.value.dialog == PaintDialog.Unsaved) model.discard()
        }
        try { compose.waitUntil(15_000) { window.closed && window.activity == null } }
        catch (error: ComposeTimeoutException) {
            val state = model.state.value
            throw AssertionError("Close ${window.id}: closed=${window.closed}, attached=${window.activity != null}, " +
                "modelClosed=${state.closed}, loading=${state.loading}, busy=${state.busy}, saving=${state.saving}, " +
                "dialog=${state.dialog}, message=${state.message}, scopeActive=${model.viewModelScope.isActive}, " +
                "activityDestroyed=${window.activity?.isDestroyed}, activityFinishing=${window.activity?.isFinishing}, " +
                "currentModel=${window.activity?.paintModel === model}", error)
        }
    }

    @Test fun menuAndKeyboardCreateIndependentTasksAndCloseOnlyTheirWindow() {
        val root = ready()
        val created = mutableListOf<PaintWindow>()
        try {
            val before = workspace.windows.keys.toSet()
            compose.onNodeWithText("File").performClick()
            compose.onNodeWithText("New window").performClick()
            compose.waitUntil(15_000) { workspace.windows.values.any { it.id !in before && it.activity != null } }
            val first = workspace.windows.values.first { it.id !in before && it.activity != null }.also { created.add(it) }
            val activity = requireNotNull(first.activity)
            val a = ready(activity)
            assertNotEquals(host.taskId, activity.taskId)
            compose.runOnIdle { a.tool(Tool.Text); a.tap(Point(2f, 2f)); a.textChanged("pending text", 3, 7, true) }
            val second = newWindow {
                assertTrue(activity.onKeyShortcut(AndroidKeyEvent.KEYCODE_N, AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN,
                    AndroidKeyEvent.KEYCODE_N, 0, AndroidKeyEvent.META_CTRL_ON or AndroidKeyEvent.META_SHIFT_ON)))
            }.also { created.add(it) }
            val b = ready(requireNotNull(second.activity))
            assertNotEquals(activity.taskId, second.activity!!.taskId)
            assertNull(b.state.value.preview)
            assertEquals("pending text", a.state.value.preview?.text)
            assertFalse(b.state.value.dirty)
            compose.runOnIdle {
                assertTrue(second.activity!!.onKeyShortcut(AndroidKeyEvent.KEYCODE_W, AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN,
                    AndroidKeyEvent.KEYCODE_W, 0, AndroidKeyEvent.META_CTRL_ON)))
            }
            compose.waitUntil(15_000) { second.closed && second.activity == null }
            assertFalse(first.closed)
            assertFalse(root.state.value.closed)
            assertEquals("pending text", a.state.value.preview?.text)
        } finally {
            created.filter { !it.closed && it.activity != null }.forEach { close(it) }
            compose.runOnIdle { host.bringWindowToFront() }
        }
    }

    @Test fun recreationRetainsWindowIdentityPixelsAndUndoAndDuplicateLaunchReusesOwner() {
        val root = ready()
        val window = newWindow(root::newWindow)
        try {
            val original = requireNotNull(window.activity)
            val model = ready(original)
            compose.runOnIdle { model.stroke(listOf(Point(10f, 10f))) }
            compose.waitUntil(15_000) { model.state.value.dirty && !model.state.value.busy }
            compose.runOnIdle { original.recreate() }
            compose.waitUntil(15_000) { window.activity != null && window.activity !== original && original.isDestroyed }
            val recreated = ready(requireNotNull(window.activity))
            assertSame(model, recreated)
            assertEquals(BLACK, recreated.state.value.raster.pixel(10, 10))
            compose.runOnIdle { recreated.undo() }
            assertFalse(recreated.state.value.dirty)
            val count = workspace.windows.size
            compose.runOnIdle { PaintWindows.launch(host, window.id) }
            compose.waitForIdle()
            assertEquals(count, workspace.windows.size)
            assertSame(recreated, window.activity!!.paintModel)
        } finally {
            close(window)
            compose.runOnIdle { host.bringWindowToFront() }
        }
    }

    @Test fun systemNewInstanceStartsBlankAndOwnedFileBringsExistingTaskForward() {
        val root = ready()
        val first = newWindow(root::newWindow)
        var second: PaintWindow? = null
        try {
            val owner = requireNotNull(first.activity)
            val a = ready(owner)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            // System UI launches a new task without our private new-window extra.
            second = newWindow {
                context.startActivity(Intent(context, PaintActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                })
            }
            val source = requireNotNull(second.activity)
            compose.waitUntil(15_000) { hostWindow?.activity != null }
            val b = ready(source)
            assertFalse(b.state.value.dirty)
            compose.runOnIdle {
                first.uri = "content://images/owned.png"
                b.tool(Tool.Text); b.tap(Point(2f, 2f)); b.textChanged("keep me", 0, 0, true)
                b.openRecent("content://images/owned.png")
            }
            compose.waitUntil(15_000) { owner.hasWindowFocus() }
            assertNull(b.state.value.dialog)
            assertEquals("keep me", b.state.value.preview?.text)
            assertFalse(a.state.value.dirty)
        } finally {
            second?.takeIf { !it.closed && it.activity != null }?.let { close(it) }
            close(first)
            compose.runOnIdle { host.bringWindowToFront() }
        }
    }

    @Test fun systemTaskRemovalRetainsDraftWithoutReopeningItWhenAnotherWindowIsCreated() {
        val root = ready()
        val retained = newWindow(root::newWindow)
        var fresh: PaintWindow? = null
        try {
            val activity = requireNotNull(retained.activity)
            val model = ready(activity)
            compose.runOnIdle { model.stroke(listOf(Point(10f, 10f))) }
            compose.waitUntil(15_000) { model.state.value.dirty && !model.state.value.busy }
            compose.runOnIdle {
                assertTrue(workspace.reserve(retained, "content://images/interrupted-open.png"))
                model.flush(); activity.finishAndRemoveTask()
            }
            compose.waitUntil(15_000) { retained.activity == null && retained.hasDraft }
            assertTrue(retained.reservedUris.isEmpty())
            compose.waitUntil(15_000) { runBlocking { retained.recovery.read()?.raster?.pixel(10, 10) == BLACK } }
            assertFalse(retained.closed)
            fresh = newWindow(root::newWindow)
            assertNull(retained.activity)
            compose.runOnIdle { workspace.show(host, retained) }
            compose.waitUntil(15_000) { retained.activity != null }
            val restored = ready(requireNotNull(retained.activity))
            assertNotSame(model, restored)
            assertEquals(BLACK, restored.state.value.raster.pixel(10, 10))
            assertTrue(restored.state.value.dirty)
            assertFalse(restored.state.value.canUndo)
        } finally {
            fresh?.takeIf { !it.closed && it.activity != null }?.let { close(it) }
            if (retained.activity != null) close(retained)
            else runBlocking { withContext(Dispatchers.Main) { workspace.close(retained) } }
            compose.runOnIdle { host.bringWindowToFront() }
        }
    }
}
