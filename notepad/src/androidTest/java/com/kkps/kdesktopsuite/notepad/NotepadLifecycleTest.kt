package com.kkps.kdesktopsuite.notepad

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import androidx.compose.ui.geometry.Rect

class NotepadLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<NotepadActivity>()

    @Test
    fun realDesktopCaptionKeepsSystemControlsAndDraggingSpaceOutsideGestureExclusions() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 35)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("document-header").fetchSemanticsNodes().isNotEmpty() }
        var caption = DesktopCaption()
        var width = 0f
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val insets = view.rootWindowInsets
            val type = android.view.WindowInsets.Type.captionBar()
            assumeTrue(insets.isVisible(type) && insets.getInsets(type).top > 0)
            width = view.width.toFloat()
            caption = DesktopCaption(insets.getInsets(type).top.toFloat(), insets.getBoundingRects(type).map {
                Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
            })
        }
        compose.onNodeWithTag("document-header").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("document-header").fetchSemanticsNode().boundsInWindow
        val layout = captionLayout(caption, Rect(0f, 0f, width, 1000f), compose.density.density)
        if (!layout.integrated) {
            assertTrue("Short or constrained captions keep accessible tabs below system controls", bounds.top >= caption.height)
            compose.onNodeWithContentDescription("New document").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            compose.runOnIdle { assertTrue(compose.activity.window.decorView.systemGestureExclusionRects.isEmpty()) }
            return
        }
        assertTrue("Header should occupy the caption", bounds.top < caption.height)
        assertTrue("Tabs must fit within the system caption", bounds.bottom <= caption.height + 1f)
        compose.runOnIdle {
            val exclusions = compose.activity.window.decorView.systemGestureExclusionRects
            assertTrue("Interactive caption controls must receive input", exclusions.isNotEmpty())
            val controls = layout.controls(compose.density.density, rtl = false)
            exclusions.forEach {
                val rect = Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                assertEquals(rect, rect.intersect(controls))
                assertTrue(caption.obstructions.none { obstacle -> obstacle.overlaps(rect) })
            }
        }
    }

    @Test
    fun activityRecreationKeepsTheSelectedTabAndItsDraft() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("document-editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Draft across recreation")
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Selected draft across recreation")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("document-editor").assertIsDisplayed().assertTextContains("Selected draft across recreation")
    }

    @Test
    fun rightClickEditingMenuUsesTheSameClipboardCommandsInTheRealWindow() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("document-editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Right click")
        val editMenu = if (compose.onAllNodesWithText("Edit").fetchSemanticsNodes().isEmpty()) "Commands" else "Edit"
        compose.onNodeWithText(editMenu).performClick()
        compose.onNodeWithText("Select all").performClick()
        // Use the real window and density: native context-menu anchors are platform coordinates.
        val position = with(compose.density) { Offset(16.dp.toPx(), 12.dp.toPx()) }
        compose.onNodeWithTag("document-editor").performMouseInput { click(position, button = MouseButton.Secondary) }
        compose.waitUntil(5_000) { compose.onNodeWithText("Copy").isDisplayed() }
        compose.onNodeWithText("Copy").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithText(editMenu).performClick()
        compose.onNodeWithText("Paste").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Right click")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("document-editor").assertTextContains("Right click")
    }
}
