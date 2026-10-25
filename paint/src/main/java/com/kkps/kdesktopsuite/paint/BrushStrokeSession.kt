package com.kkps.kdesktopsuite.paint

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes preview and commit work without restarting a stroke for each pointer sample. */
internal class BrushStrokeSession(
    val source: Raster, private val options: ToolOptions,
    private val seed: Int, private val selection: PixelRect?,
) {
    private val mutex = Mutex()
    private var renderer: BrushStrokeRenderer? = null
    private var pointCount = 0
    private var rendered = source

    fun matches(source: Raster, options: ToolOptions, seed: Int, selection: PixelRect?) =
        this.source === source && this.options == options && this.seed == seed && this.selection == selection

    // Call on a worker. Each request contains the same stroke's growing, immutable point list.
    suspend fun render(points: List<Point>): Raster = mutex.withLock {
        if (points.size <= pointCount) return@withLock rendered
        val brush = renderer ?: BrushStrokeRenderer(source,options.foreground,options.width.toFloat(),
            options.brushStyle,seed,selection).also { renderer = it }
        while (pointCount < points.size) {
            currentCoroutineContext().ensureActive()
            brush.append(points[pointCount])
            pointCount++
        }
        // Published pixels must stay immutable while the next segment is being rendered.
        brush.snapshot().also { rendered = it }
    }
}
