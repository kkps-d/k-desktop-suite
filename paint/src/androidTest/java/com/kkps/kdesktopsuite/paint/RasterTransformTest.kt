package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

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


    @Test fun resizeInterpolatesPremultipliedAlphaWithoutColorHalos() {
        val source = Raster(2,1,intArrayOf(0x00FF0000,0xFF0000FF.toInt()))
        val resized = source.transformed(RasterTransform.scale(1.5,1.0))
        assertColorNear(0x800000FF.toInt(),resized.pixel(1,0))
        assertColorNear(0xFF7F7FFF.toInt(),resized.flatten(WHITE).pixel(1,0))
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

    @Test fun exactTransformsPreserveTransparentPixelsAndSupportClippedOutput() {
        val source = Raster(2,2,intArrayOf(0x00FF0000,0x800000FF.toInt(),BLACK,WHITE))
        assertSame(source,source.transformed(RasterTransform()))
        val turned = source.transformed(RasterTransform.rotation(90.0).then(RasterTransform.translation(2.0,0.0)))
        val expected = intArrayOf(BLACK,0x00FF0000,WHITE,0x800000FF.toInt())
        expected.indices.forEach { assertColorNear(expected[it],turned.pixels[it]) }
        val moved = source.transformed(RasterTransform.translation(-1.0,1.0),PixelRect(0,0,3,3))
        assertArrayEquals(intArrayOf(0,0,0,0x800000FF.toInt(),0,0,WHITE,0,0),moved.pixels)
    }

    @Test fun fusedSelectionMatchesSeparateClearTransformAndPasteWithAlphaAndClipping() {
        val canvas = Raster(6,5,IntArray(30) { 0xFF334455.toInt() })
        val payload = Raster(2,2,intArrayOf(0x80FF0000.toInt(),0x0000FF00,0x400000FF,WHITE))
        val options = ToolOptions(background = 0xFFABCDEF.toInt())
        for (bounds in listOf(PixelRect(-1,1,4,3),PixelRect(4,-1,4,3),PixelRect(2,2,3,3))) {
            val preview = EditPreview(Tool.Select,bounds,source = PixelRect(1,1,2,2),payload = payload).rotatedSelection(33.0)
            for ((width,height) in listOf(6 to 5,8 to 7,3 to 3)) {
                val edit = preview.copy(outputWidth = width,outputHeight = height)
                val expected = canvas.clear(requireNotNull(edit.source),options.background)
                    .paste(edit.selectionPixels(),edit.bounds,width,height,options.background)
                val actual = canvas.render(edit,options)
                expected.pixels.indices.forEach { assertColorNear(expected.pixels[it],actual.pixels[it]) }
            }
        }
    }

    @Test fun backgroundCompositingInOnePassMatchesFlattening() {
        val source = Raster(2,2,intArrayOf(0x8000FF00.toInt(),0,WHITE,BLACK))
        for (transform in listOf(RasterTransform.rotation(33.0),RasterTransform.scale(-1.0,1.0),RasterTransform())) {
            val bounds = transform.bounds(2,2)
            val expected = source.transformed(transform,bounds).flatten(WHITE)
            val actual = source.transformed(transform,bounds,background = WHITE)
            expected.pixels.indices.forEach { assertColorNear(expected.pixels[it],actual.pixels[it]) }
        }
    }


    @Test fun cachedAndGlobalSamplingHaveIdenticalAlphaAtFractionalRotationEdges() {
        val payload = Raster(40,20,IntArray(800) { if (it%40 < 20) 0x80FF0000.toInt() else 0xFF0000FF.toInt() })
        val preview = EditPreview(Tool.Select,PixelRect(30,40,40,20),payload = payload).rotatedSelection(45.0)
        val request = preview.selectionImageRequest()
        val cached = payload.transformed(request.transform,PixelRect(0,0,request.width,request.height))
        assertArrayEquals(preview.selectionPixels().pixels,cached.pixels)
        val moved = preview.resizedSelection(preview.bounds.copy(x = 200,y = 300))
        assertArrayEquals(preview.selectionPixels().pixels,moved.selectionPixels().pixels)
    }

    private fun assertColorNear(expected: Int, actual: Int, tolerance: Int = 2) {
        // Compare visible premultiplied colors; zero-alpha RGB has no visual meaning.
        assertTrue("Alpha: ${expected.toUInt().toString(16)} != ${actual.toUInt().toString(16)}",
            abs((expected ushr 24)-(actual ushr 24)) <= tolerance)
        for (background in listOf(BLACK,WHITE)) {
            val before = composite(expected,background)
            val after = composite(actual,background)
            for (shift in listOf(16,8,0)) assertTrue("Visible color mismatch",
                abs((before ushr shift and 255)-(after ushr shift and 255)) <= tolerance)
        }
    }
}
