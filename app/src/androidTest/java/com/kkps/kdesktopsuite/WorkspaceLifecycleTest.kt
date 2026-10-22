package com.kkps.kdesktopsuite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

class WorkspaceLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun draftAndSelectionSurviveActivityRecreation() {
        compose.onNodeWithText("Welcome to your workspace").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Draft after recreation")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("document-editor").assertIsDisplayed().assertTextContains("Draft after recreation")
    }

    @Test
    fun switchingDestinationsPreservesWorkspace() {
        compose.onNodeWithText("Welcome to your workspace").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Draft after switching")
        compose.onNodeWithText("Guide").performClick()
        compose.onNodeWithText("Make room for your ideas").assertIsDisplayed()
        compose.onNodeWithText("Workspace").performClick()
        compose.onNodeWithTag("document-editor").assertIsDisplayed().assertTextContains("Draft after switching")
    }
}
