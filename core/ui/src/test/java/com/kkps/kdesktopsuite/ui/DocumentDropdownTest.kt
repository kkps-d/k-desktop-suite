package com.kkps.kdesktopsuite.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class DocumentDropdownTest {
    @Test fun actionMenuStaysBesideItsButtonInAnOffsetWindow() {
        val position = TabActionMenuPosition(Rect(420f, 200f, 468f, 248f), Rect(100f, 80f, 500f, 700f), 4, 8)
        assertEquals(IntOffset(228, 252), position.calculatePosition(IntRect.Zero, IntSize(1000, 800),
            LayoutDirection.Ltr, IntSize(240, 144)))
    }

    @Test fun actionMenuFlipsAboveTheButtonNearTheBottom() {
        val position = TabActionMenuPosition(Rect(420f, 650f, 468f, 698f), Rect(100f, 80f, 500f, 700f), 4, 8)
        assertEquals(IntOffset(228, 502), position.calculatePosition(IntRect.Zero, IntSize(1000, 800),
            LayoutDirection.Ltr, IntSize(240, 144)))
    }

    @Test fun actionMenuUsesTheLeadingEdgeInRtlAndRemainsInsideThePane() {
        val position = TabActionMenuPosition(Rect(140f, 200f, 188f, 248f), Rect(100f, 80f, 500f, 700f), 4, 8)
        assertEquals(IntOffset(140, 252), position.calculatePosition(IntRect.Zero, IntSize(1000, 800),
            LayoutDirection.Rtl, IntSize(240, 144)))
    }

    @Test fun scrollingRampsAtBothEdgesAndContinuesJustOutsideThem() {
        assertEquals(0f, tabEdgeScrollFraction(Offset(20f, 150f), 400f, 300f, 64f), 0.001f)
        assertEquals(-0.5f, tabEdgeScrollFraction(Offset(20f, 32f), 400f, 300f, 64f), 0.001f)
        assertEquals(0.5f, tabEdgeScrollFraction(Offset(20f, 268f), 400f, 300f, 64f), 0.001f)
        assertEquals(-1f, tabEdgeScrollFraction(Offset(20f, -10f), 400f, 300f, 64f), 0.001f)
        assertEquals(1f, tabEdgeScrollFraction(Offset(20f, 310f), 400f, 300f, 64f), 0.001f)
        assertEquals(0f, tabEdgeScrollFraction(Offset(450f, 310f), 400f, 300f, 64f), 0.001f)
    }
}
