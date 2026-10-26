package com.kkps.kdesktopsuite.paint

import kotlin.math.*

/** Maps source pixel edges into image coordinates. Composition applies [next] last. */
internal data class RasterTransform(
    val a: Double = 1.0, val b: Double = 0.0, val c: Double = 0.0,
    val d: Double = 1.0, val tx: Double = 0.0, val ty: Double = 0.0,
) {
    fun x(x: Double, y: Double) = a*x + c*y + tx
    fun y(x: Double, y: Double) = b*x + d*y + ty
    fun point(x: Double, y: Double) = Point(x(x,y).toFloat(),y(x,y).toFloat())
    fun then(next: RasterTransform) = RasterTransform(
        next.a*a + next.c*b, next.b*a + next.d*b,
        next.a*c + next.c*d, next.b*c + next.d*d,
        next.x(tx,ty), next.y(tx,ty))
    fun inverse(): RasterTransform {
        val det = a*d - b*c
        require(det.isFinite() && abs(det) > 1e-12) { "The transform is too small." }
        return RasterTransform(d/det,-b/det,-c/det,a/det,
            (c*ty-d*tx)/det,(b*tx-a*ty)/det)
    }
    fun corners(width: Int, height: Int) = listOf(point(0.0,0.0),point(width.toDouble(),0.0),
        point(width.toDouble(),height.toDouble()),point(0.0,height.toDouble()))
    fun bounds(width: Int, height: Int): PixelRect {
        val corners = corners(width,height)
        require(corners.all { it.x.isFinite() && it.y.isFinite() && abs(it.x) < 1e8 && abs(it.y) < 1e8 }) {
            "The transformed image is too large."
        }
        // Suppress floating-point noise at exact pixel edges (especially quarter turns).
        val left = floor(corners.minOf { it.x }.toDouble()+1e-5).toInt()
        val top = floor(corners.minOf { it.y }.toDouble()+1e-5).toInt()
        val right = ceil(corners.maxOf { it.x }.toDouble()-1e-5).toInt()
        val bottom = ceil(corners.maxOf { it.y }.toDouble()-1e-5).toInt()
        return PixelRect(left,top,max(1,right-left),max(1,bottom-top))
    }
    companion object {
        fun translation(x: Double, y: Double) = RasterTransform(tx = x,ty = y)
        fun scale(x: Double, y: Double) = RasterTransform(a = x,d = y)
        fun rotation(degrees: Double): RasterTransform {
            val radians = degrees*PI/180
            fun exact(value: Double) = if (abs(value-round(value)) < 1e-10) round(value) else value
            val cosine = exact(cos(radians)); val sine = exact(sin(radians))
            return RasterTransform(cosine,sine,-sine,cosine)
        }
        fun around(center: Point, transform: RasterTransform) = translation(-center.x.toDouble(),-center.y.toDouble())
            .then(transform).then(translation(center.x.toDouble(),center.y.toDouble()))
    }
}

internal fun resizeSkewTransform(bounds: PixelRect, width: Int, height: Int, horizontal: Int, vertical: Int): RasterTransform {
    checkImageSize(width,height)
    require(horizontal in -89..89 && vertical in -89..89) { "Skew angles must be between −89° and 89°." }
    val transform = RasterTransform.scale(width.toDouble()/bounds.width,height.toDouble()/bounds.height)
        .then(RasterTransform(c = tan(horizontal*PI/180)))
        .then(RasterTransform(b = tan(vertical*PI/180)))
    val result = transform.bounds(bounds.width,bounds.height)
    return RasterTransform.translation(-bounds.x.toDouble(),-bounds.y.toDouble()).then(transform)
        .then(RasterTransform.translation((bounds.x-result.x).toDouble(),(bounds.y-result.y).toDouble()))
}

internal enum class TransformFamily { QuarterTurns, Flips, Resize, Affine }

internal fun RasterTransform.family(): TransformFamily {
    val integral = listOf(a,b,c,d,tx,ty).all { it == round(it) }
    val permutation = integral && abs(a)+abs(b) == 1.0 && abs(c)+abs(d) == 1.0 && a*c+b*d == 0.0
    return when {
        permutation && a*d-b*c < 0 -> TransformFamily.Flips
        permutation -> TransformFamily.QuarterTurns
        b == 0.0 && c == 0.0 -> TransformFamily.Resize
        else -> TransformFamily.Affine
    }
}

// Three-pass benchmarks favor Canvas resampling, but Kotlin wins exact turns/flips and row copies.
// See performance/CANVAS.md; this policy is shared by settled previews and committed pixels.
internal fun RasterTransform.usesCanvas() = when (family()) {
    TransformFamily.QuarterTurns,TransformFamily.Flips -> false
    TransformFamily.Resize,TransformFamily.Affine -> true
}

/** Inverse sampling avoids holes; premultiplied channels keep transparent edges free of halos. */
internal fun Raster.transformed(transform: RasterTransform, bounds: PixelRect = transform.bounds(width,height),
    checkCancelled: () -> Unit = {}): Raster = transformRaster(transform,bounds,checkCancelled,null)

internal fun Raster.transformed(transform: RasterTransform, bounds: PixelRect, background: Int,
    checkCancelled: () -> Unit = {}): Raster = transformRaster(transform,bounds,checkCancelled,background)

private fun Raster.transformRaster(transform: RasterTransform, bounds: PixelRect, checkCancelled: () -> Unit, background: Int?): Raster {
    checkImageSize(bounds.width,bounds.height)
    if (transform == RasterTransform() && bounds == PixelRect(0,0,width,height) && background == null) return this
    if (transform.usesCanvas()) {
        val bitmap = transformedBitmap(transform,bounds,background,checkCancelled)
        return try {
            checkCancelled()
            bitmap.raster().also { checkCancelled() }
        } finally { bitmap.recycle() }
    }
    val output = IntArray(bounds.width*bounds.height) { background ?: 0 }
    transformInto(transform,bounds,output,bounds.width,bounds.x,bounds.y,background != null,checkCancelled)
    return Raster(bounds.width,bounds.height,output)
}

/** Copy the canvas once, clear the lifted region, then sample only the visible destination. */
internal fun Raster.renderSelection(payload: Raster, transform: RasterTransform, bounds: PixelRect, source: PixelRect?,
    outputWidth: Int, outputHeight: Int, background: Int, checkCancelled: () -> Unit = {}): Raster {
    checkImageSize(outputWidth,outputHeight)
    if (transform.usesCanvas()) return renderSelectionCanvas(payload,transform,bounds,source,outputWidth,outputHeight,background,checkCancelled)
    checkCancelled()
    val output = if (outputWidth == width && outputHeight == height) pixels.copyOf()
    else IntArray(outputWidth*outputHeight) { background }.also { result ->
        for (y in 0 until min(height,outputHeight)) pixels.copyInto(result,y*outputWidth,y*width,y*width+min(width,outputWidth))
    }
    source?.clipped(min(width,outputWidth),min(height,outputHeight))?.let { area ->
        for (y in area.y until area.bottom) output.fill(background or BLACK,y*outputWidth+area.x,y*outputWidth+area.right)
    }
    payload.transformInto(transform,bounds.clipped(outputWidth,outputHeight),output,outputWidth,0,0,true,checkCancelled)
    return Raster(outputWidth,outputHeight,output)
}

private fun Raster.transformInto(transform: RasterTransform, area: PixelRect, output: IntArray, outputWidth: Int,
    originX: Int, originY: Int, blend: Boolean, checkCancelled: () -> Unit) {
    // A common local origin makes cached previews and committed pixels agree at rounding boundaries.
    val samplingOrigin = transform.bounds(width,height)
    val inverse = transform.then(RasterTransform.translation(-samplingOrigin.x.toDouble(),-samplingOrigin.y.toDouble())).inverse()
    val exact = listOf(inverse.a,inverse.b,inverse.c,inverse.d,inverse.tx,inverse.ty).all { it == round(it) } &&
        abs(inverse.a)+abs(inverse.b) == 1.0 && abs(inverse.c)+abs(inverse.d) == 1.0 &&
        inverse.a*inverse.c+inverse.b*inverse.d == 0.0
    if (exact) {
        val ax = inverse.a.toInt(); val bx = inverse.b.toInt()
        val cx = inverse.c.toInt(); val dx = inverse.d.toInt()
        val offsetX = (inverse.tx+(ax+cx-1)/2.0).toInt()
        val offsetY = (inverse.ty+(bx+dx-1)/2.0).toInt()
        for (y in area.y until area.bottom) {
            checkCancelled()
            var sx = ax*(area.x-samplingOrigin.x)+cx*(y-samplingOrigin.y)+offsetX
            var sy = bx*(area.x-samplingOrigin.x)+dx*(y-samplingOrigin.y)+offsetY
            var destination = (y-originY)*outputWidth+area.x-originX
            if (!blend && ax == 1 && bx == 0 && sx >= 0 && sx+area.width <= width && sy in 0 until height) {
                pixels.copyInto(output,destination,sy*width+sx,sy*width+sx+area.width)
                continue
            }
            for (x in area.x until area.right) {
                if (sx in 0 until width && sy in 0 until height) {
                    val color = pixels[sy*width+sx]
                    output[destination] = if (blend) composite(color,output[destination]) else color
                }
                sx += ax; sy += bx; destination++
            }
        }
        return
    }
    for (y in area.y until area.bottom) {
        checkCancelled()
        for (x in area.x until area.right) {
            val px = inverse.x(x-samplingOrigin.x+0.5,y-samplingOrigin.y+0.5)
            val py = inverse.y(x-samplingOrigin.x+0.5,y-samplingOrigin.y+0.5)
            if (px < -1e-6 || py < -1e-6 || px >= width || py >= height) continue
            val sx = (px-0.5).coerceIn(0.0,width-1.0); val sy = (py-0.5).coerceIn(0.0,height-1.0)
            val ix = sx.toInt(); val iy = sy.toInt()
            val fx = sx-ix; val fy = sy-iy
            val destination = (y-originY)*outputWidth+x-originX
            if (fx < 1e-10 && fy < 1e-10) {
                val color = pixel(ix,iy)
                output[destination] = if (blend) composite(color,output[destination]) else color
                continue
            }
            val nextX = min(ix+1,width-1); val nextY = min(iy+1,height-1)
            val p00 = pixel(ix,iy); val p10 = pixel(nextX,iy)
            val p01 = pixel(ix,nextY); val p11 = pixel(nextX,nextY)
            val w00 = (1-fx)*(1-fy)*(p00 ushr 24); val w10 = fx*(1-fy)*(p10 ushr 24)
            val w01 = (1-fx)*fy*(p01 ushr 24); val w11 = fx*fy*(p11 ushr 24)
            val alpha = w00+w10+w01+w11
            val red = (p00 ushr 16 and 255)*w00+(p10 ushr 16 and 255)*w10+(p01 ushr 16 and 255)*w01+(p11 ushr 16 and 255)*w11
            val green = (p00 ushr 8 and 255)*w00+(p10 ushr 8 and 255)*w10+(p01 ushr 8 and 255)*w01+(p11 ushr 8 and 255)*w11
            val blue = (p00 and 255)*w00+(p10 and 255)*w10+(p01 and 255)*w01+(p11 and 255)*w11
            if (alpha > 0) {
                val color = (alpha.roundToInt().coerceIn(0,255) shl 24) or
                    ((red/alpha).roundToInt().coerceIn(0,255) shl 16) or
                    ((green/alpha).roundToInt().coerceIn(0,255) shl 8) or (blue/alpha).roundToInt().coerceIn(0,255)
                output[destination] = if (blend) composite(color,output[destination]) else color
            }
        }
    }
}

internal fun Raster.flatten(background: Int) = Raster(width,height,IntArray(pixels.size) { composite(pixels[it],background) })
