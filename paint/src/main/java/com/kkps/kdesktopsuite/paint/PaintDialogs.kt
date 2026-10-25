package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt

@Composable
internal fun PaintDialogs(s: PaintUiState, model: PaintViewModel) {
    val uriHandler = LocalUriHandler.current
    when (s.dialog) {
        null -> Unit
        PaintDialog.Unsaved -> AlertDialog(onDismissRequest = model::cancelReplace,
            title = { Text("Save changes to ${s.title}?") },text = { Text(stringResource(R.string.ui_your_image_has_unsaved_changes_including_any_current_preview)) },
            confirmButton = { TextButton(onClick = model::saveThenContinue) { Text(stringResource(R.string.ui_save)) } },
            dismissButton = { Row { TextButton(onClick = model::discard) { Text(stringResource(R.string.ui_discard)) }; TextButton(onClick = model::cancelReplace) { Text(stringResource(R.string.ui_cancel)) } } })
        PaintDialog.SaveFormat -> AlertDialog(onDismissRequest = model::cancelSave,
            title = { Text(stringResource(R.string.ui_save_image_as)) },text = { Text(stringResource(R.string.ui_png_preserves_pixels_jpeg_uses_lossy_compression)) },
            confirmButton = { TextButton(onClick = { model.saveFormat("image/png") }) { Text(stringResource(R.string.ui_png)) } },
            dismissButton = { Row { TextButton(onClick = { model.saveFormat("image/jpeg") }) { Text(stringResource(R.string.ui_jpeg)) }; TextButton(onClick = model::cancelSave) { Text(stringResource(R.string.ui_cancel)) } } })
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
        PaintDialog.Geometry -> GeometryDialog(s,model)
        PaintDialog.CanvasSize -> CanvasSizeDialog(s,model)
        PaintDialog.Settings -> SettingsDialog(s,model)
        PaintDialog.About -> AlertDialog(onDismissRequest = { model.dialog(null) },
            icon = { Icon(PaintIcon.Brush.vector,null,tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.ui_kd_paint)) },text = { Column {
                Text("Version ${BuildConfig.VERSION_NAME}\nDeveloped by kkps-d\nA focused raster editor for Android.")
                TextButton(onClick = { runCatching { uriHandler.openUri("https://github.com/kkps-d/k-desktop-suite") }.onFailure { model.message("No browser is available to open the source repository.") } }) { Text("Source code") }
            } },
            confirmButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_close)) } })
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
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(width,{ width = it; error = null },label = { Text(stringResource(R.string.ui_canvas_width)) },
                    suffix = { Text("px") },singleLine = true,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f))
                Text("×",Modifier.padding(horizontal = 8.dp),style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(height,{ height = it; error = null },label = { Text(stringResource(R.string.ui_canvas_height)) },
                    suffix = { Text("px") },singleLine = true,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f))
            }
            error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
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
    var background by remember { mutableStateOf(s.colorBackground) }
    var hex by remember { mutableStateOf("%06X".format((if (s.colorBackground) s.options.background else s.options.foreground) and 0xFFFFFF)) }
    var customMenu by remember { mutableStateOf(false) }
    val parsed = hex.takeIf { it.length == 6 }?.toLongOrNull(16)?.toInt()?.or(BLACK)
    fun choose(color: Int) { hex = "%06X".format(color and 0xFFFFFF) }
    AlertDialog(onDismissRequest = { model.dialog(null) },title = { Text(stringResource(R.string.ui_edit_colors)) },text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(!background,{ background = false; choose(s.options.foreground) })
                Text(stringResource(R.string.ui_foreground),Modifier.clickable { background = false; choose(s.options.foreground) })
                RadioButton(background,{ background = true; choose(s.options.background) })
                Text(stringResource(R.string.ui_background),Modifier.clickable { background = true; choose(s.options.background) })
            }
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
            presetColors.chunked(5).forEach { row -> Row { row.forEach { color -> ColorSwatch(color,"Choose color",parsed == color) { choose(color) } } } }
            Text(stringResource(R.string.ui_custom_colors),style = MaterialTheme.typography.labelLarge)
            s.preferences.customColors.chunked(5).forEachIndexed { rowIndex,row -> Row { row.forEachIndexed { index,color ->
                ColorSwatch(color,"Custom color ${rowIndex*5+index+1}") { choose(color) }
            } } }
            Box {
                TextButton(onClick = { customMenu = true },enabled = parsed != null) { Text(stringResource(R.string.ui_save_current_color_to_custom_slot)) }
                DropdownMenu(customMenu,{ customMenu = false }) { (0..9).forEach { index ->
                    DropdownMenuItem({ Text("Slot ${index+1}") },{ parsed?.let { color ->
                        model.preferences { it.copy(customColors = it.customColors.toMutableList().also { colors -> colors[index] = color }) }
                    }; customMenu = false })
                } }
            }
            TextButton(onClick = { model.options { it.copy(foreground = it.background,background = it.foreground) }; model.dialog(null) }) { Text(stringResource(R.string.ui_swap_foreground_and_background)) }
        }
    },confirmButton = { TextButton(onClick = { parsed?.let { color -> model.options { if (background) it.copy(background = color) else it.copy(foreground = color) }; model.dialog(null) } },enabled = parsed != null) { Text(stringResource(R.string.ui_apply)) } },
        dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_cancel)) } })
}

@Composable
private fun GeometryDialog(s: PaintUiState, model: PaintViewModel) {
    val rect = s.preview?.bounds ?: s.selection
    if (rect == null) { LaunchedEffect(Unit) { model.dialog(null) }; return }
    var x by remember { mutableStateOf(rect.x.toString()) }; var y by remember { mutableStateOf(rect.y.toString()) }
    var width by remember { mutableStateOf(rect.width.toString()) }; var height by remember { mutableStateOf(rect.height.toString()) }
    var locked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { model.dialog(null) },title = { Text(if (s.preview?.tool == Tool.Text) "Text and geometry" else "Move / resize preview") },text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NumericField("X (image pixels)",x,{ x = it }); NumericField("Y (image pixels)",y,{ y = it })
            NumericField("Width (pixels)",width,{ width = it; if (locked) it.toIntOrNull()?.let { w -> height = maxOf(1,(w.toFloat()*rect.height/rect.width).roundToInt()).toString() } })
            NumericField("Height (pixels)",height,{ height = it; if (locked) it.toIntOrNull()?.let { h -> width = maxOf(1,(h.toFloat()*rect.width/rect.height).roundToInt()).toString() } })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(locked,{ locked = it }); Text(stringResource(R.string.ui_maintain_aspect_ratio)) }
            if (s.preview?.tool == Tool.Text) TextOptions(s,model)
            error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        }
    },confirmButton = { TextButton(onClick = {
        try {
            val bounds = PixelRect(requireNotNull(x.toIntOrNull()),requireNotNull(y.toIntOrNull()),requireNotNull(width.toIntOrNull()),requireNotNull(height.toIntOrNull()))
            require(bounds.x in -8192..8192 && bounds.y in -8192..8192) { "Position must be between −8192 and 8192." }
            checkImageSize(bounds.width,bounds.height); model.movePreview(bounds); model.dialog(null)
        } catch (invalid: Exception) { error = invalid.message ?: "Enter valid pixel dimensions." }
    }) { Text(stringResource(R.string.ui_update_preview)) } },dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_cancel)) } })
}

@Composable
private fun NumericField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value,change,label = { Text(label) },singleLine = true,modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun TextOptions(s: PaintUiState, model: PaintViewModel) {
    Text(stringResource(R.string.ui_text_formatting),style = MaterialTheme.typography.titleSmall)
    listOf("Sans-serif","Serif","Monospace").forEach { family ->
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(s.options.font == family,{ model.options { it.copy(font = family) } }); Text(family) }
    }
    var size by remember(s.options.fontSize) { mutableStateOf(s.options.fontSize.toString()) }
    NumericField("Font size (image pixels)",size,{ value -> size = value; value.toIntOrNull()?.takeIf { it in 1..512 }?.let { number -> model.options { it.copy(fontSize = number) } } })
    listOf("Bold" to s.options.bold,"Italic" to s.options.italic,"Underline" to s.options.underline,"Strikethrough" to s.options.strike,"Opaque text background" to s.options.opaqueText).forEach { (label,selected) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected,{ value -> model.options { when(label) {
                "Bold" -> it.copy(bold = value); "Italic" -> it.copy(italic = value); "Underline" -> it.copy(underline = value)
                "Strikethrough" -> it.copy(strike = value); else -> it.copy(opaqueText = value)
            } } }); Text(label)
        }
    }
}

@Composable
private fun SettingsDialog(s: PaintUiState, model: PaintViewModel) {
    AlertDialog(onDismissRequest = { model.dialog(null) },title = { Text(stringResource(R.string.ui_settings)) },text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.ui_appearance),style = MaterialTheme.typography.titleSmall)
            listOf("System","Light","Dark").forEach { appearance -> Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(s.preferences.appearance == appearance,{ model.preferences { it.copy(appearance = appearance) } }); Text(appearance)
            } }
            Text("JPEG quality: ${s.preferences.jpegQuality}%")
            Slider(s.preferences.jpegQuality.toFloat(),{ quality -> model.preferences { it.copy(jpegQuality = quality.roundToInt()) } },valueRange = 1f..100f,
                modifier = Modifier.semantics { contentDescription = "JPEG quality" })
            Text(stringResource(R.string.ui_custom_colors_are_managed_through_colors_edit_colors),style = MaterialTheme.typography.bodySmall)
        }
    },confirmButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_done)) } })
}
