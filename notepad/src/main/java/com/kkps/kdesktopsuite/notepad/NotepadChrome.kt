package com.kkps.kdesktopsuite.notepad

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal object DocumentTabShapeDimensions {
    val topCornerRadius = 12.dp
    val bottomFootRadius = 12.dp
    val overlap = 16.dp
    val hoverBottomGap = 4.dp
}

internal object DocumentTabShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val foot = minOf(with(density) { DocumentTabShapeDimensions.bottomFootRadius.toPx() }, size.width / 4f, size.height / 2f)
        val corner = minOf(with(density) { DocumentTabShapeDimensions.topCornerRadius.toPx() }, (size.width - 2f * foot) / 2f, size.height / 2f)
        val right = size.width - foot
        val bottom = size.height
        return Outline.Generic(Path().apply {
            // The outward-curving feet stay inside the tab's bounds, including at scroll edges.
            moveTo(0f, bottom)
            quadraticTo(foot, bottom, foot, bottom - foot)
            lineTo(foot, corner)
            quadraticTo(foot, 0f, foot + corner, 0f)
            lineTo(right - corner, 0f)
            quadraticTo(right, 0f, right, corner)
            lineTo(right, bottom - foot)
            quadraticTo(right, bottom, size.width, bottom)
            close()
        })
    }
}

internal object DocumentTabHoverShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val foot = minOf(with(density) { DocumentTabShapeDimensions.bottomFootRadius.toPx() }, size.width / 4f, size.height / 2f)
        val bottom = (size.height - with(density) { DocumentTabShapeDimensions.hoverBottomGap.toPx() }).coerceAtLeast(0f)
        val corner = with(density) { DocumentTabShapeDimensions.topCornerRadius.toPx() }
        // Match the selected tab's body, leaving its outward feet and bottom join clear.
        return Outline.Rounded(RoundRect(foot, 0f, size.width - foot, bottom, CornerRadius(corner)))
    }
}
