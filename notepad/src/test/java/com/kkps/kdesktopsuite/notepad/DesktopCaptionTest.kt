package com.kkps.kdesktopsuite.notepad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class DesktopCaptionTest {
    private val region = Rect(0f, 0f, 1200f, 800f)

    @Test
    fun choosesWidestUnobstructedSpanWithUnsortedAndOverlappingControls() {
        val caption = DesktopCaption(56f, listOf(
            Rect(1080f, 0f, 1200f, 56f), Rect(0f, 0f, 48f, 56f),
            Rect(500f, 0f, 520f, 56f), Rect(510f, 0f, 540f, 56f),
            Rect(0f, 300f, 1200f, 350f),
        ))
        val layout = captionLayout(caption, region, 1f)
        assertEquals(CaptionMode.Tabs, layout.mode)
        assertEquals(Rect(540f, 0f, 1080f, 56f), layout.span)
    }

    @Test
    fun captionRequiresRoomForTabNewAndDraggingAtEveryDensity() {
        val threshold = DesktopCaptionDimensions.minTabStripWidth.value
        listOf(1f, 1.5f, 2.75f).forEach { density ->
            listOf(48f to CaptionMode.BelowCaption,
                (threshold - 1f) to CaptionMode.BelowCaption, threshold to CaptionMode.Tabs).forEach { (width, expected) ->
                val caption = DesktopCaption(56f * density, listOf(Rect(0f, 0f, 48f * density, 56f * density)))
                val bounds = Rect(0f, 0f, (48f + width) * density, 800f * density)
                assertEquals("$width dp at $density", expected, captionLayout(caption, bounds, density).mode)
            }
        }
    }

    @Test
    fun rtlMovesDraggingSpaceWithoutMirroringSystemControls() {
        val caption = DesktopCaption(56f, listOf(Rect(0f, 0f, 48f, 56f), Rect(1080f, 0f, 1200f, 56f)))
        val layout = captionLayout(caption, region, 1f)
        assertEquals(Rect(48f, 0f, 1072f, 56f), layout.controls(1f, rtl = false))
        assertEquals(Rect(56f, 0f, 1080f, 56f), layout.controls(1f, rtl = true))
    }

    @Test
    fun respectsContentOriginAndHingeRegion() {
        val caption = DesktopCaption(80f, listOf(Rect(1080f, 0f, 1200f, 80f)))
        val layout = captionLayout(caption, Rect(620f, 56f, 1180f, 800f), 1f)
        assertEquals(Rect(620f, 56f, 1080f, 80f), layout.span)
        assertEquals(CaptionMode.Tabs, layout.mode)
        assertEquals(CaptionMode.BelowCaption, captionLayout(caption, Rect(620f, 57f, 1180f, 800f), 1f).mode)
        assertEquals(CaptionMode.BelowCaption, captionLayout(caption, Rect(0f, 400f, 1200f, 800f), 1f).mode)
    }

    @Test
    fun systemHeightCaptionsIntegrateAndScaledTextFallsBackOnlyWhenNecessary() {
        listOf(1f, 1.5f, 2.75f).forEach { density ->
            val bounds = Rect(0f, 0f, 1200f * density, 800f * density)
            fun caption(height: Float) = DesktopCaption(height * density, listOf(Rect(0f, 0f, 48f * density, height * density)))
            assertEquals(CaptionMode.BelowCaption, captionLayout(caption(23f), bounds, density).mode)
            assertEquals(CaptionMode.Tabs, captionLayout(caption(24f), bounds, density).mode)
            assertEquals(CaptionMode.Tabs, captionLayout(caption(32f), bounds, density).mode)
            assertEquals(CaptionMode.Tabs, captionLayout(caption(40f), bounds, density).mode)
            assertEquals(CaptionMode.BelowCaption, captionLayout(caption(40f), bounds, density, 48f * density).mode)
            assertEquals(CaptionMode.Tabs, captionLayout(caption(48f), bounds, density, 48f * density).mode)
        }
    }

    @Test
    fun missingOrFullyObstructedGeometryFallsBackAndFullscreenUsesRegularTabs() {
        assertEquals(CaptionMode.Regular, captionLayout(DesktopCaption(), region, 1f).mode)
        assertEquals(CaptionMode.BelowCaption, captionLayout(DesktopCaption(56f), region, 1f).mode)
        val blocked = DesktopCaption(56f, listOf(Rect(0f, 0f, 1200f, 56f)))
        assertEquals(CaptionMode.BelowCaption, captionLayout(blocked, region, 1f).mode)
    }

    @Test
    fun gestureRectsClipScrolledTabsToCaptionAndControlsThenConvertToHostCoordinates() {
        val clip = Rect(48f, 0f, 300f, 40f)
        val controls = listOf(Rect(-100f, 0f, 100f, 48f), Rect(100f, 0f, 220f, 48f),
            Rect(252f, 0f, 300f, 48f), Rect(350f, 0f, 470f, 48f))
        assertEquals(listOf(Rect(38f, -4f, 90f, 36f), Rect(90f, -4f, 210f, 36f), Rect(242f, -4f, 290f, 36f)),
            captionGestureRects(controls, clip, Offset(10f, 4f)))
        assertTrue(captionGestureRects(controls, null, Offset.Zero).isEmpty())
        assertTrue(captionGestureRects(emptyList(), clip, Offset.Zero).isEmpty())
    }
}
