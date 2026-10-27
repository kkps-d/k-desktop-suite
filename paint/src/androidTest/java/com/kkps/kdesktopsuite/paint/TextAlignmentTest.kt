package com.kkps.kdesktopsuite.paint

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.abs

class TextAlignmentTest {
    @get:Rule val compose = createAndroidComposeRule<PaintActivity>()
    private val model get() = compose.activity.paintModel

    @Before fun freshTextTool() {
        compose.waitUntil(15000) { runCatching { !model.state.value.loading && !model.state.value.busy }.getOrDefault(false) }
        compose.runOnIdle {
            model.dialog(null); model.newImage()
            if (model.state.value.dialog == PaintDialog.Unsaved) model.discard()
            model.tool(Tool.Text)
            model.options { it.copy(font = "Sans-serif",fontSize = 24,bold = false,italic = false,
                underline = false,strike = false,foreground = BLACK,background = WHITE,opaqueText = true,
                textAlignment = TextAlignment.Left,verticalTextAlignment = VerticalTextAlignment.Top) }
        }
    }

    @Test fun toolbarAlignmentUpdatesLiveTextAndCommittedPixels() {
        var previousLeft = -1
        for ((alignment,label) in listOf(TextAlignment.Left to "Align left",TextAlignment.Center to "Align center",TextAlignment.Right to "Align right")) {
            compose.runOnIdle { model.createPreview(Point(20f,20f),Point(260f,160f)) }
            compose.onNodeWithTag("paint-text-overlay").performTextInput("Hello\nHi")
            compose.onNodeWithContentDescription("Horizontal alignment").performScrollTo().performClick()
            compose.onNodeWithText(label).assertExists().performClick()
            compose.onNodeWithContentDescription("Horizontal alignment")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,label))
            compose.waitUntil(10000) {
                model.state.value.preview?.let {
                    it.text == "Hello\nHi" && it.textRaster != null && it.textLayoutKey == textLayoutKey(it.text,it.bounds.width,model.state.value.options)
                } == true
            }
            lateinit var glyphs: Raster
            lateinit var bounds: PixelRect
            compose.runOnIdle {
                val preview = requireNotNull(model.state.value.preview)
                assertEquals(alignment,model.state.value.options.textAlignment)
                glyphs = requireNotNull(preview.textRaster)
                val left = glyphs.pixels.indices.filter { glyphs.pixels[it] ushr 24 != 0 }.minOf { it % glyphs.width }
                assertTrue("Text moves toward the right for $alignment",left > previousLeft)
                previousLeft = left
                bounds = preview.bounds
                model.applyPreview()
            }
            compose.waitUntil(15000) { !model.state.value.busy && model.state.value.preview == null }
            compose.runOnIdle {
                val committed = model.state.value.raster.extract(PixelRect(bounds.x,bounds.y,glyphs.width,glyphs.height))
                assertArrayEquals(glyphs.pixels.map { composite(it,WHITE) }.toIntArray(),committed.pixels)
                model.undo()
            }
        }
    }

    @Test fun verticalDropdownPositionsLiveAndCommittedTextAndFollowsResize() {
        compose.runOnIdle { model.createPreview(Point(20f,20f),Point(260f,180f)) }
        compose.onNodeWithTag("paint-text-overlay").performTextInput("Hello\nHi")
        compose.waitUntil(10000) { model.state.value.preview?.textRaster != null }
        var previousTop = -1
        for ((alignment,label) in listOf(VerticalTextAlignment.Top to "Align top",
            VerticalTextAlignment.Middle to "Align middle",VerticalTextAlignment.Bottom to "Align bottom")) {
            compose.onNodeWithContentDescription("Vertical alignment").performScrollTo().performClick()
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithContentDescription("Vertical alignment")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,label))
            val pixels = compose.onNodeWithTag("paint-text-overlay").captureToImage().toPixelMap()
            val inkRows = (0 until pixels.height).filter { y ->
                (0 until pixels.width).any { x -> pixels[x,y].let { it.red < 0.1f && it.green < 0.1f && it.blue < 0.1f } }
            }
            assertTrue("Visible text for $alignment",inkRows.isNotEmpty())
            assertTrue("Text moves down for $alignment",inkRows.first() > previousTop)
            previousTop = inkRows.first()
            compose.runOnIdle {
                val preview = requireNotNull(model.state.value.preview)
                val glyphs = requireNotNull(preview.textRaster)
                val glyphTop = glyphs.pixels.indices.first { glyphs.pixels[it] ushr 24 > 230 } / glyphs.width
                val offset = alignment.offset(preview.bounds.height,glyphs.height)
                val scale = pixels.height.toFloat() / preview.bounds.height
                assertEquals("Live text offset for $alignment",(offset + glyphTop) * scale,inkRows.first().toFloat(),2f)
                val rendered = model.state.value.raster.render(preview,model.state.value.options)
                val committed = rendered.extract(PixelRect(preview.bounds.x,preview.bounds.y + offset,glyphs.width,glyphs.height))
                assertArrayEquals(glyphs.pixels.map { composite(it,WHITE) }.toIntArray(),committed.pixels)
            }
        }
        compose.runOnIdle {
            val preview = requireNotNull(model.state.value.preview)
            model.movePreview(preview.bounds.copy(height = 220))
        }
        compose.onNodeWithContentDescription("Vertical alignment").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,"Align bottom"))
        compose.runOnIdle {
            val preview = requireNotNull(model.state.value.preview)
            val glyphs = requireNotNull(preview.textRaster)
            val result = model.state.value.raster.render(preview,model.state.value.options)
            val bottom = preview.bounds.bottom - glyphs.height
            assertArrayEquals(glyphs.pixels.map { composite(it,WHITE) }.toIntArray(),
                result.extract(PixelRect(preview.bounds.x,bottom,glyphs.width,glyphs.height)).pixels)
            model.applyPreview()
        }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.preview == null }
    }

    @Test fun newTextBoxesStartAtLeftTopAndMovingPreservesAlignment() {
        compose.runOnIdle {
            for (create in listOf<() -> Unit>({ model.tap(Point(20f,20f)) },
                { model.createPreview(Point(20f,20f),Point(260f,180f)) })) {
                model.options { it.copy(textAlignment = TextAlignment.Right,verticalTextAlignment = VerticalTextAlignment.Bottom) }
                create()
                assertEquals(TextAlignment.Left,model.state.value.options.textAlignment)
                assertEquals(VerticalTextAlignment.Top,model.state.value.options.verticalTextAlignment)
                model.options { it.copy(textAlignment = TextAlignment.Center,verticalTextAlignment = VerticalTextAlignment.Middle) }
                model.movePreview(requireNotNull(model.state.value.preview).bounds.copy(x = 30,height = 200))
                assertEquals(TextAlignment.Center,model.state.value.options.textAlignment)
                assertEquals(VerticalTextAlignment.Middle,model.state.value.options.verticalTextAlignment)
                model.cancelPreview()
            }
        }
    }

    @Test fun fallbackAlignmentUsesPhysicalEdgesForBothParagraphDirections() {
        val bounds = PixelRect(10,10,240,160)
        for (text in listOf("Hello\nHi","שלום\nאב","Hello\nשלום","\nHello\n")) {
            for (alignment in TextAlignment.entries) {
                val result = Raster.blank(260,190).render(EditPreview(Tool.Text,bounds,text = text),
                    ToolOptions(fontSize = 24,textAlignment = alignment))
                val ink = result.pixels.indices.filter { result.pixels[it] != WHITE }
                assertTrue(ink.isNotEmpty())
                val left = ink.minOf { it % result.width } - bounds.x
                val right = ink.maxOf { it % result.width } - bounds.x + 1
                when (alignment) {
                    TextAlignment.Left -> assertTrue("Left edge: $text",left in 0..4)
                    TextAlignment.Center -> assertTrue("Centered: $text",abs(left + right - bounds.width) <= 4)
                    TextAlignment.Right -> assertTrue("Right edge: $text",bounds.width - right in 0..4)
                }
            }
        }
    }

    @Test fun recoveryPreservesAlignmentAndDefaultsOlderDraftsToLeft() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir,"text-alignment-${UUID.randomUUID()}")
        try {
            val store = PaintRecovery(root)
            for ((index,alignment) in TextAlignment.entries.withIndex()) {
                val vertical = VerticalTextAlignment.entries[index]
                val options = ToolOptions(tool = Tool.Text,textAlignment = alignment,verticalTextAlignment = vertical)
                val preview = EditPreview(Tool.Text,PixelRect(0,0,240,80),text = "Draft")
                val glyphs = Raster(240,80,IntArray(240 * 80)).render(preview,options)
                val cached = preview.copy(textRaster = glyphs,textLayoutKey = textLayoutKey(preview.text,preview.bounds.width,options))
                store.write(RecoverySnapshot(Raster.blank(240,100),options,cached,null,Viewport(),null,
                    "Untitled","image/png",null,false,sequence = index + 1L))
                val restored = requireNotNull(PaintRecovery(root).read())
                assertEquals(alignment,restored.options.textAlignment)
                assertEquals(vertical,restored.options.verticalTextAlignment)
                assertArrayEquals(glyphs.pixels,requireNotNull(restored.preview?.textRaster).pixels)
                assertArrayEquals(restored.raster.render(cached,options).pixels,
                    restored.raster.render(requireNotNull(restored.preview),restored.options).pixels)
            }
            val manifest = File(root,"current.json")
            val json = JSONObject(manifest.readText())
            json.getJSONObject("options").remove("textAlignment")
            json.getJSONObject("options").remove("verticalTextAlignment")
            manifest.writeText(json.toString())
            val olderDraft = requireNotNull(PaintRecovery(root).read())
            assertEquals(TextAlignment.Left,olderDraft.options.textAlignment)
            assertEquals(VerticalTextAlignment.Top,olderDraft.options.verticalTextAlignment)
            assertNull(olderDraft.preview?.textRaster)
            json.getJSONObject("options").put("textAlignment","Unknown")
            manifest.writeText(json.toString())
            assertEquals(TextAlignment.Left,requireNotNull(PaintRecovery(root).read()).options.textAlignment)
        } finally { root.deleteRecursively() }
    }
}
