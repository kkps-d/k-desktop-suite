package com.kkps.kdesktopsuite.paint

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class RasterImage(val raster: Raster, val bitmap: ImageBitmap)

/** Pixel conversion and first texture preparation never run in composition. */
@Composable
internal fun rememberRasterImage(raster: Raster?, onError: (Int) -> Unit): RasterImage? {
    val image by produceState<RasterImage?>(null,raster) {
        if (raster == null) value = null
        else try {
            value = withContext(Dispatchers.Default) {
                val bitmap = raster.bitmap().apply { prepareToDraw() }
                RasterImage(raster,bitmap.asImageBitmap())
            }
        } catch (_: OutOfMemoryError) { onError(R.string.error_display_memory) }
    }
    return image
}

internal data class SelectionImageRequest(val payload: Raster, val transform: RasterTransform, val width: Int, val height: Int)
internal data class SelectionImage(val request: SelectionImageRequest, val bitmap: ImageBitmap)

/** Integer moves reuse the same sampled pixels: only their canvas position changes. */
internal fun EditPreview.selectionImageRequest() = SelectionImageRequest(requireNotNull(payload),
    selectionTransform.then(RasterTransform.translation(-bounds.x.toDouble(),-bounds.y.toDouble())),bounds.width,bounds.height)

@Composable
internal fun rememberSelectionImage(request: SelectionImageRequest?, dragging: Boolean, onError: (Int) -> Unit): SelectionImage? {
    val image by produceState<SelectionImage?>(null,request,dragging) {
        if (request == null) value = null
        else if (!dragging && value?.request != request) {
            var unpublished: Bitmap? = null
            try {
                value = withContext(Dispatchers.Default) {
                    val bounds = PixelRect(0,0,request.width,request.height)
                    val bitmap = if (request.transform.usesCanvas()) request.payload.transformedBitmap(request.transform,bounds,checkCancelled = { ensureActive() })
                        else request.payload.transformed(request.transform,bounds,{ ensureActive() }).bitmap()
                    unpublished = bitmap
                    ensureActive()
                    bitmap.prepareToDraw()
                    SelectionImage(request,bitmap.asImageBitmap())
                }
                unpublished = null
            } catch (_: OutOfMemoryError) { onError(R.string.error_preview_memory) }
            finally { unpublished?.recycle() }
        }
    }
    return image?.takeIf { it.request == request }
}
