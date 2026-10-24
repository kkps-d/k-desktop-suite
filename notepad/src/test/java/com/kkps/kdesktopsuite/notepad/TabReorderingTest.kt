package com.kkps.kdesktopsuite.notepad

import org.junit.Assert.assertEquals
import org.junit.Test

class TabReorderingTest {
    @Test
    fun crossesNeighborMidpointsWithoutCountingTheSourceTwice() {
        assertEquals(0, tabInsertionIndex(120f, 240f, 224f, 3, 0))
        assertEquals(0, tabInsertionIndex(343f, 240f, 224f, 3, 0))
        assertEquals(1, tabInsertionIndex(345f, 240f, 224f, 3, 0))
        assertEquals(2, tabInsertionIndex(600f, 240f, 224f, 3, 0))
        assertEquals(0, tabInsertionIndex(119f, 240f, 224f, 3, 2))
        assertEquals(1, tabInsertionIndex(343f, 240f, 224f, 3, 2))
    }

    @Test
    fun externalTabsCanBeInsertedAtEitherEndAndIntoEmptyStrips() {
        assertEquals(0, tabInsertionIndex(-100f, 120f, 104f, 4, -1))
        assertEquals(2, tabInsertionIndex(200f, 120f, 104f, 4, -1))
        assertEquals(4, tabInsertionIndex(900f, 120f, 104f, 4, -1))
        assertEquals(0, tabInsertionIndex(900f, 120f, 104f, 0, -1))
    }
}
