package com.kkps.kdesktopsuite.paint

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ClassicEditorTest {
    @get:Rule val compose = createAndroidComposeRule<PaintActivity>()
    private val model get() = compose.activity.paintModel

    @Before fun freshImage() {
        compose.waitUntil(15000) { modelAvailable() }
        compose.waitUntil(15000) { !model.state.value.loading && !model.state.value.busy }
        compose.runOnIdle {
            model.dialog(null); model.clearSelection(); model.newImage()
            if (model.state.value.dialog == PaintDialog.Unsaved) model.discard()
        }
    }
    private fun modelAvailable() = runCatching { compose.activity.paintModel }.isSuccess

    @Test fun classicHasMenusAndRibbonButNoTabs() {
        compose.onNodeWithTag("classic-ribbon").assertExists()
        compose.onNodeWithText("File").performClick()
        compose.onNodeWithText("Open image…",substring = true).assertIsDisplayed()
        compose.runOnIdle { model.dialog(null) }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).assertCountEquals(0)
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test fun imageIsCenteredAndRemainsCenteredAfterFitAndResize() {
        compose.runOnIdle {
            val view = model.state.value.viewport
            assertTrue(view.width > 0 && view.height > 0)
            val fitted = view.fitted()
            assertEquals((view.width-800*fitted.scale)/2,fitted.origin.x,0.01f)
            assertEquals((view.height-600*fitted.scale)/2,fitted.origin.y,0.01f)
            model.viewport(view.resized(1200f,900f).actual())
            assertEquals(200f,model.state.value.viewport.origin.x,0.01f)
            assertEquals(150f,model.state.value.viewport.origin.y,0.01f)
        }
    }

    @Test fun pendingShapeCancellationAndCommittedUndoRestorePixels() {
        compose.runOnIdle {
            model.tool(Tool.Rectangle)
            model.createPreview(Point(10f,10f),Point(40f,40f))
            assertTrue(model.state.value.raster.pixels.all { it == WHITE })
            model.cancelPreview()
            assertNull(model.state.value.preview)
            model.createPreview(Point(10f,10f),Point(40f,40f))
            model.applyPreview()
        }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.canUndo }
        compose.runOnIdle {
            assertTrue(model.state.value.raster.pixels.any { it != WHITE })
            model.undo(); assertTrue(model.state.value.raster.pixels.all { it == WHITE })
        }
    }

    @Test fun textInputIsMultilineAndRasterizesOnlyOnApply() {
        compose.runOnIdle { model.tool(Tool.Text); model.tap(Point(10f,10f)) }
        compose.onNodeWithTag("paint-text-overlay").performTextInput("One\nTwo café")
        compose.waitUntil(10000) {
            model.state.value.preview?.let { it.text == "One\nTwo café" && it.textLayoutKey?.text == it.text && it.textRaster != null } == true
        }
        lateinit var glyphs: Raster
        lateinit var textBounds: PixelRect
        compose.runOnIdle {
            assertTrue(model.state.value.raster.pixels.all { it == WHITE })
            val preview = requireNotNull(model.state.value.preview)
            glyphs = requireNotNull(preview.textRaster)
            textBounds = preview.bounds
            model.applyPreview()
        }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.preview == null }
        compose.runOnIdle {
            val raster = model.state.value.raster
            assertTrue(raster.pixels.any { it != WHITE })
            for (y in 0 until minOf(glyphs.height,raster.height-textBounds.y)) {
                for (x in 0 until minOf(glyphs.width,raster.width-textBounds.x)) {
                    assertEquals("Preview glyph coverage at $x,$y",composite(glyphs.pixel(x,y),WHITE),raster.pixel(textBounds.x+x,textBounds.y+y))
                }
            }
        }
    }

    @Test fun activityRecreationPreservesImageAndUndo() {
        compose.runOnIdle { model.stroke(listOf(Point(20f,20f),Point(40f,40f))) }
        compose.waitUntil(15000) { model.state.value.canUndo && !model.state.value.busy }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { modelAvailable() && !model.state.value.loading }
        compose.runOnIdle {
            assertTrue(model.state.value.canUndo); assertTrue(model.state.value.raster.pixels.any { it != WHITE })
            model.undo(); assertTrue(model.state.value.raster.pixels.all { it == WHITE })
        }
    }

    @Test fun nativeTextSelectionSurvivesActivityRecreation() {
        compose.runOnIdle { model.tool(Tool.Text); model.tap(Point(20f,20f)) }
        compose.onNodeWithTag("paint-text-overlay").performTextInput("Native selection")
        compose.waitUntil(10000) { model.state.value.preview?.text == "Native selection" }
        compose.runOnIdle { model.textEditor(requireNotNull(model.state.value.preview)).edit { selection = TextRange(2,7) } }
        compose.waitUntil(10000) { model.state.value.preview?.textSelectionStart == 2 && model.state.value.preview?.textSelectionEnd == 7 }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("paint-text-overlay").assertExists()
        compose.runOnIdle { assertEquals(TextRange(2,7),model.textEditor(requireNotNull(model.state.value.preview)).selection) }
    }

    @Test fun twoFingerNavigationRollsBackUnfinishedDrawing() {
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(0,center)
            updatePointerTo(0,center+Offset(20f,0f)); move()
            down(1,center+Offset(80f,0f))
            updatePointerTo(0,center+Offset(10f,20f))
            updatePointerTo(1,center+Offset(110f,20f)); move()
            up(0); up(1)
        }
        compose.runOnIdle { assertFalse(model.state.value.dirty); assertTrue(model.state.value.raster.pixels.all { it == WHITE }) }
    }
}
