package com.kkps.kdesktopsuite.paint

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp

internal enum class PaintIcon(@DrawableRes private val resourceId: Int) {
    Save(R.drawable.ms_save),
    Undo(R.drawable.ms_undo),
    Redo(R.drawable.ms_redo),
    Paste(R.drawable.ms_content_paste),
    Copy(R.drawable.ms_content_copy),
    Cut(R.drawable.ms_content_cut),
    Pencil(R.drawable.ms_edit),
    Brush(R.drawable.ms_brush),
    Fill(R.drawable.ms_format_color_fill),
    Eraser(R.drawable.ms_ink_eraser),
    Text(R.drawable.ms_text_fields),
    Eyedropper(R.drawable.ms_colorize),
    Select(R.drawable.ms_select),
    Line(R.drawable.ic_line),
    Rectangle(R.drawable.ms_rectangle),
    Ellipse(R.drawable.ic_ellipse),
    Pan(R.drawable.ms_pan_tool),
    Crop(R.drawable.ms_crop),
    Resize(R.drawable.ms_resize),
    Rotate(R.drawable.ms_rotate_90_degrees_cw),
    More(R.drawable.ms_more_vert),
    Swap(R.drawable.ms_swap_horiz),
    Plus(R.drawable.ms_add),
    Minus(R.drawable.ms_remove),
    Apply(R.drawable.ms_check),
    Cancel(R.drawable.ms_close),
    Chevron(R.drawable.ms_expand_more),
    Folder(R.drawable.ms_folder),
    Settings(R.drawable.ms_settings),
    Delete(R.drawable.ic_delete),
    Geometry(R.drawable.ic_geometry),
    RotateLeft(R.drawable.ic_rotate_left),
    CustomRotate(R.drawable.ic_custom_rotate),
    FlipHorizontal(R.drawable.ic_flip_horizontal),
    FlipVertical(R.drawable.ic_flip_vertical),
    Bold(R.drawable.ic_format_bold),
    Italic(R.drawable.ic_format_italic),
    Underline(R.drawable.ic_format_underline),
    Strike(R.drawable.ic_format_strike);

    val vector: ImageVector
        @Composable get() = ImageVector.vectorResource(resourceId)
}

internal val Tool.icon: ImageVector
    @Composable get() {
        if (isShape) return remember(this) {
            ImageVector.Builder(label,24.dp,24.dp,120f,120f).apply {
                addGroup(translationX = 10f,translationY = 10f)
                addPath(addPathNodes(shapePathData),fill = null,stroke = SolidColor(Color.Black),
                    strokeLineWidth = 7f,strokeLineCap = StrokeCap.Round,strokeLineJoin = StrokeJoin.Round)
                clearGroup()
            }.build()
        }
        return when (this) {
            Tool.Pencil -> PaintIcon.Pencil; Tool.Brush -> PaintIcon.Brush; Tool.Fill -> PaintIcon.Fill
            Tool.Eraser -> PaintIcon.Eraser; Tool.Text -> PaintIcon.Text; Tool.Eyedropper -> PaintIcon.Eyedropper
            Tool.Select -> PaintIcon.Select; Tool.Pan -> PaintIcon.Pan
            else -> error("Missing icon for $label")
        }.vector
    }
