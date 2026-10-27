package com.kkps.kdesktopsuite.paint

import android.graphics.Matrix
import android.graphics.Path
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Normalized geometry is shared by the gallery icons, live preview and raster output. */
internal val Tool.shapePathData: String get() = when (this) {
    Tool.Line -> "M0,100 L100,0"
    Tool.Curve -> "M0,85 C25,-60 75,160 100,15"
    Tool.Ellipse -> "M0,50 A50,50 0 1,0 100,50 A50,50 0 1,0 0,50 Z"
    Tool.Rectangle -> "M0,0 H100 V100 H0 Z"
    Tool.RoundedRectangle -> "M20,0 H80 Q100,0 100,20 V80 Q100,100 80,100 H20 Q0,100 0,80 V20 Q0,0 20,0 Z"
    Tool.Polygon -> "M0,25 L55,0 L100,40 L60,50 L85,100 L15,85 Z"
    Tool.Triangle -> "M50,0 L100,100 H0 Z"
    Tool.RightTriangle -> "M0,0 L100,100 H0 Z"
    Tool.Diamond -> "M50,0 L100,50 L50,100 L0,50 Z"
    Tool.Pentagon -> regularShape(5)
    Tool.Hexagon -> "M25,0 H75 L100,50 L75,100 H25 L0,50 Z"
    Tool.RightArrow -> "M0,30 H60 V0 L100,50 L60,100 V70 H0 Z"
    Tool.LeftArrow -> "M100,30 H40 V0 L0,50 L40,100 V70 H100 Z"
    Tool.UpArrow -> "M30,100 V40 H0 L50,0 L100,40 H70 V100 Z"
    Tool.DownArrow -> "M30,0 V60 H0 L50,100 L100,60 H70 V0 Z"
    Tool.FourPointStar -> regularShape(4,0.25f)
    Tool.FivePointStar -> regularShape(5,0.4f)
    Tool.SixPointStar -> regularShape(6,0.5f)
    Tool.RectangleCallout -> "M15,0 H85 Q100,0 100,15 V60 Q100,75 85,75 H50 L20,100 L28,75 H15 Q0,75 0,60 V15 Q0,0 15,0 Z"
    Tool.EllipseCallout -> "M28,73 C-20,58 -5,0 50,0 C115,0 115,70 55,77 L20,100 Z"
    Tool.CloudCallout -> "M18,68 C-5,72 -7,48 8,42 C-8,23 10,10 25,15 C26,-5 51,-3 57,9 C76,-7 93,5 88,20 C108,19 107,41 96,46 C110,65 90,79 77,70 C68,87 42,83 40,72 C30,82 19,79 18,68 Z M28,85 A7,5 0 1,0 42,85 A7,5 0 1,0 28,85 Z M18,96 A4,3 0 1,0 26,96 A4,3 0 1,0 18,96 Z"
    Tool.Heart -> "M50,100 C35,80 0,55 0,28 C0,-5 38,-10 50,20 C62,-10 100,-5 100,28 C100,55 65,80 50,100 Z"
    Tool.Lightning -> "M20,0 H65 L48,37 H85 L62,65 H100 L35,100 L48,72 H15 L35,42 H0 Z"
    else -> error("${label} is not a shape")
}

private fun regularShape(vertices: Int, innerRadius: Float? = null): String {
    val count = if (innerRadius == null) vertices else vertices * 2
    val points = List(count) { index ->
        val angle = -PI / 2 + index * 2 * PI / count
        val radius = if (innerRadius != null && index % 2 == 1) innerRadius else 1f
        Point((cos(angle) * radius).toFloat(),(sin(angle) * radius).toFloat())
    }
    val left = points.minOf { it.x }; val top = points.minOf { it.y }
    val width = points.maxOf { it.x } - left; val height = points.maxOf { it.y } - top
    return points.mapIndexed { index, p ->
        "${if (index == 0) "M" else "L"}${(p.x-left)*100/width},${(p.y-top)*100/height}"
    }.joinToString(" ") + " Z"
}

internal fun EditPreview.shapePath(): Path {
    if (transform == null) return untransformedShapePath()
    val geometry = copy(bounds = geometryBounds,transform = null,shapeBounds = null)
    val path = geometry.untransformedShapePath()
    val mapping = RasterTransform.translation(-geometryBounds.x.toDouble(),-geometryBounds.y.toDouble()).then(selectionTransform)
    path.transform(Matrix().apply {
        setValues(floatArrayOf(mapping.a.toFloat(),mapping.c.toFloat(),mapping.tx.toFloat(),
            mapping.b.toFloat(),mapping.d.toFloat(),mapping.ty.toFloat(),0f,0f,1f))
    })
    return path
}

private fun EditPreview.untransformedShapePath(): Path {
    if (tool == Tool.Line || tool == Tool.Curve) return Path().apply {
        moveTo(start.x,start.y)
        when {
            tool == Tool.Line || points.isEmpty() -> lineTo(end.x,end.y)
            points.size == 1 -> quadTo(points[0].x,points[0].y,end.x,end.y)
            else -> cubicTo(points[0].x,points[0].y,points[1].x,points[1].y,end.x,end.y)
        }
    }
    if (tool == Tool.Polygon && points.isNotEmpty()) return Path().apply {
        moveTo(points.first().x,points.first().y)
        points.drop(1).forEach { lineTo(it.x,it.y) }
        if (!constructing) close()
    }
    return requireNotNull(PathParser.createPathFromPathData(tool.shapePathData)).apply {
        transform(Matrix().apply {
            setScale(bounds.width / 100f,bounds.height / 100f)
            postTranslate(bounds.x.toFloat(),bounds.y.toFloat())
        })
    }
}

/** Include the bend controls so the whole curve remains reachable for moving and resizing. */
internal fun EditPreview.withShapePoint(point: Point): EditPreview {
    val updated = points + point
    val all = if (tool == Tool.Curve) listOf(start,end) + updated else updated
    val bounds = PixelRect.between(Point(all.minOf { it.x },all.minOf { it.y }),
        Point(all.maxOf { it.x },all.maxOf { it.y }))
    return copy(points = updated,bounds = bounds,constructing = tool == Tool.Polygon || updated.size < 2)
}
