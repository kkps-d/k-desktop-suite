package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.*

class TransformToolsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir,"transforms-${UUID.randomUUID()}")
    private var size by mutableStateOf(DpSize(1200.dp,800.dp))
    private var fontScale by mutableFloatStateOf(1f)

    @Before fun setup() {
        val settings = (context.applicationContext as PaintApplication).settings
        runBlocking { settings.save(settings.preferences.first().copy(defaultCanvasWidth = 800,defaultCanvasHeight = 600)) }
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(root),
                settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                PaintTheme { Box(Modifier.requiredSize(size)) { PaintScreen(state,model) } }
            }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
    }

    @After fun cleanup() {
        compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
    }

    private fun waitForEdit() = compose.waitUntil(15000) { !model.state.value.busy }

    private fun insertSelection() {
        compose.runOnIdle {
            model.insert(Raster(40,20,IntArray(800) { if (it%40 < 20) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }))
            model.movePreview(PixelRect(220,180,80,40))
        }
    }

    @Test fun resizeAndSkewTabsApplyTogetherAndUndoRestoresDimensions() {
        compose.runOnIdle { model.resizeCanvas(100,50) }; waitForEdit()
        compose.onNodeWithContentDescription("Resize and Skew").performScrollTo().performClick()
        compose.onNodeWithText("Target: Image").assertIsDisplayed()
        compose.onNodeWithTag("resize-horizontal").performTextReplacement("200")
        compose.onNodeWithTag("resize-vertical").assertTextContains("200")
        compose.onNodeWithText("Skew",useUnmergedTree = true).performClick()
        compose.onNodeWithTag("skew-horizontal").performTextReplacement("10")
        File(context.cacheDir,"skew-dialog.png").outputStream().use {
            compose.onNodeWithTag("resize-skew-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNodeWithText("Resize",useUnmergedTree = true).performClick()
        compose.onNodeWithTag("resize-horizontal").assertTextContains("200")
        compose.onNodeWithTag("transform-dimensions").assertTextEquals("Result: 218 × 100 px")
        File(context.cacheDir,"resize-dialog.png").outputStream().use {
            compose.onNodeWithTag("resize-skew-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNodeWithText("OK").performClick(); waitForEdit()
        compose.runOnIdle {
            assertEquals(218,model.state.value.raster.width); assertEquals(100,model.state.value.raster.height)
            model.undo(); assertEquals(100,model.state.value.raster.width); assertEquals(50,model.state.value.raster.height)
        }
    }

    @Test fun invalidDialogAndCancelLeaveSelectionUntouched() {
        insertSelection()
        val before = model.state.value
        compose.onNodeWithContentDescription("Resize and Skew").performScrollTo().performClick()
        compose.onNodeWithText("Target: Selection").assertExists()
        compose.onNodeWithTag("resize-horizontal").performTextReplacement("0")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Dimensions must be greater than zero.").assertExists()
        compose.onNodeWithTag("resize-horizontal").performTextReplacement("2147483647")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Each dimension must be at most 8192 pixels.").assertExists()
        compose.onNodeWithText("Skew",useUnmergedTree = true).performClick()
        compose.onNodeWithTag("skew-horizontal").performTextReplacement("90")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertSame(before.raster,model.state.value.raster); assertEquals(before.preview,model.state.value.preview)
            assertEquals(before.dirty,model.state.value.dirty)
        }
    }

    @Test fun narrowLargeTextDialogKeepsBothTabsAndActionsReachable() {
        compose.runOnIdle { size = DpSize(360.dp,640.dp); fontScale = 2f; model.dialog(PaintDialog.ResizeSkew) }
        compose.onNodeWithTag("resize-horizontal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("resize-vertical").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Skew",useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("skew-vertical").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertNull(model.state.value.dialog); assertFalse(model.state.value.dirty) }
    }

    @Test fun pointerCancellationRestoresRotationAndNumericGeometryIsAvailable() {
        insertSelection()
        val before = model.state.value.preview
        val handle = compose.onNodeWithTag("selection-rotation-handle")
        handle.performTouchInput { down(center); moveBy(Offset(20f,40f)) }
        compose.runOnIdle { assertNotEquals(before,model.state.value.preview) }
        handle.performTouchInput { cancel() }
        compose.runOnIdle { assertEquals(before,model.state.value.preview) }
        handle.performTouchInput {
            down(0,center); moveTo(0,center+Offset(20f,40f))
            down(1,center+Offset(-30f,30f)); up(0); up(1)
        }
        compose.runOnIdle { assertEquals(before,model.state.value.preview) }
        handle.performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Rotation (degrees clockwise)").performScrollTo().performTextReplacement("45")
        compose.onNodeWithText("Update preview").performClick()
        compose.runOnIdle { assertEquals(45.0,requireNotNull(model.state.value.preview).rotationDegrees,0.001) }
    }

    @Test fun compactImageMenuProvidesResizeRotateAndBothFlips() {
        compose.runOnIdle { size = DpSize(360.dp,640.dp) }
        compose.onNodeWithContentDescription("Selection and image commands").performClick()
        listOf("Resize and Skew","Rotate right 90°","Rotate left 90°","Rotate 180°","Flip horizontal","Flip vertical").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("Rotate right 90°").performScrollTo().performClick(); waitForEdit()
        compose.runOnIdle {
            assertEquals(600,model.state.value.raster.width); assertEquals(800,model.state.value.raster.height)
            model.undo(); assertEquals(800,model.state.value.raster.width)
        }
    }

    @Test fun toolbarTransformsSelectionWithoutChangingCanvasUntilApply() {
        insertSelection()
        val before = model.state.value.raster
        compose.onNodeWithContentDescription("Rotate").performScrollTo().performClick()
        compose.onNodeWithText("Rotate right 90°").performClick()
        compose.runOnIdle {
            assertSame(before,model.state.value.raster); assertFalse(model.state.value.dirty)
            assertEquals(40,model.state.value.preview?.bounds?.width); assertEquals(80,model.state.value.preview?.bounds?.height)
            model.flip(true)
            model.movePreview(PixelRect(-20,180,40,80))
            model.applyPreview()
        }
        waitForEdit()
        compose.runOnIdle {
            assertEquals(before.width,model.state.value.raster.width); assertEquals(before.height,model.state.value.raster.height)
            assertNull(model.state.value.preview)
            model.undo(); assertArrayEquals(before.pixels,model.state.value.raster.pixels)
            model.redo(); assertTrue(model.state.value.dirty)
        }
    }

    @Test fun selectAllKeepsTheRotationHandleFullyInsideTheViewport() {
        compose.runOnIdle { model.selectAll() }
        val viewport = compose.onNodeWithTag("paint-viewport").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("selection-rotation-handle").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(handle.left >= viewport.left); assertTrue(handle.top >= viewport.top)
        assertTrue(handle.right <= viewport.right); assertTrue(handle.bottom <= viewport.bottom)
        assertEquals(48f,handle.width,0.01f); assertEquals(48f,handle.height,0.01f)
    }

    @Test fun rotationHandleSupportsFreeAnglesAndEscapeRestoresPreviousPreview() {
        insertSelection()
        val original = model.state.value.preview
        val handle = compose.onNodeWithTag("selection-rotation-handle").assertIsDisplayed()
        compose.onNodeWithTag("canvas-resize-BottomRight").assertDoesNotExist()
        val handleBounds = handle.fetchSemanticsNode().boundsInRoot
        val canvasBounds = compose.onNodeWithTag("paint-canvas").fetchSemanticsNode().boundsInRoot
        val center = model.state.value.viewport.screenPoint(requireNotNull(original).selectionCenter)
        val pivot = Offset(center.x+canvasBounds.left-handleBounds.left,center.y+canvasBounds.top-handleBounds.top)
        val vector = handleBounds.center-handleBounds.topLeft-pivot
        fun target(degrees: Float): Offset {
            val radians = degrees*PI.toFloat()/180
            return handleBounds.topLeft+pivot+Offset(vector.x*cos(radians)-vector.y*sin(radians),vector.x*sin(radians)+vector.y*cos(radians))
        }
        handle.performTouchInput { down(this.center); moveTo(target(30f)-handleBounds.topLeft) }
        compose.runOnIdle { assertEquals(30.0,requireNotNull(model.state.value.preview).rotationDegrees,1.0) }
        val movedBounds = handle.fetchSemanticsNode().boundsInRoot
        handle.performTouchInput { moveTo(target(60f)-movedBounds.topLeft) }
        compose.runOnIdle {
            assertEquals(60.0,requireNotNull(model.state.value.preview).rotationDegrees,1.0)
            assertFalse(model.state.value.dirty)
        }
        handle.performKeyInput { pressKey(Key.Escape) }
        compose.runOnIdle { assertEquals(original,model.state.value.preview) }
        handle.performTouchInput { up() }
    }

    @Test fun transformedPreviewMatchesAppliedPixelsAndRecoveryRetainsTransform() {
        insertSelection()
        compose.runOnIdle { model.rotateSelection(33.0); model.viewport(model.state.value.viewport.zoomed(2f)) }
        val preview = requireNotNull(model.state.value.preview)
        val source = model.state.value.raster
        val expected = source.render(preview,model.state.value.options)
        // Wait for the worker-backed selection overlay before comparing an interior pixel.
        val point = preview.selectionTransform.point(10.0,10.0)
        compose.waitUntil(15000) {
            val screen = model.state.value.viewport.screenPoint(point)
            val pixels = compose.onNodeWithTag("paint-canvas").captureToImage().toPixelMap()
            pixels[screen.x.roundToInt(),screen.y.roundToInt()].toArgb() == expected.pixel(point.x.toInt(),point.y.toInt())
        }
        val recovery = PaintRecovery(File(root,"roundtrip"))
        runBlocking {
            recovery.write(RecoverySnapshot(source,model.state.value.options,preview,null,model.state.value.viewport,
                null,"Untitled","image/png",null,false))
            val restored = requireNotNull(recovery.read()?.preview)
            assertEquals(preview.transform,restored.transform); assertEquals(preview.rotationCenter,restored.rotationCenter)
            assertArrayEquals(preview.selectionPixels().pixels,restored.selectionPixels().pixels)
            val manifest = File(root,"roundtrip/current.json")
            val json = org.json.JSONObject(manifest.readText())
            json.getJSONObject("preview").apply { remove("transform"); remove("rotationDegrees"); remove("rotationCenter") }
            manifest.writeText(json.toString())
            val legacy = requireNotNull(PaintRecovery(File(root,"roundtrip")).read()?.preview)
            assertNull(legacy.transform); assertEquals(0.0,legacy.rotationDegrees,0.0)
        }
        compose.runOnIdle { model.applyPreview() }; waitForEdit()
        compose.runOnIdle { assertArrayEquals(expected.pixels,model.state.value.raster.pixels) }
    }

    @Test fun copyAndCropUseTheTransformedSelection() {
        insertSelection()
        compose.runOnIdle { model.rotateSelection(90.0); model.copy() }
        compose.waitUntil(15000) { model.state.value.platform != null }
        val preview = requireNotNull(model.state.value.preview)
        val request = requireNotNull(model.state.value.platform)
        val bitmap = android.graphics.BitmapFactory.decodeFile(request.file?.path)
        assertArrayEquals(preview.selectionPixels().pixels,bitmap.raster().pixels)
        bitmap.recycle()
        val expected = model.state.value.raster.render(preview,model.state.value.options).extract(preview.bounds)
        compose.runOnIdle { model.platformDone(request.id,true); model.crop() }; waitForEdit()
        compose.runOnIdle {
            assertEquals(expected.width,model.state.value.raster.width)
            assertEquals(expected.height,model.state.value.raster.height)
            assertArrayEquals(expected.pixels,model.state.value.raster.pixels)
        }
    }
}
