package com.kkps.kdesktopsuite.paint

import androidx.compose.ui.geometry.Rect
import com.kkps.kdesktopsuite.ui.DesktopCaption
import org.junit.Assert.*
import org.junit.Test

class PaintCaptionTest {
    @Test fun headerRequiresEnoughWidthAndHeightAtEveryDensity() {
        for (density in listOf(1f, 1.5f, 2.75f)) {
            for (fontScale in listOf(1f, 2f)) {
                val minHeight = 32f * fontScale * density
                val height = minHeight + 8f * density
                val requiredWidth = 500f * fontScale * density
                val caption = DesktopCaption(height, listOf(Rect(0f, 0f, 48f * density, height)))
                val region = Rect(0f, 0f, 48f * density + requiredWidth, 800f * density)
                val controls = paintCaptionControls(caption, region, density, fontScale, minHeight)
                assertNotNull(controls)
                assertEquals(500f * fontScale * density, controls!!.width, 0.01f)
                assertNull(paintCaptionControls(caption, region.copy(right = region.right - density), density, fontScale, minHeight))
                assertNull(paintCaptionControls(caption.copy(height = minHeight - density), region, density, fontScale, minHeight))
            }
        }
    }

    @Test fun respectsPhysicalSystemControlsCutoutsAndContentOrigin() {
        val caption = DesktopCaption(56f, listOf(
            Rect(1200f, 0f, 1320f, 56f), Rect(0f, 0f, 48f, 56f), Rect(48f, 0f, 100f, 56f),
        ))
        val region = Rect(20f, 8f, 1320f, 800f)
        assertEquals(Rect(100f, 8f, 1200f, 56f), paintCaptionControls(caption, region, 1f, 1f, 32f))
        assertNull(paintCaptionControls(caption, region.copy(top = 25f), 1f, 1f, 32f))
    }

    @Test fun unsupportedMissingOrBlockedCaptionsKeepTheRegularHeader() {
        val region = Rect(0f, 0f, 1200f, 800f)
        for (caption in listOf(DesktopCaption(), DesktopCaption(40f),
            DesktopCaption(40f, listOf(Rect(0f, 0f, 1200f, 40f))))) {
            assertNull(paintCaptionControls(caption, region, 1f, 1f, 32f))
        }
    }
}
