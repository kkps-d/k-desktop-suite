package com.kkps.kdesktopsuite.explorer

import androidx.compose.ui.geometry.Rect
import com.kkps.kdesktopsuite.ui.DesktopCaption
import org.junit.Assert.*
import org.junit.Test

class ExplorerCaptionTest {
    private val region = Rect(0f, 0f, 960f, 600f)
    private val caption = DesktopCaption(48f, listOf(Rect(0f, 0f, 80f, 48f), Rect(800f, 0f, 960f, 48f)))

    @Test fun reservesOnlyEightDpBesideSystemControlsWithoutMirroringTheirBounds() {
        assertEquals(Rect(80f, 0f, 792f, 48f), explorerCaptionControls(caption, region, 1f, 1f, 24f, false))
        assertEquals(Rect(88f, 0f, 800f, 48f), explorerCaptionControls(caption, region, 1f, 1f, 24f, true))
    }

    @Test fun scalesClearanceAndRequiresOneTabAndBothButtons() {
        listOf(1f, 1.5f, 2.75f).forEach { density ->
            val scaled = DesktopCaption(48f * density, listOf(Rect(0f, 0f, 80f * density, 48f * density)))
            fun controls(width: Float) = explorerCaptionControls(scaled,
                Rect(0f, 0f, (80f + width) * density, 600f * density), density, 1f, 24f * density, false)
            assertNull(controls(199f))
            assertEquals(192f * density, controls(200f)!!.width, 0.01f)
        }
    }

    @Test fun missingBlockedOrShortCaptionFallsBackAndContentOriginIsRespected() {
        assertNull(explorerCaptionControls(DesktopCaption(), region, 1f, 1f, 24f, false))
        assertNull(explorerCaptionControls(DesktopCaption(48f), region, 1f, 1f, 24f, false))
        assertNull(explorerCaptionControls(DesktopCaption(48f, listOf(region)), region, 1f, 1f, 24f, false))
        assertNull(explorerCaptionControls(caption, region, 1f, 2f, 52f, false))
        assertNull(explorerCaptionControls(caption, region.copy(top = 25f), 1f, 1f, 24f, false))
        assertEquals(Rect(80f, 16f, 792f, 48f),
            explorerCaptionControls(caption, region.copy(top = 16f), 1f, 1f, 24f, false))
    }
}
