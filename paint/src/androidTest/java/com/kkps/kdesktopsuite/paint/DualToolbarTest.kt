package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
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

class DualToolbarTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var size by mutableStateOf(DpSize(1200.dp,800.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private var rtl by mutableStateOf(false)
    private var appearance by mutableStateOf("Light")

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"toolbar-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PaintTheme(appearance) { Box(Modifier.requiredSize(size)) { PaintScreen(state,model) } }
            }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
    }

    @Test fun compactRowsScrollIndependentlyAndKeepThreeShapesAtLargeTextAndRtl() {
        compose.runOnIdle { size = DpSize(360.dp,640.dp); fontScale = 2f; rtl = true }
        compose.onNodeWithTag("paint-shape-tool").performScrollTo().performClick()
        listOf("Line","Rectangle","Ellipse").forEach {
            compose.onNodeWithTag("paint-shape-shortcut-$it").assertExists()
        }
        compose.onNodeWithTag("paint-shape-shortcut-RoundedRectangle").assertDoesNotExist()
        compose.onNodeWithText("Shape").assertDoesNotExist()
        compose.onNodeWithContentDescription("Move / resize").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy (Ctrl+C)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Select").performScrollTo().performClick()
        compose.onNodeWithText("Transparent selection").performScrollTo().assertIsNotEnabled().assertIsOff()
        compose.onNodeWithContentDescription("Flip vertical").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Commit").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasContentDescription("Zoom") and hasAnyAncestor(hasTestTag("paint-primary-toolbar"))).assertDoesNotExist()
        compose.onNodeWithTag("paint-canvas").assertIsDisplayed()
        val primary = compose.onNodeWithTag("paint-primary-toolbar").fetchSemanticsNode().boundsInRoot
        val secondary = compose.onNodeWithTag("paint-secondary-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue(primary.height > 0 && secondary.top >= primary.bottom)
        assertTrue("Rows should leave room for the canvas",secondary.height < 100 * compose.density.density)
    }

    @Test fun shortcutCountsAdaptWithoutChangingThePendingShape() {
        compose.runOnIdle { model.tool(Tool.Rectangle); model.createPreview(Point(10f,10f),Point(60f,50f)) }
        val preview = model.state.value.preview
        val shapes = listOf("Line","Rectangle","Ellipse","RoundedRectangle","Triangle","RightArrow","Diamond","Polygon","Curve","FivePointStar")
        for ((width,expected) in listOf(360 to 3,700 to 5,1000 to 8,1200 to 10)) {
            compose.runOnIdle { size = DpSize(width.dp,400.dp) }
            compose.onNodeWithTag("paint-shape-shortcut-${shapes[expected-1]}").assertExists()
            if (expected < shapes.size) compose.onNodeWithTag("paint-shape-shortcut-${shapes[expected]}").assertDoesNotExist()
            compose.onNodeWithContentDescription("Primary color").performScrollTo().performClick()
            val presets = if (width < 600) 5 else if (width < 1200) 10 else 15
            val custom = if (width < 600) 2 else if (width < 1200) 3 else 5
            compose.onAllNodesWithContentDescription("Preset color ",substring = true).assertCountEquals(presets)
            compose.onAllNodesWithContentDescription("Custom color ",substring = true).assertCountEquals(custom)
            compose.onNodeWithTag("paint-shape-tool").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(preview,model.state.value.preview) }
        }
        compose.runOnIdle { fontScale = 2f }
        compose.onNodeWithTag("paint-shape-shortcut-Triangle").assertExists()
        compose.onNodeWithTag("paint-shape-shortcut-RightArrow").assertDoesNotExist()
    }

    @Test fun colorOptionsAndReturningToShapePreserveItsPreview() {
        compose.runOnIdle { model.tool(Tool.Rectangle); model.createPreview(Point(10f,10f),Point(60f,50f)) }
        val before = model.state.value
        compose.onNodeWithContentDescription("Secondary color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Preset color 1 #000000").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Tool.Rectangle,model.state.value.options.tool)
            assertEquals(before.preview,model.state.value.preview)
            assertSame(before.raster,model.state.value.raster)
            assertEquals(BLACK,model.state.value.options.background)
        }
        compose.onNodeWithTag("paint-shape-tool").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(model.state.value.colorOptions); assertEquals(before.preview,model.state.value.preview) }
        compose.onNodeWithText("Outline").performScrollTo().assertIsOn()
        compose.onNodeWithText("Fill").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Move / resize").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Commit").performScrollTo().performClick()
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.preview == null }
        compose.runOnIdle { assertEquals(BLACK,model.state.value.raster.pixel(30,30)); model.undo(); assertEquals(WHITE,model.state.value.raster.pixel(30,30)) }
    }

    @Test fun colorOptionsPreserveOrdinaryAndPastedSelections() {
        compose.runOnIdle { model.selectAll() }
        val selection = model.state.value.selection
        compose.onNodeWithContentDescription("Primary color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Select").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(selection,model.state.value.selection); model.insert(Raster.blank(20,20,BLACK)) }
        val preview = model.state.value.preview
        compose.onNodeWithContentDescription("Secondary color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Swap foreground and background").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Select").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(preview,model.state.value.preview); assertFalse(model.state.value.dirty) }
        compose.onNodeWithContentDescription("Cancel / clear selection").performScrollTo().performClick()
        compose.runOnIdle { assertNull(model.state.value.preview); assertTrue(model.state.value.raster.pixels.all { it == WHITE }) }
    }

    @Test fun textFormattingAndColorsPreserveNativeSelectionUntilCommit() {
        compose.runOnIdle { model.tool(Tool.Text); model.tap(Point(20f,20f)) }
        compose.onNodeWithTag("paint-text-overlay").performTextInput("Native text selection")
        compose.runOnIdle { model.textEditor(requireNotNull(model.state.value.preview)).edit { selection = TextRange(2,7) } }
        compose.onNodeWithContentDescription("Primary color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Preset color 4 #EE3333").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Text").performScrollTo().performClick()
        compose.onNodeWithTag("paint-font-picker").performScrollTo().performClick()
        compose.onNodeWithText("Serif").performClick()
        compose.onNodeWithContentDescription("Bold").performScrollTo().performClick()
        compose.runOnIdle {
            val preview = requireNotNull(model.state.value.preview)
            assertEquals("Native text selection",preview.text)
            assertEquals(TextRange(2,7),model.textEditor(preview).selection)
            assertEquals("Serif",model.state.value.options.font)
            assertTrue(model.state.value.options.bold)
            assertFalse(model.state.value.dirty)
        }
        compose.onNodeWithContentDescription("Commit").performScrollTo().performClick()
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.preview == null }
        compose.runOnIdle { assertTrue(model.state.value.dirty) }
    }

    @Test fun customRotateFocusesAngleAndPreservesFloatingSelection() {
        compose.runOnIdle { model.insert(Raster.blank(80,40,BLACK)) }
        val before = model.state.value.raster
        compose.onNodeWithText("Custom rotate").performScrollTo().performClick()
        compose.onNodeWithText("Rotation (degrees clockwise)").performScrollTo().assertIsFocused().performTextReplacement("33")
        compose.onNodeWithText("Update preview").performClick()
        compose.runOnIdle {
            assertSame(before,model.state.value.raster)
            assertEquals(33.0,requireNotNull(model.state.value.preview).rotationDegrees,0.001)
        }
    }

    @Test fun cancelKeepsSelectedPixelsAndTrashDeletesThem() {
        compose.runOnIdle { model.stroke(listOf(Point(10f,10f))) }
        compose.waitUntil(15000) { !model.state.value.busy }
        compose.runOnIdle { model.selectAll() }
        compose.onNodeWithContentDescription("Cancel / clear selection").performScrollTo().performClick()
        compose.runOnIdle { assertNull(model.state.value.selection); assertEquals(BLACK,model.state.value.raster.pixel(10,10)); model.selectAll() }
        compose.onNodeWithText("Delete pixels").performScrollTo().performClick()
        compose.waitUntil(15000) { !model.state.value.busy }
        compose.runOnIdle {
            assertEquals(WHITE,model.state.value.raster.pixel(10,10))
            model.undo()
            assertEquals(BLACK,model.state.value.raster.pixel(10,10))
        }
    }

    @Test fun captureWideAndCompactToolbarLayouts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((width,height,tool) in listOf(Triple(1200,700,Tool.Rectangle),Triple(360,640,Tool.Text))) {
            for (theme in listOf("Light","Dark")) {
                compose.runOnIdle { size = DpSize(width.dp,height.dp); model.tool(tool); appearance = theme }
                File(context.getExternalFilesDir(null),"dual-toolbar-$width-$theme.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            }
        }
    }

    @Test fun completePaletteRemainsInDialogAndTargetsSecondaryColor() {
        compose.onNodeWithContentDescription("Secondary color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit colors").performScrollTo().performClick()
        compose.onNodeWithText("Foreground").assertDoesNotExist()
        compose.onNodeWithText("Background").assertDoesNotExist()
        presetColors.forEach { color ->
            compose.onNodeWithContentDescription("Choose color #${"%06X".format(color and 0xFFFFFF)}").assertExists()
        }
        compose.onNodeWithContentDescription("Custom color 10 #FFFFFF").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Choose color #3366CC").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(0xFF3366CC.toInt(),model.state.value.options.background); assertEquals(BLACK,model.state.value.options.foreground) }
    }
}
