package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class BrushStrokeTest {
    private val points = listOf(Point(20f,30f),Point(80f,65f),Point(140f,30f))

    @Test fun everyStyleProducesDistinctOpaquePixelsWithoutChangingTheSource() {
        val base = Raster.blank(160,100)
        val results = BrushStyle.entries.map { style ->
            base.brushStroke(points,BLACK,24f,style,42).also { result ->
                assertTrue("$style draws",result.pixels.any { it != WHITE })
                assertTrue("$style stays opaque",result.pixels.all { it ushr 24 == 255 })
            }.pixels.toList()
        }
        assertEquals(9,results.distinct().size)
        assertTrue(base.pixels.all { it == WHITE })
    }

    @Test fun texturesAreRepeatableAndUndoRedoRestoresExactPixels() {
        for (style in BrushStyle.entries) {
            val base = Raster.blank(160,100)
            val result = base.brushStroke(points,BLACK,24f,style,42)
            assertArrayEquals(result.pixels,base.brushStroke(points,BLACK,24f,style,42).pixels)
            val history = RasterHistory(base)
            assertTrue(history.commit(result))
            history.undo()
            assertArrayEquals(base.pixels,history.raster.pixels)
            history.redo()
            assertArrayEquals(result.pixels,history.raster.pixels)
        }
        val base = Raster.blank(160,100)
        assertFalse(base.brushStroke(points,BLACK,24f,BrushStyle.Airbrush,42).pixels.contentEquals(
            base.brushStroke(points,BLACK,24f,BrushStyle.Airbrush,43).pixels))
    }

    @Test fun allStylesRespectSelectionAndImageEdges() {
        val base = Raster.blank(30,30)
        val clip = PixelRect(10,8,12,14)
        for (style in BrushStyle.entries) {
            val result = base.brushStroke(listOf(Point(-10f,15f),Point(40f,15f)),BLACK,24f,style,9,clip)
            assertTrue(result.pixels.any { it != WHITE })
            for (y in 0 until 30) for (x in 0 until 30) {
                if (!clip.contains(x,y)) assertEquals("$style at $x,$y",WHITE,result.pixel(x,y))
            }
        }
    }

    @Test fun texturedDabsUseDistanceRatherThanPointerSampleCount() {
        val base = Raster.blank(160,100)
        val sparse = listOf(Point(20f,50f),Point(140f,50f))
        val dense = (20..140 step 4).map { Point(it.toFloat(),50f) }
        for (style in BrushStyle.entries.filter { it != BrushStyle.Solid }) {
            assertArrayEquals(style.label,base.brushStroke(sparse,BLACK,25f,style,42).pixels,
                base.brushStroke(dense,BLACK,25f,style,42).pixels)
        }
    }

    @Test fun markerAndWatercolorAccumulatePigmentAcrossStrokes() {
        val base = Raster.blank(160,100)
        for (style in listOf(BrushStyle.Marker,BrushStyle.Watercolor)) {
            val once = base.brushStroke(points,BLACK,24f,style,42)
            val twice = once.brushStroke(points,BLACK,24f,style,42)
            assertTrue(once.pixel(20,30) and 255 in 1..254)
            assertTrue((twice.pixel(20,30) and 255) < (once.pixel(20,30) and 255))
        }
    }
}
