package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
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

    @Test fun activeToolsAndColorsRevealAfterSwitchingAndResizing() {
        compose.runOnIdle { size = DpSize(360.dp,400.dp); model.tool(Tool.Eyedropper) }
        compose.onNodeWithContentDescription("Eyedropper").assertIsDisplayed()
        compose.runOnIdle { model.tool(Tool.Select) }
        compose.onNodeWithContentDescription("Select").assertIsDisplayed()
        compose.runOnIdle { model.selectColor(true) }
        compose.onNodeWithContentDescription("Secondary color").assertIsDisplayed()
        compose.runOnIdle { size = DpSize(600.dp,400.dp); model.tool(Tool.Brush) }
        compose.onNodeWithContentDescription("Brush").assertIsDisplayed()
        compose.onNodeWithTag("paint-brush-picker").assertIsDisplayed()
    }

    @Test fun compactRowsScrollIndependentlyAndKeepThreeShapesAtLargeTextAndRtl() {
        compose.runOnIdle { size = DpSize(360.dp,640.dp); fontScale = 2f; rtl = true }
        compose.onNodeWithTag("paint-shape-tool").performScrollTo().performClick()
        listOf("Line","Rectangle","Ellipse").forEach {
            compose.onNodeWithTag("paint-shape-shortcut-$it").assertExists()
        }
        compose.onNodeWithTag("paint-shape-shortcut-RoundedRectangle").assertDoesNotExist()
        compose.onNodeWithText("Shape").assertDoesNotExist()
        compose.onNodeWithContentDescription("Move/resize/skew").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy (Ctrl+C)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Select").performScrollTo().performClick()
        compose.onNodeWithText("Transparent").performScrollTo().assertIsNotEnabled().assertIsOff()
        compose.onNodeWithContentDescription("Flip").performScrollTo().performClick()
        compose.onNodeWithText("Flip vertical").assertIsDisplayed()
        compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
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
        compose.onNodeWithTag("paint-shape-outline-picker").performScrollTo().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Solid color"))
        compose.onNodeWithTag("paint-shape-fill-picker").performScrollTo().performClick()
        compose.onNodeWithTag("paint-shape-fill-Solid").performScrollTo().performClick()
        compose.onNodeWithText("Move/resize/skew").performScrollTo().assertIsEnabled()
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
        compose.onNodeWithContentDescription("Custom rotate").performScrollTo().performClick()
        compose.onNodeWithText("Rotation (degrees clockwise)").performScrollTo().assertIsFocused().performTextReplacement("33")
        compose.onNodeWithText("Apply").performClick()
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

    @Test fun primaryTransformsAreAvailableWithoutChangingToolsAndTextSizeIsCompact() {
        compose.onNodeWithContentDescription("Brush").assertIsSelected()
        compose.onNodeWithTag("paint-brush-picker").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        compose.onNode(hasContentDescription("Rotate right 90\u00b0") and hasAnyAncestor(hasTestTag("paint-primary-toolbar"))).performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Text").performScrollTo().performClick()
        compose.onNodeWithTag("paint-font-size").performScrollTo().assertHeightIsEqualTo(36.dp).performTextReplacement("512")
        compose.waitUntil(5000) { model.state.value.options.fontSize == 512 }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"font-size-max.png").outputStream().use {
            compose.onNodeWithTag("paint-secondary-toolbar").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNodeWithTag("paint-font-size").performTextReplacement("36")
        compose.waitUntil(5000) { model.state.value.options.fontSize == 36 }
        compose.onNodeWithTag("paint-font-size").performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x,-3f)) }
        compose.onNodeWithTag("paint-font-size").assertIsFocused()
        compose.onNodeWithText("px").assertIsDisplayed()
        compose.onNodeWithText("Move/resize").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { model.tap(Point(20f,20f)) }
        compose.onNodeWithText("Move/resize").assertIsEnabled()
    }

    @Test fun textSecondaryToolbarHasNoVisibilityToggle() {
        compose.runOnIdle { size = DpSize(1200.dp,400.dp); model.tool(Tool.Text); model.tap(Point(20f,20f)) }
        compose.onNodeWithTag("paint-text-overlay").performTextInput("Keep editing")
        compose.onNode(hasText("Hide toolbar") and hasAnyAncestor(hasTestTag("paint-secondary-toolbar"))).assertDoesNotExist()
        compose.onNode(hasText("Show toolbar") and hasAnyAncestor(hasTestTag("paint-secondary-toolbar"))).assertDoesNotExist()
        compose.onNodeWithTag("paint-primary-toolbar").assertIsDisplayed()
        compose.onNodeWithTag("paint-secondary-toolbar").assertIsDisplayed()
        compose.onNodeWithTag("paint-text-overlay").assertExists()
        compose.runOnIdle { assertEquals("Keep editing",model.state.value.preview?.text); assertFalse(model.state.value.dirty) }
    }

    @Test fun shapeOptionPickersAreCommandsWithoutSelectedStyling() {
        compose.runOnIdle { model.tool(Tool.Rectangle) }
        compose.onNodeWithTag("paint-shape-picker").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        compose.onNodeWithTag("paint-size-picker").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        compose.onNodeWithTag("paint-shape-outline-picker").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        compose.onNodeWithTag("paint-shape-fill-picker").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
    }

    @Test fun shapePaintDropdownsOfferAllStylesIndependentlyAtWideAndCompactWidths() {
        compose.runOnIdle { model.tool(Tool.Rectangle); model.createPreview(Point(40f,40f),Point(200f,160f)) }
        val original = model.state.value
        for (width in listOf(1200.dp,360.dp)) {
            compose.runOnIdle { size = DpSize(width,640.dp); fontScale = if (width == 360.dp) 2f else 1f; rtl = width == 360.dp }
            for (property in listOf("outline","fill")) {
                val other = if (property == "outline") model.state.value.options.fill else model.state.value.options.outline
                for (style in listOf(null) + BrushStyle.entries) {
                    compose.onNodeWithTag("paint-shape-$property-picker").performScrollTo().performClick()
                    compose.onNodeWithText("No fill").assertExists()
                    compose.onNodeWithText("Solid color").assertExists()
                    if (style == null) {
                        val context = InstrumentationRegistry.getInstrumentation().targetContext
                        File(context.cacheDir,"shape-$property-picker-${width.value.toInt()}.png").outputStream().use {
                            compose.onNodeWithTag("dropdown-menu").captureToImage().asAndroidBitmap()
                                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                        }
                    }
                    val selected = if (property == "outline") model.state.value.options.outline else model.state.value.options.fill
                    compose.onNodeWithTag("paint-shape-$property-${selected?.name ?: "None"}").assertIsSelected().assertIsFocused()
                    compose.onNodeWithTag("paint-shape-$property-${style?.name ?: "None"}").performScrollTo().performClick()
                    compose.onNodeWithTag("dropdown-menu").assertDoesNotExist()
                    compose.runOnIdle {
                        val state = model.state.value
                        assertEquals(style,if (property == "outline") state.options.outline else state.options.fill)
                        assertEquals(other,if (property == "outline") state.options.fill else state.options.outline)
                        assertEquals(original.options.brushStyle,state.options.brushStyle)
                        assertEquals(original.preview,state.preview)
                        assertSame(original.raster,state.raster)
                        assertFalse(state.dirty)
                    }
                }
            }
        }
        compose.onNodeWithTag("paint-shape-fill-picker").performScrollTo().performClick()
        compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(BrushStyle.Graphite,model.state.value.options.fill) }
        compose.onNodeWithTag("paint-shape-fill-picker").performClick()
        compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("dropdown-menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(BrushStyle.Graphite,model.state.value.options.fill) }
    }

    @Test fun invisibleShapeCannotCommitUntilAVisibleStyleIsSelected() {
        for (tool in listOf(Tool.Rectangle,Tool.Line)) {
            compose.runOnIdle {
                model.tool(tool)
                model.createPreview(Point(40f,40f),Point(200f,160f))
                model.options { it.copy(outline = null,fill = if (tool.isClosedShape) null else BrushStyle.Solid) }
                model.applyPreview()
            }
            compose.runOnIdle {
                assertNotNull(model.state.value.preview)
                assertFalse(model.state.value.dirty)
                assertTrue(model.state.value.raster.pixels.all { it == WHITE })
                model.options { it.copy(outline = BrushStyle.Crayon) }
                model.applyPreview()
            }
            compose.waitUntil(15000) { !model.state.value.busy && model.state.value.preview == null }
            compose.runOnIdle {
                assertTrue(model.state.value.dirty)
                model.undo()
                assertFalse(model.state.value.dirty)
                assertTrue(model.state.value.raster.pixels.all { it == WHITE })
            }
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
        compose.onNodeWithText("Swap foreground and background").assertDoesNotExist()
        val presetRows = compose.onAllNodesWithContentDescription("Choose color ",substring = true).fetchSemanticsNodes()
            .groupBy { it.positionInRoot.y }.values.map { it.size }
        assertEquals(listOf(10,10),presetRows)
        val customRows = compose.onAllNodes(hasContentDescription("Custom color ",substring = true) and
            hasAnyAncestor(hasTestTag("paint-dialog-custom-colors"))).fetchSemanticsNodes()
            .groupBy { it.positionInRoot.y }.values.map { it.size }
        assertEquals(listOf(10),customRows)
        presetColors.forEach { color ->
            compose.onNodeWithContentDescription("Choose color #${"%06X".format(color and 0xFFFFFF)}").assertExists()
        }
        compose.onNodeWithContentDescription("Custom color 10 #FFFFFF").performScrollTo().assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"paint-colors-dialog.png").outputStream().use {
            compose.onNodeWithTag("paint-colors-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNodeWithContentDescription("Choose color #3366CC").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(0xFF3366CC.toInt(),model.state.value.options.background); assertEquals(BLACK,model.state.value.options.foreground) }
    }
}
