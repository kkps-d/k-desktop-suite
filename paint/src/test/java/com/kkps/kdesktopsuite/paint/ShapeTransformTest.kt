package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class ShapeTransformTest {
    @Test fun resizingAcrossEachAxisRetainsEveryShapesGeometry() {
        for (tool in shapeTools) {
            val original = EditPreview.between(tool,Point(100f,100f),Point(180f,140f))
                .copy(constructing = false)
            for (horizontal in listOf(false,true)) for (vertical in listOf(false,true)) {
                val area = PixelRect(if (horizontal) 20 else 100,if (vertical) 60 else 100,80,40)
                val resized = original.resizedSelection(area,horizontal,vertical)
                assertEquals(tool,resized.tool)
                assertNull(resized.payload)
                assertEquals(area,resized.bounds)
                assertEquals(original.bounds,resized.geometryBounds)
                assertEquals(original.start,resized.start)
                assertEquals(original.end,resized.end)
                assertEquals(original.points,resized.points)
                assertEquals(Point(100f,100f),resized.selectionTransform.point(0.0,0.0))
                assertEquals(Point(if (horizontal) 20f else 180f,if (vertical) 60f else 140f),
                    resized.selectionTransform.point(80.0,40.0))
            }
        }
    }

    @Test fun rotationRetainsPivotThroughMoveResizeAndFourQuarterTurns() {
        val original = EditPreview.between(Tool.RightTriangle,Point(100f,100f),Point(180f,140f))
        var rotated = original
        repeat(4) { rotated = rotated.rotatedSelection(90.0) }
        assertEquals(original.bounds,rotated.bounds)
        assertEquals(original.selectionCorners,rotated.selectionCorners)
        assertEquals(original.selectionCenter,rotated.selectionCenter)
        val moved = original.rotatedSelection(30.0).withBounds(PixelRect(200,200,160,80))
        assertEquals(PixelRect(200,200,160,80),moved.bounds)
        assertEquals(30.0,moved.rotationDegrees,0.0)
        assertTrue(moved.containsSelection(moved.selectionCenter))
        assertFalse(moved.containsSelection(Point(0f,0f)))
    }

    @Test fun drawingClosedShapesBackwardsMirrorsTheirGeometry() {
        for (tool in shapeTools.filter { it.isClosedShape && it != Tool.Polygon }) {
            val preview = EditPreview.between(tool,Point(180f,140f),Point(100f,100f))
            assertEquals(PixelRect(100,100,80,40),preview.bounds)
            assertEquals(Point(180f,140f),preview.selectionCorners[0])
            assertEquals(Point(100f,100f),preview.selectionCorners[2])
        }
        val line = EditPreview.between(Tool.Line,Point(180f,140f),Point(100f,100f))
        assertEquals(Point(180f,140f),line.start)
        assertEquals(Point(100f,100f),line.end)
        assertNull(line.transform)
    }
}
