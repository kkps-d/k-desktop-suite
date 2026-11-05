package com.kkps.kdesktopsuite.explorer

import androidx.compose.ui.geometry.Rect
import com.kkps.kdesktopsuite.ui.DesktopCaption
import com.kkps.kdesktopsuite.ui.desktopCaptionSpan

internal fun explorerCaptionControls(
    caption: DesktopCaption, region: Rect, density: Float, fontScale: Float,
    minHeight: Float, rtl: Boolean,
): Rect? {
    val span = desktopCaptionSpan(caption, region) ?: return null
    // One readable tab, New, menu, and the same compact drag gap used by Notepad.
    val dragGap = 8f * density
    if (span.height < minHeight || span.width < (128f * fontScale + 64f) * density + dragGap) return null
    return if (rtl) span.copy(left = span.left + dragGap) else span.copy(right = span.right - dragGap)
}
