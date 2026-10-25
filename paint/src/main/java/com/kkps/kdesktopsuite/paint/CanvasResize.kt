package com.kkps.kdesktopsuite.paint

import kotlin.math.min
import kotlin.math.roundToInt

internal enum class CanvasResizeHandle(val label: String, val horizontal: Int, val vertical: Int) {
    TopLeft("Top left",-1,-1), Top("Top",0,-1), TopRight("Top right",1,-1),
    Right("Right",1,0), BottomRight("Bottom right",1,1), Bottom("Bottom",0,1),
    BottomLeft("Bottom left",-1,1), Left("Left",-1,0);

    fun bounds(width: Int, height: Int, delta: Point): PixelRect {
        val maxHeight = if (horizontal == 0) min(8192,MAX_PIXELS / width) else 8192
        val newHeight = (height + vertical * delta.y.roundToInt()).coerceIn(1,maxHeight)
        val newWidth = (width + horizontal * delta.x.roundToInt()).coerceIn(1,min(8192,MAX_PIXELS / newHeight))
        return PixelRect(if (horizontal < 0) width - newWidth else 0,
            if (vertical < 0) height - newHeight else 0,newWidth,newHeight)
    }
}
