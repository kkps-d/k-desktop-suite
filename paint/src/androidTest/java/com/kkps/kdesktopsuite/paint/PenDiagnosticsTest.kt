package com.kkps.kdesktopsuite.paint

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PenDiagnosticsTest {
    @get:Rule val compose = createAndroidComposeRule<PaintActivity>()

    @Before fun openDebug() {
        compose.waitUntil(15_000) {
            runCatching { !compose.activity.paintModel.state.value.loading }.getOrDefault(false)
        }
        compose.runOnUiThread {
            compose.activity.setContent { PaintTheme { PaintDebugScreen {} } }
        }
        compose.onNodeWithTag("paint-debug-pen-attachment").assertTextEquals("Attachment: Unknown / unavailable")
    }

    @Test fun hoverAndStationaryButtonEventsUpdateWithoutSwitchingTools() {
        val options = compose.activity.paintModel.state.value.options
        event(MotionEvent.ACTION_HOVER_ENTER)
        hover("Yes"); button("No")
        event(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_STYLUS_PRIMARY)
        hover("Yes"); button("Yes")
        event(MotionEvent.ACTION_BUTTON_RELEASE)
        button("No")
        event(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_STYLUS_PRIMARY)
        hover("No"); button("Yes")
        event(MotionEvent.ACTION_UP)
        button("No")
        event(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY)
        event(MotionEvent.ACTION_HOVER_EXIT, MotionEvent.BUTTON_STYLUS_PRIMARY)
        hover("No"); button("No")
        event(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.BUTTON_PRIMARY, MotionEvent.TOOL_TYPE_MOUSE)
        hover("No"); button("No")
        compose.runOnIdle { assertEquals(options, compose.activity.paintModel.state.value.options) }
    }

    @Test fun attachmentBroadcastsAndInterruptedInputDoNotLeaveStaleStatus() {
        val context = compose.activity
        fun attachment(inserted: Boolean?) {
            context.sendBroadcast(Intent("com.samsung.pen.INSERT").setPackage(context.packageName).apply {
                inserted?.let { putExtra("penInsert", it) }
            })
        }
        attachment(false)
        compose.waitUntil { context.penStatus.attached == false }
        compose.onNodeWithTag("paint-debug-pen-attachment").assertTextEquals("Attachment: Detached")
        attachment(null)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(false, context.penStatus.attached) }
        event(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.BUTTON_STYLUS_PRIMARY)
        attachment(true)
        compose.waitUntil { context.penStatus.attached == true }
        compose.onNodeWithTag("paint-debug-pen-attachment").assertTextEquals("Attachment: Attached")
        hover("No"); button("No")
        event(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.BUTTON_STYLUS_PRIMARY)
        event(MotionEvent.ACTION_CANCEL)
        hover("No"); button("No")
        event(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.BUTTON_STYLUS_PRIMARY)
        compose.runOnIdle { context.onWindowFocusChanged(false) }
        hover("No"); button("No")
        event(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.BUTTON_STYLUS_PRIMARY)
        compose.runOnUiThread { context.setContent { PaintTheme {} } }
        compose.waitForIdle()
        event(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.BUTTON_STYLUS_PRIMARY)
        compose.runOnIdle {
            assertFalse(context.penStatus.hovering)
            assertFalse(context.penStatus.buttonHeld)
        }
    }

    private fun hover(value: String) {
        compose.onNodeWithTag("paint-debug-pen-hover").assertTextEquals("Hovering: $value")
    }

    private fun button(value: String) {
        compose.onNodeWithTag("paint-debug-pen-button").assertTextEquals("Side button held: $value")
    }

    private fun event(action: Int, buttons: Int = 0, tool: Int = MotionEvent.TOOL_TYPE_STYLUS) {
        compose.runOnUiThread {
            val time = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(time, time, action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool }),
                arrayOf(MotionEvent.PointerCoords().apply { x = 200f; y = 250f }),
                0, buttons, 1f, 1f, 0, 0,
                if (tool == MotionEvent.TOOL_TYPE_MOUSE) InputDevice.SOURCE_MOUSE else InputDevice.SOURCE_STYLUS, 0)
            try {
                if (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL))
                    compose.activity.dispatchTouchEvent(event)
                else compose.activity.dispatchGenericMotionEvent(event)
            } finally { event.recycle() }
        }
    }
}
