@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kkps.kdesktopsuite.paint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.kkps.kdesktopsuite.ui.AppDropdownMenu
import com.kkps.kdesktopsuite.ui.AppMenuButton
import com.kkps.kdesktopsuite.ui.AppMenuItem

// Input is blocked immediately; brief operations need not flash disabled colors.
internal val LocalPaintBusyVisible = compositionLocalOf { true }

@Composable
internal fun paintTonalIconColors(preserveEnabled: Boolean, container: Color = Color.Unspecified): IconButtonColors {
    val colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = container)
    return if (preserveEnabled) colors.copy(disabledContainerColor = colors.containerColor,
        disabledContentColor = colors.contentColor) else colors
}

@Composable
internal fun PaintButton(icon: PaintIcon, label: String, enabled: Boolean = true, selected: Boolean = false, busy: Boolean = false, action: () -> Unit) {
    PaintButton(icon.vector,label,enabled = enabled,selected = selected,busy = busy,action = action)
}

@Composable
private fun PaintButton(icon: ImageVector, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, busy: Boolean = false, action: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = action, enabled = enabled && !busy, modifier = modifier.size(48.dp)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
            .semantics { this.selected = selected; if (selected) stateDescription = "Selected" }) {
            Icon(icon, label, tint = if (!enabled || (busy && LocalPaintBusyVisible.current)) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PaintScreen(state: PaintUiState, model: PaintViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var showBusy by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy) {
        if (state.busy) delay(250)
        showBusy = state.busy
    }
    var cursor by remember { mutableStateOf<Point?>(Point(0f,0f)) }
    var selectionDrag by remember { mutableStateOf<PixelRect?>(null) }
    var canvasResize by remember { mutableStateOf<PixelRect?>(null) }
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.message(null) } }
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(!imeVisible && (debugOpen || state.settingsOpen || state.dialog != null || state.preview != null || state.selection != null || state.focusMode)) {
        if (debugOpen) debugOpen = false else model.back()
    }
    CompositionLocalProvider(LocalPaintBusyVisible provides showBusy) {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
                val width = maxWidth
                val short = maxHeight < 480.dp
                Column(Modifier.fillMaxSize().onKeyEvent { event -> if (state.settingsOpen || debugOpen) false else handlePaintKey(event,state,model) }) {
                    if (debugOpen) PaintDebugScreen { debugOpen = false }
                    else if (state.settingsOpen) PaintSettingsScreen(state,model) else {
                        if (!state.focusMode) {
                            PaintHeader(state,model,width.value,showBusy)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            if (!state.ribbonCollapsed) PaintRibbon(state,model,width.value)
                            else TextButton(onClick = model::toggleRibbon) { Text(stringResource(R.string.ui_show_drawing_tools)) }
                            if (state.preview != null || state.selection != null) SelectionActions(state,model)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        } else Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.End) {
                            PaintOperationProgress(state,model,showBusy)
                            TextButton(onClick = model::toggleFocus) { Text(stringResource(R.string.ui_exit_focus_mode)) }
                        }
                        if (short && !state.ribbonCollapsed && !state.focusMode) {
                            TextButton(onClick = model::toggleRibbon,modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ui_collapse_ribbon)) }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            PaintCanvas(state,model,onCursorChange = { cursor = it },onSelectionChange = { selectionDrag = it },
                                onCanvasResizeChange = { canvasResize = it })
                        }
                        if (state.showInformation && !state.focusMode) PaintStatus(state,model,cursor,selectionDrag,canvasResize)
                    }
                }
                SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter))
                if (!debugOpen) PaintDialogs(state,model) {
                    model.dialog(null)
                    debugOpen = true
                }
            }
        }
    }
}

@Composable
private fun PaintHeader(s: PaintUiState, model: PaintViewModel, width: Float, showBusy: Boolean) {
    val compact = width < 700 * LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth().testTag("paint-header")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),verticalAlignment = Alignment.CenterVertically) {
            FileMenu(s,model)
            ViewMenu(s,model)
            HelpMenu(model)
            VerticalDivider(Modifier.padding(horizontal = 8.dp).height(20.dp))
            Row(Modifier.weight(1f).padding(horizontal = 8.dp),verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.dirty || s.preview?.hasContent == true) Box(Modifier.size(6.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant,CircleShape).testTag("paint-unsaved-indicator")
                    .semantics { contentDescription = "Unsaved changes" })
                Text(s.title,modifier = Modifier.testTag("paint-document-title"),maxLines = 1,
                    overflow = TextOverflow.Ellipsis,style = MaterialTheme.typography.titleSmall)
            }
            if (!compact) PaintHeaderActions(s,model,showBusy)
            TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                tooltip = { PlainTooltip { Text(stringResource(R.string.ui_settings)) } },state = rememberTooltipState()) {
                IconButton(onClick = { model.showSettings(true) }) {
                    Icon(painterResource(R.drawable.ic_settings),stringResource(R.string.ui_settings))
                }
            }
        }
        if (compact) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End) { PaintHeaderActions(s,model,showBusy) }
    }
}

@Composable
private fun PaintHeaderActions(s: PaintUiState, model: PaintViewModel, showBusy: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically,modifier = Modifier.testTag("paint-header-actions")) {
        PaintOperationProgress(s,model,showBusy)
        PaintButton(PaintIcon.Save,"Save (Ctrl+S)",!s.loading && !s.saving,busy = s.busy) { model.save() }
        PaintButton(PaintIcon.Undo,"Undo (Ctrl+Z)",s.canUndo || s.preview != null,busy = s.busy,action = model::undo)
        PaintButton(PaintIcon.Redo,"Redo (Ctrl+Shift+Z)",s.canRedo,busy = s.busy,action = model::redo)
    }
}

@Composable
private fun PaintOperationProgress(s: PaintUiState, model: PaintViewModel, showBusy: Boolean) {
    if (s.loading || (s.busy && showBusy) || s.saving) Row(Modifier.testTag("paint-operation-progress"),verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(20.dp)
            .semantics { contentDescription = if (s.loading) "Loading image" else if (s.saving) "Saving image" else "Editing image" },strokeWidth = 2.dp)
        if (s.busy && !s.loading) PaintButton(PaintIcon.Cancel,"Cancel operation",action = model::cancelOperation)
        VerticalDivider(Modifier.padding(horizontal = 8.dp).height(20.dp))
    }
}

@Composable
private fun HelpMenu(model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppMenuButton(stringResource(R.string.ui_help)) { open = true }
        AppDropdownMenu(open,{ open = false }) {
            AppMenuItem({ Text(stringResource(R.string.ui_about_kd_paint)) },{ open = false; model.dialog(PaintDialog.About) })
        }
    }
}

@Composable
private fun FileMenu(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppMenuButton(stringResource(R.string.ui_file)) { open = true }
        AppDropdownMenu(open,{ open = false }) {
            fun close(action: () -> Unit) { open = false; action() }
            AppMenuItem({ Text(stringResource(R.string.ui_new_image_ctrl_n)) },{ close(model::newImage) },enabled = !s.busy,shortcut = "Ctrl+N")
            AppMenuItem({ Text(stringResource(R.string.ui_open_image_ctrl_o)) },{ close(model::open) },enabled = !s.busy,shortcut = "Ctrl+O")
            AppMenuItem({ Text(stringResource(R.string.ui_save_ctrl_s)) },{ close { model.save() } },enabled = !s.busy,shortcut = "Ctrl+S")
            AppMenuItem({ Text(stringResource(R.string.ui_save_as_ctrl_shift_s)) },{ close { model.save(true) } },enabled = !s.busy,shortcut = "Ctrl+Shift+S")
            if (s.preferences.recent.isNotEmpty()) {
                HorizontalDivider()
                s.preferences.recent.forEachIndexed { index, uri ->
                    AppMenuItem({ Text("Recent ${index+1}: ${android.net.Uri.decode(uri.substringAfterLast('/')).takeLast(44)}",maxLines = 2) },
                        { close { model.openRecent(uri) } })
                }
                HorizontalDivider()
            }
            AppMenuItem({ Text(stringResource(R.string.ui_insert_image)) },{ close(model::insertPicker) },enabled = !s.busy)
            AppMenuItem({ Text(stringResource(R.string.ui_share_png)) },{ close(model::share) },enabled = !s.busy)
            AppMenuItem({ Text(stringResource(R.string.ui_image_properties)) },{ close { model.dialog(PaintDialog.Properties) } })
            AppMenuItem({ Text(stringResource(R.string.ui_close_image_ctrl_w)) },{ close(model::newImage) },enabled = !s.busy,shortcut = "Ctrl+W")
        }
    }
}

@Composable
private fun ViewMenu(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppMenuButton(stringResource(R.string.ui_view)) { open = true }
        AppDropdownMenu(open,{ open = false }) {
            AppMenuItem({ Text(stringResource(R.string.ui_zoom_in_ctrl)) },{ model.viewport(s.viewport.zoomed(s.viewport.scale + 0.1f)); open = false },shortcut = "Ctrl++")
            AppMenuItem({ Text(stringResource(R.string.ui_zoom_out_ctrl)) },{ model.viewport(s.viewport.zoomed(s.viewport.scale - 0.1f)); open = false },shortcut = "Ctrl+−")
            AppMenuItem({ Text(stringResource(R.string.ui_actual_size_ctrl_0)) },{ model.viewport(s.viewport.actual()); open = false },shortcut = "Ctrl+0")
            AppMenuItem({ Text(stringResource(R.string.ui_fit_image)) },{ model.viewport(s.viewport.fitted()); open = false })
            AppMenuItem({ Text(stringResource(R.string.ui_pan_tool)) },{ model.tool(Tool.Pan); open = false })
            AppMenuItem({ Text(if (s.ribbonCollapsed) "Show ribbon" else "Collapse ribbon") },{ model.toggleRibbon(); open = false })
            AppMenuItem({ Text(stringResource(R.string.ui_status_bar)) },{ model.toggleInformation(); open = false },
                trailingIcon = { Text(if (s.showInformation) "✓" else "",Modifier.width(24.dp)) },
                checked = s.showInformation)
            AppMenuItem({ Text(stringResource(R.string.ui_focus_mode)) },{ model.toggleFocus(); open = false })
        }
    }
}

@Composable
private fun RibbonGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxHeight().heightIn(min = 128.dp).padding(horizontal = 8.dp,vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.SpaceBetween) {
        content()
        Text(label,style = MaterialTheme.typography.labelSmall,modifier = Modifier.padding(top = 4.dp))
    }
    VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 6.dp),color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun PaintRibbon(s: PaintUiState, model: PaintViewModel, width: Float) {
    val selected = s.selection != null || s.preview?.payload != null
    val tools = listOf(Tool.Pencil,Tool.Fill,Tool.Text,Tool.Pan,Tool.Eraser,Tool.Eyedropper,Tool.Zoom)
    var lastShape by rememberSaveable { mutableStateOf(Tool.Rectangle) }
    val shape = s.options.tool.takeIf { it.isShape } ?: lastShape
    LaunchedEffect(shape) { lastShape = shape }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).horizontalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("classic-ribbon"),verticalAlignment = Alignment.Top) {
        if (width < 760) {
            RibbonGroup("Clipboard") { ClipboardMenu(s,model) }
            RibbonGroup("Image") { ImageMenu(s,model) }
            RibbonGroup("Tools") { ToolMenu(s,model,Tool.entries.filterNot { it.isShape }) }
            RibbonGroup("Brushes") { BrushPicker(s,model) }
            RibbonGroup("Shapes") { ShapePicker(s,model,shape) { lastShape = it }; ShapeStyles(s,model) }
            RibbonGroup("Size") { WidthPicker(s,model) }
            RibbonGroup("Colors") { ColorControls(s,model,false) }
        } else {
            RibbonGroup("Clipboard") {
                if (width < 970) ClipboardMenu(s,model) else Row {
                    PaintButton(PaintIcon.Paste,"Paste (Ctrl+V)",action = model::paste)
                    Column { PaintButton(PaintIcon.Cut,"Cut (Ctrl+X)",selected) { model.copy(true) }
                        PaintButton(PaintIcon.Copy,"Copy (Ctrl+C)",selected) { model.copy() } }
                }
            }
            RibbonGroup("Image") {
                if (width < 970) ImageMenu(s,model) else Row {
                    PaintButton(PaintIcon.Select,"Rectangular selection",selected = s.options.tool == Tool.Select) { model.tool(Tool.Select) }
                    Column { PaintButton(PaintIcon.Crop,"Crop to selection",s.selection != null || s.preview?.payload != null,action = model::crop)
                        TextButton(onClick = model::selectAll) { Text(stringResource(R.string.ui_all)) } }
                    Column {
                        PaintButton(PaintIcon.Resize,stringResource(R.string.ui_resize_and_skew),enabled = !s.loading && !s.saving,busy = s.busy) { model.dialog(PaintDialog.ResizeSkew) }
                        RotationMenu(s,model)
                    }
                }
            }
            RibbonGroup("Tools") {
                Column { tools.chunked(4).forEach { row -> Row { row.forEach { tool ->
                    PaintButton(tool.icon,tool.label,selected = s.options.tool == tool,busy = s.busy) { model.tool(tool) }
                } } } }
            }
            RibbonGroup("Brushes") {
                BrushPicker(s,model)
            }
            RibbonGroup("Shapes") {
                ShapePicker(s,model,shape) { lastShape = it }
                ShapeStyles(s,model)
            }
            RibbonGroup("Size") { WidthPicker(s,model) }
            RibbonGroup("Colors") { ColorControls(s,model,width >= 1320) }
        }
    }
}

@Composable
private fun ClipboardMenu(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        PaintButton(PaintIcon.Paste,"Clipboard commands") { open = true }
        AppDropdownMenu(open,{ open = false }) {
            AppMenuItem({ Text(stringResource(R.string.ui_paste)) },{ open = false; model.paste() })
            AppMenuItem({ Text(stringResource(R.string.ui_copy)) },{ open = false; model.copy() },enabled = s.selection != null || s.preview?.payload != null)
            AppMenuItem({ Text(stringResource(R.string.ui_cut)) },{ open = false; model.copy(true) },enabled = s.selection != null || s.preview?.payload != null)
            AppMenuItem({ Text(stringResource(R.string.ui_insert_image)) },{ open = false; model.insertPicker() })
        }
    }
}

@Composable
private fun ImageMenu(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        PaintButton(PaintIcon.Select,"Selection and image commands",selected = s.options.tool == Tool.Select) { open = true }
        AppDropdownMenu(open,{ open = false }) {
            AppMenuItem({ Text(stringResource(R.string.ui_rectangular_selection)) },{ open = false; model.tool(Tool.Select) })
            AppMenuItem({ Text(stringResource(R.string.ui_select_all)) },{ open = false; model.selectAll() })
            AppMenuItem({ Text(stringResource(R.string.ui_clear_selection)) },{ open = false; model.clearSelection() },enabled = s.selection != null)
            AppMenuItem({ Text(stringResource(R.string.ui_crop_to_selection)) },{ open = false; model.crop() },enabled = s.selection != null || s.preview?.payload != null)
            AppMenuItem({ Text(stringResource(R.string.ui_move_resize_selection)) },{ open = false; model.dialog(PaintDialog.Geometry) },enabled = s.selection != null || s.preview != null)
            AppMenuItem({ Text(stringResource(R.string.ui_resize_and_skew)) },{ open = false; model.dialog(PaintDialog.ResizeSkew) },enabled = !s.busy && !s.loading && !s.saving)
            TransformMenuItems(s,model) { open = false }
        }
    }
}

@Composable
private fun RotationMenu(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        PaintButton(PaintIcon.Rotate,stringResource(R.string.ui_rotate),enabled = !s.loading && !s.saving,busy = s.busy) { open = true }
        AppDropdownMenu(open,{ open = false }) { TransformMenuItems(s,model) { open = false } }
    }
}

@Composable
private fun TransformMenuItems(s: PaintUiState, model: PaintViewModel, close: () -> Unit) {
    val enabled = !s.busy && !s.loading && !s.saving
    listOf(R.string.ui_rotate_right to 90,R.string.ui_rotate_left to -90,R.string.ui_rotate_180 to 180).forEach { (label,degrees) ->
        AppMenuItem({ Text(stringResource(label)) },{ close(); model.rotate(degrees) },enabled = enabled)
    }
    AppMenuItem({ Text(stringResource(R.string.ui_flip_horizontal)) },{ close(); model.flip(true) },enabled = enabled)
    AppMenuItem({ Text(stringResource(R.string.ui_flip_vertical)) },{ close(); model.flip(false) },enabled = enabled)
}

@Composable
private fun ToolMenu(s: PaintUiState, model: PaintViewModel, tools: List<Tool>) {
    var open by remember { mutableStateOf(false) }
    Box {
        PaintButton((if (s.options.tool in tools) s.options.tool else tools.first()).icon,"Choose drawing tool") { open = true }
        AppDropdownMenu(open,{ open = false }) { tools.forEach { tool ->
            AppMenuItem({ Text(tool.label) },{ open = false; model.tool(tool) },leadingIcon = { Icon(tool.icon,null) })
        } }
    }
}

@Composable
private fun BrushPicker(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val style = s.options.brushStyle
    val colors = ButtonDefaults.filledTonalButtonColors(containerColor =
        if (s.options.tool == Tool.Brush) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
    val preserveEnabled = s.busy && !s.loading && !LocalPaintBusyVisible.current
    Box {
        FilledTonalButton(onClick = { open = true },enabled = !s.busy && !s.loading,
            colors = if (preserveEnabled) colors.copy(disabledContainerColor = colors.containerColor,
                disabledContentColor = colors.contentColor) else colors,
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("paint-brush-picker")
                .semantics { contentDescription = "Choose brush: ${style.label}"; selected = s.options.tool == Tool.Brush }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrushPreview(style,Modifier.size(72.dp,28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(style.label,style = MaterialTheme.typography.labelSmall)
                    Icon(PaintIcon.Chevron.vector,null,Modifier.size(16.dp))
                }
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
private fun ShapePicker(s: PaintUiState, model: PaintViewModel, shape: Tool, onSelect: (Tool) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Box {
        TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text("Choose shape: ${shape.label}") } },state = rememberTooltipState()) {
            FilledTonalIconButton(onClick = { open = true },enabled = !s.busy && !s.loading,
                colors = paintTonalIconColors(s.busy && !s.loading && !LocalPaintBusyVisible.current,container =
                    if (s.options.tool.isShape) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                modifier = Modifier.size(width = 64.dp,height = 48.dp).testTag("paint-shape-picker")
                    .semantics { contentDescription = "Choose shape: ${shape.label}"; selected = s.options.tool.isShape }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(shape.icon,null)
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
                                onSelect(tool); open = false; model.tool(tool)
                            }
                        }
                    }
                }
            }
            LaunchedEffect(open) { if (open) { withFrameNanos { }; focus.requestFocus() } }
        }
    }
}

@Composable
private fun ShapeStyles(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text(stringResource(R.string.ui_style)) }
        AppDropdownMenu(open,{ open = false }) {
            AppMenuItem({ Text(if (s.options.outline) "✓ Outline" else "Outline") },{ model.options { it.copy(outline = !it.outline) } })
            AppMenuItem({ Text(if (s.options.fill) "✓ Fill" else "Fill") },{ model.options { it.copy(fill = !it.fill) } })
            AppMenuItem({ Text(if (s.options.constrain) "✓ Constrain proportions" else "Constrain proportions") },{ model.options { it.copy(constrain = !it.constrain) } })
        }
    }
}

@Composable
internal fun ColorSwatch(color: Int, label: String, selected: Boolean = false, action: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(role = Role.Button,onClick = action)
        .semantics { contentDescription = "$label #${"%06X".format(color and 0xFFFFFF)}"; if (selected) stateDescription = "Selected" }
        .padding(6.dp).border(if (selected) 2.dp else 1.dp,
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
        .background(Color(color),RoundedCornerShape(6.dp)))
}

@Composable
private fun ColorControls(s: PaintUiState, model: PaintViewModel, palette: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(s.options.foreground,"Foreground") { model.colors() }
                if (palette) Text(stringResource(R.string.ui_foreground),style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(s.options.background,"Background") { model.colors(true) }
                if (palette) Text(stringResource(R.string.ui_background),style = MaterialTheme.typography.labelSmall)
            }
        }
        if (palette) Column {
            presetColors.chunked(10).forEach { row -> Row { row.forEach { color ->
                ColorSwatch(color,"Set foreground",s.options.foreground == color) { model.options { it.copy(foreground = color) } }
            } } }
        }
        if (palette) Column {
            PaintButton(PaintIcon.Swap,"Swap foreground and background") { model.options { it.copy(foreground = it.background,background = it.foreground) } }
            TextButton({ model.colors() }) { Text(stringResource(R.string.ui_edit_colors)) }
        }
    }
}

@Composable
private fun SelectionActions(s: PaintUiState, model: PaintViewModel) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceContainer),verticalAlignment = Alignment.CenterVertically) {
        if (s.preview != null) {
            PaintButton(PaintIcon.Apply,"Apply preview",busy = s.busy) { model.applyPreview() }
            PaintButton(PaintIcon.Cancel,"Cancel preview",busy = s.busy,action = model::cancelPreview)
        } else TextButton(onClick = model::clearSelection) { Text(stringResource(R.string.ui_clear_selection)) }
        TextButton(onClick = { model.dialog(PaintDialog.Geometry) }) { Text(stringResource(R.string.ui_move_resize)) }
        if (s.selection != null || s.preview?.payload != null) {
            PaintButton(PaintIcon.Copy,"Copy selection") { model.copy() }
            PaintButton(PaintIcon.Cut,"Cut selection") { model.copy(true) }
            TextButton(onClick = model::deleteSelection) { Text(stringResource(R.string.ui_delete_pixels)) }
        }
        if (s.preview?.tool == Tool.Text) TextButton(onClick = { model.dialog(PaintDialog.Geometry) }) { Text(stringResource(R.string.ui_text_options)) }
        if (s.preview?.tool?.isShape == true) ShapeStyles(s,model)
        if (s.preview?.constructing == true) {
            TextButton(onClick = model::finishShape,enabled = !s.busy) { Text("Finish shape") }
            Text(if (s.preview.tool == Tool.Curve) "Place two bend points" else "Add vertices, then finish",
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PaintStatus(s: PaintUiState, model: PaintViewModel, cursor: Point?, selectionDrag: PixelRect?, canvasResize: PixelRect?) {
    val fieldWidth = 144.dp * LocalDensity.current.fontScale
    val selection = if (s.options.tool == Tool.Select) selectionDrag ?: s.preview?.bounds ?: s.selection else null
    val position = cursor?.let { "${it.x.toInt()}, ${it.y.toInt()} px" }.orEmpty()
    val dimensions = if (s.options.tool == Tool.Select) "${selection?.width ?: 0} x ${selection?.height ?: 0} px" else ""
    val zoom = (s.viewport.scale * 100).roundToInt()
    var zoomMenu by remember { mutableStateOf(false) }
    val zoomSlider = rememberSliderState(value = s.viewport.scale,steps = 48,trackRange = 0.1f..5f)
    val zoomInteraction = remember { MutableInteractionSource() }
    zoomSlider.value = s.viewport.scale.coerceIn(0.1f,5f)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).testTag("paint-status-bar")) {
            Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth).heightIn(min = 32.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(position,Modifier.width(fieldWidth).padding(horizontal = 12.dp).testTag("paint-cursor-position")
                    .semantics { contentDescription = "Cursor position: $position" },color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,maxLines = 1)
                VerticalDivider(Modifier.height(20.dp))
                Text(dimensions,Modifier.width(fieldWidth).padding(horizontal = 12.dp).testTag("paint-selection-dimensions")
                    .semantics { contentDescription = "Selection dimensions: $dimensions" },color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,maxLines = 1)
                VerticalDivider(Modifier.height(20.dp))
                TextButton(onClick = { model.dialog(PaintDialog.CanvasSize) },enabled = !s.busy && !s.loading,
                    modifier = Modifier.width(fieldWidth).heightIn(min = 28.dp).testTag("paint-canvas-dimensions")
                        .semantics { contentDescription = "Resize canvas" },contentPadding = PaddingValues(horizontal = 12.dp,vertical = 2.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${canvasResize?.width ?: s.raster.width} x ${canvasResize?.height ?: s.raster.height} px",
                            Modifier.weight(1f,fill = false),style = MaterialTheme.typography.labelMedium,maxLines = 1)
                        Icon(painterResource(R.drawable.ic_more_horiz),null,Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton({ model.viewport(s.viewport.fitted()) },modifier = Modifier.heightIn(min = 28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp,vertical = 2.dp)) { Text(stringResource(R.string.ui_fit),style = MaterialTheme.typography.labelMedium) }
                TextButton({ model.viewport(s.viewport.actual()) },modifier = Modifier.heightIn(min = 28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp,vertical = 2.dp)) { Text(stringResource(R.string.ui_actual_size),style = MaterialTheme.typography.labelMedium) }
                Box {
                    TextButton({ zoomMenu = true },Modifier.heightIn(min = 28.dp).testTag("paint-zoom-menu").semantics { contentDescription = "Zoom level: $zoom%" },
                        contentPadding = PaddingValues(horizontal = 8.dp,vertical = 2.dp)) {
                        Text("$zoom%",style = MaterialTheme.typography.labelMedium)
                        Icon(PaintIcon.Chevron.vector,null,Modifier.size(16.dp))
                    }
                    AppDropdownMenu(zoomMenu,{ zoomMenu = false }) {
                        listOf(10,25,50,100,200,300,400,500).forEach { percent ->
                            AppMenuItem({ Text("$percent%") },{ model.viewport(s.viewport.zoomed(percent / 100f)); zoomMenu = false },
                                trailingIcon = { if (zoom == percent) Icon(PaintIcon.Apply.vector,null) })
                        }
                    }
                }
                IconButton(onClick = { model.viewport(s.viewport.zoomed(s.viewport.scale - 0.1f)) },enabled = s.viewport.scale > 0.1f,modifier = Modifier.size(28.dp)) {
                    Icon(painterResource(R.drawable.ic_zoom_out),stringResource(R.string.ui_zoom_out),Modifier.size(18.dp))
                }
                Slider(state = zoomSlider,onValueChange = { model.viewport(s.viewport.zoomed(it)) },
                    interactionSource = zoomInteraction,
                    modifier = Modifier.width(160.dp).heightIn(min = 32.dp).testTag("paint-zoom-slider")
                        .semantics { contentDescription = "Zoom"; stateDescription = "$zoom%" },
                    thumb = {
                        Spacer(Modifier.size(16.dp).indication(zoomInteraction,ripple(bounded = false,radius = 20.dp))
                            .background(MaterialTheme.colorScheme.primary,CircleShape))
                    },
                    track = { slider ->
                        SliderDefaults.Track(sliderState = slider,modifier = Modifier.height(4.dp),
                            drawStopIndicator = null,drawTick = { _, _ -> },thumbTrackGapSize = 0.dp)
                    })
                IconButton(onClick = { model.viewport(s.viewport.zoomed(s.viewport.scale + 0.1f)) },enabled = s.viewport.scale < 5f,modifier = Modifier.size(28.dp)) {
                    Icon(painterResource(R.drawable.ic_zoom_in),stringResource(R.string.ui_zoom_in),Modifier.size(18.dp))
                }
            }
        }
    }
}

internal fun handlePaintKey(event: KeyEvent, s: PaintUiState, model: PaintViewModel): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape) return model.back()
    if (!event.isCtrlPressed) {
        if (s.preview?.tool == Tool.Text) return false
        if (event.key == Key.Enter && s.preview != null) { model.applyPreview(); return true }
        return false
    }
    when (event.key) {
        Key.N -> model.newImage(); Key.O -> model.open(); Key.S -> model.save(event.isShiftPressed)
        Key.W -> model.newImage(); Key.Z -> if (event.isShiftPressed) model.redo() else model.undo()
        Key.Y -> model.redo(); Key.A -> model.selectAll(); Key.C -> model.copy(); Key.X -> model.copy(true); Key.V -> model.paste()
        Key.Zero -> model.viewport(s.viewport.actual())
        Key.Equals, Key.Plus, Key.NumPadAdd -> model.viewport(s.viewport.zoomed(s.viewport.scale + 0.1f))
        Key.Minus, Key.NumPadSubtract -> model.viewport(s.viewport.zoomed(s.viewport.scale - 0.1f))
        Key.Enter -> model.applyPreview()
        else -> return false
    }
    return true
}
