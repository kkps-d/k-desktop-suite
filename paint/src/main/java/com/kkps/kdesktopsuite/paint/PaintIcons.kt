package com.kkps.kdesktopsuite.paint

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal enum class PaintIcon(private val path: String) {
    Save("M4,3H18L21,6V21H3V3H4 M7,3V9H17V3 M7,21V13H17V21"),
    Undo("M9,5L4,10L9,15 M4,10H14C22,10 22,20 15,20"),
    Redo("M15,5L20,10L15,15 M20,10H10C2,10 2,20 9,20"),
    Paste("M9,5H5V21H20V5H16 M9,3H16V7H9Z"),
    Copy("M9,8H21V21H9Z M15,8V3H3V16H9"),
    Cut("M10,8L20,21 M10,16L20,3 M5,3A4,4 0,1 0,5 11A4,4 0,1 0,5 3 M5,13A4,4 0,1 0,5 21A4,4 0,1 0,5 13"),
    Pencil("M4,16L15,5L20,10L9,21H4Z M14,6L18,2L22,6L18,10"),
    Brush("M9,14L18,3Q23,0 22,6L14,17 M9,14Q3,13 5,19Q6,21 2,22Q12,24 14,17Z"),
    Fill("M5,7L13,1L22,10L14,18Z M3,10L11,18 M3,21H21 M19,14Q24,21 19,21Q15,21 19,14"),
    Eraser("M3,14L13,3L22,11L12,21H8Z M8,9L17,17 M12,21H22"),
    Text("M3,4H21 M12,4V21 M8,21H16"),
    Eyedropper("M16,2L22,8 M14,4L20,10 M16,6L5,17L3,22L8,20L19,9"),
    Select("M3,8V3H8 M16,3H21V8 M21,16V21H16 M8,21H3V16 M3,11V13 M11,3H13 M21,11V13 M11,21H13"),
    Line("M3,21L21,3"),
    Rectangle("M3,4H21V20H3Z"),
    Ellipse("M21,12A9,8 0,1 1,3 12A9,8 0,1 1,21 12"),
    Zoom("M16,10A6,6 0,1 1,4 10A6,6 0,1 1,16 10 M15,15L22,22 M7,10H13 M10,7V13"),
    Pan("M7,12V6Q7,3 9,4V12 M9,12V3Q11,0 13,3V12 M13,12V5Q15,2 17,5V13 M17,13V9Q19,6 21,9V17Q21,22 16,23H11L3,15Q2,12 5,12L7,15"),
    Crop("M7,2V17H22 M2,7H17V22"),
    More("M12,3V6 M12,10V13 M12,17V20"),
    Swap("M3,7H21L17,3 M21,17H3L7,21"),
    Plus("M4,12H20 M12,4V20"),
    Minus("M4,12H20"),
    Apply("M3,12L9,19L22,4"),
    Cancel("M5,5L19,19 M19,5L5,19"),
    Chevron("M5,8L12,15L19,8"),
    Folder("M3,5H10L13,8H21V21H3Z"),
    Settings("M12,3V6 M12,18V21 M3,12H6 M18,12H21 M5,5L7,7 M17,17L19,19 M5,19L7,17 M17,7L19,5 M16,12A4,4 0,1 1,8 12A4,4 0,1 1,16 12"),
    ;
    val vector: ImageVector by lazy {
        ImageVector.Builder(name,24.dp,24.dp,24f,24f).addPath(PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
}

internal val Tool.icon: PaintIcon get() = when (this) {
    Tool.Pencil -> PaintIcon.Pencil; Tool.Brush -> PaintIcon.Brush; Tool.Fill -> PaintIcon.Fill
    Tool.Eraser -> PaintIcon.Eraser; Tool.Text -> PaintIcon.Text; Tool.Eyedropper -> PaintIcon.Eyedropper
    Tool.Select -> PaintIcon.Select; Tool.Line -> PaintIcon.Line; Tool.Rectangle -> PaintIcon.Rectangle
    Tool.Ellipse -> PaintIcon.Ellipse; Tool.Zoom -> PaintIcon.Zoom; Tool.Pan -> PaintIcon.Pan
}
