package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class RasterTransformTest {
    private val fixture = Raster(2,3,intArrayOf(1,2,3,4,5,6).map { BLACK or it }.toIntArray())

    @Test fun quarterTurnsAndFlipsMapEveryPixelExactly() {
        val right = fixture.transformed(RasterTransform.rotation(90.0).then(RasterTransform.translation(3.0,0.0)))
        assertEquals(3,right.width); assertEquals(2,right.height)
        assertArrayEquals(intArrayOf(5,3,1,6,4,2).map { BLACK or it }.toIntArray(),right.pixels)
        val left = right.transformed(RasterTransform.rotation(-90.0).then(RasterTransform.translation(0.0,3.0)))
        assertArrayEquals(fixture.pixels,left.pixels)
        val horizontal = RasterTransform.scale(-1.0,1.0).then(RasterTransform.translation(2.0,0.0))
        assertArrayEquals(intArrayOf(2,1,4,3,6,5).map { BLACK or it }.toIntArray(),fixture.transformed(horizontal).pixels)
        assertArrayEquals(fixture.pixels,fixture.transformed(horizontal).transformed(horizontal).pixels)
        val vertical = RasterTransform.scale(1.0,-1.0).then(RasterTransform.translation(0.0,3.0))
        assertArrayEquals(intArrayOf(5,6,3,4,1,2).map { BLACK or it }.toIntArray(),fixture.transformed(vertical).pixels)
    }

    @Test fun resizeSkewUsesLocalBoundsAndAppliesHorizontalBeforeVertical() {
        val bounds = PixelRect(40,60,100,50)
        val resize = resizeSkewTransform(bounds,200,100,0,0)
        assertEquals(Point(40f,60f),resize.point(40.0,60.0))
        assertEquals(Point(240f,160f),resize.point(140.0,110.0))
        val skew = resizeSkewTransform(PixelRect(0,0,2,3),2,3,45,45)
        assertEquals(Point(5f,8f),skew.point(2.0,3.0))
        assertEquals(PixelRect(0,0,5,8),skew.bounds(2,3))
        val negative = resizeSkewTransform(PixelRect(0,0,2,3),2,3,-45,0)
        assertEquals(PixelRect(0,0,5,3),negative.bounds(2,3))
    }

    @Test fun resizeInterpolatesPremultipliedAlphaWithoutColorHalos() {
        val source = Raster(2,1,intArrayOf(0x00FF0000,0xFF0000FF.toInt()))
        val resized = source.transformed(RasterTransform.scale(1.5,1.0))
        assertEquals(0x800000FF.toInt(),resized.pixel(1,0))
        assertEquals(0xFF7F7FFF.toInt(),resized.flatten(WHITE).pixel(1,0))
    }

    @Test fun transformedSelectionKeepsSourceAndTransparentUncoveredAreas() {
        val selection = EditPreview(Tool.Select,PixelRect(10,20,2,3),source = PixelRect(10,20,2,3),payload = fixture)
        val result = selection.transformedSelection(RasterTransform.around(selection.selectionCenter,RasterTransform.rotation(35.0)))
        assertSame(fixture,result.payload)
        assertEquals(selection.source,result.source)
        assertTrue(result.selectionPixels().pixels.any { it == 0 })
        assertFalse(result.containsSelection(Point(result.bounds.x.toFloat(),result.bounds.y.toFloat())))
        val moved = result.resizedSelection(result.bounds.copy(x = result.bounds.x+15,y = result.bounds.y+10))
        assertEquals(result.selectionCenter.x+15,moved.selectionCenter.x,0.001f)
        assertArrayEquals(result.selectionPixels().pixels,moved.selectionPixels().pixels)
    }

    @Test fun fourSelectionQuarterTurnsRestoreMixedParityBoundsAndPixels() {
        val original = EditPreview(Tool.Select,PixelRect(10,20,2,3),payload = fixture)
        var result = original
        repeat(4) { result = result.rotatedSelection(90.0) }
        assertEquals(original.bounds,result.bounds)
        assertArrayEquals(fixture.pixels,result.selectionPixels().pixels)
        val right = original.rotatedSelection(90.0)
        assertEquals(3,right.bounds.width); assertEquals(2,right.bounds.height)
        assertArrayEquals(intArrayOf(5,3,1,6,4,2).map { BLACK or it }.toIntArray(),right.selectionPixels().pixels)
    }

    @Test fun identityIsExactAndDimensionChangesAreUndoable() {
        assertArrayEquals(fixture.pixels,fixture.transformed(RasterTransform()).pixels)
        val history = RasterHistory(fixture)
        assertFalse(history.commit(fixture.transformed(RasterTransform())))
        val resized = fixture.transformed(RasterTransform.scale(2.0,2.0))
        assertTrue(history.commit(resized))
        history.undo(); assertSame(fixture,history.raster)
        history.redo(); assertSame(resized,history.raster)
    }

    @Test fun excessiveAndSingularTransformsAreRejectedBeforeAllocating() {
        assertThrows(IllegalArgumentException::class.java) { fixture.transformed(RasterTransform.scale(10000.0,1.0)) }
        assertThrows(IllegalArgumentException::class.java) { RasterTransform.scale(0.0,1.0).inverse() }
        assertThrows(IllegalArgumentException::class.java) { resizeSkewTransform(PixelRect(0,0,2,3),2,3,90,0) }
    }
}
