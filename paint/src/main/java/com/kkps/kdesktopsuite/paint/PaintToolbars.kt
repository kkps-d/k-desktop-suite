@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.AppDropdownMenu
import com.kkps.kdesktopsuite.ui.AppMenuItem

private val commonShapes = listOf(Tool.Line,Tool.Rectangle,Tool.Ellipse,Tool.RoundedRectangle,
    Tool.Triangle,Tool.RightArrow,Tool.Diamond,Tool.Polygon,Tool.Curve,Tool.FivePointStar)

@Composable
private fun toolbarTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

@Composable
internal fun PaintToolbars(s: PaintUiState, model: PaintViewModel, width: Float) {
    val availableWidth = width / LocalDensity.current.fontScale
    val labels = availableWidth >= 840
    val enabled = !s.loading
    val selected = s.selection != null || s.preview?.payload != null
    val shape = s.options.tool.takeIf { it.isShape } ?: s.lastShape
    val primaryScroll = rememberScrollState()
    Column(Modifier.testTag("classic-ribbon")) {
        if (!s.ribbonCollapsed) Row(Modifier.fillMaxWidth().horizontalScroll(primaryScroll)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("paint-primary-toolbar"),
            verticalAlignment = Alignment.CenterVertically) {
            PaintButton(PaintIcon.Copy,"${stringResource(R.string.ui_copy)} (Ctrl+C)",enabled && selected,busy = s.busy) { model.copy() }
            PaintButton(PaintIcon.Cut,"${stringResource(R.string.ui_cut)} (Ctrl+X)",enabled && selected,busy = s.busy) { model.copy(true) }
            PaintButton(PaintIcon.Paste,"${stringResource(R.string.ui_paste)} (Ctrl+V)",enabled,busy = s.busy,action = model::paste)
            ToolbarDivider()
            PaintButton(Tool.Select.icon,"Select",enabled = enabled,busy = s.busy,selected = s.options.tool == Tool.Select) { model.tool(Tool.Select) }
            TransformControls(s,model)
            ToolbarDivider()
            listOf(Tool.Pan,Tool.Pencil,Tool.Brush,Tool.Eraser,Tool.Fill,Tool.Text,Tool.Eyedropper).forEach { tool ->
                PaintButton(tool.icon,tool.label,enabled = enabled,busy = s.busy,selected = s.options.tool == tool) { model.tool(tool) }
            }
            PaintButton(shape.icon,"Shape",modifier = Modifier.testTag("paint-shape-tool"),
                enabled = enabled,busy = s.busy,selected = s.options.tool.isShape) { model.tool(shape) }
            ToolbarDivider()
            ToolbarColor(s.options.foreground,stringResource(R.string.ui_foreground),!s.colorBackground,enabled && !s.busy) { model.selectColor(false) }
            ToolbarColor(s.options.background,stringResource(R.string.ui_background),s.colorBackground,enabled && !s.busy) { model.selectColor(true) }
        }
        if (!s.ribbonCollapsed) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        key(if (s.colorOptions) "colors" else if (s.options.tool.isShape) "shapes" else s.options.tool.name) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceContainer).testTag("paint-secondary-toolbar")
                .heightIn(min = 48.dp),verticalAlignment = Alignment.CenterVertically) {
                if (availableWidth >= 600) {
                    val name = if (s.colorOptions) {
                        if (s.colorBackground) stringResource(R.string.ui_background) else stringResource(R.string.ui_foreground)
                    } else if (s.options.tool.isShape) "Shape" else s.options.tool.label
                    Text(name,Modifier.padding(horizontal = 12.dp),maxLines = 1,style = MaterialTheme.typography.labelLarge)
                    ToolbarDivider()
                }
                if (s.colorOptions) PaletteOptions(s,model,availableWidth)
                else when {
                    s.options.tool == Tool.Eyedropper -> {
                        ToggleButton(checked = s.assignCustomColor,onCheckedChange = model::assignCustomColor,
                            enabled = enabled && !s.busy,modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(stringResource(R.string.ui_assign_to_custom_color),maxLines = 1)
                        }
                        if (s.assignCustomColor) s.preferences.customColors.forEachIndexed { index,color ->
                            ColorSwatch(color,"Custom color ${index+1}",s.eyedropperSlot == index,enabled && !s.busy) { model.eyedropperSlot(index) }
                        }
                    }
                    s.options.tool == Tool.Select -> {
                        PreviewActions(s,model)
                        ToolbarCheck("Transparent",false,false,"Available in a future update") { }
                        ToolbarAction(PaintIcon.Crop,stringResource(R.string.ui_crop_to_selection),labels,enabled && !s.busy && selected,model::crop)
                        GeometryAction(s,model,labels)
                        TextButton(onClick = model::selectAll,enabled = enabled && !s.busy,colors = toolbarTextButtonColors(),modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Select All" }) {
                            Icon(PaintIcon.SelectAll.vector,null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_select_all),maxLines = 1)
                        }
                        ToolbarAction(PaintIcon.Delete,stringResource(R.string.ui_delete_pixels),labels,enabled && !s.busy && selected,model::deleteSelection)

                    }
                    s.options.tool == Tool.Brush -> { BrushPicker(s,model); WidthPicker(s,model) }
                    s.options.tool == Tool.Eraser -> WidthPicker(s,model)
                    s.options.tool == Tool.Text -> {
                        PreviewActions(s,model)
                        FontOptions(s,model)
                        ToolbarCheck("Opaque background",s.options.opaqueText,enabled && !s.busy) { value -> model.options { it.copy(opaqueText = value) } }
                        GeometryAction(s,model,labels)
                    }
                    s.options.tool.isShape -> {
                        PreviewActions(s,model)
                        val count = when { availableWidth < 600 -> 3; availableWidth < 840 -> 5; availableWidth < 1200 -> 8; else -> 10 }
                        commonShapes.take(count).forEach { tool ->
                            PaintButton(tool.icon,tool.label,enabled = enabled,selected = s.options.tool == tool,
                                busy = s.busy,modifier = Modifier.testTag("paint-shape-shortcut-${tool.name}")) { model.tool(tool) }
                        }
                        ShapePicker(s,model,shape)
                        ToolbarCheck("Outline",s.options.outline,enabled && !s.busy) { value -> model.options { it.copy(outline = value) } }
                        ToolbarCheck("Fill",s.options.fill,enabled && !s.busy) { value -> model.options { it.copy(fill = value) } }
                        ToolbarCheck("Keep aspect ratio",s.options.constrain,enabled && !s.busy) { value -> model.options { it.copy(constrain = value) } }
                        WidthPicker(s,model)
                        GeometryAction(s,model,labels)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformControls(s: PaintUiState, model: PaintViewModel) {
    val enabled = !s.busy && !s.loading && !s.saving
    PaintButton(PaintIcon.RotateLeft,stringResource(R.string.ui_rotate_left),!s.loading && !s.saving,busy = s.busy) { model.rotate(-90) }
    PaintButton(PaintIcon.Rotate,stringResource(R.string.ui_rotate_right),!s.loading && !s.saving,busy = s.busy) { model.rotate(90) }
    PaintButton(PaintIcon.CustomRotate,"Custom rotate",!s.loading && !s.saving,busy = s.busy) { model.dialog(PaintDialog.Rotation) }
    var flipOpen by remember { mutableStateOf(false) }
    Box {
        ToolbarTooltip("Flip") {
            TextButton(onClick = { flipOpen = true },enabled = enabled,colors = toolbarTextButtonColors(),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Flip" },contentPadding = PaddingValues(8.dp)) {
                Icon(PaintIcon.FlipHorizontal.vector,null)
                Icon(PaintIcon.Chevron.vector,null,Modifier.size(16.dp))
            }
        }
        TransformMenu(flipOpen,{ flipOpen = false }) { initialFocus ->
            AppMenuItem({ Text(stringResource(R.string.ui_flip_vertical)) },{ flipOpen = false; model.flip(false) },
                leadingIcon = { Icon(PaintIcon.FlipVertical.vector,null) },modifier = initialFocus)
            AppMenuItem({ Text(stringResource(R.string.ui_flip_horizontal)) },{ flipOpen = false; model.flip(true) },
                leadingIcon = { Icon(PaintIcon.FlipHorizontal.vector,null) })
        }
    }
}

@Composable
private fun TransformMenu(open: Boolean, dismiss: () -> Unit, content: @Composable ColumnScope.(Modifier) -> Unit) {
    val focus = remember { FocusRequester() }
    AppDropdownMenu(open,dismiss) {
        Column(Modifier.onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { dismiss(); true } else false
        }) { content(Modifier.focusRequester(focus)) }
        LaunchedEffect(open) { if (open) { withFrameNanos { }; focus.requestFocus() } }
    }
}

@Composable
private fun ToolbarTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },state = rememberTooltipState(),content = content)
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(Modifier.height(24.dp).padding(horizontal = 6.dp),color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ToolbarAction(icon: PaintIcon, label: String, showLabel: Boolean, enabled: Boolean, action: () -> Unit) {
    if (!showLabel) PaintButton(icon,label,enabled,action = action)
    else TextButton(onClick = action,enabled = enabled,colors = toolbarTextButtonColors(),modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label }) {
        Icon(icon.vector,null)
        Spacer(Modifier.width(6.dp))
        Text(label,maxLines = 1)
    }
}

@Composable
private fun ToolbarCheck(label: String, checked: Boolean, enabled: Boolean, tooltip: String = label, onChange: (Boolean) -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(tooltip) } },state = rememberTooltipState()) {
        Row(Modifier.heightIn(min = 48.dp).toggleable(checked,enabled = enabled,role = Role.Checkbox,onValueChange = onChange)
            .padding(end = 12.dp),verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked,onCheckedChange = null,enabled = enabled,modifier = Modifier.padding(horizontal = 8.dp))
            Text(label,maxLines = 1,style = MaterialTheme.typography.labelLarge,color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
    }
}

@Composable
private fun PreviewActions(s: PaintUiState, model: PaintViewModel) {
    val enabled = !s.busy && !s.loading
    PaintButton(PaintIcon.Cancel,stringResource(R.string.ui_clear_selection),enabled && (s.preview != null || s.selection != null),action = model::clearSelection)
    PaintButton(PaintIcon.Apply,"Commit",enabled && s.preview != null) { model.applyPreview() }
    ToolbarDivider()
}

@Composable
private fun GeometryAction(s: PaintUiState, model: PaintViewModel, labels: Boolean) {
    val label = stringResource(if (s.options.tool == Tool.Text) R.string.ui_move_resize else R.string.ui_move_resize_skew)
    val hasTarget = s.options.tool == Tool.Select || s.selection != null || s.preview != null
    ToolbarAction(PaintIcon.Geometry,label,labels,hasTarget && !s.busy && !s.loading && !s.saving) { model.dialog(PaintDialog.Transform) }
}

@Composable
private fun ToolbarColor(color: Int, label: String, selected: Boolean, enabled: Boolean, action: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text("$label #${"%06X".format(color and 0xFFFFFF)}") } },state = rememberTooltipState()) {
        Box(Modifier.size(48.dp).clickable(enabled = enabled,role = Role.Button,onClick = action)
            .semantics { contentDescription = label; stateDescription = "#${"%06X".format(color and 0xFFFFFF)}"; this.selected = selected }
            .padding(6.dp).border(if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,CircleShape)
            .padding(3.dp).background(Color(color),CircleShape))
    }
}

@Composable
internal fun ColorSwatch(color: Int, label: String, selected: Boolean = false, enabled: Boolean = true, saving: Boolean = false, action: () -> Unit) {
    val description = "$label #${"%06X".format(color and 0xFFFFFF)}"
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(description) } },state = rememberTooltipState()) {
        Box(Modifier.size(48.dp).clickable(enabled = enabled,role = Role.Button,onClick = action)
            .semantics { contentDescription = description; this.selected = selected }
            .padding(6.dp).border(if (saving) 3.dp else if (selected) 2.dp else 1.dp,
                if (saving || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,RoundedCornerShape(6.dp))
            .background(Color(color),RoundedCornerShape(6.dp)))
    }
}

@Composable
private fun PaletteOptions(s: PaintUiState, model: PaintViewModel, width: Float) {
    val presets = if (width < 600) 5 else if (width < 1200) 10 else 15
    val custom = if (width < 600) 2 else if (width < 1200) 3 else 5
    val current = if (s.colorBackground) s.options.background else s.options.foreground
    presetColors.take(presets).forEachIndexed { index,color ->
        ColorSwatch(color,"Preset color ${index+1}",current == color,!s.busy && !s.loading) { model.options { if (s.colorBackground) it.copy(background = color) else it.copy(foreground = color) } }
    }
    ToolbarDivider()
    s.preferences.customColors.take(custom).forEachIndexed { index,color ->
        ColorSwatch(color,"Custom color ${index+1}",current == color,!s.busy && !s.loading) { model.options { if (s.colorBackground) it.copy(background = color) else it.copy(foreground = color) } }
    }
    PaintButton(PaintIcon.Chevron,stringResource(R.string.ui_edit_colors),!s.busy && !s.loading) { model.colors() }
    PaintButton(PaintIcon.Swap,stringResource(R.string.ui_swap_foreground_and_background),!s.busy && !s.loading) { model.options { it.copy(foreground = it.background,background = it.foreground) } }
}

@Composable
private fun FontOptions(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    val enabled = !s.busy && !s.loading
    Box {
        TextButton(onClick = { open = true },enabled = enabled,colors = toolbarTextButtonColors(),modifier = Modifier.heightIn(min = 48.dp).testTag("paint-font-picker").semantics { contentDescription = "Font family" }) {
            Text(s.options.font,maxLines = 1)
            Icon(PaintIcon.Chevron.vector,null)
        }
        AppDropdownMenu(open,{ open = false }) {
            PaintFonts.names.forEach { font ->
                AppMenuItem({ Text(font,fontFamily = PaintFonts.family(font)) },{ model.options { it.copy(font = font) }; open = false },
                    checked = s.options.font == font)
            }
        }
    }
    val size = rememberTextFieldState(s.options.fontSize.toString())
    LaunchedEffect(s.options.fontSize) {
        if (size.text.toString().toIntOrNull() != s.options.fontSize) size.edit { replace(0,length,s.options.fontSize.toString()) }
    }
    LaunchedEffect(size) {
        snapshotFlow { size.text.toString() }.collect { value ->
            value.toIntOrNull()?.takeIf { it in 1..512 }?.let { number -> model.options { it.copy(fontSize = number) } }
        }
    }
    val sizeFocus = remember { FocusRequester() }
    // The smaller outline keeps a 48dp tap area, including the space above and below it.
    Box(Modifier.width((56 + 36 * LocalDensity.current.fontScale).dp).heightIn(min = 48.dp)
        .pointerInput(enabled) { detectTapGestures { if (enabled) sizeFocus.requestFocus() } }
        .padding(horizontal = 4.dp,vertical = 6.dp),contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            OutlinedTextField(state = size,enabled = enabled,lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).focusRequester(sizeFocus)
                    .testTag("paint-font-size").semantics { contentDescription = "Font size (image pixels)" },
                textStyle = MaterialTheme.typography.bodyMedium,
                trailingIcon = { Text("px",Modifier.padding(end = 6.dp),style = MaterialTheme.typography.labelMedium) },
                contentPadding = PaddingValues(horizontal = 8.dp,vertical = 2.dp),
                isError = size.text.toString().toIntOrNull() !in 1..512,
                inputTransformation = InputTransformation {
                    if (length > 3 || !asCharSequence().all(Char::isDigit)) revertAllChanges()
                },keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
    PaintButton(PaintIcon.Bold,"Bold",enabled,selected = s.options.bold) { model.options { it.copy(bold = !it.bold) } }
    PaintButton(PaintIcon.Italic,"Italic",enabled,selected = s.options.italic) { model.options { it.copy(italic = !it.italic) } }
    PaintButton(PaintIcon.Underline,"Underline",enabled,selected = s.options.underline) { model.options { it.copy(underline = !it.underline) } }
    PaintButton(PaintIcon.Strike,"Strikethrough",enabled,selected = s.options.strike) { model.options { it.copy(strike = !it.strike) } }
    ToolbarDivider()
    AlignmentDropdown(stringResource(R.string.ui_horizontal_alignment),s.options.textAlignment,enabled,listOf(
        Triple(TextAlignment.Left,PaintIcon.AlignLeft,R.string.ui_align_left),
        Triple(TextAlignment.Center,PaintIcon.AlignCenter,R.string.ui_align_center),
        Triple(TextAlignment.Right,PaintIcon.AlignRight,R.string.ui_align_right),
    )) { value -> model.options { it.copy(textAlignment = value) } }
    AlignmentDropdown(stringResource(R.string.ui_vertical_alignment),s.options.verticalTextAlignment,enabled,listOf(
        Triple(VerticalTextAlignment.Top,PaintIcon.AlignTop,R.string.ui_align_top),
        Triple(VerticalTextAlignment.Middle,PaintIcon.AlignMiddle,R.string.ui_align_middle),
        Triple(VerticalTextAlignment.Bottom,PaintIcon.AlignBottom,R.string.ui_align_bottom),
    )) { value -> model.options { it.copy(verticalTextAlignment = value) } }
}

@Composable
private fun <T> AlignmentDropdown(label: String, selected: T, enabled: Boolean,
    choices: List<Triple<T,PaintIcon,Int>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = choices.first { it.first == selected }
    val currentLabel = stringResource(current.third)
    Box {
        ToolbarTooltip("$label: $currentLabel") {
            TextButton(onClick = { open = true },enabled = enabled,colors = toolbarTextButtonColors(),contentPadding = PaddingValues(8.dp),
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = label
                    stateDescription = currentLabel
                }) {
                Icon(current.second.vector,null)
                Icon(PaintIcon.Chevron.vector,null,Modifier.size(16.dp))
            }
        }
        AppDropdownMenu(open,{ open = false }) {
            choices.forEach { (value,icon,text) ->
                AppMenuItem(text = { Text(stringResource(text)) },onClick = { onSelect(value); open = false },
                    enabled = enabled,checked = selected == value,leadingIcon = { Icon(icon.vector,null) },
                    trailingIcon = if (selected == value) {{ Icon(PaintIcon.Apply.vector,null) }} else null)
            }
        }
    }
}

@Composable
private fun BrushPicker(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val style = s.options.brushStyle
    val colors = toolbarTextButtonColors()
    val preserveEnabled = s.busy && !s.loading && !LocalPaintBusyVisible.current
    Box {
        TextButton(onClick = { open = true },enabled = !s.busy && !s.loading,
            colors = if (preserveEnabled) colors.copy(disabledContainerColor = colors.containerColor,
                disabledContentColor = colors.contentColor) else colors,
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("paint-brush-picker")
                .semantics { contentDescription = "Choose brush: ${style.label}" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrushPreview(style,Modifier.size(72.dp,28.dp))
                Icon(PaintIcon.Chevron.vector,null,Modifier.size(16.dp))
            }
        }
        AppDropdownMenu(open,{ open = false }) {
            Column(Modifier.width(280.dp).selectableGroup().onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { open = false; true } else false
            }) {
                BrushStyle.entries.forEach { brush ->
                    AppMenuItem(text = { Text(brush.label) },onClick = { open = false; model.brush(brush) },
                        leadingIcon = { BrushPreview(brush,Modifier.size(88.dp,36.dp)) },
                        trailingIcon = { if (brush == style) Icon(PaintIcon.Apply.vector,null,Modifier.size(20.dp)) },
                        modifier = (if (brush == style) Modifier.focusRequester(focus) else Modifier)
                            .focusProperties { canFocus = true }
                            .heightIn(min = 48.dp).testTag("paint-brush-${brush.name}")
                            .semantics { selected = brush == style })
                }
            }
            LaunchedEffect(open) { if (open) { withFrameNanos { }; focus.requestFocus() } }
        }
    }
}

@Composable
private fun BrushPreview(style: BrushStyle, modifier: Modifier) {
    val image = remember(style) {
        val points = List(97) { index ->
            val t = index / 96f
            Point(12f+t*152f,36f-kotlin.math.sin(t*kotlin.math.PI*2).toFloat()*15f)
        }
        val bitmap = Raster.blank(176,72).brushStroke(points,BLACK,18f,style,42).bitmap()
        bitmap.asImageBitmap()
    }
    Image(image,null,modifier.background(Color.White,RoundedCornerShape(4.dp)).padding(2.dp))
}

@Composable
private fun ShapePicker(s: PaintUiState, model: PaintViewModel, shape: Tool) {
    var open by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Box {
        TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text("More shapes") } },state = rememberTooltipState()) {
            IconButton(onClick = { open = true },enabled = !s.busy && !s.loading,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)).let { colors ->
                    if (s.busy && !s.loading && !LocalPaintBusyVisible.current) colors.copy(disabledContentColor = colors.contentColor) else colors
                },
                modifier = Modifier.size(width = 64.dp,height = 48.dp).testTag("paint-shape-picker")
                    .semantics { contentDescription = "More shapes" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(PaintIcon.More.vector,null)
                    Icon(PaintIcon.Chevron.vector,null,Modifier.size(16.dp))
                }
            }
        }
        AppDropdownMenu(open,{ open = false }) {
            Column(Modifier.padding(8.dp).selectableGroup().onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { open = false; true } else false
            }) {
                shapeTools.chunked(4).forEach { shapes ->
                    Row {
                        shapes.forEach { tool ->
                            PaintButton(tool.icon,tool.label,selected = s.options.tool == tool,
                                modifier = (if (tool == shape) Modifier.focusRequester(focus) else Modifier)
                                    .focusProperties { canFocus = true }) {
                                open = false; model.tool(tool)
                            }
                        }
                    }
                }
            }
            LaunchedEffect(open) { if (open) { withFrameNanos { }; focus.requestFocus() } }
        }
    }
}
