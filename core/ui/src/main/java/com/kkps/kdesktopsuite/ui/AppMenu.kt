package com.kkps.kdesktopsuite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

object AppMenuDimensions {
    val horizontalWindowMargin = 8.dp
    val verticalWindowMargin = 48.dp
    val itemHeight = 44.dp
}

@Composable
fun AppMenuButton(label: String, onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        TextButton(onClick = onClick,modifier = Modifier.heightIn(min = 32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp,vertical = 4.dp)) { Text(label) }
    }
}

@Composable
fun AppMenuItem(
    text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null, enabled: Boolean = true, checked: Boolean? = null,
    shortcut: String? = null, leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val textColor = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.copy(alpha = 0.38f) }
    Row(modifier.widthIn(min = 200.dp,max = 320.dp).fillMaxWidth().heightIn(min = AppMenuDimensions.itemHeight)
        .semantics { if (checked != null) toggleableState = ToggleableState(checked) }
        .clickable(interactionSource = interaction,indication = ripple(focusRingShape = RectangleShape),
            enabled = enabled,role = if (checked == null) Role.Button else Role.Checkbox,onClick = onClick)
        .padding(horizontal = 12.dp),verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                if (leadingIcon != null) { leadingIcon(); Spacer(Modifier.width(12.dp)) }
                Box(Modifier.weight(1f)) { text() }
                if (trailingIcon != null || shortcut != null) {
                    Spacer(Modifier.width(12.dp))
                    if (trailingIcon != null) trailingIcon() else Text(requireNotNull(shortcut),style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun AppDropdownMenu(
    expanded: Boolean, onDismissRequest: () -> Unit, offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    DropdownMenuPopup(expanded = expanded,onDismissRequest = onDismissRequest,
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below,offset),
        modifier = Modifier
            .widthIn(max = with(density) { window.width.toDp() - AppMenuDimensions.horizontalWindowMargin * 2 }.coerceAtLeast(0.dp))
            .heightIn(max = with(density) { window.height.toDp() - AppMenuDimensions.verticalWindowMargin * 2 }.coerceAtLeast(0.dp))
            .testTag("dropdown-menu")) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes(shape = MenuDefaults.shape,inactiveShape = MenuDefaults.shape),
            modifier = Modifier.verticalScroll(rememberScrollState()),containerColor = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,shadowElevation = MenuDefaults.ShadowElevation,
            contentPadding = PaddingValues(0.dp),content = content)
    }
}
