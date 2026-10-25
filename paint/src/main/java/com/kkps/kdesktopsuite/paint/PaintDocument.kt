package com.kkps.kdesktopsuite.paint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import androidx.core.graphics.createBitmap
import java.util.UUID

internal enum class Tool(val label: String) {
    Pencil("Pencil"), Brush("Brush"), Fill("Fill"), Eraser("Eraser"), Text("Text"),
    Eyedropper("Eyedropper"), Select("Select"), Line("Line"), Rectangle("Rectangle"), Ellipse("Ellipse"),
    Zoom("Zoom"), Pan("Pan"),
}
internal data class ToolOptions(
    val tool: Tool = Tool.Brush, val foreground: Int = BLACK, val background: Int = WHITE,
    val width: Int = 3, val eraserWidth: Int = 8, val outline: Boolean = true,
    val fill: Boolean = false, val constrain: Boolean = false,
    val font: String = "Sans-serif", val fontSize: Int = 24, val bold: Boolean = false,
    val italic: Boolean = false, val underline: Boolean = false, val strike: Boolean = false,
    val opaqueText: Boolean = false,
) {
    val strokeWidth get() = if (tool == Tool.Eraser) eraserWidth else width
}

internal data class EditPreview(
    val tool: Tool, val bounds: PixelRect, val start: Point = Point(bounds.x.toFloat(), bounds.y.toFloat()),
    val end: Point = Point(bounds.right.toFloat(), bounds.bottom.toFloat()),
    val source: PixelRect? = null, val payload: Raster? = null,
    val text: String = "", val outputWidth: Int? = null, val outputHeight: Int? = null,
    val id: String = UUID.randomUUID().toString(),
    val textSelectionStart: Int = 0, val textSelectionEnd: Int = 0,
    val textRaster: Raster? = null, val textLayoutKey: TextLayoutKey? = null,
) {
    val hasContent get() = payload != null || tool in setOf(Tool.Line, Tool.Rectangle, Tool.Ellipse) || text.isNotEmpty()
}

internal data class TextLayoutKey(val text: String, val width: Int, val font: String, val size: Int,
    val bold: Boolean, val italic: Boolean, val underline: Boolean, val strike: Boolean)
internal fun textLayoutKey(text: String, width: Int, options: ToolOptions) = TextLayoutKey(text,width,
    options.font,options.fontSize,options.bold,options.italic,options.underline,options.strike)

internal fun Raster.bitmap(): Bitmap = createBitmap(width,height).apply { setPixels(pixels,0,width,0,0,width,height) }
internal fun Bitmap.raster(): Raster = Raster(width, height, IntArray(width * height).also {
    getPixels(it, 0, width, 0, 0, width, height)
})

internal fun Raster.render(preview: EditPreview, options: ToolOptions, mask: PixelRect? = null): Raster {
    if (preview.tool == Tool.Select) {
        val cleared = preview.source?.let { clear(it, options.background) } ?: this
        return preview.payload?.let {
            cleared.paste(it, preview.bounds, preview.outputWidth ?: width, preview.outputHeight ?: height, options.background)
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
    val shape = RectF(rect.x.toFloat(), rect.y.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
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
    } else {
        fun drawShape() {
            when (preview.tool) {
                Tool.Line -> canvas.drawLine(preview.start.x, preview.start.y, preview.end.x, preview.end.y, paint)
                Tool.Rectangle -> canvas.drawRect(shape, paint)
                Tool.Ellipse -> canvas.drawOval(shape, paint)
                else -> Unit
            }
        }
        if (options.fill && preview.tool != Tool.Line) {
            paint.style = Paint.Style.FILL; paint.color = options.background; drawShape()
        }
        if (options.outline || preview.tool == Tool.Line) {
            paint.style = Paint.Style.STROKE; paint.color = options.foreground; drawShape()
        }
    }
    return bitmap.raster().also { bitmap.recycle() }
}

internal val presetColors = listOf(
    0xFF000000, 0xFF777777, 0xFFAA2222, 0xFFEE3333, 0xFFEE8822, 0xFFFFCC33, 0xFF338844, 0xFF22AABB, 0xFF3366CC, 0xFF8844AA,
    0xFFFFFFFF, 0xFFCCCCCC, 0xFFAA7755, 0xFFFFAAAA, 0xFFFFCC99, 0xFFFFFFAA, 0xFFAAEEAA, 0xFFAAEEEE, 0xFFAACCEE, 0xFFDDCCEE,
).map { it.toInt() }
