package com.kkps.kdesktopsuite.paint

import kotlin.math.*

internal const val WHITE: Int = -1
internal const val BLACK: Int = -16777216
internal const val MAX_PIXELS = 16_777_216

internal fun checkImageSize(width: Int, height: Int) {
    require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= MAX_PIXELS) {
        "Images must be 1–8192 pixels per side and no more than 16 megapixels."
    }
    require(width.toLong() * height * 16 < Runtime.getRuntime().maxMemory() * 2 / 3) {
        "This image needs more working memory than is available."
    }
}

internal data class Point(val x: Float, val y: Float)
internal data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right get() = x + width
    val bottom get() = y + height
    fun contains(x: Int, y: Int) = x >= this.x && x < right && y >= this.y && y < bottom
    fun clipped(width: Int, height: Int): PixelRect {
        val left = x.coerceIn(0, width)
        val top = y.coerceIn(0, height)
        return PixelRect(left, top, (right.coerceIn(left, width) - left), (bottom.coerceIn(top, height) - top))
    }
    companion object {
        fun between(a: Point, b: Point) = PixelRect(
            floor(min(a.x, b.x)).toInt(), floor(min(a.y, b.y)).toInt(),
            max(1, ceil(abs(b.x - a.x)).toInt()), max(1, ceil(abs(b.y - a.y)).toInt()),
        )
    }
}

/** Published rasters are immutable. Editing always produces a separately owned pixel array. */
internal class Raster(val width: Int, val height: Int, internal val pixels: IntArray) {
    init { require(width > 0 && height > 0 && pixels.size == width * height) }
    fun pixel(x: Int, y: Int) = pixels[y * width + x]
    fun resizeCanvas(width: Int, height: Int, background: Int): Raster {
        checkImageSize(width,height)
        if (width == this.width && height == this.height) return this
        val output = IntArray(width * height) { background or BLACK }
        for (y in 0 until min(this.height,height)) {
            pixels.copyInto(output,y * width,y * this.width,y * this.width + min(this.width,width))
        }
        return Raster(width,height,output)
    }
    fun extract(rect: PixelRect): Raster {
        val area = rect.clipped(width, height)
        require(area.width > 0 && area.height > 0) { "Select some image pixels first." }
        return Raster(area.width, area.height, IntArray(area.width * area.height) { index ->
            pixel(area.x + index % area.width, area.y + index / area.width)
        })
    }
    fun clear(rect: PixelRect, color: Int): Raster {
        val result = pixels.copyOf()
        val area = rect.clipped(width, height)
        for (y in area.y until area.bottom) result.fill(color or BLACK, y * width + area.x, y * width + area.right)
        return Raster(width, height, result)
    }
    fun stroke(points: List<Point>, color: Int, size: Float, hard: Boolean, clip: PixelRect?): Raster {
        if (points.isEmpty()) return this
        val output = pixels.copyOf()
        val radius = max(0.5f, size / 2)
        fun dab(point: Point) {
            val cx = floor(point.x) + 0.5f
            val cy = floor(point.y) + 0.5f
            for (y in floor(cy - radius).toInt()..ceil(cy + radius).toInt()) {
                for (x in floor(cx - radius).toInt()..ceil(cx + radius).toInt()) {
                    if (x !in 0 until width || y !in 0 until height || clip?.contains(x, y) == false) continue
                    val distance = hypot(x + 0.5f - cx, y + 0.5f - cy)
                    val coverage = if (hard) {
                        if (distance <= radius) 1f else 0f
                    } else (radius + 0.5f - distance).coerceIn(0f, 1f)
                    if (coverage > 0) output[y * width + x] = composite(color, output[y * width + x], coverage)
                }
            }
        }
        dab(points.first())
        points.zipWithNext { start, end ->
            val steps = ceil(hypot(end.x - start.x, end.y - start.y) * 2).toInt().coerceIn(1, 32768)
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                dab(Point(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t))
            }
        }
        return Raster(width, height, output)
    }
    fun fill(x: Int, y: Int, color: Int, clip: PixelRect?): Raster {
        if (x !in 0 until width || y !in 0 until height || clip?.contains(x, y) == false) return this
        val target = pixel(x, y)
        val replacement = color or BLACK
        if (target == replacement) return this
        val output = pixels.copyOf()
        // Scanline fill bounds auxiliary memory by rows rather than allocating a full-image queue.
        val seeds = ArrayDeque<Int>()
        seeds.add(y * width + x)
        fun matches(px: Int, py: Int) = px in 0 until width && py in 0 until height &&
            clip?.contains(px, py) != false && output[py * width + px] == target
        while (seeds.isNotEmpty()) {
            val seed = seeds.removeLast()
            val sy = seed / width
            var left = seed % width
            if (!matches(left, sy)) continue
            while (matches(left - 1, sy)) left--
            var above = false
            var below = false
            var px = left
            while (matches(px, sy)) {
                output[sy * width + px] = replacement
                val up = matches(px, sy - 1)
                val down = matches(px, sy + 1)
                if (up && !above) seeds.add((sy - 1) * width + px)
                if (down && !below) seeds.add((sy + 1) * width + px)
                above = up
                below = down
                px++
            }
        }
        return Raster(width, height, output)
    }
    fun paste(source: Raster, target: PixelRect, newWidth: Int = width, newHeight: Int = height, background: Int = WHITE): Raster {
        checkImageSize(newWidth, newHeight)
        require(target.width > 0 && target.height > 0)
        val output = IntArray(newWidth * newHeight) { background }
        for (y in 0 until min(height, newHeight)) pixels.copyInto(output, y * newWidth, y * width, y * width + min(width, newWidth))
        val visible = target.clipped(newWidth, newHeight)
        for (y in visible.y until visible.bottom) for (x in visible.x until visible.right) {
            val sx = ((x - target.x + 0.5f) * source.width / target.width - 0.5f).coerceIn(0f, source.width - 1f)
            val sy = ((y - target.y + 0.5f) * source.height / target.height - 0.5f).coerceIn(0f, source.height - 1f)
            val ix = sx.toInt(); val iy = sy.toInt()
            val top = interpolate(source.pixel(ix, iy), source.pixel(min(ix + 1, source.width - 1), iy), sx - ix)
            val bottom = interpolate(source.pixel(ix, min(iy + 1, source.height - 1)), source.pixel(min(ix + 1, source.width - 1), min(iy + 1, source.height - 1)), sx - ix)
            output[y * newWidth + x] = composite(interpolate(top, bottom, sy - iy), output[y * newWidth + x])
        }
        return Raster(newWidth, newHeight, output)
    }
    companion object {
        fun blank(width: Int = 800, height: Int = 600, color: Int = WHITE): Raster {
            checkImageSize(width, height)
            return Raster(width, height, IntArray(width * height) { color or BLACK })
        }
    }
}

private fun interpolate(a: Int, b: Int, t: Float): Int {
    var result = 0
    for (shift in intArrayOf(0, 8, 16, 24)) {
        val channel = ((a ushr shift and 255) * (1 - t) + (b ushr shift and 255) * t).roundToInt()
        result = result or (channel shl shift)
    }
    return result
}

internal fun composite(source: Int, destination: Int, coverage: Float = 1f): Int {
    val alpha = (source ushr 24) / 255f * coverage
    var result = BLACK
    for (shift in intArrayOf(0, 8, 16)) {
        result = result or (((source ushr shift and 255) * alpha + (destination ushr shift and 255) * (1 - alpha)).roundToInt() shl shift)
    }
    return result
}

internal class RasterHistory(initial: Raster, private val budget: Long = 64L * 1024 * 1024) {
    var raster = initial; private set
    var revision = 0L; private set
    var savedRevision: Long? = 0L
    private var nextRevision = 1L
    private data class Change(val area: PixelRect?, val before: Raster, val after: Raster, val oldRevision: Long, val newRevision: Long) {
        val bytes get() = (before.pixels.size.toLong() + after.pixels.size) * 4
    }
    private val undo = ArrayDeque<Change>()
    private val redo = ArrayDeque<Change>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val dirty get() = savedRevision != revision
    internal data class PreparedChange(val source: Raster, val result: Raster, val area: PixelRect?, val before: Raster, val after: Raster)

    /** Pixel comparison and patch allocation can run on a worker before the single-writer commit. */
    fun prepare(candidate: Raster): PreparedChange? {
        val source = raster
        if (candidate === source) return null
        val sameSize = source.width == candidate.width && source.height == candidate.height
        var area: PixelRect? = null
        if (sameSize) {
            var left = source.width; var top = source.height; var right = -1; var bottom = -1
            for (i in source.pixels.indices) if (source.pixels[i] != candidate.pixels[i]) {
                val x = i % source.width; val y = i / source.width
                left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
            }
            if (right < 0) return null
            area = PixelRect(left, top, right - left + 1, bottom - top + 1)
        }
        return PreparedChange(source,candidate,area,area?.let(source::extract) ?: source,area?.let(candidate::extract) ?: candidate)
    }
    fun commit(candidate: Raster): Boolean = prepare(candidate)?.let { commit(it) } ?: false

    fun commit(prepared: PreparedChange): Boolean {
        check(prepared.source === raster) { "This edit was prepared for an earlier raster." }
        val change = Change(prepared.area,prepared.before,prepared.after,revision,nextRevision++)
        undo.add(change); redo.clear(); raster = prepared.result; revision = change.newRevision
        while (undo.sumOf { it.bytes } > budget && undo.isNotEmpty()) undo.removeFirst()
        return true
    }
    private fun restore(change: Change, backwards: Boolean) {
        val pixels = if (backwards) change.before else change.after
        raster = if (change.area == null) pixels else {
            val output = raster.pixels.copyOf()
            for (y in 0 until pixels.height) pixels.pixels.copyInto(output,
                (change.area.y + y) * raster.width + change.area.x, y * pixels.width, (y + 1) * pixels.width)
            Raster(raster.width, raster.height, output)
        }
        revision = if (backwards) change.oldRevision else change.newRevision
    }
    fun undo() { if (undo.isNotEmpty()) undo.removeLast().also { restore(it, true); redo.add(it) } }
    fun redo() { if (redo.isNotEmpty()) redo.removeLast().also { restore(it, false); undo.add(it) } }
}
