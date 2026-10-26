package com.kkps.kdesktopsuite.paint

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BrushStrokeSessionTest {
    @Test fun incrementalPreviewsMatchWholeStrokesAndKeepPublishedPixelsImmutable() = runBlocking {
        val base = Raster.blank(180,140)
        val points = listOf(Point(-5.2f,16.4f),Point(35.7f,32.1f),Point(35.7f,32.1f),
            Point(139.4f,110.6f),Point(20f,55f),Point(190f,145f))
        for (style in BrushStyle.entries) for (width in listOf(3,25,100)) {
            val options = ToolOptions(brushStyle = style,width = width,foreground = 0x803A79C2.toInt())
            val clip = PixelRect(9,11,150,110)
            val session = BrushStrokeSession(base,options,42,clip)
            val snapshots = mutableListOf<Pair<Raster,IntArray>>()
            for (count in listOf(1,2,4,6)) {
                val prefix = points.take(count)
                val preview = session.render(prefix)
                assertArrayEquals("$style, $width px, $count points",
                    base.brushStroke(prefix,options.foreground,width.toFloat(),style,42,clip).pixels,preview.pixels)
                snapshots.add(preview to preview.pixels.copyOf())
            }
            snapshots.forEach { (raster,pixels) -> assertArrayEquals(pixels,raster.pixels) }
            assertSame(snapshots.last().first,session.render(points))
            assertTrue(base.pixels.all { it == WHITE })
        }
    }

    @Test fun longStrokesOnlyReadNewSamplesAndReuseTheFinalPreviewForCommit() = runBlocking {
        val base = Raster.blank(800,600)
        val options = ToolOptions(width = 100,brushStyle = BrushStyle.Watercolor)
        val session = BrushStrokeSession(base,options,73,null)
        val points = (0..300).map { Point(50f + it * 2,100f + (it % 100) * 3) }
        var reads = 0
        var latest = base
        val start = System.nanoTime()
        for (count in 1..points.size) {
            val samples = object : AbstractList<Point>() {
                override val size = count
                override fun get(index: Int): Point {
                    reads++
                    return points[index]
                }
            }
            latest = session.render(samples)
        }
        assertEquals(points.size,reads)
        assertSame(latest,session.render(points))
        assertArrayEquals(base.brushStroke(points,BLACK,100f,BrushStyle.Watercolor,73).pixels,latest.pixels)
        println("301 incremental 100px previews and commit: ${(System.nanoTime()-start)/1_000_000.0} ms")
    }

    @Test fun previewsCannotBeReusedForDifferentEdits() {
        val base = Raster.blank(20,20)
        val options = ToolOptions()
        val session = BrushStrokeSession(base,options,42,null)
        assertTrue(session.matches(base,options,42,null))
        assertFalse(session.matches(Raster.blank(20,20),options,42,null))
        assertFalse(session.matches(base,options.copy(width = 100),42,null))
        assertFalse(session.matches(base,options,43,null))
        assertFalse(session.matches(base,options,42,PixelRect(0,0,10,10)))
        assertFalse(session.matches(base,options,42,null,erasing = true))
    }

    @Test fun pencilUsesOnePixelSolidBrushRegardlessOfSelectedBrushSize() = runBlocking {
        val base = Raster.blank(12,8)
        val options = ToolOptions(tool = Tool.Pencil,width = 100,brushStyle = BrushStyle.Marker)
        val points = listOf(Point(2f,3f),Point(9f,3f))
        val preview = BrushStrokeSession(base,options,42,null).render(points)
        val tip = options.strokeBrush()

        assertEquals(1,options.strokeWidth)
        assertEquals(1f,tip.size)
        assertArrayEquals(base.brushStroke(points,BLACK,1f,BrushStyle.Solid,42).pixels,preview.pixels)
        assertEquals(8,preview.pixels.count { it == BLACK })
        assertEquals(WHITE,preview.pixel(5,2))
        assertEquals(WHITE,preview.pixel(5,4))
    }

    @Test fun eraserUsesOpaqueSquareBrushAndPreviewMatchesCommit() = runBlocking {
        val red = 0xFFFF0000.toInt()
        val base = Raster.blank(12,12,BLACK)
        val options = ToolOptions(tool = Tool.Eraser,eraserWidth = 4,background = red)
        val points = listOf(Point(5f,5f))
        val preview = BrushStrokeSession(base,options,42,null).render(points)
        val tip = options.strokeBrush()

        assertArrayEquals(base.brushStroke(points,red,4f,BrushStyle.Solid,42,square = true).pixels,preview.pixels)
        assertEquals(16,preview.pixels.count { it == red })
        for (y in 3..6) for (x in 3..6) assertEquals(red,preview.pixel(x,y))
        assertEquals(BLACK,preview.pixel(2,5))
        assertEquals(BLACK,preview.pixel(7,5))
        assertTrue(tip.square)
    }

    @Test fun stylusEraserUsesSquareBrushEvenWhenAnotherToolIsSelected() = runBlocking {
        val base = Raster.blank(12,12,BLACK)
        val options = ToolOptions(tool = Tool.Brush,eraserWidth = 3,background = WHITE)
        val session = BrushStrokeSession(base,options,42,null,erasing = true)

        assertTrue(session.matches(base,options,42,null,erasing = true))
        assertEquals(9,session.render(listOf(Point(5f,5f))).pixels.count { it == WHITE })
    }

    @Test fun squareEraserClipsToTheImageAndSelection() {
        val base = Raster.blank(10,10,BLACK)
        val points = listOf(Point(-20f,-20f),Point(0f,0f),Point(20f,20f))
        val clip = PixelRect(1,1,3,3)
        val result = base.brushStroke(points,WHITE,4f,BrushStyle.Solid,42,clip,square = true)

        assertTrue(result.pixels.any { it == WHITE })
        for (y in 0 until 10) for (x in 0 until 10) {
            if (!clip.contains(x,y)) assertEquals(BLACK,result.pixel(x,y))
        }
    }

    @Test fun boundedHistoryIncludesEveryBrushPixelAndRestoresUndoExactly() {
        val base = Raster.blank(240,180)
        for (style in BrushStyle.entries) for (width in listOf(1,25,100)) {
            for (points in listOf(listOf(Point(-20.5f,10.3f),Point(250f,190f)),
                listOf(Point(70.8f,65.2f)),listOf(Point(300f,300f)))) {
                val result = base.brushStroke(points,0x803A79C2.toInt(),width.toFloat(),style,42)
                val history = RasterHistory(base)
                val expected = history.prepare(result)
                val bounded = history.prepare(result,base.strokeBounds(points,width.toFloat()))
                assertEquals("$style, $width px",expected?.area,bounded?.area)
                if (bounded != null) {
                    history.commit(bounded)
                    history.undo()
                    assertArrayEquals(base.pixels,history.raster.pixels)
                    history.redo()
                    assertArrayEquals(result.pixels,history.raster.pixels)
                }
            }
        }
    }
}
