package com.kkps.kdesktopsuite

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kkps.kdesktopsuite.ui.KDesktopSuiteApp
import com.kkps.kdesktopsuite.ui.theme.KDesktopSuiteTheme
import com.kkps.kdesktopsuite.workspace.WorkspaceViewModel
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AdaptiveWorkspaceTest {
    @get:Rule
    val compose = createComposeRule()

    private var windowSize by mutableStateOf(DpSize(1200.dp, 900.dp))

    private fun showWorkspace(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val workspace: WorkspaceViewModel = viewModel()
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(windowSize)) {
                KDesktopSuiteTheme {
                    KDesktopSuiteApp(
                        workspace::textFor,
                        workspace::updateText,
                        // Real host IME/caption insets are invalid at the synthetic window density.
                        contentWindowInsets = WindowInsets(0),
                    )
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    @Test
    fun compactWindowNavigatesFromListToEditorAndBack() {
        windowSize = DpSize(400.dp, 800.dp)
        showWorkspace()
        compose.onNodeWithTag("document-list").assertIsDisplayed()
        compose.onNodeWithText("Welcome to your workspace").performClick()
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to documents").performClick()
        compose.onNodeWithTag("document-list").assertIsDisplayed()
    }

    @Test
    fun expandedWindowShowsListBesideEditor() {
        showWorkspace()
        compose.onNodeWithText("Welcome to your workspace").performClick()
        compose.onNodeWithTag("document-list").assertIsDisplayed()
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
    }

    @Test
    fun resizingPreservesDraftAndSelection() {
        showWorkspace()
        compose.onNodeWithText("Welcome to your workspace").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Draft across window sizes")
        compose.runOnIdle { windowSize = DpSize(400.dp, 800.dp) }
        compose.onNodeWithTag("document-editor").assertIsDisplayed().assertTextContains("Draft across window sizes")
        compose.onNodeWithContentDescription("Back to documents").performClick()
        compose.onNodeWithText("Welcome to your workspace").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("Draft across window sizes")
        compose.runOnIdle { windowSize = DpSize(1200.dp, 900.dp) }
        // Window metrics arrive asynchronously after a resize.
        try {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onNodeWithTag("document-list").isDisplayed()
            }
        } catch (error: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("AdaptiveResize")
            throw error
        }
        compose.onNodeWithTag("document-list").assertIsDisplayed()
        compose.onNodeWithTag("document-editor").assertTextContains("Draft across window sizes")
    }

    @Test
    fun savedNavigationRestoresSelectedDocument() {
        windowSize = DpSize(400.dp, 800.dp)
        val restoration = StateRestorationTester(compose)
        showWorkspace(restoration)
        compose.onNodeWithText("Ideas for the suite").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Ideas for the suite").assertIsDisplayed()
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
    }
}
