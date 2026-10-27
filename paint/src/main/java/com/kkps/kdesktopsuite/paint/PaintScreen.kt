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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
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
internal fun PaintButton(icon: ImageVector, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, busy: Boolean = false, action: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = action, enabled = enabled && !busy, modifier = modifier.size(48.dp)
            .semantics { this.selected = selected; if (selected) stateDescription = "Selected" }) {
            Box(Modifier.size(40.dp).background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(12.dp)),contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (!enabled || (busy && LocalPaintBusyVisible.current)) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
            }
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
                val portrait = maxHeight > maxWidth
                Column(Modifier.fillMaxSize().onKeyEvent { event -> if (state.settingsOpen || debugOpen) false else handlePaintKey(event,state,model) }) {
                    if (debugOpen) PaintDebugScreen { debugOpen = false }
                    else if (state.settingsOpen) PaintSettingsScreen(state,model) else {
                        if (!state.focusMode) {
                            PaintHeader(state,model,width.value,showBusy)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PaintToolbars(state,model,width.value)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        } else Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.End) {
                            PaintOperationProgress(state,model,showBusy)
                            TextButton(onClick = model::toggleFocus) { Text(stringResource(R.string.ui_exit_focus_mode)) }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            PaintCanvas(state,model,onCursorChange = { cursor = it },onSelectionChange = { selectionDrag = it },
                                onCanvasResizeChange = { canvasResize = it })
                        }
                        if (state.showInformation && !state.focusMode) PaintStatus(state,model,cursor,selectionDrag,canvasResize,portrait)
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
            if (!compact) PaintHeaderActions(s,model,showBusy)
            Row(Modifier.weight(1f).padding(horizontal = 8.dp),verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.dirty || s.preview?.hasContent == true) Box(Modifier.size(6.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant,CircleShape).testTag("paint-unsaved-indicator")
                    .semantics { contentDescription = "Unsaved changes" })
                Text(s.title,modifier = Modifier.testTag("paint-document-title"),maxLines = 1,
                    overflow = TextOverflow.Ellipsis,style = MaterialTheme.typography.titleSmall)
            }
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
        VerticalDivider(Modifier.padding(horizontal = 8.dp).height(20.dp))
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
                Box {
                    var recentOpen by remember { mutableStateOf(false) }
                    AppMenuItem({ Text(stringResource(R.string.ui_recent)) },{ recentOpen = true },
                        enabled = !s.busy,trailingIcon = { Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›") })
                    AppDropdownMenu(recentOpen,{ recentOpen = false },anchorPosition = MenuAnchorPosition.End) {
                        s.preferences.recent.forEachIndexed { index, uri ->
                            AppMenuItem({ Text("${index+1}: ${android.net.Uri.decode(uri.substringAfterLast('/')).takeLast(44)}",maxLines = 2) },
                                { recentOpen = false; close { model.openRecent(uri) } },enabled = !s.busy,
                                modifier = Modifier.onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                                        recentOpen = false
                                        true
                                    } else false
                                })
                        }
                    }
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
            AppMenuItem({ Text(stringResource(if (s.ribbonCollapsed) R.string.ui_show_toolbar else R.string.ui_hide_toolbar)) },{ model.toggleRibbon(); open = false })
            AppMenuItem({ Text(stringResource(R.string.ui_status_bar)) },{ model.toggleInformation(); open = false },
                trailingIcon = { Text(if (s.showInformation) "✓" else "",Modifier.width(24.dp)) },
                checked = s.showInformation)
            AppMenuItem({ Text(stringResource(R.string.ui_focus_mode)) },{ model.toggleFocus(); open = false })
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PaintStatus(s: PaintUiState, model: PaintViewModel, cursor: Point?, selectionDrag: PixelRect?, canvasResize: PixelRect?, portrait: Boolean) {
    val fontScale = LocalDensity.current.fontScale
    val fieldWidth = 144.dp * fontScale
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
            val stacked = portrait || maxWidth < fieldWidth * 3 + 400.dp * fontScale
            val informationWidth = if (stacked) maxWidth else fieldWidth * 3 + 18.dp
            val zoomWidth = if (stacked) maxWidth else maxWidth - informationWidth
            FlowRow(Modifier.fillMaxWidth(),maxItemsInEachRow = if (stacked) 1 else 2) {
                Row(Modifier.width(informationWidth).horizontalScroll(rememberScrollState())
                    .widthIn(min = informationWidth.coerceAtLeast(360.dp * fontScale)).heightIn(min = 32.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(dimensions,Modifier.weight(1f).padding(horizontal = 12.dp).testTag("paint-selection-dimensions")
                        .semantics { contentDescription = "Selection dimensions: $dimensions" },color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,maxLines = 1)
                    VerticalDivider(Modifier.height(20.dp))
                    Text(position,Modifier.weight(1f).padding(horizontal = 12.dp).testTag("paint-cursor-position")
                        .semantics { contentDescription = "Cursor position: $position" },color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,maxLines = 1)
                    VerticalDivider(Modifier.height(20.dp))
                    TextButton(onClick = { model.dialog(PaintDialog.CanvasSize) },enabled = !s.busy && !s.loading,
                        modifier = Modifier.weight(1f).heightIn(min = 28.dp).testTag("paint-canvas-dimensions")
                            .semantics { contentDescription = "Resize canvas" },contentPadding = PaddingValues(horizontal = 12.dp,vertical = 2.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${canvasResize?.width ?: s.raster.width} x ${canvasResize?.height ?: s.raster.height} px",
                                Modifier.weight(1f,fill = false),style = MaterialTheme.typography.labelMedium,maxLines = 1)
                            Icon(painterResource(R.drawable.ic_more_horiz),null,Modifier.size(16.dp))
                        }
                    }
                }
                Row(Modifier.width(zoomWidth).horizontalScroll(rememberScrollState())
                    .widthIn(min = zoomWidth.coerceAtLeast(360.dp * fontScale)).heightIn(min = 32.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.End) {
                    if (!stacked) Spacer(Modifier.weight(1f))
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
                        AppDropdownMenu(zoomMenu,{ zoomMenu = false },anchorPosition = MenuAnchorPosition.Above) {
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
                        modifier = (if (stacked) Modifier.weight(1f) else Modifier.width(160.dp)).heightIn(min = 32.dp).testTag("paint-zoom-slider")
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
