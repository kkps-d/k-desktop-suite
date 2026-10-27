package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kkps.kdesktopsuite.ui.AppAboutDialog

@Composable
internal fun PaintDialogs(s: PaintUiState, model: PaintViewModel, onOpenDebug: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    when (s.dialog) {
        null -> Unit
        PaintDialog.ReopenLastEdit -> AlertDialog(onDismissRequest = { model.resolveStartup(false) },
            title = { Text(stringResource(R.string.ui_reopen_last_edit_question)) },
            text = { Text(stringResource(R.string.ui_reopen_last_edit_description)) },
            confirmButton = { TextButton(onClick = { model.resolveStartup(true) }) { Text(stringResource(R.string.ui_reopen)) } },
            dismissButton = { TextButton(onClick = { model.resolveStartup(false) }) { Text(stringResource(R.string.ui_start_new_image)) } })
        PaintDialog.Unsaved -> AlertDialog(onDismissRequest = model::cancelReplace,
            title = { Text("Save changes to ${s.title}?") },text = { Text(stringResource(R.string.ui_your_image_has_unsaved_changes_including_any_current_preview)) },
            confirmButton = { TextButton(onClick = model::saveThenContinue) { Text(stringResource(R.string.ui_save)) } },
            dismissButton = { Row { TextButton(onClick = model::discard) { Text(stringResource(R.string.ui_discard)) }; TextButton(onClick = model::cancelReplace) { Text(stringResource(R.string.ui_cancel)) } } })
        PaintDialog.SaveFormat -> AlertDialog(onDismissRequest = model::cancelSave,
            title = { Text(stringResource(R.string.ui_save_image_as)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.ui_png_preserves_pixels_jpeg_uses_lossy_compression))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { model.saveFormat("image/png") },modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.ui_png))
                        }
                        Button(onClick = { model.saveFormat("image/jpeg") },modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.ui_jpeg))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = model::cancelSave) { Text(stringResource(R.string.ui_cancel)) } })
        PaintDialog.JpegWarning -> AlertDialog(onDismissRequest = model::cancelSave,
            title = { Text(stringResource(R.string.ui_save_a_jpeg_image)) },text = { Text("JPEG compression can change colors and sharp edges. The current full-quality raster stays in your draft. Quality: ${s.preferences.jpegQuality}%.") },
            confirmButton = { TextButton(onClick = model::continueSave) { Text(stringResource(R.string.ui_continue)) } },
            dismissButton = { TextButton(onClick = model::cancelSave) { Text(stringResource(R.string.ui_cancel)) } })
        PaintDialog.Conflict -> AlertDialog(onDismissRequest = model::cancelSave,
            title = { Text(stringResource(R.string.ui_the_saved_image_changed)) },text = { Text(stringResource(R.string.ui_another_app_changed_the_file_overwrite_its_current_contents_or_save_a_separat)) },
            confirmButton = { TextButton(onClick = model::overwrite) { Text(stringResource(R.string.ui_overwrite)) } },
            dismissButton = { Row { TextButton(onClick = model::conflictSaveAs) { Text(stringResource(R.string.ui_save_as)) }; TextButton(onClick = model::cancelSave) { Text(stringResource(R.string.ui_cancel)) } } })
        PaintDialog.Oversized -> AlertDialog(onDismissRequest = { model.dialog(null) },
            title = { Text(stringResource(R.string.ui_inserted_image_is_larger_than_the_canvas)) },text = { Text(stringResource(R.string.ui_expand_the_canvas_to_preserve_the_entire_image_or_keep_the_canvas_and_clip_th)) },
            confirmButton = { TextButton(onClick = { model.oversized(true) }) { Text(stringResource(R.string.ui_expand_canvas)) } },
            dismissButton = { Row { TextButton(onClick = { model.oversized(false) }) { Text(stringResource(R.string.ui_keep_canvas)) }; TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_cancel)) } } })
        PaintDialog.Colors -> ColorsDialog(s,model)
        PaintDialog.Rotation -> RotationDialog(s,model)
        PaintDialog.CanvasSize -> CanvasSizeDialog(s,model)
        PaintDialog.Transform -> TransformDialog(s,model)
        PaintDialog.StrokeSize -> StrokeSizeDialog(s,model)
        PaintDialog.About -> AppAboutDialog(title = stringResource(R.string.ui_about_kd_paint),
            version = stringResource(R.string.ui_about_version,BuildConfig.VERSION_NAME),developer = stringResource(R.string.ui_about_developer),
            sourceLabel = stringResource(R.string.ui_source_code),dismissLabel = stringResource(R.string.ui_dismiss),
            icon = {
                Image(painterResource(R.drawable.ic_paint),stringResource(R.string.ui_open_debug),
                    Modifier.size(48.dp).testTag("about-paint-logo").clickable(
                        interactionSource = remember { MutableInteractionSource() },indication = null,onClick = onOpenDebug))
            },
            onSourceClick = { runCatching { uriHandler.openUri("https://github.com/kkps-d/k-desktop-suite") }
                .onFailure { model.message("No browser is available to open the source repository.") } },
            onDismiss = { model.dialog(null) })
        PaintDialog.Properties -> AlertDialog(onDismissRequest = { model.dialog(null) },title = { Text(stringResource(R.string.ui_image_properties)) },
            text = { Text("${s.title}\n\n${s.raster.width} × ${s.raster.height} pixels\nOpaque sRGB, 32-bit working raster\n${if (s.mime == "image/jpeg") "JPEG" else "PNG"}\n\n${if (s.uri == null) "Not saved to a file" else s.uri}\n\n${if (s.dirty) "Unsaved changes" else "Saved revision"}") },
            confirmButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_close)) } })
    }
}

@Composable
private fun CanvasSizeDialog(s: PaintUiState, model: PaintViewModel) {
    var width by rememberSaveable { mutableStateOf(s.raster.width.toString()) }
    var height by rememberSaveable { mutableStateOf(s.raster.height.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { model.dialog(null) },title = { Text(stringResource(R.string.ui_canvas_size)) },text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            CanvasSizeFields(width,height,{ width = it; error = null },{ height = it; error = null })
            error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            listOf(
                "4:3" to listOf(640 to 480,800 to 600,1024 to 768,1600 to 1200),
                "16:9" to listOf(1280 to 720,1920 to 1080,2560 to 1440,3840 to 2160),
                "1:1" to listOf(256 to 256,512 to 512,1024 to 1024,2048 to 2048)
            ).forEach { (ratio,sizes) ->
                Text(ratio,Modifier.padding(top = 12.dp),style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizes.forEach { (w,h) ->
                        FilterChip(
                            selected = width.toIntOrNull() == w && height.toIntOrNull() == h,
                            onClick = { width = w.toString(); height = h.toString(); error = null },
                            label = { Text("$w × $h") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    },confirmButton = { TextButton(onClick = {
        try {
            val w = requireNotNull(width.toIntOrNull()) { "Enter a valid width in pixels." }
            val h = requireNotNull(height.toIntOrNull()) { "Enter a valid height in pixels." }
            checkImageSize(w,h)
            model.resizeCanvas(w,h)
            model.dialog(null)
        } catch (invalid: IllegalArgumentException) { error = invalid.message }
    },enabled = !s.busy && !s.loading) { Text(stringResource(R.string.ui_resize)) } },
        dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_cancel)) } })
}

@Composable
private fun ColorsDialog(s: PaintUiState, model: PaintViewModel) {
    val background = s.colorBackground
    var hex by remember { mutableStateOf("%06X".format((if (s.colorBackground) s.options.background else s.options.foreground) and 0xFFFFFF)) }
    var savingCustomColor by remember { mutableStateOf(false) }
    val parsed = hex.takeIf { it.length == 6 }?.toLongOrNull(16)?.toInt()?.or(BLACK)
    fun choose(color: Int) { hex = "%06X".format(color and 0xFFFFFF) }
    AlertDialog(onDismissRequest = { model.dialog(null) },modifier = Modifier.testTag("paint-colors-dialog"),title = { Text(if (background) "Edit secondary color" else "Edit primary color") },text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(Color(parsed ?: WHITE)).border(1.dp,MaterialTheme.colorScheme.outline))
                OutlinedTextField(hex,{ hex = it.filter { c -> c.isDigit() || c.uppercaseChar() in 'A'..'F' }.take(6).uppercase() },
                    label = { Text(stringResource(R.string.ui_rgb_hex)) },prefix = { Text(stringResource(R.string.ui_)) },singleLine = true,modifier = Modifier.padding(start = 12.dp).weight(1f),isError = parsed == null)
            }
            listOf("Red" to 16,"Green" to 8,"Blue" to 0).forEach { (label,shift) ->
                val component = (parsed ?: WHITE) ushr shift and 255
                var entry by remember(component) { mutableStateOf(component.toString()) }
                OutlinedTextField(entry,{ value ->
                    entry = value.filter(Char::isDigit).take(3)
                    entry.toIntOrNull()?.takeIf { it in 0..255 }?.let { number ->
                        choose(((parsed ?: WHITE) and (255 shl shift).inv()) or (number shl shift))
                    }
                },label = { Text(label) },singleLine = true,isError = entry.toIntOrNull() !in 0..255,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Slider(component.toFloat(),{ value ->
                    val current = parsed ?: WHITE
                    choose((current and (255 shl shift).inv()) or (value.roundToInt() shl shift))
                },valueRange = 0f..255f,modifier = Modifier.semantics { contentDescription = label })
            }
            Text(stringResource(R.string.ui_preset_colors),style = MaterialTheme.typography.labelLarge)
            Column(Modifier.horizontalScroll(rememberScrollState()).testTag("paint-dialog-preset-colors")) {
                presetColors.chunked(10).forEach { row -> Row { row.forEach { color -> ColorSwatch(color,"Choose color",parsed == color) { choose(color) } } } }
            }
            Text(stringResource(R.string.ui_custom_colors),style = MaterialTheme.typography.labelLarge)
            Column(Modifier.horizontalScroll(rememberScrollState()).testTag("paint-dialog-custom-colors")) {
                s.preferences.customColors.chunked(10).forEachIndexed { rowIndex,row -> Row { row.forEachIndexed { index,color ->
                    ColorSwatch(color,"Custom color ${rowIndex*10+index+1}",enabled = !savingCustomColor || parsed != null,saving = savingCustomColor) {
                        if (savingCustomColor) {
                            parsed?.let { chosen ->
                                model.preferences { it.copy(customColors = it.customColors.toMutableList().also { colors -> colors[rowIndex*10+index] = chosen }) }
                            }
                            savingCustomColor = false
                        } else choose(color)
                    }
                } } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (savingCustomColor) {
                    Text(stringResource(R.string.ui_select_a_slot_above),Modifier.weight(1f),style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { savingCustomColor = false }) { Text(stringResource(R.string.ui_cancel)) }
                } else TextButton(onClick = { savingCustomColor = true },enabled = parsed != null) {
                    Text(stringResource(R.string.ui_save_custom_color))
                }
            }
        }
    },confirmButton = { TextButton(onClick = { parsed?.let { color -> model.options { if (background) it.copy(background = color) else it.copy(foreground = color) }; model.dialog(null) } },enabled = parsed != null) { Text(stringResource(R.string.ui_apply)) } },
        dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_cancel)) } })
}

@Composable
private fun NumericField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value,change,label = { Text(label) },singleLine = true,modifier = modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun RotationDialog(s: PaintUiState, model: PaintViewModel) {
    var angle by rememberSaveable { mutableStateOf((s.preview?.rotationDegrees ?: 0.0).toString()) }
    val degrees = angle.toDoubleOrNull()?.takeIf { it.isFinite() }
    val focus = remember { FocusRequester() }
    AlertDialog(onDismissRequest = { model.dialog(null) },title = { Text("Custom rotate") },text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val windowFocused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(windowFocused) { if (windowFocused) focus.requestFocus() }
            NumericField(stringResource(R.string.ui_rotation_degrees),angle,{ angle = it },Modifier.focusRequester(focus))
        }
    },confirmButton = {
        TextButton(onClick = { degrees?.let {
            if (s.selection != null || s.preview?.canTransform == true) model.rotateSelection(it-(s.preview?.rotationDegrees ?: 0.0))
            else model.rotate(it)
            model.dialog(null)
        } },enabled = degrees != null && !s.busy) { Text("Apply") }
    },dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text("Cancel") } })
}
