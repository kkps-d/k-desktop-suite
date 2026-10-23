package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal object DocumentTabShapeDimensions {
    val topCornerRadius = 12.dp
    val bottomFootRadius = 12.dp
}

internal object DropdownMenuDimensions {
    // These window margins mirror Material's popup position provider.
    val horizontalWindowMargin = 8.dp
    val verticalWindowMargin = 48.dp
    val verticalContentPadding = 8.dp
}

internal object DocumentTabShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val foot = minOf(with(density) { DocumentTabShapeDimensions.bottomFootRadius.toPx() }, size.width / 4f, size.height / 4f)
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

@Composable
internal fun NotepadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    // The expressive popup's native position provider reserves 8dp at both horizontal edges.
    // Constrain oversized menus too, so large fonts and narrow windows retain that clearance.
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .widthIn(max = with(density) { window.width.toDp() - DropdownMenuDimensions.horizontalWindowMargin * 2 }.coerceAtLeast(0.dp))
            .heightIn(max = with(density) { window.height.toDp() - DropdownMenuDimensions.verticalWindowMargin * 2 }.coerceAtLeast(0.dp))
            .testTag("dropdown-menu"),
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(shape = MenuDefaults.shape, inactiveShape = MenuDefaults.shape),
            modifier = Modifier.verticalScroll(rememberScrollState()),
            containerColor = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
            contentPadding = PaddingValues(vertical = DropdownMenuDimensions.verticalContentPadding),
            content = content,
        )
    }
}
