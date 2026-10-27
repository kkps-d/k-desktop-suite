package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class PaintDebugScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dragReversesImmediatelyAfterOvershootingEitherLimit() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            PaintTheme {
                Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) {
                    Box(Modifier.width(480.dp).fillMaxHeight()) { PaintDebugScreen {} }
                }
            }
        }
        val handle = compose.onNodeWithTag("paint-debug-size-scrub",useUnmergedTree = true)
        val input = compose.onNodeWithTag("paint-debug-size-input")

        handle.performTouchInput { down(center); moveBy(Offset(400f*density,0f)) }
        input.assertTextContains("100")
        handle.performTouchInput { moveBy(Offset(-20f*density,0f)) }
        input.assertTextContains("90")
        handle.performTouchInput { moveBy(Offset(-600f*density,0f)) }
        input.assertTextContains("1")
        handle.performTouchInput { moveBy(Offset(20f*density,0f)); up() }
        input.assertTextContains("11")
    }
}
