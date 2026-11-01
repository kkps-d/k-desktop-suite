package com.kkps.kdesktopsuite.paint

import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ShapePaintTest {
    @Test fun everyBrushProducesDistinctClippedOutlineAndFillTextures() {
        val base = Raster.blank(160,140)
        val preview = EditPreview(Tool.Rectangle,PixelRect(20,20,120,100))
        val outlines = mutableSetOf<List<Int>>()
        val fills = mutableSetOf<List<Int>>()
        val sheet = createBitmap(160 * BrushStyle.entries.size,280)
        val canvas = Canvas(sheet)
        for (style in BrushStyle.entries) {
            val outline = base.render(preview,ToolOptions(width = 12,outline = style))
            val fill = base.render(preview,ToolOptions(outline = null,fill = style,background = BLACK))
            assertTrue("Outline $style",outline.pixels.any { it != WHITE })
            assertTrue("Fill $style",fill.pixels.any { it != WHITE })
            assertEquals("Hollow $style",WHITE,outline.pixel(80,70))
            assertEquals("Outside outline $style",WHITE,outline.pixel(10,10))
            assertEquals("Outside fill $style",WHITE,fill.pixel(19,70))
            assertEquals("Outside fill $style",WHITE,fill.pixel(80,120))
            assertTrue("Distinct outline $style",outlines.add(outline.pixels.toList()))
            assertTrue("Distinct fill $style",fills.add(fill.pixels.toList()))
            assertArrayEquals("Stable texture $style",fill.pixels,
                base.render(preview,ToolOptions(outline = null,fill = style,background = BLACK)).pixels)
            canvas.drawBitmap(outline.bitmap(),style.ordinal*160f,0f,null)
            canvas.drawBitmap(fill.bitmap(),style.ordinal*160f,140f,null)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir,"shape-paint-styles.png").outputStream().use {
            sheet.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        sheet.recycle()
        assertTrue(base.pixels.all { it == WHITE })
    }

    @Test fun independentStylesKeepColorsSharpCornersAndSelectionClipping() {
        val base = Raster.blank(160,140)
        val preview = EditPreview(Tool.Rectangle,PixelRect(20,20,120,100))
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val options = ToolOptions(width = 16,outline = BrushStyle.Solid,fill = BrushStyle.Crayon,
            foreground = red,background = blue)
        val result = base.render(preview,options)
        assertEquals("Sharp miter corner",red,result.pixel(13,13))
        assertEquals(red,result.pixel(20,70))
        assertTrue((40..100).any { y -> result.pixel(80,y) != WHITE })
        for (y in 40..100) {
            val color = result.pixel(80,y)
            assertEquals("Fill contains only secondary blue",255,color and 255)
            assertEquals(color shr 8 and 255,color shr 16 and 255)
        }
        val clipped = base.render(preview,options,PixelRect(80,0,80,140))
        for (y in 0 until 140) for (x in 0 until 80) assertEquals(WHITE,clipped.pixel(x,y))
        assertEquals(result.pixel(100,70),clipped.pixel(100,70))
    }

    @Test fun noFillCanHideBothPartsAndOpenShapesNeverFill() {
        val base = Raster.blank(160,140)
        for (tool in listOf(Tool.Rectangle,Tool.Line,Tool.Curve)) {
            val preview = EditPreview(tool,PixelRect(20,20,120,100))
            assertArrayEquals(base.pixels,base.render(preview,ToolOptions(outline = null,fill = null)).pixels)
            if (!tool.isClosedShape) {
                assertArrayEquals(base.pixels,base.render(preview,ToolOptions(outline = null,fill = BrushStyle.Oil)).pixels)
                assertTrue(base.render(preview,ToolOptions(outline = BrushStyle.Graphite)).pixels.any { it != WHITE })
            }
        }
    }
}
