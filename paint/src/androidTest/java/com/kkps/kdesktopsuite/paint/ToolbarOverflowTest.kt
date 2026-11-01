package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ToolbarOverflowTest {
    @get:Rule val compose = createComposeRule()
    private var width by mutableStateOf(360.dp)
    private var count by mutableIntStateOf(12)
    private var rtl by mutableStateOf(false)

    private fun content() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PaintTheme {
                    Box(Modifier.requiredWidth(width)) {
                        PaintToolbarRow("toolbar",MaterialTheme.colorScheme.surfaceContainer) {
                            repeat(count) { index ->
                                TextButton({},modifier = Modifier.width(64.dp)) { Text("$index") }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun arrowsScrollBothWaysAndDisableAtTheEnds() {
        content()
        compose.onNodeWithTag("toolbar-backward").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("toolbar-forward").assertIsEnabled().performClick()
        compose.onNodeWithTag("toolbar-backward").assertIsEnabled()
        repeat(4) { compose.onNodeWithTag("toolbar-forward").performClick() }
        compose.onNodeWithTag("toolbar-forward").assertIsNotEnabled()
        compose.onNodeWithText("11").assertIsDisplayed()
        repeat(4) { compose.onNodeWithTag("toolbar-backward").performClick() }
        compose.onNodeWithTag("toolbar-backward").assertIsNotEnabled()
        compose.onNodeWithText("0").assertIsDisplayed()
    }

    @Test fun arrowsDisappearWhenContentFitsAfterResizingOrChangingTools() {
        content()
        compose.onNodeWithTag("toolbar-forward").assertExists()
        compose.runOnIdle { width = 1000.dp }
        compose.onNodeWithTag("toolbar-forward").assertDoesNotExist()
        compose.onNodeWithTag("toolbar-backward").assertDoesNotExist()
        compose.runOnIdle { width = 360.dp }
        compose.onNodeWithTag("toolbar-forward").assertExists()
        compose.runOnIdle { count = 2 }
        compose.onNodeWithTag("toolbar-forward").assertDoesNotExist()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test fun rtlPlacesForwardAtTheLeftAndRevealsLaterControls() {
        rtl = true
        content()
        val forward = compose.onNodeWithTag("toolbar-forward").fetchSemanticsNode().boundsInRoot
        val backward = compose.onNodeWithTag("toolbar-backward").fetchSemanticsNode().boundsInRoot
        assertTrue(forward.right <= backward.left)
        repeat(5) { compose.onNodeWithTag("toolbar-forward").performClick() }
        compose.onNodeWithText("11").assertIsDisplayed()
        compose.onNodeWithTag("toolbar-forward").assertIsNotEnabled()
    }
}
