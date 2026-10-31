package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class ColorSlotTest {
    @Test fun slotsWithTheSameColorHaveDifferentIdentities() {
        val preferences = PaintPreferences()
        val first = ColorSlot(ColorPalette.Custom,0)
        val second = ColorSlot(ColorPalette.Custom,1)
        val preset = ColorSlot(ColorPalette.Preset,10)
        assertEquals(WHITE,first.color(preferences))
        assertEquals(first.color(preferences),second.color(preferences))
        assertEquals(first.color(preferences),preset.color(preferences))
        assertNotEquals(first,second)
        assertNotEquals(first,preset)
    }

    @Test fun missingSlotsHaveNoColor() {
        val preferences = PaintPreferences()
        assertNull(ColorSlot(ColorPalette.Custom,-1).color(preferences))
        assertNull(ColorSlot(ColorPalette.Custom,10).color(preferences))
        assertNull(ColorSlot(ColorPalette.Preset,20).color(preferences))
    }

    @Test fun initialColorsDoNotImplySelectedSlots() {
        val state = PaintUiState(raster = Raster.blank(1,1))
        assertNull(state.foregroundSlot)
        assertNull(state.backgroundSlot)
    }

    @Test fun preferencesInvalidateOnlySlotsWhoseColorsChanged() {
        val first = ColorSlot(ColorPalette.Custom,0)
        val second = ColorSlot(ColorPalette.Custom,1)
        val state = PaintUiState(raster = Raster.blank(1,1),options = ToolOptions(foreground = WHITE),
            foregroundSlot = first,backgroundSlot = second)
        val preferences = state.preferences.copy(customColors = listOf(BLACK) + List(9) { WHITE })
        val changed = state.withPreferences(preferences)
        assertNull(changed.foregroundSlot)
        assertEquals(second,changed.backgroundSlot)
        assertEquals(state.options,changed.options)
        assertNull(changed.withPreferences(state.preferences).foregroundSlot)
        assertEquals(state,state.withPreferences(state.preferences.copy(appearance = "Dark")).copy(preferences = state.preferences))
        assertNull(state.withPreferences(preferences.copy(customColors = emptyList())).backgroundSlot)
    }
}
