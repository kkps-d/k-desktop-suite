package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class RasterTest {
    @Test fun colorKeyMatchesExactRgbAtEveryAlphaWithoutChangingSourcePixels() {
        val colors = intArrayOf(0xFF00FF00.toInt(),0x8000FF00.toInt(),0x0000FF00,
            0xFF00FE00.toInt(),0x80FF0000.toInt(),0x00123456)
        val source = Raster(3,2,colors.copyOf())
        val keyed = source.colorKeyed(0x1200FF00)
        assertArrayEquals(intArrayOf(0,0,0,colors[3],colors[4],colors[5]),keyed.pixels)
        assertArrayEquals(colors,source.pixels)
        assertSame(source,source.colorKeyed(null))
    }

    @Test fun cancelledKeyPreparationKeepsTheOriginalRaster() {
        val source = Raster.blank(3,3)
        var rows = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            source.colorKeyed(WHITE) { if (++rows == 2) throw java.util.concurrent.CancellationException() }
        }
        assertTrue(source.pixels.all { it == WHITE })
    }

    @Test fun keyedSelectionClearsTheWholeSourceAndBlendsOnlyNonmatchingPixels() {
        val key = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()
        val payload = Raster(2,1,intArrayOf(key,0x80FF0000.toInt()))
        val base = Raster.blank(6,2,blue)
        val preview = EditPreview(Tool.Select,PixelRect(3,0,2,1),source = PixelRect(0,0,2,1),
            payload = payload,transparencyKey = key)
        val options = ToolOptions(background = WHITE,transparentSelection = true)
        val result = base.render(preview,options)
        assertEquals(WHITE,result.pixel(0,0))
        assertEquals(WHITE,result.pixel(1,0))
        assertEquals(blue,result.pixel(3,0))
        assertEquals(composite(payload.pixel(1,0),blue),result.pixel(4,0))
        assertEquals(key,base.render(preview,options.copy(transparentSelection = false)).pixel(3,0))
        assertEquals(key,payload.pixel(0,0))
    }

    @Test fun canvasResizePreservesPixelsAndFillsOnlyAddedSpace() {
        val original = Raster(2,2,intArrayOf(BLACK,WHITE,WHITE,BLACK))
        val background = 0xFF00FF00.toInt()
        val expanded = original.resizeCanvas(3,3,background)
        assertArrayEquals(intArrayOf(BLACK,WHITE,background,WHITE,BLACK,background,background,background,background),expanded.pixels)
        val cropped = expanded.resizeCanvas(1,2,WHITE)
        assertArrayEquals(intArrayOf(BLACK,WHITE),cropped.pixels)
        assertArrayEquals(intArrayOf(BLACK,WHITE,WHITE,BLACK),original.pixels)
        assertSame(original,original.resizeCanvas(2,2,background))
    }

    @Test fun onePixelPencilHasExactCoverage() {
        val original = Raster.blank(5,5)
        val result = original.brushStroke(listOf(Point(2f,2f)),BLACK,1f,BrushStyle.Solid,42)
        assertEquals(1,result.pixels.count { it == BLACK })
        assertEquals(BLACK,result.pixel(2,2))
        assertTrue(original.pixels.all { it == WHITE })
    }
    @Test fun sparseSamplesInterpolateAndSelectionClips() {
        val result = Raster.blank(12,3).brushStroke(listOf(Point(0f,1f),Point(11f,1f)),BLACK,1f,BrushStyle.Solid,42,PixelRect(3,0,4,3))
        assertEquals(4,result.pixels.count { it == BLACK })
        for (x in 3..6) assertEquals(BLACK,result.pixel(x,1))
    }
    @Test fun fillIsFourConnectedAndDoesNotTouchDiagonalRegions() {
        val raster = Raster(3,3,intArrayOf(WHITE,BLACK,WHITE, BLACK,WHITE,BLACK, WHITE,BLACK,WHITE))
        val result = raster.fill(0,0,0xFFFF0000.toInt(),null)
        assertEquals(0xFFFF0000.toInt(),result.pixel(0,0))
        assertEquals(WHITE,result.pixel(1,1))
        assertEquals(WHITE,result.pixel(2,0))
    }
    @Test fun fillRespectsSelectionAndNoOpReturnsSameRaster() {
        val raster = Raster.blank(4,4)
        assertSame(raster,raster.fill(0,0,WHITE,null))
        val filled = raster.fill(1,1,BLACK,PixelRect(1,1,2,2))
        assertEquals(4,filled.pixels.count { it == BLACK })
    }
    @Test fun sourceClearingAndAlphaInsertionAreOpaque() {
        val red = 0xFFFF0000.toInt(); val blue = 0xFF0000FF.toInt()
        val base = Raster.blank(3,3,red)
        val cleared = base.clear(PixelRect(0,0,1,1),blue)
        val inserted = cleared.paste(Raster(1,1,intArrayOf(0x8000FF00.toInt())),PixelRect(1,1,1,1))
        assertEquals(blue,inserted.pixel(0,0))
        assertEquals(255,inserted.pixel(1,1) ushr 24)
        assertEquals(127,inserted.pixel(1,1) ushr 16 and 255)
        assertEquals(128,inserted.pixel(1,1) ushr 8 and 255)
    }
    @Test fun extractAndOversizedInsertionPreservePositions() {
        val raster = Raster(2,2,intArrayOf(BLACK,WHITE,WHITE,BLACK))
        assertArrayEquals(intArrayOf(WHITE,BLACK),raster.extract(PixelRect(1,0,1,2)).pixels)
        val expanded = raster.paste(Raster.blank(2,2,0xFF00FF00.toInt()),PixelRect(2,0,2,2),4,2)
        assertEquals(4,expanded.width)
        assertEquals(BLACK,expanded.pixel(0,0))
        assertEquals(0xFF00FF00.toInt(),expanded.pixel(3,1))
        assertArrayEquals(intArrayOf(BLACK,WHITE,WHITE,BLACK),raster.pixels)
    }
    @Test fun undoRedoAndSavedRevisionTrackPixelAndDimensionChanges() {
        val initial = Raster.blank(4,3)
        val history = RasterHistory(initial)
        assertFalse(history.commit(Raster.blank(4,3)))
        history.commit(initial.brushStroke(listOf(Point(1f,1f)),BLACK,1f,BrushStyle.Solid,42))
        history.savedRevision = history.revision
        val saved = history.raster.pixels.copyOf()
        history.commit(history.raster.extract(PixelRect(1,1,2,1)))
        assertTrue(history.dirty)
        history.undo()
        assertEquals(4,history.raster.width); assertEquals(3,history.raster.height)
        assertArrayEquals(saved,history.raster.pixels); assertFalse(history.dirty)
        history.redo(); assertEquals(2,history.raster.width); assertTrue(history.dirty)
        history.undo(); history.commit(history.raster.clear(PixelRect(0,0,1,1),BLACK))
        assertFalse(history.canRedo)
    }
    @Test fun memoryBudgetEvictsCompleteChangesWithoutChangingCurrentRaster() {
        val history = RasterHistory(Raster.blank(3,3),8)
        history.commit(history.raster.clear(PixelRect(0,0,1,1),BLACK))
        history.commit(history.raster.clear(PixelRect(2,2,1,1),BLACK))
        history.undo(); assertEquals(WHITE,history.raster.pixel(2,2))
        assertEquals(BLACK,history.raster.pixel(0,0)); assertFalse(history.canUndo)
    }
    @Test fun invalidSizesAreRejected() {
        for ((width,height) in listOf(0 to 1,1 to -1,8193 to 1,8192 to 8192)) {
            assertThrows(IllegalArgumentException::class.java) { checkImageSize(width,height) }
        }
    }

    @Test fun preparedHistoryPatchCannotApplyToANewerRaster() {
        val history = RasterHistory(Raster.blank(4,4))
        val prepared = requireNotNull(history.prepare(history.raster.clear(PixelRect(0,0,1,1),BLACK)))
        history.commit(history.raster.clear(PixelRect(3,3,1,1),BLACK))
        assertThrows(IllegalStateException::class.java) { history.commit(prepared) }
        assertEquals(WHITE,history.raster.pixel(0,0))
        assertEquals(BLACK,history.raster.pixel(3,3))
    }
}
