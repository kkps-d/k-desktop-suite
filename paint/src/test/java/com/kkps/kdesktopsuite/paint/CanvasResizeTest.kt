package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class CanvasResizeTest {
    @Test fun eachHandleMovesOnlyItsEdgesAndKeepsTheOppositeEdgesFixed() {
        val expected = listOf(
            PixelRect(20,30,780,570),PixelRect(0,30,800,570),PixelRect(0,30,820,570),
            PixelRect(0,0,820,600),PixelRect(0,0,820,630),PixelRect(0,0,800,630),
            PixelRect(20,0,780,630),PixelRect(20,0,780,600),
        )
        CanvasResizeHandle.entries.zip(expected).forEach { (handle,bounds) ->
            assertEquals(handle.name,bounds,handle.bounds(800,600,Point(20f,30f)))
        }
    }

    @Test fun resizeCannotInvertTheCanvasOrExceedImageLimits() {
        for (handle in CanvasResizeHandle.entries) {
            for (delta in listOf(Point(-100000f,-100000f),Point(100000f,100000f))) {
                val bounds = handle.bounds(800,600,delta)
                assertTrue(bounds.width in 1..8192 && bounds.height in 1..8192)
                assertTrue(bounds.width.toLong() * bounds.height <= MAX_PIXELS)
            }
        }
        val bottom = CanvasResizeHandle.Bottom.bounds(8192,100,Point(0f,10000f))
        assertEquals(8192,bottom.width)
        assertEquals(2048,bottom.height)
    }

    @Test fun topLeftResizeCropsAndExpandsTheCorrectPixelsAndUndoesAsOneEdit() {
        val original = Raster(3,2,intArrayOf(BLACK,WHITE,BLACK,WHITE,BLACK,WHITE))
        val cropped = original.resizeCanvas(PixelRect(1,1,2,1),WHITE)
        assertArrayEquals(intArrayOf(BLACK,WHITE),cropped.pixels)
        val expanded = original.resizeCanvas(PixelRect(-1,-1,4,3),0xFF00FF00.toInt())
        assertEquals(BLACK,expanded.pixel(1,1))
        assertEquals(WHITE,expanded.pixel(3,2))
        assertEquals(0xFF00FF00.toInt(),expanded.pixel(0,0))
        val history = RasterHistory(original)
        history.commit(expanded)
        history.undo()
        assertArrayEquals(original.pixels,history.raster.pixels)
        assertFalse(history.canUndo)
        history.redo()
        assertArrayEquals(expanded.pixels,history.raster.pixels)
    }
}
