package com.kkps.kdesktopsuite.paint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/** A bitmap destination uses Android's software renderer, independently of the display Canvas. */
internal fun Raster.transformedBitmap(transform: RasterTransform, bounds: PixelRect,
    background: Int? = null, checkCancelled: () -> Unit = {}): Bitmap {
    checkImageSize(bounds.width,bounds.height)
    transform.inverse()
    checkCancelled()
    val output = Bitmap.createBitmap(bounds.width,bounds.height,Bitmap.Config.ARGB_8888)
    try {
        output.density = Bitmap.DENSITY_NONE
        checkCancelled()
        val canvas = Canvas(output)
        if (background != null) canvas.drawColor(background,PorterDuff.Mode.SRC)
        canvas.drawTransformed(this,transform,bounds,bounds.x,bounds.y,checkCancelled)
        checkCancelled()
        return output
    } catch (error: Throwable) {
        output.recycle()
        throw error
    }
}

internal fun Raster.renderSelectionCanvas(payload: Raster, transform: RasterTransform, bounds: PixelRect,
    source: PixelRect?, outputWidth: Int, outputHeight: Int, background: Int, checkCancelled: () -> Unit = {}): Raster {
    checkImageSize(outputWidth,outputHeight)
    transform.inverse()
    checkCancelled()
    val output = Bitmap.createBitmap(outputWidth,outputHeight,Bitmap.Config.ARGB_8888)
    try {
        output.density = Bitmap.DENSITY_NONE
        checkCancelled()
        val canvas = Canvas(output)
        canvas.drawColor(background,PorterDuff.Mode.SRC)
        val replace = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        val base = bitmap().apply { density = Bitmap.DENSITY_NONE }
        try { checkCancelled(); canvas.drawBitmap(base,0f,0f,replace) }
        finally { base.recycle() }
        val cleared = source?.clipped(minOf(width,outputWidth),minOf(height,outputHeight))
        cleared?.let { area ->
            replace.color = background or BLACK
            canvas.drawRect(area.x.toFloat(),area.y.toFloat(),area.right.toFloat(),area.bottom.toFloat(),replace)
        }
        val destination = bounds.clipped(outputWidth,outputHeight)
        canvas.drawTransformed(payload,transform,destination,0,0,checkCancelled)
        checkCancelled()
        // Reading only the affected area preserves untouched low-alpha pixels and bounded undo patches.
        val result = if (outputWidth == width && outputHeight == height) pixels.copyOf()
        else IntArray(outputWidth*outputHeight) { background }.also { copy ->
            for (y in 0 until minOf(height,outputHeight)) pixels.copyInto(copy,y*outputWidth,y*width,y*width+minOf(width,outputWidth))
        }
        for (area in listOfNotNull(cleared,destination).distinct()) {
            if (area.width > 0 && area.height > 0) {
                checkCancelled()
                output.getPixels(result,area.y*outputWidth+area.x,outputWidth,area.x,area.y,area.width,area.height)
            }
        }
        checkCancelled()
        return Raster(outputWidth,outputHeight,result)
    } finally { output.recycle() }
}

private fun Canvas.drawTransformed(payload: Raster, transform: RasterTransform, area: PixelRect,
    originX: Int, originY: Int, checkCancelled: () -> Unit) {
    if (area.width == 0 || area.height == 0) return
    // Match the local origin used by cached selection previews, even when the destination is clipped.
    val samplingOrigin = transform.bounds(payload.width,payload.height)
    val local = transform.then(RasterTransform.translation(-samplingOrigin.x.toDouble(),-samplingOrigin.y.toDouble()))
    val matrix = Matrix().apply { setValues(floatArrayOf(
        local.a.toFloat(),local.c.toFloat(),local.tx.toFloat(),
        local.b.toFloat(),local.d.toFloat(),local.ty.toFloat(),0f,0f,1f)) }
    val paint = Paint().apply {
        isFilterBitmap = when (transform.family()) {
            TransformFamily.QuarterTurns,TransformFamily.Flips -> false
            TransformFamily.Resize,TransformFamily.Affine -> true
        }
        isAntiAlias = false
        isDither = false
    }
    checkCancelled()
    val input = payload.bitmap().apply { density = Bitmap.DENSITY_NONE }
    try {
        checkCancelled()
        val saved = save()
        try {
            clipRect(area.x-originX,area.y-originY,area.right-originX,area.bottom-originY)
            translate((samplingOrigin.x-originX).toFloat(),(samplingOrigin.y-originY).toFloat())
            drawBitmap(input,matrix,paint)
        } finally { restoreToCount(saved) }
        checkCancelled()
    } finally { input.recycle() }
}
