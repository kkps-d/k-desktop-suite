package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PaintStatusTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var size by mutableStateOf(DpSize(1200.dp,800.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private var operationVisible by mutableStateOf(false)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"status-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                PaintTheme { Box(Modifier.requiredSize(size)) { PaintScreen(if (operationVisible) state.copy(busy = true) else state,model) } }
            }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
        compose.waitUntil(15000) { runCatching { compose.onNodeWithTag("paint-status-bar").assertIsDisplayed() }.isSuccess }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
    }

    @Test fun cursorAndSelectionUseImagePixelsAndKeepFixedWidths() {
        val cursorWidth = compose.onNodeWithTag("paint-cursor-position").fetchSemanticsNode().boundsInRoot.width
        val selectionWidth = compose.onNodeWithTag("paint-selection-dimensions").fetchSemanticsNode().boundsInRoot.width
        lateinit var origin: Point
        compose.runOnIdle {
            model.viewport(model.state.value.viewport.zoomed(0.5f))
            origin = model.state.value.viewport.screenPoint(Point(0.5f,0.5f))
        }
        compose.onNodeWithTag("paint-canvas").performMouseInput { enter(Offset(origin.x,origin.y)) }
        compose.onNodeWithTag("paint-cursor-position").assertTextEquals("0, 0 px")
        compose.runOnIdle { model.tool(Tool.Select); model.createPreview(Point(10f,20f),Point(40f,60f)) }
        compose.onNodeWithTag("paint-selection-dimensions").assertTextEquals("30 x 40 px")
        compose.runOnIdle { model.movePreview(PixelRect(10,20,1000,700)) }
        compose.onNodeWithTag("paint-selection-dimensions").assertTextEquals("1000 x 700 px")
        assertEquals(cursorWidth,compose.onNodeWithTag("paint-cursor-position").fetchSemanticsNode().boundsInRoot.width,0f)
        assertEquals(selectionWidth,compose.onNodeWithTag("paint-selection-dimensions").fetchSemanticsNode().boundsInRoot.width,0f)
    }

    @Test fun zoomPresetsButtonsAndSliderShareActualZoom() {
        compose.onNodeWithTag("paint-zoom-menu").performScrollTo().performClick()
        for (percent in listOf(10,25,50,100,200,300,400,500)) compose.onNodeWithText("$percent%",useUnmergedTree = true).assertExists()
        compose.onNodeWithText("25%",useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Zoom in").performScrollTo().performClick()
        compose.onNodeWithTag("paint-zoom-menu").assertTextContains("35%")
        compose.onNodeWithContentDescription("Zoom out").performClick()
        compose.onNodeWithTag("paint-zoom-menu").assertTextContains("25%")
        compose.onNodeWithTag("paint-zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.onNodeWithContentDescription("Zoom in").assertIsNotEnabled()
        compose.onNodeWithTag("paint-zoom-menu").assertTextContains("500%")
        compose.onNodeWithTag("paint-zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.1f) }
        compose.onNodeWithContentDescription("Zoom out").assertIsNotEnabled()
        compose.onNodeWithText("Actual",useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1f,model.state.value.viewport.scale,0f) }
    }

    @Test fun zoomMenuOpensAgainstBottomBar() {
        compose.onNodeWithTag("paint-zoom-menu").performScrollTo().performClick()
        val button = compose.onNodeWithTag("paint-zoom-menu").fetchSemanticsNode()
        val menu = compose.onNodeWithTag("dropdown-menu").fetchSemanticsNode()
        val gap = button.positionOnScreen.y - menu.positionOnScreen.y - menu.size.height
        assertTrue("Zoom menu is $gap px above its button",gap in 0f..8f)
    }

    @Test fun selectionDimensionsUpdateBeforePointerRelease() {
        lateinit var start: Point
        lateinit var end: Point
        compose.runOnIdle {
            model.tool(Tool.Select)
            model.viewport(model.state.value.viewport.zoomed(1f))
            start = model.state.value.viewport.screenPoint(Point(100f,100f))
            end = model.state.value.viewport.screenPoint(Point(200f,180f))
        }
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(Offset(start.x,start.y))
            moveTo(Offset(end.x,end.y))
        }
        compose.onNodeWithTag("paint-selection-dimensions").assertTextEquals("100 x 80 px")
        compose.onNodeWithTag("paint-cursor-position").assertTextEquals("200, 180 px")
        compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
        compose.onNodeWithTag("paint-selection-dimensions").assertTextEquals("100 x 80 px")
    }

    @Test fun statusBarMenuHasCheckedToggle() {
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Status bar").assertIsOn().performClick()
        compose.onNodeWithTag("paint-status-bar").assertDoesNotExist()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Status bar").assertIsOff().performClick()
        compose.onNodeWithTag("paint-status-bar").assertIsDisplayed()
    }

    @Test fun canvasDialogValidatesSizeAndResizeSupportsUndoRedo() {
        compose.runOnIdle {
            model.options { it.copy(background = 0xFF00FF00.toInt()) }
            model.tool(Tool.Rectangle)
            model.createPreview(Point(10f,10f),Point(40f,40f))
        }
        compose.onNodeWithTag("paint-canvas-dimensions").performClick()
        compose.onNodeWithText("Width").performTextReplacement("0")
        compose.onNodeWithText("Resize",useUnmergedTree = true).performClick()
        compose.onNodeWithText("Images must be",substring = true).assertIsDisplayed()
        compose.onNodeWithText("Width").performTextReplacement("1000")
        compose.onNodeWithText("Height").performTextReplacement("700")
        compose.onNodeWithText("Resize",useUnmergedTree = true).performClick()
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.raster.width == 1000 }
        compose.onNodeWithTag("paint-canvas-dimensions").assertTextContains("1000 x 700 px")
        compose.runOnIdle {
            assertEquals(0xFF00FF00.toInt(),model.state.value.raster.pixel(999,699))
            assertEquals(BLACK,model.state.value.raster.pixel(10,10))
            model.undo()
            assertEquals(800,model.state.value.raster.width)
            assertEquals(BLACK,model.state.value.raster.pixel(10,10))
            model.redo()
            assertEquals(1000,model.state.value.raster.width)
            assertEquals(700,model.state.value.raster.height)
        }
    }

    @Test fun narrowWindowKeepsCanvasSizeAndZoomReachableWithLargeText() {
        compose.runOnIdle { size = DpSize(360.dp,640.dp); fontScale = 2f }
        compose.onNodeWithTag("paint-canvas-dimensions").performScrollTo().performClick()
        compose.onNodeWithText("Canvas size").assertIsDisplayed()
        compose.onNodeWithText("Cancel",useUnmergedTree = true).performClick()
        compose.onNodeWithTag("paint-zoom-menu").performScrollTo().performClick()
        compose.onNodeWithText("25%",useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(0.25f,model.state.value.viewport.scale,0f) }
    }

    @Test fun statusBarStacksInformationAboveZoomAndReturnsToOneRowWhenResized() {
        for (window in listOf(DpSize(360.dp,640.dp),DpSize(700.dp,400.dp),DpSize(900.dp,1000.dp),DpSize(1200.dp,800.dp))) {
            compose.runOnIdle { size = window }
            val selection = compose.onNodeWithTag("paint-selection-dimensions").fetchSemanticsNode().boundsInRoot
            val cursor = compose.onNodeWithTag("paint-cursor-position").fetchSemanticsNode().boundsInRoot
            val canvas = compose.onNodeWithTag("paint-canvas-dimensions").fetchSemanticsNode().boundsInRoot
            val zoom = compose.onNodeWithTag("paint-zoom-menu").fetchSemanticsNode().boundsInRoot
            val slider = compose.onNodeWithTag("paint-zoom-slider").fetchSemanticsNode().boundsInRoot
            assertEquals(selection.center.y,cursor.center.y,1f)
            assertEquals(selection.center.y,canvas.center.y,1f)
            assertTrue(selection.right <= cursor.left)
            assertTrue(cursor.right <= canvas.left)
            assertEquals(zoom.center.y,slider.center.y,1f)
            if (window.width < 1200.dp) {
                assertTrue("Information stays above zoom at $window",canvas.bottom <= zoom.top)
            } else {
                assertEquals(canvas.center.y,zoom.center.y,1f)
            }
        }
    }

    @Test fun allEightHandlesResizeInImagePixelsAtDifferentZoomLevels() {
        for (scale in listOf(0.5f,0.75f)) {
            for (handle in CanvasResizeHandle.entries) {
                compose.runOnIdle { model.resizeCanvas(800,600) }
                compose.waitUntil(15000) { !model.state.value.busy && model.state.value.raster.width == 800 && model.state.value.raster.height == 600 }
                compose.runOnIdle { model.viewport(model.state.value.viewport.zoomed(scale)) }
                compose.onNodeWithTag("canvas-resize-${handle.name}").assertIsDisplayed().performTouchInput {
                    swipe(center,center + Offset(-handle.horizontal * 80f * scale,-handle.vertical * 60f * scale),200)
                }
                val width = if (handle.horizontal == 0) 800 else 720
                val height = if (handle.vertical == 0) 600 else 540
                compose.waitUntil(15000) { !model.state.value.busy && model.state.value.raster.width == width && model.state.value.raster.height == height }
            }
        }
    }

    @Test fun handleDragPreviewsSizeAndCancellationLeavesPixelsAndHistoryUntouched() {
        compose.runOnIdle { model.viewport(model.state.value.viewport.zoomed(0.5f)) }
        compose.onNodeWithTag("canvas-resize-Right").performTouchInput {
            down(center)
            moveTo(center + Offset(40f,0f))
        }
        compose.onNodeWithTag("paint-canvas-dimensions").assertTextContains("880 x 600 px")
        compose.runOnIdle {
            assertEquals(800,model.state.value.raster.width)
            assertFalse(model.state.value.canUndo)
        }
        compose.onNodeWithTag("canvas-resize-Right").performTouchInput { cancel() }
        compose.onNodeWithTag("paint-canvas-dimensions").assertTextContains("800 x 600 px")
        compose.runOnIdle { assertFalse(model.state.value.canUndo) }
        compose.onNodeWithTag("canvas-resize-Right").performTouchInput {
            down(center)
            moveTo(center + Offset(40f,0f))
        }
        compose.onNodeWithTag("canvas-resize-Right").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        compose.onNodeWithTag("canvas-resize-Right").performTouchInput { up() }
        compose.onNodeWithTag("paint-canvas-dimensions").assertTextContains("800 x 600 px")
        compose.runOnIdle { assertFalse(model.state.value.canUndo) }
    }

    @Test fun focusedHandleResizesWithArrowKeys() {
        compose.runOnIdle { model.viewport(model.state.value.viewport.zoomed(0.5f)) }
        compose.onNodeWithTag("canvas-resize-Right").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("canvas-resize-Right").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.raster.width == 801 }
        compose.runOnIdle {
            assertEquals(600,model.state.value.raster.height)
            model.undo()
            assertEquals(800,model.state.value.raster.width)
            assertFalse(model.state.value.canUndo)
        }
    }

    @Test fun draggingTopLeftPreservesOppositeCornerAndUndoRestoresOriginal() {
        compose.runOnIdle {
            model.viewport(model.state.value.viewport.zoomed(0.5f))
            model.stroke(listOf(Point(100f,100f)))
        }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.canUndo }
        compose.onNodeWithTag("canvas-resize-TopLeft").performTouchInput {
            swipe(center,center + Offset(20f,10f),200)
        }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.raster.width == 760 }
        compose.runOnIdle {
            assertEquals(580,model.state.value.raster.height)
            assertEquals(BLACK,model.state.value.raster.pixel(60,80))
            model.undo()
            assertEquals(800,model.state.value.raster.width)
            assertEquals(600,model.state.value.raster.height)
            assertEquals(BLACK,model.state.value.raster.pixel(100,100))
        }
    }

    @Test fun headerHasHelpSettingsAndLeadingUnsavedIndicator() {
        compose.onNodeWithContentDescription("KD Paint").assertDoesNotExist()
        compose.onNodeWithContentDescription("More commands").assertDoesNotExist()
        compose.onNodeWithText("Help").assertIsDisplayed().performClick()
        compose.onNodeWithText("About KD Paint").performClick()
        compose.onNodeWithTag("about-dialog").assertIsDisplayed()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText("Developed by kkps-d").assertIsDisplayed()
        compose.onNodeWithText("Source code").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        compose.runOnIdle { model.stroke(listOf(Point(100f,100f))) }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.dirty }
        val dot = compose.onNodeWithTag("paint-unsaved-indicator").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("paint-document-title").fetchSemanticsNode().boundsInRoot
        assertTrue(dot.right < title.left)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-page").assertIsDisplayed()
        compose.onNodeWithTag("classic-ribbon").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("paint-unsaved-indicator").assertIsDisplayed()
        compose.runOnIdle { assertEquals(BLACK,model.state.value.raster.pixel(100,100)) }
    }

    @Test fun aboutLogoOpensDebugAndBackReturnsToPaint() {
        compose.onNodeWithText("Help").performClick()
        compose.onNodeWithText("About KD Paint").performClick()
        compose.onNodeWithTag("about-paint-logo").performClick()

        compose.onNodeWithTag("about-dialog").assertDoesNotExist()
        compose.onNodeWithTag("paint-debug-screen").assertIsDisplayed()
        compose.onNodeWithText("Debug").assertIsDisplayed()
        compose.onNodeWithText("Components").assertIsDisplayed()
        compose.onNodeWithTag("classic-ribbon").assertDoesNotExist()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("paint-debug-screen").assertDoesNotExist()
        compose.onNodeWithTag("classic-ribbon").assertIsDisplayed()
    }

    @Test fun operationControlsStayInTheHeaderAndStatusBarIsCompact() {
        compose.onNodeWithTag("paint-status-bar").assertHeightIsEqualTo(32.dp)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { operationVisible = true }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("paint-operation-progress").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(250)
        compose.mainClock.autoAdvance = true
        compose.onNode(hasTestTag("paint-operation-progress") and hasAnyAncestor(hasTestTag("paint-header"))).assertIsDisplayed()
        compose.onNode(hasContentDescription("Cancel operation") and hasAnyAncestor(hasTestTag("paint-header"))).assertIsDisplayed()
        val progress = compose.onNodeWithTag("paint-operation-progress").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithContentDescription("Save (Ctrl+S)").fetchSemanticsNode().boundsInRoot
        assertTrue(progress.right <= save.left)
        compose.runOnIdle { size = DpSize(360.dp,640.dp) }
        compose.onNodeWithText("Help").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel operation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Redo (Ctrl+Shift+Z)").assertIsDisplayed()
    }

    @Test fun briefCommitsBlockToolbarInputWithoutFlashingItsColors() {
        val controls = listOf(compose.onNodeWithContentDescription("Save (Ctrl+S)"),
            compose.onNodeWithContentDescription("Pencil"),compose.onNodeWithTag("paint-brush-picker"),
            compose.onNodeWithTag("paint-shape-tool"),compose.onNodeWithTag("paint-size-picker"))
        fun pixels(node: SemanticsNodeInteraction): IntArray {
            if (node !== controls.first()) node.performScrollTo()
            val image = node.captureToImage().asAndroidBitmap()
            return IntArray(image.width * image.height).also {
                image.getPixels(it,0,image.width,0,0,image.width,image.height)
            }
        }
        controls.forEachIndexed { index,node ->
            if (index > 0) node.performScrollTo()
            node.assertIsEnabled()
        }
        val before = controls.map(::pixels)
        compose.mainClock.autoAdvance = false
        repeat(3) {
            compose.runOnIdle { operationVisible = true }
            compose.mainClock.advanceTimeBy(100)
            controls.forEachIndexed { index,node ->
                node.assertIsNotEnabled()
                assertArrayEquals("Control $index during brief commit",before[index],pixels(node))
            }
            compose.onNodeWithTag("paint-operation-progress").assertDoesNotExist()
            compose.runOnIdle { operationVisible = false }
            compose.mainClock.advanceTimeBy(32)
            controls.forEachIndexed { index,node ->
                node.assertIsEnabled()
                assertArrayEquals("Control $index after commit",before[index],pixels(node))
            }
        }
        compose.runOnIdle { operationVisible = true }
        compose.mainClock.advanceTimeBy(600)
        controls.forEachIndexed { index,node ->
            node.assertIsNotEnabled()
            assertFalse("Control $index shows sustained busy state",before[index].contentEquals(pixels(node)))
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun defaultCanvasSizePersistsAndOnlyChangesNewImages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = (context.applicationContext as PaintApplication).settings
        val original = model.state.value.preferences
        var reopened: PaintViewModel? = null
        try {
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Apply").assertDoesNotExist()
            compose.onNodeWithTag("canvas-width-input").performScrollTo().performTextReplacement("0")
            compose.onNodeWithText("Images must be",substring = true).assertIsDisplayed()
            compose.runOnIdle { assertEquals(original.defaultCanvasWidth,model.state.value.preferences.defaultCanvasWidth) }
            compose.onNodeWithTag("canvas-width-input").performTextReplacement("640")
            compose.onNodeWithTag("canvas-height-input").performTextReplacement("480")
            runBlocking { withTimeout(15000) { settings.preferences.first { it.defaultCanvasWidth == 640 && it.defaultCanvasHeight == 480 } } }
            compose.runOnIdle {
                assertEquals(original.defaultCanvasWidth,model.state.value.raster.width)
                assertEquals(original.defaultCanvasHeight,model.state.value.raster.height)
            }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.runOnIdle { model.newImage() }
            compose.onNodeWithTag("paint-canvas-dimensions").assertTextContains("640 x 480 px")
            compose.runOnIdle {
                reopened = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"default-size-${UUID.randomUUID()}")),
                    settings,SavedStateHandle(),scope)
            }
            compose.waitUntil(15000) { reopened?.state?.value?.loading == false }
            compose.runOnIdle {
                val reopenedState = requireNotNull(reopened).state.value
                assertEquals(640,reopenedState.raster.width)
                assertEquals(480,reopenedState.raster.height)
            }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("canvas-width-input").performScrollTo().performTextReplacement("")
            compose.onNodeWithTag("reset-default-canvas-size").performClick()
            compose.onNodeWithTag("canvas-width-input").assertTextContains("800")
            compose.onNodeWithTag("canvas-height-input").assertTextContains("600")
            runBlocking { withTimeout(15000) { settings.preferences.first { it.defaultCanvasWidth == 800 && it.defaultCanvasHeight == 600 } } }
            compose.runOnIdle { assertEquals(640,model.state.value.raster.width); assertEquals(480,model.state.value.raster.height) }
        } finally {
            compose.runOnIdle { reopened?.viewModelScope?.cancel(); model.preferences { original } }
            runBlocking { withTimeout(15000) { settings.preferences.first { it == original } } }
        }
    }

    @Test fun narrowSettingsPageScrollsAtLargeFontSizes() {
        compose.runOnIdle { size = DpSize(360.dp,640.dp); fontScale = 2f }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Auto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("canvas-width-input").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reset-default-canvas-size").assertIsDisplayed()
        compose.onNodeWithContentDescription("JPEG quality").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("paint-canvas").assertIsDisplayed()
    }
}
