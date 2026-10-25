package com.kkps.kdesktopsuite.paint

import kotlin.math.*

internal data class Viewport(
    val width: Float = 0f, val height: Float = 0f,
    val imageWidth: Int = 800, val imageHeight: Int = 600,
    val scale: Float = 1f, val center: Point = Point(400f, 300f), val fit: Boolean = true,
    val margin: Float = 8f,
) {
    val origin get() = Point(width / 2 - center.x * scale, height / 2 - center.y * scale)
    fun imagePoint(point: Point) = Point((point.x - origin.x) / scale, (point.y - origin.y) / scale)
    fun screenPoint(point: Point) = Point(origin.x + point.x * scale, origin.y + point.y * scale)
    fun resized(width: Float, height: Float, imageWidth: Int = this.imageWidth, imageHeight: Int = this.imageHeight, margin: Float = this.margin): Viewport {
        val updated = copy(width = width, height = height, imageWidth = imageWidth, imageHeight = imageHeight, margin = margin)
        return if (fit) updated.fitted() else updated.clamped()
    }
    fun fitted(): Viewport {
        val zoom = min((width - margin * 2).coerceAtLeast(1f) / imageWidth,
            (height - margin * 2).coerceAtLeast(1f) / imageHeight).coerceIn(0.1f, 5f)
        return copy(scale = zoom, center = Point(imageWidth / 2f, imageHeight / 2f), fit = true).clamped()
    }
    fun actual() = copy(scale = 1f, fit = false).clamped()
    fun zoomed(newScale: Float, anchor: Point = Point(width / 2, height / 2)): Viewport {
        val imageAnchor = imagePoint(anchor)
        val zoom = newScale.coerceIn(0.1f, 5f)
        return copy(scale = zoom, fit = false, center = Point(
            imageAnchor.x - (anchor.x - width / 2) / zoom,
            imageAnchor.y - (anchor.y - height / 2) / zoom,
        )).clamped()
    }
    fun panned(delta: Point) = copy(center = Point(center.x - delta.x / scale, center.y - delta.y / scale), fit = false).clamped()
    fun clamped(): Viewport {
        fun axis(value: Float, viewport: Float, image: Int): Float {
            val half = (viewport / 2 - margin).coerceAtLeast(0f) / scale
            return if (image <= half * 2) image / 2f else value.coerceIn(half, image - half)
        }
        return copy(center = Point(axis(center.x, width, imageWidth), axis(center.y, height, imageHeight)))
    }
}
