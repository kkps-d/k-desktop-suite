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

/** Inverse sampling avoids holes; premultiplied channels keep transparent edges free of halos. */
internal fun Raster.transformed(transform: RasterTransform, bounds: PixelRect = transform.bounds(width,height), checkCancelled: () -> Unit = {}): Raster {
    checkImageSize(bounds.width,bounds.height)
    val inverse = transform.inverse()
    val output = IntArray(bounds.width*bounds.height)
    for (y in 0 until bounds.height) {
        checkCancelled()
        for (x in 0 until bounds.width) {
            val px = inverse.x(x+bounds.x+0.5,y+bounds.y+0.5)
            val py = inverse.y(x+bounds.x+0.5,y+bounds.y+0.5)
            if (px < -1e-6 || py < -1e-6 || px >= width || py >= height) continue
            val sx = (px-0.5).coerceIn(0.0,width-1.0); val sy = (py-0.5).coerceIn(0.0,height-1.0)
            val ix = sx.toInt(); val iy = sy.toInt()
            val fx = sx-ix; val fy = sy-iy
            if (fx < 1e-10 && fy < 1e-10) { output[y*bounds.width+x] = pixel(ix,iy); continue }
            var alpha = 0.0; var red = 0.0; var green = 0.0; var blue = 0.0
            for (j in 0..1) for (i in 0..1) {
                val color = pixel(min(ix+i,width-1),min(iy+j,height-1))
                val weight = (if (i == 0) 1-fx else fx)*(if (j == 0) 1-fy else fy)*(color ushr 24)
                alpha += weight; red += (color ushr 16 and 255)*weight
                green += (color ushr 8 and 255)*weight; blue += (color and 255)*weight
            }
            if (alpha > 0) output[y*bounds.width+x] = (alpha.roundToInt().coerceIn(0,255) shl 24) or
                ((red/alpha).roundToInt().coerceIn(0,255) shl 16) or
                ((green/alpha).roundToInt().coerceIn(0,255) shl 8) or (blue/alpha).roundToInt().coerceIn(0,255)
        }
    }
    return Raster(bounds.width,bounds.height,output)
}

internal fun Raster.flatten(background: Int) = Raster(width,height,IntArray(pixels.size) { composite(pixels[it],background) })
