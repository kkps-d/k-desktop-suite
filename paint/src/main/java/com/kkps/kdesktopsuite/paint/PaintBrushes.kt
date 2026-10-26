package com.kkps.kdesktopsuite.paint

import kotlin.math.*

internal enum class BrushStyle(val label: String, val defaultWidth: Int = 8) {
    Solid("Solid", 3), CalligraphyLeft("Calligraphy left"), CalligraphyRight("Calligraphy right"),
    Airbrush("Airbrush", 16), Oil("Oil"), Crayon("Crayon"), Marker("Marker"),
    Graphite("Graphite"), Watercolor("Watercolor", 16);

    val widthPresets get() = if (this == Solid) listOf(1,3,5,8) else listOf(4,8,16,24)
}

internal data class StrokeBrush(val color: Int, val size: Float, val style: BrushStyle, val square: Boolean = false)

internal fun ToolOptions.strokeBrush(erasing: Boolean = false) = when {
    erasing || tool == Tool.Eraser -> StrokeBrush(background,eraserWidth.toFloat(),BrushStyle.Solid,square = true)
    tool == Tool.Pencil -> StrokeBrush(foreground,1f,BrushStyle.Solid)
    else -> StrokeBrush(foreground,width.toFloat(),brushStyle)
}

/** Seeded, distance-spaced dabs keep textures stable as pointer samples arrive. */
internal fun Raster.brushStroke(
    points: List<Point>, color: Int, size: Float, style: BrushStyle, seed: Int, clip: PixelRect? = null,
    square: Boolean = false,
): Raster {
    if (points.isEmpty()) return this
    val renderer = BrushStrokeRenderer(this,color,size,style,seed,clip,square)
    points.forEach(renderer::append)
    return renderer.snapshot()
}

/** Owned by one worker; appending never redraws an earlier segment. */
internal class BrushStrokeRenderer(
    source: Raster, private val color: Int, size: Float, private val style: BrushStyle,
    private val seed: Int, clip: PixelRect?, private val square: Boolean = false,
) {
    private val width = source.width
    private val height = source.height
    private val output = source.pixels.copyOf()
    private val round = if (style == BrushStyle.Solid && !square) RoundStrokeDabs(width,height,output,color,size,clip) else null
    private var previous: Point? = null
    private val radius = max(0.5f,size / 2)
    private val spacing = max(0.5f,size * 0.16f)
    private val area = clip?.clipped(width,height) ?: PixelRect(0,0,width,height)
    private val squareSize = size.toInt().coerceAtLeast(1)
    private var dabIndex = 0

    private fun grain(x: Int, y: Int, layer: Int = 0): Float {
        var hash = seed xor (x * 374761393) xor (y * 668265263) xor (layer * 1274126177)
        hash = (hash xor (hash ushr 13)) * 1274126177
        return ((hash xor (hash ushr 16)) ushr 8) / 16777216f
    }

    private fun dab(point: Point) {
        if (square) {
            val left = max(area.x,floor(point.x).toInt()-squareSize/2)
            val right = min(area.right,floor(point.x).toInt()-squareSize/2+squareSize)
            val top = max(area.y,floor(point.y).toInt()-squareSize/2)
            val bottom = min(area.bottom,floor(point.y).toInt()-squareSize/2+squareSize)
            if (left >= right || top >= bottom) return
            for (y in top until bottom) {
                val start = y*width+left
                if (color ushr 24 == 255) output.fill(color,start,y*width+right)
                else for (x in left until right) output[y*width+x] = composite(color,output[y*width+x])
            }
            return
        }
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

    private var untilNextDab = spacing

    fun append(end: Point) {
        val start = previous
        previous = end
        if (start == null) {
            if (round != null) round.dab(end) else dab(end)
            return
        }
        val length = hypot(end.x-start.x,end.y-start.y)
        if (round != null) {
            val steps = ceil(length * 2).toInt().coerceIn(1,32768)
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                round.dab(Point(start.x+(end.x-start.x)*t,start.y+(end.y-start.y)*t))
            }
        } else if (length > 0f) {
            var distance = untilNextDab
            while (distance <= length) {
                val t = distance / length
                dab(Point(start.x+(end.x-start.x)*t,start.y+(end.y-start.y)*t))
                distance += spacing
            }
            untilNextDab = distance-length
        }
    }

    fun snapshot() = Raster(width,height,output.copyOf())
}
