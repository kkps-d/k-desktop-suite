package com.kkps.kdesktopsuite.paint

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RasterImagesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun integerMovesReusePixelsAndDraggingDefersResamplingUntilRelease() {
        val payload = Raster(40,20,IntArray(800) { if (it%40 < 20) 0x80FF0000.toInt() else 0xFF0000FF.toInt() })
        val original = payload.pixels.copyOf()
        var preview by mutableStateOf(EditPreview(Tool.Select,PixelRect(10,20,40,20),payload = payload))
        var dragging by mutableStateOf(false)
        var displayed: SelectionImage? = null
        compose.setContent {
            val image = rememberSelectionImage(preview.selectionImageRequest(),dragging) { fail(it) }
            SideEffect { displayed = image }
        }
        compose.waitUntil(5000) { displayed != null }
        val initial = displayed
        compose.runOnIdle { preview = preview.resizedSelection(preview.bounds.copy(x = 30,y = 40)) }
        compose.runOnIdle { assertSame(initial,displayed); dragging = true; preview = preview.rotatedSelection(30.0) }
        compose.runOnIdle { assertNull(displayed); preview = preview.rotatedSelection(15.0) }
        compose.runOnIdle { assertNull(displayed); dragging = false }
        compose.waitUntil(5000) { displayed != null }
        compose.runOnIdle {
            assertNotSame(initial,displayed)
            val expected = preview.selectionPixels().bitmap()
            try { assertArrayEquals(expected.raster().pixels,requireNotNull(displayed).bitmap.asAndroidBitmap().raster().pixels) }
            finally { expected.recycle() }
            assertArrayEquals(original,payload.pixels)
        }
    }

    @Test fun canvasBitmapReplacementPreservesThePreviousImmutableImage() {
        val original = Raster.blank(30,20,BLACK)
        val replacement = Raster.blank(30,20,WHITE)
        var source by mutableStateOf(original)
        var displayed: RasterImage? = null
        compose.setContent {
            val image = rememberRasterImage(source) { fail(it) }
            SideEffect { displayed = image }
        }
        compose.waitUntil(5000) { displayed?.raster === original }
        val previous = requireNotNull(displayed)
        compose.runOnIdle { source = replacement }
        compose.waitUntil(5000) { displayed?.raster === replacement }
        compose.runOnIdle {
            assertEquals(BLACK,previous.bitmap.asAndroidBitmap().getPixel(0,0))
            assertEquals(WHITE,requireNotNull(displayed).bitmap.asAndroidBitmap().getPixel(0,0))
        }
    }
}
