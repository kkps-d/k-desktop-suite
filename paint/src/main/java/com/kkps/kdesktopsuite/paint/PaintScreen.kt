@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kkps.kdesktopsuite.paint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun PaintButton(icon: PaintIcon, label: String, enabled: Boolean = true, selected: Boolean = false, action: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(48.dp)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
            .semantics { if (selected) stateDescription = "Selected" }) {
            Icon(icon.vector, label, tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PaintScreen(state: PaintUiState, model: PaintViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var cursor by remember { mutableStateOf<Point?>(Point(0f,0f)) }
    var selectionDrag by remember { mutableStateOf<PixelRect?>(null) }
    var canvasResize by remember { mutableStateOf<PixelRect?>(null) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.message(null) } }
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(!imeVisible && (state.dialog != null || state.preview != null || state.selection != null || state.focusMode)) { model.back() }
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
            val width = maxWidth
            val short = maxHeight < 480.dp
            Column(Modifier.fillMaxSize().onKeyEvent { event -> handlePaintKey(event,state,model) }) {
                if (!state.focusMode) {
                    PaintHeader(state,model,width.value)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (!state.ribbonCollapsed) PaintRibbon(state,model,width.value)
                    else TextButton(onClick = model::toggleRibbon) { Text(stringResource(R.string.ui_show_drawing_tools)) }
                    if (state.preview != null || state.selection != null) SelectionActions(state,model)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                } else Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = model::toggleFocus) { Text(stringResource(R.string.ui_exit_focus_mode)) }
                }
                if (short && !state.ribbonCollapsed && !state.focusMode) {
                    TextButton(onClick = model::toggleRibbon,modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ui_collapse_ribbon)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PaintCanvas(state,model,onCursorChange = { cursor = it },onSelectionChange = { selectionDrag = it },
                        onCanvasResizeChange = { canvasResize = it })
                    if (state.loading || state.busy || state.saving) Row(Modifier.align(Alignment.TopEnd).padding(8.dp),verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp),strokeWidth = 2.dp)
                        if (state.busy && !state.loading) PaintButton(PaintIcon.Cancel,"Cancel operation",action = model::cancelOperation)
                    }
                }
                if (state.showInformation && !state.focusMode) PaintStatus(state,model,cursor,selectionDrag,canvasResize)
            }
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter))
            PaintDialogs(state,model)
        }
    }
}

@Composable
private fun PaintHeader(s: PaintUiState, model: PaintViewModel, width: Float) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp).testTag("paint-header"),verticalAlignment = Alignment.CenterVertically) {
        if (width >= 700) Icon(PaintIcon.Brush.vector,"KD Paint",Modifier.padding(8.dp),tint = MaterialTheme.colorScheme.primary)
        FileMenu(s,model)
        ViewMenu(s,model)
        Text(s.title + if (s.dirty || s.preview?.hasContent == true) " •" else "",modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            maxLines = 1,overflow = TextOverflow.Ellipsis,style = MaterialTheme.typography.titleSmall)
        PaintButton(PaintIcon.Save,"Save (Ctrl+S)",!s.busy && !s.loading) { model.save() }
        PaintButton(PaintIcon.Undo,"Undo (Ctrl+Z)",s.canUndo || s.preview != null,action = model::undo)
        if (width >= 450) PaintButton(PaintIcon.Redo,"Redo (Ctrl+Shift+Z)",s.canRedo,action = model::redo)
        var more by remember { mutableStateOf(false) }
        Box {
            PaintButton(PaintIcon.More,"More commands") { more = true }
            DropdownMenu(more,{ more = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.ui_settings)) },{ more = false; model.dialog(PaintDialog.Settings) })
                DropdownMenuItem({ Text(stringResource(R.string.ui_about_kd_paint)) },{ more = false; model.dialog(PaintDialog.About) })
                if (width < 450) DropdownMenuItem({ Text(stringResource(R.string.ui_redo)) },{ more = false; model.redo() },enabled = s.canRedo)
            }
        }
    }
}

@Composable
private fun FileMenu(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text(stringResource(R.string.ui_file)) }
        DropdownMenu(open,{ open = false }) {
            fun close(action: () -> Unit) { open = false; action() }
            DropdownMenuItem({ Text(stringResource(R.string.ui_new_image_ctrl_n)) },{ close(model::newImage) },enabled = !s.busy)
            DropdownMenuItem({ Text(stringResource(R.string.ui_open_image_ctrl_o)) },{ close(model::open) },enabled = !s.busy)
            DropdownMenuItem({ Text(stringResource(R.string.ui_save_ctrl_s)) },{ close { model.save() } },enabled = !s.busy)
            DropdownMenuItem({ Text(stringResource(R.string.ui_save_as_ctrl_shift_s)) },{ close { model.save(true) } },enabled = !s.busy)
            if (s.preferences.recent.isNotEmpty()) {
                HorizontalDivider()
                s.preferences.recent.forEachIndexed { index, uri ->
                    DropdownMenuItem({ Text("Recent ${index+1}: ${android.net.Uri.decode(uri.substringAfterLast('/')).takeLast(44)}",maxLines = 2) },
                        { close { model.openRecent(uri) } })
                }
                HorizontalDivider()
            }
            DropdownMenuItem({ Text(stringResource(R.string.ui_insert_image)) },{ close(model::insertPicker) },enabled = !s.busy)
            DropdownMenuItem({ Text(stringResource(R.string.ui_share_png)) },{ close(model::share) },enabled = !s.busy)
            DropdownMenuItem({ Text(stringResource(R.string.ui_image_properties)) },{ close { model.dialog(PaintDialog.Properties) } })
            DropdownMenuItem({ Text(stringResource(R.string.ui_close_image_ctrl_w)) },{ close(model::newImage) },enabled = !s.busy)
        }
    }
}

@Composable
private fun ViewMenu(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text(stringResource(R.string.ui_view)) }
        DropdownMenu(open,{ open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.ui_zoom_in_ctrl)) },{ model.viewport(s.viewport.zoomed(s.viewport.scale + 0.1f)); open = false })
            DropdownMenuItem({ Text(stringResource(R.string.ui_zoom_out_ctrl)) },{ model.viewport(s.viewport.zoomed(s.viewport.scale - 0.1f)); open = false })
            DropdownMenuItem({ Text(stringResource(R.string.ui_actual_size_ctrl_0)) },{ model.viewport(s.viewport.actual()); open = false })
            DropdownMenuItem({ Text(stringResource(R.string.ui_fit_image)) },{ model.viewport(s.viewport.fitted()); open = false })
            DropdownMenuItem({ Text(stringResource(R.string.ui_pan_tool)) },{ model.tool(Tool.Pan); open = false })
            DropdownMenuItem({ Text(if (s.ribbonCollapsed) "Show ribbon" else "Collapse ribbon") },{ model.toggleRibbon(); open = false })
            DropdownMenuItem({ Text(stringResource(R.string.ui_status_bar)) },{ model.toggleInformation(); open = false },
                trailingIcon = { Text(if (s.showInformation) "✓" else "",Modifier.width(24.dp)) },
                modifier = Modifier.semantics { role = Role.Checkbox; toggleableState = if (s.showInformation)
                    androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off })
            DropdownMenuItem({ Text(stringResource(R.string.ui_focus_mode)) },{ model.toggleFocus(); open = false })
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
    val tools = listOf(Tool.Pencil,Tool.Fill,Tool.Text,Tool.Eraser,Tool.Eyedropper,Tool.Zoom)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).horizontalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("classic-ribbon"),verticalAlignment = Alignment.Top) {
        if (width < 760) {
            RibbonGroup("Clipboard") { ClipboardMenu(s,model) }
            RibbonGroup("Image") { ImageMenu(s,model) }
            RibbonGroup("Tools") { ToolMenu(s,model,Tool.entries) }
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
                }
            }
            RibbonGroup("Tools") {
                Column { tools.chunked(3).forEach { row -> Row { row.forEach { tool ->
                    PaintButton(tool.icon,tool.label,selected = s.options.tool == tool,enabled = !s.busy) { model.tool(tool) }
                } } } }
            }
            RibbonGroup("Brushes") {
                PaintButton(PaintIcon.Brush,"Solid brush",selected = s.options.tool == Tool.Brush,enabled = !s.busy) { model.tool(Tool.Brush) }
                Text(stringResource(R.string.ui_solid),style = MaterialTheme.typography.labelSmall)
            }
            RibbonGroup("Shapes") {
                val shapes = listOf(Tool.Line,Tool.Rectangle,Tool.Ellipse)
                if (width < 1120) ToolMenu(s,model,shapes) else Row { shapes.forEach { tool ->
                    PaintButton(tool.icon,tool.label,selected = s.options.tool == tool,enabled = !s.busy) { model.tool(tool) }
                } }
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
        DropdownMenu(open,{ open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.ui_paste)) },{ open = false; model.paste() })
            DropdownMenuItem({ Text(stringResource(R.string.ui_copy)) },{ open = false; model.copy() },enabled = s.selection != null || s.preview?.payload != null)
            DropdownMenuItem({ Text(stringResource(R.string.ui_cut)) },{ open = false; model.copy(true) },enabled = s.selection != null || s.preview?.payload != null)
            DropdownMenuItem({ Text(stringResource(R.string.ui_insert_image)) },{ open = false; model.insertPicker() })
        }
    }
}

@Composable
private fun ImageMenu(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        PaintButton(PaintIcon.Select,"Selection and image commands",selected = s.options.tool == Tool.Select) { open = true }
        DropdownMenu(open,{ open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.ui_rectangular_selection)) },{ open = false; model.tool(Tool.Select) })
            DropdownMenuItem({ Text(stringResource(R.string.ui_select_all)) },{ open = false; model.selectAll() })
            DropdownMenuItem({ Text(stringResource(R.string.ui_clear_selection)) },{ open = false; model.clearSelection() },enabled = s.selection != null)
            DropdownMenuItem({ Text(stringResource(R.string.ui_crop_to_selection)) },{ open = false; model.crop() },enabled = s.selection != null || s.preview?.payload != null)
            DropdownMenuItem({ Text(stringResource(R.string.ui_move_resize_selection)) },{ open = false; model.dialog(PaintDialog.Geometry) },enabled = s.selection != null || s.preview != null)
        }
    }
}

@Composable
private fun ToolMenu(s: PaintUiState, model: PaintViewModel, tools: List<Tool>) {
    var open by remember { mutableStateOf(false) }
    Box {
        PaintButton((if (s.options.tool in tools) s.options.tool else tools.first()).icon,"Choose drawing tool") { open = true }
        DropdownMenu(open,{ open = false }) { tools.forEach { tool ->
            DropdownMenuItem({ Text(tool.label) },{ open = false; model.tool(tool) },leadingIcon = { Icon(tool.icon.vector,null) })
        } }
    }
}

@Composable
private fun WidthPicker(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    var text by remember(s.options.strokeWidth) { mutableStateOf(s.options.strokeWidth.toString()) }
    val enabled = s.options.tool in listOf(Tool.Brush,Tool.Pencil,Tool.Eraser,Tool.Line,Tool.Rectangle,Tool.Ellipse)
    Box {
        TextButton({ open = true },enabled = enabled) { Text("${s.options.strokeWidth} px") }
        DropdownMenu(open,{ open = false }) {
            (if (s.options.tool == Tool.Eraser) listOf(4,8,16,24) else listOf(1,3,5,8)).forEach { value ->
                DropdownMenuItem({ Text("$value px") },{ model.options { if (it.tool == Tool.Eraser) it.copy(eraserWidth = value) else it.copy(width = value) }; open = false })
            }
            OutlinedTextField(text,{ text = it.filter(Char::isDigit).take(3) },label = { Text(stringResource(R.string.ui_width_1_100_px)) },modifier = Modifier.width(180.dp).padding(8.dp),singleLine = true)
            TextButton({ text.toIntOrNull()?.takeIf { it in 1..100 }?.let { value ->
                model.options { if (it.tool == Tool.Eraser) it.copy(eraserWidth = value) else it.copy(width = value) }; open = false
            } },enabled = text.toIntOrNull() in 1..100) { Text(stringResource(R.string.ui_apply_width)) }
        }
    }
}

@Composable
private fun ShapeStyles(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text(stringResource(R.string.ui_style)) }
        DropdownMenu(open,{ open = false }) {
            DropdownMenuItem({ Text(if (s.options.outline) "✓ Outline" else "Outline") },{ model.options { it.copy(outline = !it.outline) } })
            DropdownMenuItem({ Text(if (s.options.fill) "✓ Fill" else "Fill") },{ model.options { it.copy(fill = !it.fill) } })
            DropdownMenuItem({ Text(if (s.options.constrain) "✓ Constrain proportions" else "Constrain proportions") },{ model.options { it.copy(constrain = !it.constrain) } })
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
            PaintButton(PaintIcon.Apply,"Apply preview",!s.busy) { model.applyPreview() }
            PaintButton(PaintIcon.Cancel,"Cancel preview",!s.busy,action = model::cancelPreview)
        } else TextButton(onClick = model::clearSelection) { Text(stringResource(R.string.ui_clear_selection)) }
        TextButton(onClick = { model.dialog(PaintDialog.Geometry) }) { Text(stringResource(R.string.ui_move_resize)) }
        if (s.selection != null || s.preview?.payload != null) {
            PaintButton(PaintIcon.Copy,"Copy selection") { model.copy() }
            PaintButton(PaintIcon.Cut,"Cut selection") { model.copy(true) }
            TextButton(onClick = model::deleteSelection) { Text(stringResource(R.string.ui_delete_pixels)) }
        }
        if (s.preview?.tool == Tool.Text) TextButton(onClick = { model.dialog(PaintDialog.Geometry) }) { Text(stringResource(R.string.ui_text_options)) }
        if (s.preview?.tool in listOf(Tool.Line,Tool.Rectangle,Tool.Ellipse)) ShapeStyles(s,model)
        s.preview?.bounds?.let { bounds ->
            if (bounds.x < 0 || bounds.y < 0 || bounds.right > s.raster.width || bounds.bottom > s.raster.height)
                Text("Preview extends beyond the image; Apply clips the result.",color = MaterialTheme.colorScheme.error,modifier = Modifier.padding(8.dp))
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
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).testTag("paint-status-bar")) {
        Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth).heightIn(min = 48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(position,Modifier.width(fieldWidth).padding(horizontal = 12.dp).testTag("paint-cursor-position")
                .semantics { contentDescription = "Cursor position: $position" },style = MaterialTheme.typography.labelMedium,maxLines = 1)
            VerticalDivider(Modifier.height(20.dp))
            Text(dimensions,Modifier.width(fieldWidth).padding(horizontal = 12.dp).testTag("paint-selection-dimensions")
                .semantics { contentDescription = "Selection dimensions: $dimensions" },style = MaterialTheme.typography.labelMedium,maxLines = 1)
            VerticalDivider(Modifier.height(20.dp))
            TextButton(onClick = { model.dialog(PaintDialog.CanvasSize) },enabled = !s.busy && !s.loading,
                modifier = Modifier.width(fieldWidth).testTag("paint-canvas-dimensions")
                    .semantics { contentDescription = "Resize canvas" },contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("${canvasResize?.width ?: s.raster.width} x ${canvasResize?.height ?: s.raster.height} px",
                    Modifier.fillMaxWidth(),style = MaterialTheme.typography.labelMedium,maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            TextButton({ model.viewport(s.viewport.fitted()) }) { Text(stringResource(R.string.ui_fit)) }
            TextButton({ model.viewport(s.viewport.actual()) }) { Text(stringResource(R.string.ui_actual_size)) }
            Box {
                TextButton({ zoomMenu = true },Modifier.testTag("paint-zoom-menu").semantics { contentDescription = "Zoom level: $zoom%" }) {
                    Text("$zoom%")
                    Icon(PaintIcon.Chevron.vector,null,Modifier.size(18.dp))
                }
                DropdownMenu(zoomMenu,{ zoomMenu = false }) {
                    listOf(10,25,50,100,200,300,400,500).forEach { percent ->
                        DropdownMenuItem({ Text("$percent%") },{ model.viewport(s.viewport.zoomed(percent / 100f)); zoomMenu = false },
                            trailingIcon = { if (zoom == percent) Icon(PaintIcon.Apply.vector,null) })
                    }
                }
            }
            IconButton(onClick = { model.viewport(s.viewport.zoomed(s.viewport.scale - 0.1f)) },enabled = s.viewport.scale > 0.1f) {
                Icon(painterResource(R.drawable.ic_zoom_out),stringResource(R.string.ui_zoom_out))
            }
            Slider(state = zoomSlider,onValueChange = { model.viewport(s.viewport.zoomed(it)) },
                interactionSource = zoomInteraction,
                modifier = Modifier.width(160.dp).heightIn(min = 48.dp).testTag("paint-zoom-slider")
                    .semantics { contentDescription = "Zoom"; stateDescription = "$zoom%" },
                thumb = {
                    Spacer(Modifier.size(16.dp).indication(zoomInteraction,ripple(bounded = false,radius = 20.dp))
                        .background(MaterialTheme.colorScheme.primary,CircleShape))
                },
                track = { slider ->
                    SliderDefaults.Track(sliderState = slider,modifier = Modifier.height(4.dp),
                        drawStopIndicator = null,drawTick = { _, _ -> },thumbTrackGapSize = 0.dp)
                })
            IconButton(onClick = { model.viewport(s.viewport.zoomed(s.viewport.scale + 0.1f)) },enabled = s.viewport.scale < 5f) {
                Icon(painterResource(R.drawable.ic_zoom_in),stringResource(R.string.ui_zoom_in))
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
