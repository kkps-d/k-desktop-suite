package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.toPixelMap
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
import kotlin.math.roundToInt

class CanvasToolsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var size by mutableStateOf(DpSize(1200.dp,800.dp))

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"tools-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            PaintTheme { Box(Modifier.requiredSize(size)) { PaintScreen(state,model) } }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
    }

    private fun screenPoint(point: Point): Offset {
        val screen = model.state.value.viewport.screenPoint(point)
        return Offset(screen.x,screen.y)
    }

    private fun assertCanvasColors(vararg samples: Pair<Point,Int>) {
        val pixels = compose.onNodeWithTag("paint-canvas").captureToImage().toPixelMap()
        for ((point,color) in samples) {
            val screen = screenPoint(point)
            assertEquals("Canvas color at $point",color,pixels[screen.x.roundToInt(),screen.y.roundToInt()].toArgb())
        }
    }

    @Test fun brushGalleryOffersPreviewsAndRemembersEachWidthAtWideAndCompactWidths() {
        for (width in listOf(1200.dp,360.dp)) {
            compose.runOnIdle { size = DpSize(width,800.dp) }
            compose.onNodeWithTag("paint-brush-picker").performScrollTo().performClick()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null),"brush-picker-${width.value.toInt()}.png").outputStream().use {
                compose.onNodeWithTag("dropdown-menu").captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            for (style in BrushStyle.entries) {
                compose.onNodeWithTag("paint-brush-${style.name}").assertExists()
                    .performScrollTo().performClick()
                compose.runOnIdle {
                    assertEquals(Tool.Brush,model.state.value.options.tool)
                    assertEquals(style,model.state.value.options.brushStyle)
                    model.options { it.copy(width = style.ordinal+10) }
                }
                compose.onNodeWithTag("paint-brush-picker")
                    .assertContentDescriptionEquals("Choose brush: ${style.label}").performClick()
            }
            compose.onNodeWithTag("paint-brush-Watercolor").assertIsSelected().assertIsFocused()
            compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(Key.Escape) }
            compose.onNodeWithTag("dropdown-menu").assertDoesNotExist()
            compose.runOnIdle {
                for (style in BrushStyle.entries) {
                    model.brush(style)
                    assertEquals(style.ordinal+10,model.state.value.options.width)
                }
                model.tool(Tool.Pencil)
                model.options { it.copy(width = 2) }
                model.tool(Tool.Brush)
                assertEquals(18,model.state.value.options.width)
            }
            compose.onNodeWithTag("paint-brush-picker").performClick()
            compose.onNodeWithTag("paint-brush-Watercolor").assertIsFocused()
            compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.Enter) }
            compose.runOnIdle { assertEquals(BrushStyle.Graphite,model.state.value.options.brushStyle) }
        }
    }

    @Test fun texturedLivePreviewMatchesCommittedPixelsAndUndo() {
        val start = Point(100f,100f)
        val end = Point(200f,140f)
        for (style in BrushStyle.entries) {
            compose.runOnIdle {
                model.brush(style)
                model.options { it.copy(width = 24) }
                model.viewport(model.state.value.viewport.actual())
            }
            compose.onNodeWithTag("paint-canvas").performTouchInput {
                down(screenPoint(start)); moveTo(screenPoint(end))
            }
            // Wait for the worker-rendered preview before comparing it with the commit.
            try { compose.waitUntil(15000) {
                val image = compose.onNodeWithTag("paint-canvas").captureToImage().toPixelMap()
                // Grain and spray can leave the exact center pixel untouched.
                (145..155).any { x -> (115..125).any { y ->
                    val p = screenPoint(Point(x.toFloat(),y.toFloat()))
                    image[p.x.roundToInt(),p.y.roundToInt()].toArgb() != WHITE
                } }
            } } catch (error: ComposeTimeoutException) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                File(context.getExternalFilesDir(null),"brush-preview-failure.png").outputStream().use {
                    compose.onNodeWithTag("paint-canvas").captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
                throw AssertionError("No live preview for $style at ${screenPoint(start)}",error)
            }
            val before = compose.onNodeWithTag("paint-canvas").captureToImage().toPixelMap()
            compose.runOnIdle {
                assertFalse(model.state.value.dirty)
                assertTrue(model.state.value.raster.pixels.all { it == WHITE })
            }
            compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
            compose.waitUntil(15000) { !model.state.value.busy }
            val after = compose.onNodeWithTag("paint-canvas").captureToImage().toPixelMap()
            for (y in 88..152) for (x in 88..212) {
                val p = screenPoint(Point(x.toFloat(),y.toFloat()))
                assertEquals("$style at $x,$y",before[p.x.roundToInt(),p.y.roundToInt()],after[p.x.roundToInt(),p.y.roundToInt()])
            }
            compose.runOnIdle {
                val result = model.state.value.raster.pixels.copyOf()
                model.undo()
                assertTrue(model.state.value.raster.pixels.all { it == WHITE })
                model.redo()
                assertArrayEquals(result,model.state.value.raster.pixels)
                model.undo()
            }
        }
    }

    @Test fun shapesShowTheirOutlineAndFillBeforePointerRelease() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val start = Point(100f,100f)
        val end = Point(200f,160f)
        for (tool in listOf(Tool.Line,Tool.Rectangle,Tool.Ellipse)) {
            compose.runOnIdle {
                model.cancelPreview()
                model.tool(tool)
                model.options { it.copy(foreground = red,background = blue,width = 8,outline = true,fill = true,constrain = false) }
                model.viewport(model.state.value.viewport.actual())
            }
            val original = model.state.value.raster
            compose.onNodeWithTag("paint-canvas").performTouchInput { down(screenPoint(start)); moveTo(screenPoint(end)) }
            compose.runOnIdle {
                assertNull(model.state.value.preview)
                assertSame(original,model.state.value.raster)
                assertFalse(model.state.value.dirty)
            }
            val samples = if (tool == Tool.Line) arrayOf(Point(150f,130f) to red,Point(150f,115f) to WHITE)
                else arrayOf(Point(150f,102f) to red,Point(150f,130f) to blue)
            assertCanvasColors(*samples)
            compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
            compose.runOnIdle { assertEquals(PixelRect(100,100,100,60),model.state.value.preview?.bounds) }
            assertCanvasColors(*samples)
        }
    }

    @Test fun constrainedReverseDragMatchesTheReleasedShape() {
        compose.runOnIdle {
            model.tool(Tool.Rectangle)
            model.options { it.copy(width = 8,fill = true,foreground = BLACK,background = 0xFFFF0000.toInt(),constrain = true) }
            model.viewport(model.state.value.viewport.actual())
        }
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screenPoint(Point(220f,220f))); moveTo(screenPoint(Point(120f,180f)))
        }
        assertCanvasColors(Point(170f,122f) to BLACK,Point(170f,150f) to 0xFFFF0000.toInt())
        compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
        compose.runOnIdle { assertEquals(PixelRect(120,120,100,100),model.state.value.preview?.bounds) }
        assertCanvasColors(Point(170f,122f) to BLACK,Point(170f,150f) to 0xFFFF0000.toInt())
    }

    @Test fun movingSelectionShowsPixelsAndClearsSourceBeforePointerRelease() {
        compose.runOnIdle {
            model.tool(Tool.Pencil)
            model.options { it.copy(width = 20,foreground = BLACK,background = WHITE) }
            model.viewport(model.state.value.viewport.actual())
            model.stroke(listOf(Point(130f,130f)))
        }
        compose.waitUntil(15000) { !model.state.value.busy }
        compose.runOnIdle {
            model.tool(Tool.Select)
            model.createPreview(Point(100f,100f),Point(160f,160f))
        }
        val original = model.state.value.raster
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screenPoint(Point(120f,120f))); moveTo(screenPoint(Point(240f,220f)))
        }
        compose.runOnIdle {
            assertSame(original,model.state.value.raster)
            assertEquals(PixelRect(220,200,60,60),model.state.value.preview?.bounds)
        }
        assertCanvasColors(Point(130f,130f) to WHITE,Point(250f,230f) to BLACK)
        compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
        assertCanvasColors(Point(130f,130f) to WHITE,Point(250f,230f) to BLACK)
        compose.runOnIdle { model.cancelPreview() }
        assertCanvasColors(Point(130f,130f) to BLACK,Point(250f,230f) to WHITE)
    }

    @Test fun secondFingerCancelsShapePreviewWithoutChangingPixels() {
        compose.runOnIdle { model.tool(Tool.Line); model.options { it.copy(width = 8,constrain = false) } }
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(0,screenPoint(Point(100f,100f))); moveTo(0,screenPoint(Point(200f,160f)))
        }
        assertCanvasColors(Point(150f,130f) to BLACK)
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(1,screenPoint(Point(300f,200f))); up(0); up(1)
        }
        compose.runOnIdle {
            assertNull(model.state.value.preview)
            assertFalse(model.state.value.dirty)
        }
        assertCanvasColors(Point(150f,130f) to WHITE)
    }

    @Test fun resizedTransparentSelectionMatchesCommittedPixelsAtZoom() {
        compose.runOnIdle {
            model.insert(Raster(40,40,IntArray(1600) { 0x80FF0000.toInt() }))
            model.movePreview(PixelRect(220,180,80,80))
            model.viewport(model.state.value.viewport.zoomed(1.25f))
        }
        val sample = Point(260f,220f) to 0xFFFF7F7F.toInt()
        assertCanvasColors(sample)
        compose.runOnIdle { assertFalse(model.state.value.dirty); model.applyPreview() }
        compose.waitUntil(15000) { !model.state.value.busy }
        assertCanvasColors(sample)
        compose.runOnIdle { model.undo() }
        assertCanvasColors(Point(260f,220f) to WHITE)
    }

    @Test fun handIsAvailableInExpandedRibbonAndCompactToolMenu() {
        compose.onNodeWithContentDescription("Hand").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Tool.Pan,model.state.value.options.tool); size = DpSize(360.dp,640.dp) }
        compose.onNodeWithContentDescription("Choose drawing tool").performClick()
        compose.onNodeWithText("Pencil").performClick()
        compose.onNodeWithContentDescription("Choose drawing tool").performClick()
        compose.onNodeWithText("Hand").performClick()
        compose.runOnIdle { assertEquals(Tool.Pan,model.state.value.options.tool) }
    }

    @Test fun oneFingerHandDragPansWithoutEditingOrResizing() {
        lateinit var before: PaintUiState
        compose.runOnIdle {
            model.tool(Tool.Pan)
            model.viewport(model.state.value.viewport.zoomed(3f))
            before = model.state.value
        }
        compose.onNodeWithTag("canvas-resize-BottomRight").assertDoesNotExist()
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            swipe(center,center + Offset(-60f,-40f))
        }
        compose.runOnIdle {
            val after = model.state.value
            assertEquals(before.viewport.center.x + 20f,after.viewport.center.x,0.1f)
            assertEquals(before.viewport.center.y + 40f/3,after.viewport.center.y,0.1f)
            assertEquals(before.viewport.scale,after.viewport.scale,0f)
            assertSame(before.raster,after.raster)
            assertFalse(after.dirty)
            assertNull(after.preview)
            assertNull(after.selection)
        }
    }

    @Test fun twoFingerPanAndPinchRemainAvailableWithHandAndEditingTools() {
        for (tool in listOf(Tool.Pan,Tool.Pencil,Tool.Select)) {
            lateinit var before: Viewport
            compose.runOnIdle {
                model.tool(tool)
                model.viewport(model.state.value.viewport.zoomed(2f).copy(center = Point(400f,300f)))
                before = model.state.value.viewport
            }
            compose.onNodeWithTag("paint-canvas").performTouchInput {
                down(0,center - Offset(50f,0f)); down(1,center + Offset(50f,0f))
                updatePointerTo(0,center + Offset(-80f,20f))
                updatePointerTo(1,center + Offset(20f,20f)); move()
                up(0)
                updatePointerTo(1,center + Offset(40f,40f)); move()
                up(1)
            }
            compose.runOnIdle {
                assertEquals(before.center.x + 5f,model.state.value.viewport.center.x,0.1f)
                assertEquals(before.center.y - 20f,model.state.value.viewport.center.y,0.1f)
                assertEquals(before.scale,model.state.value.viewport.scale,0f)
            }
            compose.onNodeWithTag("paint-canvas").performTouchInput {
                down(0,center - Offset(40f,0f)); down(1,center + Offset(40f,0f))
                updatePointerTo(0,center - Offset(80f,0f))
                updatePointerTo(1,center + Offset(80f,0f)); move()
                up(0); up(1)
            }
            compose.runOnIdle {
                assertEquals(4f,model.state.value.viewport.scale,0.01f)
                assertFalse(model.state.value.dirty)
                assertTrue(model.state.value.raster.pixels.all { it == WHITE })
                assertNull(model.state.value.preview)
                assertNull(model.state.value.selection)
            }
        }
    }

    @Test fun switchingToolsClearsSelectionAndAllowsDrawingOutsideIt() {
        compose.runOnIdle {
            for (tool in Tool.entries.filter { it != Tool.Select }) {
                model.tool(Tool.Select)
                model.createPreview(Point(10f,10f),Point(40f,40f))
                model.tool(Tool.Select)
                assertNotNull(model.state.value.selection)
                model.tool(tool)
                assertNull("Selection after switching to $tool",model.state.value.selection)
            }
            model.tool(Tool.Select)
            model.createPreview(Point(10f,10f),Point(40f,40f))
            model.tool(Tool.Pencil)
            model.stroke(listOf(Point(70f,70f)))
        }
        compose.waitUntil(15000) { !model.state.value.busy }
        compose.runOnIdle { assertEquals(BLACK,model.state.value.raster.pixel(70,70)) }
    }

    @Test fun switchingToolsCommitsMovedSelectionAndKeepsUndo() {
        compose.runOnIdle { model.tool(Tool.Pencil); model.stroke(listOf(Point(20f,20f))) }
        compose.waitUntil(15000) { !model.state.value.busy }
        compose.runOnIdle {
            model.tool(Tool.Select)
            model.createPreview(Point(10f,10f),Point(40f,40f))
            model.movePreview(PixelRect(60,60,30,30))
            model.tool(Tool.Pencil)
        }
        compose.waitUntil(15000) { !model.state.value.busy }
        compose.runOnIdle {
            val state = model.state.value
            assertEquals(Tool.Pencil,state.options.tool)
            assertNull(state.selection)
            assertNull(state.preview)
            assertEquals(WHITE,state.raster.pixel(20,20))
            assertEquals(BLACK,state.raster.pixel(70,70))
            model.undo()
            assertEquals(BLACK,model.state.value.raster.pixel(20,20))
            assertEquals(WHITE,model.state.value.raster.pixel(70,70))
        }
    }
}
