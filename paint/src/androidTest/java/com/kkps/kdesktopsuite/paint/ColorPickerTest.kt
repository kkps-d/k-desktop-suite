package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
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

class ColorPickerTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private lateinit var settings: PaintSettings
    private lateinit var originalPreferences: PaintPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var width by mutableStateOf(1200.dp)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = (context.applicationContext as PaintApplication).settings
        runBlocking {
            originalPreferences = settings.preferences.first()
            settings.save(PaintPreferences())
        }
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"colors-${UUID.randomUUID()}")),
                settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            PaintTheme { Box(Modifier.requiredSize(width,800.dp)) { PaintScreen(state,model) } }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
        if (::originalPreferences.isInitialized) runBlocking { settings.save(originalPreferences) }
    }

    private fun screen(point: Point): Offset {
        val p = model.state.value.viewport.screenPoint(point)
        return Offset(p.x,p.y)
    }

    private fun prepareEyedropper() {
        compose.runOnIdle { model.brush(BrushStyle.Solid); model.strokeWidth(40); model.stroke(listOf(Point(100f,100f),Point(150f,100f))) }
        compose.waitUntil(15000) { !model.state.value.busy }
        compose.runOnIdle { model.options { it.copy(foreground = 0xFF3366CC.toInt()) }; model.tool(Tool.Eyedropper) }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun touchPreviewsWhileHeldAndReleaseSavesFinalColorWithoutSwitchingTools() {
        prepareEyedropper()
        compose.onNodeWithText("Assign to custom color").performClick().assertIsOn()
        val raster = model.state.value.raster
        compose.onNodeWithTag("paint-canvas").performTouchInput { down(screen(Point(200f,100f))) }
        compose.onNodeWithTag("paint-eyedropper-preview").assertContentDescriptionEquals("Picked color #FFFFFF")
        compose.runOnIdle { assertEquals(0xFF3366CC.toInt(),model.state.value.options.foreground) }
        compose.onNodeWithTag("paint-canvas").performTouchInput { moveTo(screen(Point(120f,100f))) }
        compose.onNodeWithTag("paint-eyedropper-preview").assertContentDescriptionEquals("Picked color #000000")
        capture("paint-eyedropper-held.png")
        compose.runOnIdle { assertEquals(WHITE,model.state.value.preferences.customColors[0]) }
        compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
        compose.onNodeWithTag("paint-eyedropper-preview").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(Tool.Eyedropper,model.state.value.options.tool)
            assertEquals(BLACK,model.state.value.options.foreground)
            assertEquals(BLACK,model.state.value.preferences.customColors[0])
            assertSame(raster,model.state.value.raster)
        }
        compose.onNodeWithTag("paint-canvas").performTouchInput { click(screen(Point(200f,100f))) }
        compose.runOnIdle {
            assertEquals(Tool.Eyedropper,model.state.value.options.tool)
            assertEquals(WHITE,model.state.value.preferences.customColors[0])
        }
    }

    @Test fun mousePreviewAndAllTenSlotTargetsWorkAtCompactWidth() {
        prepareEyedropper()
        compose.runOnIdle { width = 360.dp }
        compose.onNodeWithText("Assign to custom color").assertIsOff().performClick().assertIsOn()
        compose.onAllNodes(hasContentDescription("Custom color ",substring = true) and
            hasAnyAncestor(hasTestTag("paint-secondary-toolbar"))).assertCountEquals(10)
        compose.onNodeWithContentDescription("Custom color 10 #FFFFFF").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("paint-canvas").performMouseInput { moveTo(screen(Point(120f,100f))); press() }
        compose.onNodeWithTag("paint-eyedropper-preview").assertContentDescriptionEquals("Picked color #000000")
        compose.onNodeWithTag("paint-canvas").performMouseInput { release() }
        compose.onNodeWithTag("paint-eyedropper-preview").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(Tool.Eyedropper,model.state.value.options.tool)
            assertEquals(List(9) { WHITE } + BLACK,model.state.value.preferences.customColors)
        }
        compose.onNodeWithContentDescription("Custom color 10 #000000").assertIsSelected()
        compose.onNodeWithText("Assign to custom color").performScrollTo().performClick().assertIsOff()
        compose.onAllNodes(hasContentDescription("Custom color ",substring = true) and
            hasAnyAncestor(hasTestTag("paint-secondary-toolbar"))).assertCountEquals(0)
        compose.onNodeWithTag("paint-canvas").performMouseInput { moveTo(screen(Point(200f,100f))); press(); release() }
        compose.runOnIdle {
            assertEquals(WHITE,model.state.value.options.foreground)
            assertEquals(List(9) { WHITE } + BLACK,model.state.value.preferences.customColors)
        }
        compose.onNodeWithText("Assign to custom color").performClick()
        compose.onNodeWithContentDescription("Custom color 10 #000000").assertIsSelected()
    }

    @Test fun assignmentDefaultsOffAndPicksOnlyTheSelectedPrimaryOrSecondaryColor() {
        prepareEyedropper()
        compose.runOnIdle { model.options { it.copy(background = 0xFF00AA00.toInt()) } }
        compose.onNodeWithText("Assign to custom color").assertIsOff()
        compose.onAllNodes(hasContentDescription("Custom color ",substring = true) and
            hasAnyAncestor(hasTestTag("paint-secondary-toolbar"))).assertCountEquals(0)
        compose.onNodeWithTag("paint-canvas").performTouchInput { click(screen(Point(120f,100f))) }
        compose.runOnIdle {
            assertEquals(BLACK,model.state.value.options.foreground)
            assertEquals(0xFF00AA00.toInt(),model.state.value.options.background)
            assertEquals(List(10) { WHITE },model.state.value.preferences.customColors)
        }
        compose.onNodeWithContentDescription("Secondary color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Eyedropper").performScrollTo().performClick()
        compose.onNodeWithTag("paint-canvas").performTouchInput { click(screen(Point(200f,100f))) }
        compose.runOnIdle {
            assertEquals(Tool.Eyedropper,model.state.value.options.tool)
            assertEquals(BLACK,model.state.value.options.foreground)
            assertEquals(WHITE,model.state.value.options.background)
            assertEquals(List(10) { WHITE },model.state.value.preferences.customColors)
        }
        compose.onNodeWithText("Assign to custom color").performClick()
        compose.onNodeWithContentDescription("Custom color 10 #FFFFFF").performScrollTo().performClick()
        compose.onNodeWithTag("paint-canvas").performTouchInput { click(screen(Point(120f,100f))) }
        compose.runOnIdle {
            assertEquals(BLACK,model.state.value.options.background)
            assertEquals(List(9) { WHITE } + BLACK,model.state.value.preferences.customColors)
        }
    }

    @Test fun navigationAndCancelledOrOutsideGesturesDoNotPickAColor() {
        prepareEyedropper()
        compose.onNodeWithText("Assign to custom color").performClick()
        compose.onNodeWithTag("paint-canvas").performTouchInput { down(screen(Point(120f,100f))) }
        compose.onNodeWithTag("paint-eyedropper-preview").assertExists()
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(1,screen(Point(200f,100f)))
            moveBy(1,Offset(20f,0f))
            up(1)
            up(0)
        }
        compose.runOnIdle { assertEquals("After navigation",0xFF3366CC.toInt(),model.state.value.options.foreground) }
        compose.onNodeWithTag("paint-eyedropper-preview").assertDoesNotExist()
        compose.onNodeWithTag("paint-canvas").performTouchInput { down(screen(Point(120f,100f))); cancel() }
        compose.runOnIdle { assertEquals("After cancellation",0xFF3366CC.toInt(),model.state.value.options.foreground) }
        compose.onNodeWithTag("paint-eyedropper-preview").assertDoesNotExist()
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screen(Point(120f,100f)))
            moveTo(screen(Point(-10f,-10f)))
            up()
        }
        compose.runOnIdle {
            assertEquals(0xFF3366CC.toInt(),model.state.value.options.foreground)
            assertEquals(List(10) { WHITE },model.state.value.preferences.customColors)
        }
    }

    @Test fun saveCustomColorSelectsSlotDirectlyAndCancelPreservesExistingColors() {
        compose.runOnIdle { model.colors() }
        compose.onNodeWithContentDescription("Choose color #3366CC").performScrollTo().performClick()
        compose.onNodeWithText("Save custom color").performScrollTo().performClick()
        compose.onNodeWithText("Select a slot above").assertIsDisplayed()
        capture("paint-save-custom-slot.png")
        compose.onNodeWithText("Save custom color").assertDoesNotExist()
        compose.onNodeWithTag("dropdown-menu").assertDoesNotExist()
        compose.onAllNodesWithText("Cancel")[0].performClick()
        compose.onNodeWithText("Select a slot above").assertDoesNotExist()
        compose.runOnIdle { assertEquals(List(10) { WHITE },model.state.value.preferences.customColors) }
        compose.onNodeWithText("Save custom color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Custom color 10 #FFFFFF").performScrollTo().performClick()
        compose.onNodeWithText("Select a slot above").assertDoesNotExist()
        compose.onNodeWithText("Save custom color").performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(List(9) { WHITE } + 0xFF3366CC.toInt(),model.state.value.preferences.customColors) }
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(0xFF3366CC.toInt(),model.state.value.options.foreground) }
        assertEquals(0xFF3366CC.toInt(),runBlocking { settings.preferences.first() }.customColors[9])
    }
}
