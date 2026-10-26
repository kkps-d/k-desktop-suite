package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class RasterTransformTest {
    private val fixture = Raster(2,3,intArrayOf(1,2,3,4,5,6).map { BLACK or it }.toIntArray())

    @Test fun transformFamiliesRecognizeComposedAndFractionalTransforms() {
        assertEquals(TransformFamily.QuarterTurns,RasterTransform.translation(12.0,-4.0).family())
        assertEquals(TransformFamily.QuarterTurns,RasterTransform.rotation(90.0).family())
        assertEquals(TransformFamily.QuarterTurns,RasterTransform.rotation(180.0).family())
        assertEquals(TransformFamily.Flips,RasterTransform.scale(-1.0,1.0).family())
        assertEquals(TransformFamily.Flips,RasterTransform.scale(1.0,-1.0).family())
        assertEquals(TransformFamily.Resize,RasterTransform.scale(1.25,0.75).family())
        assertEquals(TransformFamily.Resize,RasterTransform.translation(0.5,0.0).family())
        assertEquals(TransformFamily.Affine,RasterTransform.rotation(33.0).family())
        assertEquals(TransformFamily.Affine,RasterTransform(c = 0.2).family())
        assertEquals(TransformFamily.Affine,RasterTransform.scale(2.0,1.0).then(RasterTransform.rotation(90.0)).family())
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

    @Test fun selectionHistoryRegionIncludesSourceAndClippedDestination() {
        val preview = EditPreview(Tool.Select,PixelRect(-2,4,5,4),source = PixelRect(6,1,2,2),payload = fixture)
        assertEquals(PixelRect(0,1,8,7),preview.affectedSelectionArea(10,10))
        val moved = preview.resizedSelection(preview.bounds.copy(x = preview.bounds.x+12,y = preview.bounds.y+8))
        assertEquals(preview.selectionImageRequest(),moved.selectionImageRequest())
    }
}
