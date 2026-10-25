package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
