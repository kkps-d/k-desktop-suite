@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.explorer

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kkps.kdesktopsuite.ui.DesktopCaption
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExplorerShellTest {
    @get:Rule val compose = createAndroidComposeRule<ExplorerActivity>()
    private val model get() = compose.activity.explorer
    private fun tab(id: String) = compose.onNodeWithTag("document-tab-$id")
    private fun tabNumber(number: Int) = tab(model.tabs.first { it.number == number }.id)
    private fun activities(): List<ExplorerActivity> {
        var result = emptyList<ExplorerActivity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = Stage.values().filter { it != Stage.DESTROYED }.flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
            }.filterIsInstance<ExplorerActivity>()
        }
        return result
    }
    @After fun closeExtraWindows() {
        val host = compose.activity
        val extra = activities().filter { it !== host }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { extra.forEach { it.finishAndRemoveTask() } }
    }
    private fun shortcut(activity: ExplorerActivity, key: Int, shift: Boolean = false) {
        assertTrue(activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0,
            KeyEvent.META_CTRL_ON or if (shift) KeyEvent.META_SHIFT_ON else 0)))
    }
    private fun newWindow(action: () -> Unit): ExplorerActivity {
        val before = activities().toSet()
        compose.runOnIdle(action)
        compose.waitUntil(10_000) { activities().any { it !in before } }
        return activities().first { it !in before }
    }
    private fun mouseDrag(from: Offset, to: Offset, beforeRelease: () -> Unit = {}) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val time = SystemClock.uptimeMillis()
        fun send(action: Int, position: Offset) {
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
            val coordinates = MotionEvent.PointerCoords().apply { x = position.x; y = position.y; pressure = 1f }
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, 1,
                arrayOf(properties), arrayOf(coordinates), 0, if (action == MotionEvent.ACTION_UP) 0 else MotionEvent.BUTTON_PRIMARY,
                1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, from)
        try {
            repeat(20) { step -> send(MotionEvent.ACTION_MOVE, from + (to - from) * ((step + 1) / 20f)); SystemClock.sleep(24) }
            beforeRelease()
        } finally { send(MotionEvent.ACTION_UP, to) }
    }
    private fun screenPoint(point: Offset): Offset {
        val origin = IntArray(2)
        compose.runOnIdle { compose.activity.window.decorView.getLocationOnScreen(origin) }
        return point + Offset(origin[0].toFloat(), origin[1].toFloat())
    }

    @Test fun visibleControlsCloseNeighborsAndFinalTabReturnsToWelcome() {
        tabNumber(1).assertIsSelected()
        repeat(4) { compose.onNodeWithContentDescription("New document").performClick() }
        tabNumber(5).assertIsSelected().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close New tab 4").performClick()
        tabNumber(5).assertIsSelected()
        compose.onNodeWithContentDescription("Close New tab 5").performClick()
        tabNumber(3).assertIsSelected()
        tabNumber(2).performClick().assertIsSelected()
        compose.runOnIdle { model.tabs.toList().forEach { model.close(it.id) } }
        compose.onNodeWithTag("welcome-tab").assertIsDisplayed()
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithContentDescription("New document").performClick()
        tabNumber(6).assertIsSelected()
    }

    @Test fun plusStaysAdjacentWithNotepadDimensionsAndCaptionFallbackPreservesTabs() {
        var caption by mutableStateOf(DesktopCaption(48f, listOf(Rect(0f, 0f, 80f, 48f), Rect(680f, 0f, 840f, 48f))))
        var rtl by mutableStateOf(false)
        compose.runOnIdle {
            compose.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    ExplorerTheme {
                        Box(Modifier.width(840.dp)) { ExplorerScreen(model, {}, model::close, {}, caption, WindowInsets(0)) }
                    }
                }
            }
        }
        val first = tabNumber(1).fetchSemanticsNode().boundsInWindow
        val plus = compose.onNodeWithContentDescription("New document").fetchSemanticsNode().boundsInWindow
        assertEquals(80f, first.left, 1f)
        assertEquals(8f, first.top, 1f)
        assertEquals(240f, first.width, 1f)
        assertEquals(40f, first.height, 1f)
        assertEquals(first.right, plus.left, 1f)
        assertEquals(48f, plus.width, 1f)
        compose.onNodeWithContentDescription("New document").performClick()
        val second = tabNumber(2).fetchSemanticsNode().boundsInWindow
        assertEquals(16f, first.right - second.left, 1f)
        compose.runOnIdle { rtl = true }
        val reversed = tabNumber(1).fetchSemanticsNode().boundsInWindow
        assertEquals(680f, reversed.right, 1f)
        compose.runOnIdle { caption = caption.copy(obstructions = emptyList()) }
        assertTrue(tabNumber(2).fetchSemanticsNode().boundsInWindow.top >= 48f)
        tabNumber(2).assertIsSelected()
    }

    @Test fun compactSelectorProvidesIndependentCloseAndMoveActionsAtLargeFonts() {
        compose.runOnIdle {
            repeat(19) { model.newTab() }
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    ExplorerTheme { Box(Modifier.width(160.dp)) { ExplorerScreen(model, {}, model::close, {}, DesktopCaption(), WindowInsets(0)) } }
                }
            }
        }
        compose.onNodeWithTag("document-switcher").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Close New tab 1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(19, model.tabs.size); assertEquals(20, model.tabs.last().number) }
        compose.onNodeWithTag("document-switcher").performClick()
        compose.onNodeWithText("New tab 2").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(model.tabs.first().id, model.activeId) }
    }

    @Test fun arrowsWrapFocusWithoutSelectingAndActivationRevealsTheTab() {
        lateinit var inputMode: InputModeManager
        compose.runOnIdle {
            compose.activity.setContent {
                inputMode = LocalInputModeManager.current
                ExplorerTheme { ExplorerScreen(model, {}, model::close, {}, activity = compose.activity) }
            }
            repeat(19) { model.newTab() }
        }
        tabNumber(20).assertIsDisplayed()
        compose.runOnIdle { model.select(model.tabs.first().id); assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        tabNumber(1).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        tabNumber(1).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionLeft) }
        tabNumber(20).assertIsFocused().assertIsDisplayed()
        tabNumber(1).assertIsSelected()
        tabNumber(20).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
        tabNumber(20).assertIsSelected()
    }

    @Test fun recreationKeepsIdentitiesOrderAndNotepadKeyboardShortcuts() {
        compose.runOnIdle {
            shortcut(compose.activity, KeyEvent.KEYCODE_N)
            shortcut(compose.activity, KeyEvent.KEYCODE_N)
            model.reorder(model.tabs.last().id, 0)
            shortcut(compose.activity, KeyEvent.KEYCODE_TAB)
        }
        val ids = model.tabs.map { it.id }
        val active = model.activeId
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { assertEquals(ids, model.tabs.map { it.id }); assertEquals(active, model.activeId) }
        tab(active).assertIsSelected()
        compose.runOnIdle { shortcut(compose.activity, KeyEvent.KEYCODE_W) }
        assertEquals(2, model.tabs.size)
    }

    @Test fun menuAndKeyboardOpenIndependentTasksAndExplicitCloseClosesOnlyThatWindow() {
        val host = compose.activity
        compose.onNodeWithText("File").performClick()
        val before = activities().toSet()
        compose.onNodeWithText("New window").performClick()
        compose.waitUntil(10_000) { activities().any { it !in before } }
        val second = activities().first { it !in before }
        assertNotEquals(host.taskId, second.taskId)
        val third = newWindow { shortcut(second, KeyEvent.KEYCODE_N, shift = true) }
        assertNotEquals(second.taskId, third.taskId)
        compose.runOnIdle {
            shortcut(third, KeyEvent.KEYCODE_W)
            assertTrue(third.explorer.tabs.isEmpty())
            assertFalse(third.isFinishing)
            third.finishAndRemoveTask()
        }
        compose.waitUntil(10_000) { third.isDestroyed }
        assertFalse(host.isFinishing)
        assertFalse(second.isFinishing)
    }

    @Test fun nativeMouseReorderPreviewsThenCommitsAndToolbarDropCancels() {
        compose.runOnIdle { repeat(2) { model.newTab() } }
        val original = model.tabs.toList()
        val from = compose.onNodeWithTag("tab-title-${original[0].id}", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val to = compose.onNodeWithTag("tab-title-${original[2].id}", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        mouseDrag(screenPoint(Offset(from.left + 8f, from.center.y)), screenPoint(Offset(to.left + 88f, to.center.y))) {
            assertEquals(original, model.tabs)
            assertNotNull(model.workspace.dragging)
        }
        compose.waitUntil(5_000) { model.workspace.dragging == null }
        compose.runOnIdle { assertEquals(listOf(original[1], original[2], original[0]), model.tabs) }
        val title = compose.onNodeWithTag("tab-title-${original[0].id}", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val toolbar = compose.onNodeWithTag("command-bar").fetchSemanticsNode().boundsInWindow
        val before = model.tabs.toList()
        mouseDrag(screenPoint(Offset(title.left + 8f, title.center.y)), screenPoint(toolbar.center))
        compose.waitUntil(5_000) { model.workspace.dragging == null }
        compose.runOnIdle { assertEquals(before, model.tabs) }
    }

    @Test fun acknowledgementMovesSameTabToNewAndExistingWindowsWithoutDuplication() {
        val host = compose.activity
        compose.runOnIdle { model.newTab() }
        val id = model.tabs.first().id
        val detached = newWindow { ExplorerWindows.launch(host, model, id) }
        compose.waitUntil(10_000) { detached.explorer.tabs.any { it.id == id } }
        assertEquals(listOf(id), detached.explorer.tabs.map { it.id })
        assertEquals(1, model.tabs.size)
        compose.runOnIdle {
            val transfer = model.workspace.beginTransfer(detached.explorer, id)!!
            assertTrue(model.workspace.move(transfer.token, model, model.tabs.size))
        }
        compose.waitUntil(10_000) { detached.isDestroyed }
        assertEquals(2, model.tabs.size)
        assertEquals(1, model.tabs.count { it.id == id })
    }
}
