package com.kkps.kdesktopsuite.paint

import org.junit.Assert.*
import org.junit.Test

class FingerInputTest {
    @Test fun automaticUsesAttachmentBeforeSessionFallback() {
        assertFalse(FingerInput.Automatic.navigationOnly(true,true,true))
        assertTrue(FingerInput.Automatic.navigationOnly(true,false,false))
        assertFalse(FingerInput.Automatic.navigationOnly(true,null,false))
        assertTrue(FingerInput.Automatic.navigationOnly(true,null,true))
    }

    @Test fun explicitChoicesAndUnsupportedDevicesRemainUsable() {
        for (attached in listOf(true,false,null)) {
            assertFalse(FingerInput.Draw.navigationOnly(true,attached,true))
            assertTrue(FingerInput.Navigate.navigationOnly(true,attached,false))
            for (mode in FingerInput.entries) assertFalse(mode.navigationOnly(false,attached,true))
        }
        val defaults = PaintPreferences()
        assertEquals(FingerInput.Automatic,defaults.fingerInput)
        assertTrue(defaults.stylusHoverCursor)
        assertTrue(defaults.stylusButtonEraser)
    }
}
