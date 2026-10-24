package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal object DocumentTabShapeDimensions {
    val topCornerRadius = 12.dp
    val bottomFootRadius = 12.dp
    val overlap = 16.dp
    val hoverBottomGap = 4.dp
}

internal object DropdownMenuDimensions {
    // These window margins mirror Material's popup position provider.
    val horizontalWindowMargin = 8.dp
    val verticalWindowMargin = 48.dp
    val itemHeight = 44.dp
}

@Composable
internal fun NotepadMenuItem(
    text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null, enabled: Boolean = true, checked: Boolean? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val textColor = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.copy(alpha = 0.38f) }
    Row(
        modifier.widthIn(min = 200.dp, max = 320.dp).fillMaxWidth().heightIn(min = DropdownMenuDimensions.itemHeight)
            .semantics { if (checked != null) toggleableState = ToggleableState(checked) }
            .clickable(interactionSource = interaction, indication = ripple(focusRingShape = RectangleShape),
                enabled = enabled, role = if (checked == null) Role.Button else Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Box(Modifier.weight(1f)) { text() }
                if (trailingIcon != null) {
                    Spacer(Modifier.width(12.dp))
                    trailingIcon()
                }
            }
        }
    }
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
            contentPadding = PaddingValues(0.dp),
            content = content,
        )
    }
}
