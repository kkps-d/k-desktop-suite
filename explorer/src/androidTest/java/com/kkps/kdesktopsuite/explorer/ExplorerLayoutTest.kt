package com.kkps.kdesktopsuite.explorer

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.DesktopCaption
import org.junit.Rule
import org.junit.Test

class ExplorerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ExplorerActivity>()
    private lateinit var inputMode: InputModeManager

    private fun layout(width: Int = 960, height: Int = 480, scale: Float = 1f, rtl: Boolean = false) {
        compose.runOnIdle {
            compose.activity.setContent {
                val density = LocalDensity.current
                inputMode = LocalInputModeManager.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    ExplorerTheme {
                        Box(Modifier.width(width.dp).height(height.dp)) {
                            val model = compose.activity.explorer
                            ExplorerScreen(model, {}, model::close, {}, DesktopCaption(), WindowInsets(0))
                        }
                    }
                }
            }
        }
    }

    @Test fun desktopMatchesReferenceAndSearchDoesNotBrowseOrFilterFiles() {
        layout()
        compose.onNodeWithTag("explorer-locations").assertIsDisplayed()
        compose.onNodeWithText("Date modified").assertIsDisplayed()
        compose.onNodeWithText("Type").assertIsDisplayed()
        compose.onNodeWithText("Size").assertIsDisplayed()
        compose.onNodeWithTag("explorer-file-list").performScrollToNode(hasTestTag("preview-file-Budget.xlsx"))
        compose.onNodeWithTag("preview-file-Budget.xlsx").assertIsSelected()
        compose.onNodeWithTag("explorer-status").assertTextEquals("8 items · 1 selected")
        compose.onNodeWithTag("explorer-search").performTextInput("not a real query")
        compose.onNodeWithTag("explorer-file-list").performScrollToNode(hasTestTag("preview-file-Timeline.csv"))
        compose.onNodeWithTag("preview-file-Timeline.csv").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Up").performClick()
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.onNodeWithTag("explorer-status").assertTextEquals("8 items · 1 selected")
        compose.onNodeWithTag("explorer-breadcrumbs").assert(hasAnyDescendant(hasText("Reports")))
    }

    @Test fun presentationViewSelectionAndInformationRemainSampleOnly() {
        layout()
        compose.onNodeWithText("Icons").performClick()
        compose.onNodeWithTag("explorer-icons").assertIsDisplayed()
        compose.onNodeWithTag("explorer-status").assertTextEquals("8 items · 1 selected")
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithTag("explorer-file-list").performScrollToNode(hasTestTag("preview-file-Budget.xlsx"))
        compose.onNodeWithTag("preview-file-Budget.xlsx").assertIsSelected().performClick()
        compose.onNodeWithTag("explorer-status").assertTextEquals("8 items")
        compose.onNodeWithTag("preview-file-Budget.xlsx").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Item information").performClick()
        compose.onNode(hasText("Budget.xlsx") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText("Sample content") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
    }

    @Test fun compactRtlAndLargeFontsKeepLocationsAndSearchReachable() {
        layout(width = 360, height = 480, scale = 2f, rtl = true)
        compose.onNodeWithContentDescription("Locations").performClick()
        compose.onNodeWithTag("explorer-locations-drawer").assertIsDisplayed()
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithContentDescription("Close locations").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Close locations").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("explorer-locations-drawer").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Locations").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("explorer-locations-drawer").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Search this folder").performClick()
        compose.onNodeWithTag("explorer-search").assertIsDisplayed().performTextInput("sample")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithTag("explorer-status").assertTextEquals("8 items · 1 selected")
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Icons").performClick()
        compose.onNodeWithTag("explorer-icons").assertIsDisplayed()
    }

    @Test fun shortWindowCollapsesLocationsAndPreservesScrollableContent() {
        layout(width = 760, height = 320)
        compose.onNodeWithContentDescription("Locations").assertIsDisplayed()
        compose.onNodeWithTag("explorer-file-list").performScrollToNode(hasTestTag("preview-file-Timeline.csv"))
        compose.onNodeWithTag("preview-file-Timeline.csv").assertIsDisplayed()
        compose.onNodeWithTag("explorer-status").assertIsDisplayed()
        compose.onNodeWithText("File").performClick()
        compose.onNodeWithText("New window").assertIsDisplayed()
    }

}
