package com.kkps.kdesktopsuite.paint

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class RasterImage(val raster: Raster, val bitmap: ImageBitmap)

/** Pixel conversion and first texture preparation never run in composition. */
@Composable
internal fun rememberRasterImage(raster: Raster?, onError: (String) -> Unit): RasterImage? {
    val image by produceState<RasterImage?>(null,raster) {
        if (raster == null) value = null
        else try {
            value = withContext(Dispatchers.Default) {
                val bitmap = raster.bitmap().apply { prepareToDraw() }
                RasterImage(raster,bitmap.asImageBitmap())
            }
        } catch (_: OutOfMemoryError) { onError("Not enough memory to display this image.") }
    }
    return image
}

internal data class SelectionImageRequest(val payload: Raster, val transform: RasterTransform, val width: Int, val height: Int)
internal data class SelectionImage(val request: SelectionImageRequest, val bitmap: ImageBitmap)

/** Integer moves reuse the same sampled pixels: only their canvas position changes. */
internal fun EditPreview.selectionImageRequest() = SelectionImageRequest(requireNotNull(payload),
    selectionTransform.then(RasterTransform.translation(-bounds.x.toDouble(),-bounds.y.toDouble())),bounds.width,bounds.height)

@Composable
internal fun rememberSelectionImage(request: SelectionImageRequest?, dragging: Boolean, onError: (String) -> Unit): SelectionImage? {
    val image by produceState<SelectionImage?>(null,request,dragging) {
        if (request == null) value = null
        else if (!dragging && value?.request != request) try {
            value = withContext(Dispatchers.Default) {
                val pixels = request.payload.transformed(request.transform,PixelRect(0,0,request.width,request.height),{ ensureActive() })
                SelectionImage(request,pixels.bitmap().apply { prepareToDraw() }.asImageBitmap())
            }
        } catch (_: OutOfMemoryError) { onError("Not enough memory to preview this selection.") }
    }
    return image?.takeIf { it.request == request }
}
