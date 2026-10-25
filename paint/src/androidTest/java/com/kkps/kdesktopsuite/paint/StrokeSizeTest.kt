package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class StrokeSizeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var size by mutableStateOf(DpSize(1200.dp,800.dp))
    private var largeText by mutableStateOf(false)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"size-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if (largeText) 2f else 1f)) {
                PaintTheme { Box(Modifier.requiredSize(size)) { PaintScreen(state,model) } }
            }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
        compose.runOnIdle { model.brush(BrushStyle.Solid); model.strokeWidth(3); model.viewport(model.state.value.viewport.zoomed(2f)) }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
    }

    private fun openPicker() {
        compose.onNodeWithTag("paint-size-picker").performScrollTo().performClick()
    }

    private fun openCustomSize() {
        openPicker()
        compose.onNodeWithTag("paint-size-custom").performScrollTo().performClick()
    }

    private fun capture(tag: String, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),filename).outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun presetsShowLinesAndSelectTheRightToolWidthWithoutChangingZoom() {
        for (width in listOf(1200.dp,360.dp)) {
            compose.runOnIdle { size = DpSize(width,800.dp) }
            for (tool in listOf(Tool.Brush,Tool.Pencil,Tool.Line,Tool.Eraser)) {
                compose.runOnIdle { model.tool(tool) }
                openPicker()
                val preset = if (tool == Tool.Eraser) 16 else 5
                compose.onNodeWithTag("paint-size-preview-$preset",useUnmergedTree = true).assertExists()
                compose.onNodeWithTag("paint-size-$preset").performScrollTo().performClick()
                compose.onNodeWithTag("dropdown-menu").assertDoesNotExist()
                compose.runOnIdle {
                    assertEquals(preset,model.state.value.options.strokeWidth)
                    assertEquals(2f,model.state.value.viewport.scale,0f)
                    assertFalse(model.state.value.dirty)
                }
                openPicker()
                compose.onNodeWithTag("paint-size-$preset").assertIsSelected().assertIsFocused()
                compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(Key.Escape) }
            }
            compose.runOnIdle { model.brush(BrushStyle.Marker) }
            openPicker()
            compose.onNodeWithTag("paint-size-24").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(24,model.state.value.options.brushWidths[BrushStyle.Marker])
                model.brush(BrushStyle.Solid)
                assertEquals(5,model.state.value.options.width)
            }
        }
    }

    @Test fun openPreviewsFollowZoomAndKeyboardSelectionWorks() {
        openPicker()
        val before = compose.onNodeWithTag("paint-size-preview-8",useUnmergedTree = true).captureToImage().toPixelMap()
        compose.runOnIdle { model.viewport(model.state.value.viewport.zoomed(5f)) }
        compose.onNodeWithText("Preview at 500% zoom").assertExists()
        val after = compose.onNodeWithTag("paint-size-preview-8",useUnmergedTree = true).captureToImage().toPixelMap()
        fun thickness(image: androidx.compose.ui.graphics.PixelMap) =
            (0 until image.height).sumOf { (1-image[image.width/2,it].red).toDouble() }
        assertTrue(thickness(before) in 16.0..18.1)
        assertEquals(thickness(before)*2.5,thickness(after),0.6)
        capture("dropdown-menu","size-picker-500.png")
        compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(5,model.state.value.options.width) }
    }

    @Test fun customDialogPreviewsEditsValidatesAndAppliesOnlyOnConfirmation() {
        openCustomSize()
        val input = compose.onNodeWithTag("paint-custom-size-input")
        input.assertTextContains("3").performTextReplacement("37")
        val preview = compose.onNodeWithTag("paint-custom-size-preview").captureToImage().toPixelMap()
        val thickness = (0 until preview.height).sumOf { (1-preview[preview.width/2,it].red).toDouble() }
        assertEquals(74.0,thickness,0.6)
        capture("paint-custom-size-preview","size-custom-37-at-200.png")
        capture("paint-custom-size-dialog","size-custom-dialog.png")
        compose.runOnIdle { assertEquals(3,model.state.value.options.width) }
        for (invalid in listOf("","0","101","-1","abc")) {
            input.performTextReplacement(invalid)
            compose.onNodeWithTag("paint-custom-size-apply").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(3,model.state.value.options.width) }
        }
        input.performTextReplacement("37")
        compose.onNodeWithTag("paint-custom-size-apply").performClick()
        compose.runOnIdle {
            assertNull(model.state.value.dialog)
            assertEquals(37,model.state.value.options.width)
            assertEquals(37,model.state.value.options.brushWidths[BrushStyle.Solid])
        }
        openCustomSize()
        compose.onNodeWithTag("paint-custom-size-input").performTextReplacement("12")
        compose.onNodeWithTag("paint-custom-size-cancel").performClick()
        compose.runOnIdle { assertEquals(37,model.state.value.options.width) }
        openCustomSize()
        compose.onNodeWithTag("paint-custom-size-input").assertTextContains("37").performTextReplacement("42")
        compose.onNodeWithTag("paint-custom-size-input").performImeAction()
        compose.runOnIdle { assertEquals(42,model.state.value.options.width); assertNull(model.state.value.dialog) }
    }

    @Test fun compactLargeTextKeepsCustomSizeAndActionsReachableAndEraserWidthSeparate() {
        compose.runOnIdle {
            size = DpSize(360.dp,400.dp); largeText = true
            model.tool(Tool.Eraser)
            model.viewport(model.state.value.viewport.zoomed(5f))
        }
        openCustomSize()
        compose.onNodeWithTag("paint-custom-size-input").performTextReplacement("100")
        compose.onNodeWithTag("paint-custom-size-preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("paint-custom-size-apply").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(100,model.state.value.options.eraserWidth)
            assertEquals(3,model.state.value.options.width)
        }
        compose.runOnIdle { model.tool(Tool.Fill) }
        compose.onNodeWithTag("paint-size-picker").performScrollTo().assertIsNotEnabled()
    }
}

class StrokeSizePreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thicknessUsesPhysicalPixelsAtEveryZoomAndDensityIncludingLargeSizes() {
        var density by mutableFloatStateOf(1f)
        var zoom by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(8)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
                StrokeSizePreview(ToolOptions(),width,zoom,Modifier.width(256.dp).testTag("preview"))
            }
        }
        fun thickness(): Double {
            val image = compose.onNodeWithTag("preview").captureToImage().toPixelMap()
            return (0 until image.height).sumOf { (1-image[image.width/2,it].red).toDouble() }
        }
        val actualWidth = thickness()
        assertTrue(actualWidth in 8.0..9.1)
        for (displayDensity in listOf(1f,2f)) for (scale in listOf(0.5f,1f,2f,5f)) {
            compose.runOnIdle { density = displayDensity; zoom = scale }
            assertEquals("Density $displayDensity, zoom $scale",actualWidth*scale,thickness(),0.6)
        }
        compose.runOnIdle { width = 100; zoom = 1f }
        val largeWidth = thickness()
        compose.runOnIdle { zoom = 5f }
        val image = compose.onNodeWithTag("preview").captureToImage().toPixelMap()
        val thickness = (0 until image.height).sumOf { (1-image[image.width/2,it].red).toDouble() }
        assertEquals(largeWidth*5,thickness,0.6)
        assertTrue(image.height > 500)
    }
}
