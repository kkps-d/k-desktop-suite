@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.AppDropdownMenu
import com.kkps.kdesktopsuite.ui.AppMenuItem
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun WidthPicker(s: PaintUiState, model: PaintViewModel) {
    var open by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val available = !s.loading &&
        (s.options.tool.isShape || s.options.tool in listOf(Tool.Brush,Tool.Pencil,Tool.Eraser))
    val presets = when (s.options.tool) {
        Tool.Eraser -> listOf(4,8,16,24)
        Tool.Brush -> s.options.brushStyle.widthPresets
        else -> listOf(1,3,5,8)
    }
    val label = pluralStringResource(R.plurals.ui_choose_stroke_size,s.options.strokeWidth,s.options.strokeWidth)
    Box {
        TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text(label) } },state = rememberTooltipState()) {
            FilledTonalIconButton(onClick = { open = true },enabled = available && !s.busy,
                colors = paintTonalIconColors(available && s.busy && !LocalPaintBusyVisible.current),
                modifier = Modifier.size(64.dp,48.dp).testTag("paint-size-picker").semantics { contentDescription = label }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = LocalContentColor.current
                    Canvas(Modifier.size(24.dp)) {
                        listOf(1f,3f,5f).forEachIndexed { index,width ->
                            val y = (4+index*7).dp.toPx()
                            drawLine(color,Offset(2.dp.toPx(),y),Offset(22.dp.toPx(),y),width.dp.toPx())
                        }
                    }
                    Icon(PaintIcon.Chevron.vector,null,Modifier.size(16.dp))
                }
            }
        }
        AppDropdownMenu(open,{ open = false }) {
            Column(Modifier.width(256.dp).selectableGroup().onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { open = false; true } else false
            }) {
                Text(stringResource(R.string.ui_stroke_preview_zoom,(s.viewport.scale*100).roundToInt()),
                    Modifier.padding(12.dp),style = MaterialTheme.typography.labelMedium)
                presets.forEach { width ->
                    val selected = width == s.options.strokeWidth
                    val description = pluralStringResource(R.plurals.ui_stroke_size_description,width,width,(s.viewport.scale*100).roundToInt())
                    AppMenuItem(text = {
                        StrokeSizePreview(s.options,width,s.viewport.scale,Modifier.fillMaxWidth().testTag("paint-size-preview-$width"))
                    },onClick = { model.strokeWidth(width); open = false },
                        trailingIcon = { Box(Modifier.size(24.dp)) { if (selected) Icon(PaintIcon.Apply.vector,null) } },
                        modifier = (if (selected) Modifier.focusRequester(focus) else Modifier)
                            .focusProperties { canFocus = true }.testTag("paint-size-$width")
                            .semantics { contentDescription = description; this.selected = selected })
                }
                HorizontalDivider()
                AppMenuItem(text = { Text(stringResource(R.string.ui_custom_size_menu)) },onClick = {
                    open = false; model.dialog(PaintDialog.StrokeSize)
                },modifier = (if (s.options.strokeWidth !in presets) Modifier.focusRequester(focus) else Modifier)
                    .heightIn(min = 48.dp).focusProperties { canFocus = true }.testTag("paint-size-custom"))
            }
            LaunchedEffect(open) { if (open) { withFrameNanos { }; focus.requestFocus() } }
        }
    }
}

@Composable
internal fun StrokeSizeDialog(s: PaintUiState, model: PaintViewModel) {
    var text by rememberSaveable { mutableStateOf(s.options.strokeWidth.toString()) }
    var previewWidth by rememberSaveable { mutableIntStateOf(s.options.strokeWidth) }
    val width = text.toIntOrNull()?.takeIf { it in 1..100 }
    val enabled = width != null && !s.busy && !s.loading
    val focus = remember { FocusRequester() }
    fun apply() {
        if (enabled) { model.strokeWidth(requireNotNull(width)); model.dialog(null) }
    }
    AlertDialog(onDismissRequest = { model.dialog(null) },modifier = Modifier.testTag("paint-custom-size-dialog"),
        title = { Text(stringResource(R.string.ui_custom_size)) },text = {
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(text,onValueChange = {
                text = it.take(4)
                text.toIntOrNull()?.takeIf { value -> value in 1..100 }?.let { value -> previewWidth = value }
            },label = { Text(stringResource(R.string.ui_width_1_100_px)) },singleLine = true,isError = width == null,
                supportingText = { Text(stringResource(R.string.ui_stroke_size_range)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number,imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { apply() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("paint-custom-size-input"))
            Text(stringResource(R.string.ui_stroke_preview_zoom,(s.viewport.scale*100).roundToInt()),
                style = MaterialTheme.typography.labelMedium)
            StrokeSizePreview(s.options,previewWidth,s.viewport.scale,Modifier.fillMaxWidth().testTag("paint-custom-size-preview"))
        }
    },confirmButton = { TextButton(onClick = { apply() },enabled = enabled,
        modifier = Modifier.testTag("paint-custom-size-apply")) { Text(stringResource(R.string.ui_apply_width)) } },
        dismissButton = { TextButton(onClick = { model.dialog(null) },modifier = Modifier.testTag("paint-custom-size-cancel")) {
            Text(stringResource(R.string.ui_cancel))
        } })
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Canvas zoom is screen pixels per image pixel; display density must not scale it again. */
@Composable
internal fun StrokeSizePreview(options: ToolOptions, width: Int, zoom: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val length = with(density) { ceil(128.dp.toPx()/zoom).toInt() }
    val image = remember(options.tool,options.brushStyle,width,length) {
        val raster = Raster.blank(length+width+4,width+4)
        val start = Point(width/2f+1,(width+2)/2f)
        val end = Point(start.x+length,start.y)
        val points = listOf(start,end)
        val rendered = when (options.tool) {
            Tool.Brush -> raster.brushStroke(points,BLACK,width.toFloat(),options.brushStyle,42)
            Tool.Pencil, Tool.Eraser -> raster.stroke(points,BLACK,width.toFloat(),true,null)
            else -> raster.render(EditPreview.between(Tool.Line,start,end),options.copy(width = width,foreground = BLACK))
        }
        rendered.bitmap().asImageBitmap()
    }
    val height = with(density) { ((width+4)*zoom).toDp() } + 16.dp
    Canvas(modifier.heightIn(min = 48.dp).height(height).clipToBounds().background(Color.White)) {
        withTransform({
            translate(floor((size.width-image.width*zoom)/2),floor((size.height-image.height*zoom)/2))
            scale(zoom,zoom,pivot = Offset.Zero)
        }) {
            drawImage(image,filterQuality = if (zoom >= 1f) FilterQuality.None else FilterQuality.Low)
        }
    }
}
