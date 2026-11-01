package com.kkps.kdesktopsuite.paint

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.util.LruCache
import kotlin.math.PI
import kotlin.math.sin

private val shapeBrushTextures = LruCache<Pair<BrushStyle,Int>,Bitmap>(24)

/** Image-space brush textures keep sharp shape joins and match preview/export at any zoom. */
internal fun shapeBrushShader(style: BrushStyle, color: Int): Shader {
    val key = style to color
    val bitmap = shapeBrushTextures.get(key) ?: run {
        // Render past every edge, then crop one periodic tile so repeated strokes have no seams.
        var texture = Raster.blank(176,176)
        for (row in 0..11) {
            val points = List(177) { x ->
                Point(x.toFloat(),row * 16f + sin((x - 24) * PI / 64).toFloat() * 4f)
            }
            texture = texture.brushStroke(points,BLACK,24f,style,42)
        }
        // The stroke engine composites onto opaque pixels. Turn its monochrome coverage into alpha.
        val mask = texture.extract(PixelRect(24,24,128,128))
        val pixels = IntArray(mask.pixels.size) { index ->
            val alpha = (255 - (mask.pixels[index] and 255)) * (color ushr 24) / 255
            (alpha shl 24) or (color and 0xFFFFFF)
        }
        Raster(128,128,pixels).bitmap()
    }.also { shapeBrushTextures.put(key,it) }
    return BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)
}
