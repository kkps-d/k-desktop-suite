package com.kkps.kdesktopsuite.paint

import kotlin.math.*

internal enum class BrushStyle(val label: String, val defaultWidth: Int = 8) {
    Solid("Solid", 3), CalligraphyLeft("Calligraphy left"), CalligraphyRight("Calligraphy right"),
    Airbrush("Airbrush", 16), Oil("Oil"), Crayon("Crayon"), Marker("Marker"),
    Graphite("Graphite"), Watercolor("Watercolor", 16);

    val widthPresets get() = if (this == Solid) listOf(1,3,5,8) else listOf(4,8,16,24)
}

/** Seeded, distance-spaced dabs keep textures stable as pointer samples arrive. */
internal fun Raster.brushStroke(
    points: List<Point>, color: Int, size: Float, style: BrushStyle, seed: Int, clip: PixelRect? = null,
): Raster {
    if (style == BrushStyle.Solid) return stroke(points,color,size,false,clip)
    if (points.isEmpty()) return this
    val output = pixels.copyOf()
    val radius = max(0.5f,size / 2)
    val spacing = max(0.5f,size * 0.16f)
    val area = clip?.clipped(width,height) ?: PixelRect(0,0,width,height)
    var dabIndex = 0

    fun grain(x: Int, y: Int, layer: Int = 0): Float {
        var hash = seed xor (x * 374761393) xor (y * 668265263) xor (layer * 1274126177)
        hash = (hash xor (hash ushr 13)) * 1274126177
        return ((hash xor (hash ushr 16)) ushr 8) / 16777216f
    }

    fun dab(point: Point) {
        val cx = point.x + 0.5f
        val cy = point.y + 0.5f
        val left = max(area.x,floor(cx-radius-1).toInt())
        val right = min(area.right-1,ceil(cx+radius+1).toInt())
        val top = max(area.y,floor(cy-radius-1).toInt())
        val bottom = min(area.bottom-1,ceil(cy+radius+1).toInt())
        for (y in top..bottom) for (x in left..right) {
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            val distance = hypot(dx,dy)
            val edge = (radius + 0.5f - distance).coerceIn(0f,1f)
            if (edge == 0f) continue
            val noise = grain(x,y)
            val coverage = when (style) {
                BrushStyle.CalligraphyLeft, BrushStyle.CalligraphyRight -> {
                    val direction = if (style == BrushStyle.CalligraphyLeft) -1 else 1
                    val along = (dx + direction * dy) * 0.7071f
                    val across = (dy - direction * dx) * 0.7071f
                    val thinRadius = max(0.5f,radius * 0.22f)
                    val nibDistance = hypot(along / radius,across / thinRadius)
                    ((1f-nibDistance) * thinRadius + 0.5f).coerceIn(0f,1f)
                }
                BrushStyle.Airbrush -> if (grain(x,y,dabIndex+1) < 0.18f * edge)
                    (1f-distance/(radius+0.5f)).coerceIn(0f,1f) * 0.65f else 0f
                BrushStyle.Oil -> {
                    val bristle = grain(0,y)
                    edge * (if (bristle < 0.24f) 0.04f else if (noise < 0.08f) 0f else 0.85f)
                }
                BrushStyle.Crayon -> edge * if (noise < 0.38f) 0f else 0.25f + noise * 0.5f
                BrushStyle.Marker -> {
                    val nib = max(abs(dx),abs(dy) * 1.6f)
                    (radius + 0.5f - nib).coerceIn(0f,1f) * 0.16f
                }
                BrushStyle.Graphite -> edge * if (noise < 0.2f) 0f else noise * 0.23f
                BrushStyle.Watercolor -> {
                    val wash = (1f-distance/(radius+0.5f)).coerceIn(0f,1f)
                    edge * (0.025f + wash * 0.055f) * (0.6f + noise * 0.4f)
                }
                BrushStyle.Solid -> error("Solid uses the round stroke renderer")
            }
            if (coverage > 0f) {
                val index = y * width + x
                output[index] = composite(color,output[index],coverage)
            }
        }
        dabIndex++
    }

    dab(points.first())
    var untilNextDab = spacing
    for (index in 1 until points.size) {
        val start = points[index-1]
        val end = points[index]
        val length = hypot(end.x-start.x,end.y-start.y)
        if (length == 0f) continue
        var distance = untilNextDab
        while (distance <= length) {
            val t = distance / length
            dab(Point(start.x+(end.x-start.x)*t,start.y+(end.y-start.y)*t))
            distance += spacing
        }
        untilNextDab = distance-length
    }
    return Raster(width,height,output)
}
