package com.kkps.kdesktopsuite.paint

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun Tool.displayLabel(): String = stringResource(when (this) {
    Tool.Pencil -> R.string.ui_tool_pencil
    Tool.Brush -> R.string.ui_tool_brush
    Tool.Fill -> R.string.ui_tool_fill
    Tool.Eraser -> R.string.ui_tool_eraser
    Tool.Text -> R.string.ui_tool_text
    Tool.Eyedropper -> R.string.ui_tool_eyedropper
    Tool.Select -> R.string.ui_tool_select
    Tool.Line -> R.string.ui_tool_line
    Tool.Curve -> R.string.ui_tool_curve
    Tool.Ellipse -> R.string.ui_tool_ellipse
    Tool.Rectangle -> R.string.ui_tool_rectangle
    Tool.RoundedRectangle -> R.string.ui_tool_roundedrectangle
    Tool.Polygon -> R.string.ui_tool_polygon
    Tool.Triangle -> R.string.ui_tool_triangle
    Tool.RightTriangle -> R.string.ui_tool_righttriangle
    Tool.Diamond -> R.string.ui_tool_diamond
    Tool.Pentagon -> R.string.ui_tool_pentagon
    Tool.Hexagon -> R.string.ui_tool_hexagon
    Tool.RightArrow -> R.string.ui_tool_rightarrow
    Tool.LeftArrow -> R.string.ui_tool_leftarrow
    Tool.UpArrow -> R.string.ui_tool_uparrow
    Tool.DownArrow -> R.string.ui_tool_downarrow
    Tool.FourPointStar -> R.string.ui_tool_fourpointstar
    Tool.FivePointStar -> R.string.ui_tool_fivepointstar
    Tool.SixPointStar -> R.string.ui_tool_sixpointstar
    Tool.RectangleCallout -> R.string.ui_tool_rectanglecallout
    Tool.EllipseCallout -> R.string.ui_tool_ellipsecallout
    Tool.CloudCallout -> R.string.ui_tool_cloudcallout
    Tool.Heart -> R.string.ui_tool_heart
    Tool.Lightning -> R.string.ui_tool_lightning
    Tool.Pan -> R.string.ui_pan
})

@Composable
internal fun BrushStyle.displayLabel(): String = stringResource(when (this) {
    BrushStyle.Solid -> R.string.ui_brushstyle_solid
    BrushStyle.CalligraphyLeft -> R.string.ui_brushstyle_calligraphyleft
    BrushStyle.CalligraphyRight -> R.string.ui_brushstyle_calligraphyright
    BrushStyle.Airbrush -> R.string.ui_brushstyle_airbrush
    BrushStyle.Oil -> R.string.ui_brushstyle_oil
    BrushStyle.Crayon -> R.string.ui_brushstyle_crayon
    BrushStyle.Marker -> R.string.ui_brushstyle_marker
    BrushStyle.Graphite -> R.string.ui_brushstyle_graphite
    BrushStyle.Watercolor -> R.string.ui_brushstyle_watercolor
})

@Composable
internal fun CanvasResizeHandle.displayLabel(): String = stringResource(when (this) {
    CanvasResizeHandle.TopLeft -> R.string.ui_canvasresizehandle_topleft
    CanvasResizeHandle.Top -> R.string.ui_canvasresizehandle_top
    CanvasResizeHandle.TopRight -> R.string.ui_canvasresizehandle_topright
    CanvasResizeHandle.Right -> R.string.ui_canvasresizehandle_right
    CanvasResizeHandle.BottomRight -> R.string.ui_canvasresizehandle_bottomright
    CanvasResizeHandle.Bottom -> R.string.ui_canvasresizehandle_bottom
    CanvasResizeHandle.BottomLeft -> R.string.ui_canvasresizehandle_bottomleft
    CanvasResizeHandle.Left -> R.string.ui_canvasresizehandle_left
})

@Composable
internal fun fontDisplayLabel(name: String): String = stringResource(when (name) {
    "Sans-serif" -> R.string.ui_font_sans_serif
    "Serif" -> R.string.ui_font_serif
    "Monospace" -> R.string.ui_font_monospace
    "System" -> R.string.ui_font_system
    else -> R.string.ui_font_system
})

@Composable
internal fun paintDisplayTitle(state: PaintUiState): String =
    if (state.uri == null && state.title == "Untitled") stringResource(R.string.ui_untitled) else state.title
