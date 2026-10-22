package com.kkps.kdesktopsuite.notepad

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class NotepadLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<NotepadActivity>()

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
        compose.onNodeWithText("Copy").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithText(editMenu).performClick()
        compose.onNodeWithText("Paste").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Right click")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("document-editor").assertTextContains("Right click")
    }
}
