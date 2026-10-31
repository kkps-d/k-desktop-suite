package com.kkps.kdesktopsuite.paint

import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class PaintCaptionLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<PaintActivity>()

    @Test fun nativeCaptionReservesSystemControlsAndClearsExclusionsOnSettingsAndFocusMode() {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        compose.waitUntil(15000) { !compose.activity.paintModel.state.value.loading }
        compose.onNodeWithTag("paint-header").assertIsDisplayed()
        var height = 0f
        var obstacles = emptyList<Rect>()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val insets = view.rootWindowInsets
            val type = WindowInsets.Type.captionBar()
            assumeTrue(insets.isVisible(type) && insets.getInsets(type).top > 0)
            assertTrue(view.windowInsetsController!!.systemBarsAppearance and
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND != 0)
            height = insets.getInsets(type).top.toFloat()
            obstacles = insets.getBoundingRects(type).map { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
        }
        val header = compose.onNodeWithTag("paint-header").fetchSemanticsNode().boundsInWindow
        val title = compose.onNodeWithTag("paint-document-title").fetchSemanticsNode().boundsInWindow
        val integrated = header.top < height
        assertTrue(compose.onNodeWithTag("paint-primary-toolbar").fetchSemanticsNode().boundsInWindow.top >= height)
        compose.runOnIdle {
            val exclusions = compose.activity.window.decorView.systemGestureExclusionRects
            if (integrated) {
                assertTrue(header.bottom <= height + 1f)
                assertTrue(exclusions.isNotEmpty())
                val claimedWidth = exclusions.sumOf { it.width() }
                assertTrue("Leave caption space for dragging", claimedWidth < header.width)
                exclusions.forEach {
                    val rect = Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                    assertTrue(obstacles.none { obstacle -> obstacle.overlaps(rect) })
                    assertFalse("The title remains available for window dragging", rect.overlaps(title))
                    assertTrue(rect.top >= header.top && rect.bottom <= height)
                }
            } else assertTrue(exclusions.isEmpty())
        }
        compose.onNodeWithText("File").performClick()
        compose.onNodeWithText("Image properties").assertIsDisplayed()
        compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("paint-header").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(compose.activity.window.decorView.systemGestureExclusionRects.isEmpty())
            compose.activity.paintModel.showSettings(false)
            compose.activity.paintModel.toggleFocus()
        }
        val exit = compose.onNodeWithText("Exit focus mode")
        exit.assertIsDisplayed()
        assertTrue(exit.fetchSemanticsNode().boundsInWindow.top >= height)
        compose.runOnIdle { assertTrue(compose.activity.window.decorView.systemGestureExclusionRects.isEmpty()) }
        exit.performClick()
        compose.onNodeWithTag("paint-header").assertIsDisplayed()
    }
}
