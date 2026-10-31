package com.kkps.kdesktopsuite.notepad

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.DesktopCaption
import com.kkps.kdesktopsuite.ui.desktopCaptionSpan

internal object DesktopCaptionDimensions {
    val dragAreaWidth = 8.dp
    val minContentHeight = 24.dp
    val minTabStripWidth get() = TabBarDimensions.minTabWidth + TabBarDimensions.buttonSize + dragAreaWidth
}

internal enum class CaptionMode { Regular, Tabs, BelowCaption }

internal data class CaptionLayout(val mode: CaptionMode, val span: Rect = Rect.Zero) {
    val integrated: Boolean get() = mode == CaptionMode.Tabs

    fun controls(density: Float, rtl: Boolean): Rect = when {
        rtl -> span.copy(left = span.left + DesktopCaptionDimensions.dragAreaWidth.value * density)
        else -> span.copy(right = span.right - DesktopCaptionDimensions.dragAreaWidth.value * density)
    }
}

internal fun captionLayout(
    caption: DesktopCaption, region: Rect, density: Float,
    minRowHeight: Float = DesktopCaptionDimensions.minContentHeight.value * density,
): CaptionLayout {
    if (caption.height <= 0f) return CaptionLayout(CaptionMode.Regular)
    val span = desktopCaptionSpan(caption, region) ?: return CaptionLayout(CaptionMode.BelowCaption)
    if (span.height < minRowHeight) return CaptionLayout(CaptionMode.BelowCaption)
    val mode = if (span.width >= DesktopCaptionDimensions.minTabStripWidth.value * density) CaptionMode.Tabs else CaptionMode.BelowCaption
    return CaptionLayout(mode, span)
}
