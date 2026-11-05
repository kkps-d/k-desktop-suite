package com.kkps.kdesktopsuite.explorer

import android.view.KeyEvent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import androidx.activity.compose.setContent
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

    @Test fun captionFitsWithCompactGapAndFallsBackForMissingGeometryOrLargeText() {
        val model = compose.activity.explorer
        val caption = DesktopCaption(48f, listOf(Rect(0f, 0f, 80f, 48f), Rect(680f, 0f, 840f, 48f)))
        var geometry by mutableStateOf(caption)
        var rtl by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)
        compose.runOnIdle {
            compose.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    ExplorerTheme {
                        Box(Modifier.width(840.dp)) {
                            ExplorerScreen(model, {}, { model.close(it) }, {}, geometry,
                                androidx.compose.foundation.layout.WindowInsets(0))
                        }
                    }
                }
            }
            model.newTab()
        }
        compose.onNodeWithTag("explorer-header-caption").assertIsDisplayed()
        val header = compose.onNodeWithTag("explorer-header-caption").fetchSemanticsNode().boundsInWindow
        assertEquals(80f, header.left, 1f)
        assertEquals(672f, header.right, 1f)
        assertEquals(48f, compose.onNodeWithTag("explorer-workspace").fetchSemanticsNode().boundsInWindow.top, 1f)
        compose.runOnIdle { rtl = true }
        val reversed = compose.onNodeWithTag("explorer-header-caption").fetchSemanticsNode().boundsInWindow
        assertEquals(88f, reversed.left, 1f)
        assertEquals(680f, reversed.right, 1f)
        compose.runOnIdle { geometry = caption.copy(obstructions = emptyList()) }
        compose.onNodeWithTag("explorer-header-below").assertIsDisplayed()
        compose.onNodeWithTag("explorer-header-caption").assertDoesNotExist()
        compose.runOnIdle { geometry = caption.copy(height = 24f); fontScale = 2f }
        compose.onNodeWithTag("explorer-header-below").assertIsDisplayed()
        compose.onNodeWithTag("tab-2").assertIsSelected()
        compose.runOnIdle { geometry = caption; fontScale = 1f; rtl = false }
        compose.onNodeWithTag("explorer-header-caption").assertIsDisplayed()
        compose.onNodeWithTag("tab-2").assertIsSelected()
    }

    @Test fun nativeCaptionButtonsReceiveInputWithoutClaimingTheDragGap() {
        compose.onNodeWithTag("explorer-header-caption").assertIsDisplayed()
        val button = compose.onNodeWithContentDescription("New tab").fetchSemanticsNode().boundsInWindow
        val origin = IntArray(2)
        compose.runOnIdle { compose.activity.window.decorView.getLocationOnScreen(origin) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                origin[0] + button.center.x, origin[1] + button.center.y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        compose.onNodeWithTag("tab-2").assertIsSelected()
        compose.runOnIdle {
            val insets = compose.activity.window.decorView.rootWindowInsets
            val controls = insets.getBoundingRects(android.view.WindowInsets.Type.captionBar())
            val exclusions = compose.activity.window.decorView.systemGestureExclusionRects
            assertTrue(exclusions.isNotEmpty())
            assertTrue(exclusions.none { excluded -> controls.any { android.graphics.Rect.intersects(it, excluded) } })
            val rightmost = exclusions.maxOf { it.right }
            val nextSystemControl = controls.filter { it.left >= rightmost }.minBy { it.left }
            assertEquals(8f, (nextSystemControl.left - rightmost).toFloat(), 1f)
        }
        val lastTab = compose.onNodeWithTag("tab-2").fetchSemanticsNode().boundsInWindow
        val dragX = origin[0] + (lastTab.right + button.left) / 2f
        val dragY = origin[1] + button.center.y
        val dragTime = SystemClock.uptimeMillis()
        fun dragEvent(action: Int, dx: Float, dy: Float) {
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
            val coordinates = MotionEvent.PointerCoords().apply { x = dragX + dx; y = dragY + dy; pressure = 1f }
            val event = MotionEvent.obtain(dragTime, SystemClock.uptimeMillis(), action, 1,
                arrayOf(properties), arrayOf(coordinates), 0,
                if (action == MotionEvent.ACTION_UP) 0 else MotionEvent.BUTTON_PRIMARY,
                1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        dragEvent(MotionEvent.ACTION_DOWN, 0f, 0f)
        try {
            repeat(10) { step ->
                dragEvent(MotionEvent.ACTION_MOVE, (step + 1) * 4f, (step + 1) * 2f)
                SystemClock.sleep(24)
            }
        } finally { dragEvent(MotionEvent.ACTION_UP, 40f, 20f) }
        compose.waitUntil(5_000) {
            val moved = IntArray(2)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.window.decorView.getLocationOnScreen(moved)
            }
            moved[0] > origin[0] + 20 && moved[1] > origin[1] + 10
        }
        compose.onNodeWithTag("tab-2").assertIsSelected()
    }

    @Test fun tabsCanBeCreatedSelectedAndClosedThroughVisibleControls() {
        compose.onNodeWithTag("explorer-workspace").assertExists()
        compose.onNodeWithTag("tab-1").assertIsSelected()
        repeat(4) { compose.onNodeWithContentDescription("New tab").performClick() }
        compose.onNodeWithTag("tab-5").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close New tab 4").performClick()
        compose.onNodeWithTag("tab-5").assertIsSelected()
        compose.onNodeWithContentDescription("Close New tab 5").performClick()
        compose.onNodeWithTag("tab-3").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("tab-2").performClick().assertIsSelected()
    }

    @Test fun compactSelectorKeepsEveryTabAndCloseReachableAtLargeFontSize() {
        val model = compose.activity.explorer
        compose.runOnIdle {
            repeat(19) { model.newTab() }
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    ExplorerTheme {
                        Box(Modifier.width(280.dp)) {
                            ExplorerScreen(model, {}, { model.close(it) }, {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("tab-selector").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Close New tab 1").performClick()
        compose.runOnIdle {
            assertEquals(19, model.tabs.size)
            assertEquals(20, model.activeId)
        }
        compose.onNodeWithText("New tab 2").performClick()
        compose.onNodeWithTag("tab-selector").assertTextContains("New tab 2", substring = true)
        compose.onNodeWithContentDescription("New tab").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(21, model.activeId) }
    }

    @Test fun overflowingTabsRevealKeyboardSelectionAndArrowFocusDoesNotSelect() {
        val host = compose.activity
        lateinit var inputMode: InputModeManager
        compose.runOnIdle {
            host.setContent {
                inputMode = LocalInputModeManager.current
                ExplorerTheme {
                    ExplorerScreen(host.explorer, host::newWindow, { host.explorer.close(it) }, {})
                }
            }
        }
        compose.runOnIdle { repeat(19) { host.explorer.newTab() } }
        compose.onNodeWithTag("tab-20").assertIsSelected().assertIsDisplayed()
        compose.runOnIdle { shortcut(host, KeyEvent.KEYCODE_1) }
        compose.onNodeWithTag("tab-1").assertIsSelected().assertIsDisplayed()
        // Selectable controls follow Android's keyboard/touch input-mode focus policy.
        compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        compose.onNodeWithTag("tab-1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
            .performKeyInput {
                pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
            }
        compose.onNodeWithTag("tab-1").assertIsSelected()
        compose.onNodeWithTag("tab-2").assertIsFocused()
        compose.onNodeWithTag("tab-2").performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.Enter)
            keyUp(androidx.compose.ui.input.key.Key.Enter)
        }
        compose.onNodeWithTag("tab-2").assertIsSelected()
        compose.runOnIdle { shortcut(host, KeyEvent.KEYCODE_9) }
        compose.onNodeWithTag("tab-20").assertIsSelected().assertIsDisplayed()
    }

    @Test fun recreationAndKeyboardShortcutsPreserveWindowTabs() {
        val host = compose.activity
        compose.runOnIdle {
            shortcut(host, KeyEvent.KEYCODE_T)
            shortcut(host, KeyEvent.KEYCODE_T)
            shortcut(host, KeyEvent.KEYCODE_TAB)
            assertEquals(1, host.explorer.activeId)
            shortcut(host, KeyEvent.KEYCODE_TAB, shift = true)
            assertEquals(3, host.explorer.activeId)
            shortcut(host, KeyEvent.KEYCODE_2)
            shortcut(host, KeyEvent.KEYCODE_W)
            assertEquals(listOf(1, 3), host.explorer.tabs)
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-3").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(1, 3), compose.activity.explorer.tabs) }
    }

    @Test fun menuAndShortcutLaunchSeparateTasksAndLastTabClosesOnlyItsWindow() {
        val host = compose.activity
        compose.onNodeWithContentDescription("Window menu").performClick()
        compose.onNodeWithText("New window").performClick()
        compose.waitUntil(10_000) { activities().any { it !== host } }
        val second = activities().first { it !== host }
        assertNotEquals(host.taskId, second.taskId)
        compose.runOnIdle {
            shortcut(second, KeyEvent.KEYCODE_T)
            shortcut(host, KeyEvent.KEYCODE_T)
            shortcut(host, KeyEvent.KEYCODE_T)
            assertEquals(listOf(1, 2), second.explorer.tabs)
            assertEquals(listOf(1, 2, 3), host.explorer.tabs)
            shortcut(second, KeyEvent.KEYCODE_N, shift = true)
        }
        compose.waitUntil(10_000) { activities().any { it !== host && it !== second } }
        val third = activities().first { it !== host && it !== second }
        assertNotEquals(second.taskId, third.taskId)
        compose.runOnIdle { shortcut(third, KeyEvent.KEYCODE_W) }
        compose.waitUntil(10_000) { third.isDestroyed }
        compose.runOnIdle {
            assertFalse(host.isFinishing)
            assertFalse(second.isFinishing)
            assertEquals(listOf(1, 2, 3), host.explorer.tabs)
            shortcut(second, KeyEvent.KEYCODE_W, shift = true)
        }
        compose.waitUntil(10_000) { second.isDestroyed }
    }
}
