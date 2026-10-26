package com.kkps.kdesktopsuite.paint

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CanvasRasterTransformTest {
    @Test fun canvasMapsOpaqueQuarterTurnsAndBothFlipsWithoutFiltering() {
        for ((width,height) in listOf(2 to 3,3 to 4,3 to 3,4 to 4)) {
            val source = Raster(width,height,IntArray(width*height) { BLACK or (it+1) })
            val transforms = listOf(RasterTransform.rotation(90.0),RasterTransform.rotation(-90.0),
                RasterTransform.rotation(180.0),RasterTransform.scale(-1.0,1.0),RasterTransform.scale(1.0,-1.0))
            for (transform in transforms) {
                val bounds = transform.bounds(width,height)
                val bitmap = source.transformedBitmap(transform,bounds)
                try {
                    assertFalse(bitmap.isRecycled)
                    assertEquals(Bitmap.DENSITY_NONE,bitmap.density)
                    val actual = bitmap.raster()
                    val inverse = transform.inverse()
                    for (y in 0 until actual.height) for (x in 0 until actual.width) {
                        val sx = inverse.x(x+bounds.x+0.5,y+bounds.y+0.5).toInt()
                        val sy = inverse.y(x+bounds.x+0.5,y+bounds.y+0.5).toInt()
                        assertEquals(source.pixel(sx,sy),actual.pixel(x,y))
                    }
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun canvasFilteringKeepsTransparentEdgesFreeOfHiddenColor() {
        val source = Raster(2,1,intArrayOf(0x00FF0000,0xFF0000FF.toInt()))
        val bitmap = source.transformedBitmap(RasterTransform.scale(1.5,1.0),PixelRect(0,0,3,1))
        try {
            val middle = bitmap.getPixel(1,0)
            assertTrue(abs((middle ushr 24)-128) <= 2)
            assertEquals(0,middle ushr 16 and 255)
            assertEquals(0,middle ushr 8 and 255)
            assertEquals(255,middle and 255)
            for (background in listOf(BLACK,WHITE)) {
                val result = composite(middle,background)
                val expected = composite(0x800000FF.toInt(),background)
                for (shift in listOf(16,8,0)) assertTrue(abs((result ushr shift and 255)-(expected ushr shift and 255)) <= 2)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun canvasHandlesSkewClippingAndLowAlphaWithoutMutatingSources() {
        val source = Raster(5,3,IntArray(15) { ((it*17) shl 24) or 0x004080C0 })
        val original = source.pixels.copyOf()
        for ((horizontal,vertical) in listOf(15 to 0,-15 to 0,0 to 15,0 to -15,12 to -10)) {
            val transform = resizeSkewTransform(PixelRect(0,0,5,3),7,4,horizontal,vertical)
            val bounds = transform.bounds(5,3)
            val bitmap = source.transformedBitmap(transform,bounds)
            try {
                assertEquals(bounds.width,bitmap.width)
                assertEquals(bounds.height,bitmap.height)
                assertTrue(bitmap.raster().pixels.any { it ushr 24 > 0 })
            } finally { bitmap.recycle() }
        }
        val canvas = Raster(20,20,IntArray(400) { 0x014080C0 })
        val canvasBefore = canvas.pixels.copyOf()
        val transform = RasterTransform.rotation(33.0).then(RasterTransform.translation(-2.0,1.0))
        val result = canvas.renderSelectionCanvas(source,transform,transform.bounds(5,3),PixelRect(1,1,2,2),20,20,WHITE)
        assertEquals(canvas.pixel(19,19),result.pixel(19,19))
        assertArrayEquals(canvasBefore,canvas.pixels)
        assertArrayEquals(original,source.pixels)
        val moved = canvas.renderSelectionCanvas(source,RasterTransform.translation(10.0,10.0),PixelRect(10,10,5,3),
            PixelRect(1,1,2,2),20,20,WHITE)
        assertEquals(canvas.pixel(5,5),moved.pixel(5,5))
    }

    @Test fun cancellationAtEveryCanvasStageDiscardsPrivateBitmaps() {
        val source = Raster(3,2,IntArray(6) { WHITE })
        for (stop in 1..6) {
            var checks = 0
            assertThrows(CancellationException::class.java) {
                source.transformedBitmap(RasterTransform.rotation(33.0),PixelRect(0,0,4,4),checkCancelled = {
                    if (++checks == stop) throw CancellationException()
                })
            }
            assertEquals(stop,checks)
        }
        var checks = 0
        assertThrows(CancellationException::class.java) {
            source.renderSelectionCanvas(source,RasterTransform(),PixelRect(0,0,3,2),null,3,2,WHITE) {
                if (++checks == 4) throw CancellationException()
            }
        }
        assertArrayEquals(IntArray(6) { WHITE },source.pixels)
    }
}
