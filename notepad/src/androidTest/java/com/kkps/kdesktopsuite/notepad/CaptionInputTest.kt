package com.kkps.kdesktopsuite.notepad

import com.kkps.kdesktopsuite.ui.CaptionInput

import android.graphics.Rect as AndroidRect
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CaptionInputTest {
    @Test
    fun updatesAndRemovesVisibleControlsWithoutChangingExistingExclusions() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = View(instrumentation.targetContext)
            val original = AndroidRect(0, 100, 20, 120)
            view.systemGestureExclusionRects = listOf(original)
            val input = CaptionInput(view)
            input.clip = Rect(48f, 0f, 300f, 40f)
            input.update("tab", Rect(20f, 0f, 160f, 48f))
            assertEquals(listOf(original, AndroidRect(48, 0, 160, 40)), view.systemGestureExclusionRects)
            input.update("tab", Rect(-100f, 0f, 20f, 48f))
            assertEquals(listOf(original), view.systemGestureExclusionRects)
            input.update("new", Rect(252f, 0f, 300f, 48f))
            assertEquals(listOf(original, AndroidRect(252, 0, 300, 40)), view.systemGestureExclusionRects)
            input.update("new", null)
            assertEquals(listOf(original), view.systemGestureExclusionRects)
            input.update("switcher", Rect(48f, 0f, 200f, 48f))
            input.clip = null
            assertEquals(listOf(original), view.systemGestureExclusionRects)
            input.dispose()
            assertEquals(listOf(original), view.systemGestureExclusionRects)
        }
    }
}
