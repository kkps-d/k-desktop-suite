package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class ViewportTest {
    @Test fun zoomIsLimitedToTenThroughFiveHundredPercent() {
        val viewport = Viewport(width = 400f,height = 300f)
        assertEquals(0.1f,viewport.zoomed(0f).scale,0f)
        assertEquals(5f,viewport.zoomed(8f).scale,0f)
        assertEquals(0.25f,viewport.zoomed(0.25f).scale,0f)
    }

    @Test fun fittingImageIsCenteredOnBothAxes() {
        val viewport = Viewport(width = 1200f,height = 900f,fit = false).clamped()
        assertEquals(200f,viewport.origin.x,0.001f)
        assertEquals(150f,viewport.origin.y,0.001f)
        assertEquals(viewport,viewport.panned(Point(100f,100f)))
    }
    @Test fun fitRecalculatesDuringResize() {
        val wide = Viewport().resized(1200f,900f)
        assertEquals(884f/600f,wide.scale,0.001f)
        val narrow = wide.resized(400f,800f)
        assertEquals(0.48f,narrow.scale,0.001f)
        assertEquals(8f,narrow.origin.x,0.001f)
        assertEquals(256f,narrow.origin.y,0.001f)
    }
    @Test fun explicitZoomPreservesImageAnchor() {
        val viewport = Viewport(width = 400f,height = 300f,scale = 2f,fit = false).clamped()
        val anchor = Point(120f,80f)
        val before = viewport.imagePoint(anchor)
        val zoomed = viewport.zoomed(3f,anchor)
        assertEquals(before.x,zoomed.imagePoint(anchor).x,0.001f)
        assertEquals(before.y,zoomed.imagePoint(anchor).y,0.001f)
    }
    @Test fun overflowingAxesPanWithinBoundsWhileFittingAxesCenter() {
        val viewport = Viewport(width = 400f,height = 900f,fit = false).clamped()
        val panned = viewport.panned(Point(10000f,-10000f))
        assertEquals(8f,panned.origin.x,0.001f)
        assertEquals(150f,panned.origin.y,0.001f)
        val otherEdge = panned.panned(Point(-20000f,0f))
        assertEquals(-408f,otherEdge.origin.x,0.001f)
    }
    @Test fun imageSpaceHitTestingDoesNotDependOnDensity() {
        for (scale in listOf(0.5f,1f,4f)) {
            val viewport = Viewport(width = 1200f,height = 900f,scale = scale,fit = false,margin = 24f).clamped()
            val target = Point(100.5f,92.5f)
            val roundTrip = viewport.imagePoint(viewport.screenPoint(target))
            assertEquals(target.x,roundTrip.x,0.001f); assertEquals(target.y,roundTrip.y,0.001f)
        }
    }
    @Test fun resizePreservesOversizedViewedCenterAndActualSize() {
        val original = Viewport(width = 400f,height = 300f,scale = 2f,center = Point(500f,400f),fit = false).clamped()
        val resized = original.resized(600f,400f)
        assertEquals(original.center,resized.center)
        assertEquals(1f,resized.actual().scale,0f)
    }
}
