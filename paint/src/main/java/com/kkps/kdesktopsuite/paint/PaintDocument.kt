package com.kkps.kdesktopsuite.paint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import androidx.core.graphics.createBitmap
import java.util.UUID
import kotlin.math.*

internal enum class Tool(val label: String, val isShape: Boolean = false) {
    Pencil("Pencil"), Brush("Brush"), Fill("Fill"), Eraser("Eraser"), Text("Text"),
    Eyedropper("Eyedropper"), Select("Select"),
    Line("Line", true), Curve("Curve", true), Ellipse("Ellipse", true), Rectangle("Rectangle", true),
    RoundedRectangle("Rounded rectangle", true), Polygon("Polygon", true), Triangle("Triangle", true),
    RightTriangle("Right triangle", true), Diamond("Diamond", true), Pentagon("Pentagon", true), Hexagon("Hexagon", true),
    RightArrow("Right arrow", true), LeftArrow("Left arrow", true), UpArrow("Up arrow", true), DownArrow("Down arrow", true),
    FourPointStar("Four-point star", true), FivePointStar("Five-point star", true), SixPointStar("Six-point star", true),
    RectangleCallout("Rounded rectangle callout", true), EllipseCallout("Ellipse callout", true), CloudCallout("Cloud callout", true),
    Heart("Heart", true), Lightning("Lightning bolt", true),
    Zoom("Zoom"), Pan("Hand");
    val isClosedShape get() = isShape && this != Line && this != Curve
}

internal val shapeTools = Tool.entries.filter { it.isShape }
internal data class ToolOptions(
    val tool: Tool = Tool.Brush, val foreground: Int = BLACK, val background: Int = WHITE,
    val width: Int = 3, val eraserWidth: Int = 8, val outline: Boolean = true,
    val fill: Boolean = false, val constrain: Boolean = false,
    val font: String = "Sans-serif", val fontSize: Int = 24, val bold: Boolean = false,
    val italic: Boolean = false, val underline: Boolean = false, val strike: Boolean = false,
    val opaqueText: Boolean = false,
    val brushStyle: BrushStyle = BrushStyle.Solid,
    val brushWidths: Map<BrushStyle,Int> = emptyMap(),
) {
    val strokeWidth get() = when (tool) {
        Tool.Pencil -> 1
        Tool.Eraser -> eraserWidth
        else -> width
    }
}

internal data class EditPreview(
    val tool: Tool, val bounds: PixelRect, val start: Point = Point(bounds.x.toFloat(), bounds.y.toFloat()),
    val end: Point = Point(bounds.right.toFloat(), bounds.bottom.toFloat()),
    val source: PixelRect? = null, val payload: Raster? = null,
    val text: String = "", val outputWidth: Int? = null, val outputHeight: Int? = null,
    val id: String = UUID.randomUUID().toString(),
    val textSelectionStart: Int = 0, val textSelectionEnd: Int = 0,
    val textRaster: Raster? = null, val textLayoutKey: TextLayoutKey? = null,
    val points: List<Point> = emptyList(), val constructing: Boolean = false,
    val transform: RasterTransform? = null, val rotationDegrees: Double = 0.0,
    val rotationCenter: Point? = null,
) {
    val hasContent get() = payload != null || tool.isShape || text.isNotEmpty()

    val selectionTransform get() = transform ?: RasterTransform.scale(
        bounds.width.toDouble()/requireNotNull(payload).width,bounds.height.toDouble()/payload.height)
        .then(RasterTransform.translation(bounds.x.toDouble(),bounds.y.toDouble()))
    val selectionCenter get() = rotationCenter ?: Point(bounds.x+bounds.width/2f,bounds.y+bounds.height/2f)
    fun selectionPixels(checkCancelled: () -> Unit = {}) = requireNotNull(payload).transformed(selectionTransform,bounds,checkCancelled)
    fun affectedSelectionArea(width: Int, height: Int): PixelRect {
        val original = source ?: bounds
        val left = min(original.x,bounds.x); val top = min(original.y,bounds.y)
        return PixelRect(left,top,max(original.right,bounds.right)-left,max(original.bottom,bounds.bottom)-top).clipped(width,height)
    }
    fun resizedSelection(area: PixelRect, flipHorizontal: Boolean = false, flipVertical: Boolean = false): EditPreview = transformedSelection(
        RasterTransform.translation(-bounds.x.toDouble(),-bounds.y.toDouble())
            .then(RasterTransform.scale(
                area.width.toDouble()/bounds.width * if (flipHorizontal) -1 else 1,
                area.height.toDouble()/bounds.height * if (flipVertical) -1 else 1))
            .then(RasterTransform.translation(
                (if (flipHorizontal) area.right else area.x).toDouble(),
                (if (flipVertical) area.bottom else area.y).toDouble())))
    fun rotatedSelection(degrees: Double): EditPreview {
        val center = selectionCenter
        var next = RasterTransform.around(center,RasterTransform.rotation(degrees))
        // Keep exact quarter turns pixel-aligned, retaining the pivot for subsequent turns.
        if (abs(degrees % 90) < 1e-8 && abs(rotationDegrees % 90) < 1e-8) {
            val corners = selectionTransform.then(next).corners(requireNotNull(payload).width,payload.height)
            val left = corners.minOf { it.x }.toDouble(); val top = corners.minOf { it.y }.toDouble()
            next = next.then(RasterTransform.translation(round(left)-left,round(top)-top))
        }
        return transformedSelection(next,(rotationDegrees+degrees)%360,center)
    }
    fun transformedSelection(next: RasterTransform, angle: Double = rotationDegrees, center: Point? = null): EditPreview {
        val combined = selectionTransform.then(next)
        val area = combined.bounds(requireNotNull(payload).width,payload.height)
        checkImageSize(area.width,area.height)
        return copy(bounds = area,transform = combined,rotationDegrees = angle,
            rotationCenter = center ?: next.point(selectionCenter.x.toDouble(),selectionCenter.y.toDouble()))
    }
    fun containsSelection(point: Point): Boolean {
        val source = selectionTransform.inverse().point(point.x.toDouble(),point.y.toDouble())
        return source.x >= 0 && source.y >= 0 && source.x < requireNotNull(payload).width && source.y < payload.height
    }

    companion object {
        fun between(tool: Tool, start: Point, end: Point, constrain: Boolean = false): EditPreview {
            var target = end
            if (constrain) {
                if (tool == Tool.Line || tool == Tool.Curve || tool == Tool.Polygon) {
                    val angle = (atan2(end.y - start.y, end.x - start.x) / (PI / 4)).roundToInt() * PI / 4
                    val length = hypot(end.x - start.x, end.y - start.y)
                    target = Point(start.x + (cos(angle) * length).toFloat(), start.y + (sin(angle) * length).toFloat())
                } else {
                    val size = max(abs(end.x - start.x), abs(end.y - start.y))
                    target = Point(start.x + size * if (end.x < start.x) -1 else 1, start.y + size * if (end.y < start.y) -1 else 1)
                }
            }
            return EditPreview(tool, PixelRect.between(start, target), start, target,
                points = if (tool == Tool.Polygon) listOf(start,target) else emptyList(),
                constructing = tool == Tool.Curve || tool == Tool.Polygon)
        }
    }
}

internal data class TextLayoutKey(val text: String, val width: Int, val font: String, val size: Int,
    val bold: Boolean, val italic: Boolean, val underline: Boolean, val strike: Boolean)
internal fun textLayoutKey(text: String, width: Int, options: ToolOptions) = TextLayoutKey(text,width,
    options.font,options.fontSize,options.bold,options.italic,options.underline,options.strike)

internal fun Raster.bitmap(): Bitmap = createBitmap(width,height).apply { setPixels(pixels,0,width,0,0,width,height) }
internal fun Bitmap.raster(): Raster = Raster(width, height, IntArray(width * height).also {
    getPixels(it, 0, width, 0, 0, width, height)
})

internal fun Raster.render(preview: EditPreview, options: ToolOptions, mask: PixelRect? = null,
    checkCancelled: () -> Unit = {}): Raster {
    if (preview.tool == Tool.Select) {
        return preview.payload?.let { payload ->
            renderSelection(payload,preview.selectionTransform,preview.bounds,preview.source,
                preview.outputWidth ?: width,preview.outputHeight ?: height,options.background,checkCancelled)
        } ?: this
    }
    val bitmap = bitmap()
    val canvas = Canvas(bitmap)
    mask?.let { canvas.clipRect(it.x,it.y,it.right,it.bottom) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = options.foreground
        strokeWidth = options.width.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val rect = preview.bounds
    if (preview.tool == Tool.Text) {
        if (preview.textRaster != null && preview.textLayoutKey == textLayoutKey(preview.text,rect.width,options)) {
            if (options.opaqueText) {
                paint.color = options.background
                canvas.drawRect(rect.x.toFloat(),rect.y.toFloat(),rect.right.toFloat(),rect.y+maxOf(rect.height,preview.textRaster.height).toFloat(),paint)
            }
            paint.colorFilter = PorterDuffColorFilter(options.foreground,PorterDuff.Mode.SRC_IN)
            val glyphs = preview.textRaster.bitmap()
            canvas.drawBitmap(glyphs,rect.x.toFloat(),rect.y.toFloat(),paint)
            glyphs.recycle()
            return bitmap.raster().also { bitmap.recycle() }
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.foreground; textSize = options.fontSize.toFloat()
            val family = when (options.font) { "Serif" -> "serif"; "Monospace" -> "monospace"; else -> "sans-serif" }
            val style = when {
                options.bold && options.italic -> Typeface.BOLD_ITALIC
                options.bold -> Typeface.BOLD
                options.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            typeface = Typeface.create(family, style)
            isUnderlineText = options.underline; isStrikeThruText = options.strike
        }
        val layout = StaticLayout.Builder.obtain(preview.text, 0, preview.text.length, textPaint, rect.width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .build()
        if (options.opaqueText) {
            paint.color = options.background
            canvas.drawRect(rect.x.toFloat(), rect.y.toFloat(), rect.right.toFloat(), rect.y + maxOf(rect.height, layout.height).toFloat(), paint)
        }
        canvas.withTranslation(rect.x.toFloat(), rect.y.toFloat()) { layout.draw(this) }
    } else canvas.drawShapePreview(preview, options)
    return bitmap.raster().also { bitmap.recycle() }
}

/** Used by both the live canvas overlay and committed raster output. */
internal fun Canvas.drawShapePreview(preview: EditPreview, options: ToolOptions) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = options.width.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    if (!preview.tool.isShape) return
    val path = preview.shapePath()
    fun drawShape() = drawPath(path,paint)
    if (options.fill && preview.tool.isClosedShape && !preview.constructing) {
        paint.style = Paint.Style.FILL; paint.color = options.background; drawShape()
    }
    if (options.outline || !preview.tool.isClosedShape || preview.constructing) {
        paint.style = Paint.Style.STROKE; paint.color = options.foreground; drawShape()
    }
}

internal val presetColors = listOf(
    0xFF000000, 0xFF777777, 0xFFAA2222, 0xFFEE3333, 0xFFEE8822, 0xFFFFCC33, 0xFF338844, 0xFF22AABB, 0xFF3366CC, 0xFF8844AA,
    0xFFFFFFFF, 0xFFCCCCCC, 0xFFAA7755, 0xFFFFAAAA, 0xFFFFCC99, 0xFFFFFFAA, 0xFFAAEEAA, 0xFFAAEEEE, 0xFFAACCEE, 0xFFDDCCEE,
).map { it.toInt() }
