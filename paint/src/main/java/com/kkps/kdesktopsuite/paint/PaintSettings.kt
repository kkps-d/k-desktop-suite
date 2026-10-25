@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.AppSettingsPage
import kotlin.math.roundToInt

@Composable
internal fun PaintSettingsScreen(s: PaintUiState, model: PaintViewModel) {
    val preferences = s.preferences
    var width by rememberSaveable(preferences.defaultCanvasWidth) { mutableStateOf(preferences.defaultCanvasWidth.toString()) }
    var height by rememberSaveable(preferences.defaultCanvasHeight) { mutableStateOf(preferences.defaultCanvasHeight.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val quality = rememberSliderState(value = preferences.jpegQuality.toFloat(),trackRange = 1f..100f)
    quality.value = preferences.jpegQuality.toFloat()
    AppSettingsPage(stringResource(R.string.ui_settings),stringResource(R.string.ui_back),{ model.showSettings(false) }) { availableWidth ->
        val stackChoices = availableWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_appearance),Modifier.semantics { heading() },style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                verticalArrangement = Arrangement.spacedBy(8.dp),maxItemsInEachRow = if (stackChoices) 1 else 3) {
                listOf("Light","Dark","System").forEachIndexed { index,appearance ->
                    ToggleButton(checked = preferences.appearance == appearance,
                        onCheckedChange = { model.preferences { it.copy(appearance = appearance) } },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("theme-$appearance").semantics { role = Role.RadioButton },
                        shapes = if (stackChoices) ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.size) else when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },icon = if (preferences.appearance == appearance) ({ Icon(PaintIcon.Apply.vector,null) }) else null) {
                        Text(if (appearance == "System") stringResource(R.string.ui_theme_auto) else appearance)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_default_canvas_size),Modifier.semantics { heading() },style = MaterialTheme.typography.titleMedium)
            CanvasSizeFields(width,height,{ width = it; error = null },{ height = it; error = null })
            error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = {
                try {
                    val w = requireNotNull(width.toIntOrNull()) { "Enter a valid width in pixels." }
                    val h = requireNotNull(height.toIntOrNull()) { "Enter a valid height in pixels." }
                    checkImageSize(w,h)
                    model.preferences { it.copy(defaultCanvasWidth = w,defaultCanvasHeight = h) }
                } catch (invalid: IllegalArgumentException) { error = invalid.message }
            },enabled = width != preferences.defaultCanvasWidth.toString() || height != preferences.defaultCanvasHeight.toString(),
                modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ui_apply)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_jpeg_quality,preferences.jpegQuality),Modifier.semantics { heading() },style = MaterialTheme.typography.titleMedium)
            Slider(state = quality,onValueChange = { value -> model.preferences { it.copy(jpegQuality = value.roundToInt()) } },
                modifier = Modifier.semantics { contentDescription = "JPEG quality" })
        }
    }
}

@Composable
internal fun CanvasSizeFields(width: String, height: String, onWidthChange: (String) -> Unit, onHeightChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(width,onWidthChange,label = { Text(stringResource(R.string.ui_canvas_width)) },
            suffix = { Text("px") },singleLine = true,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("canvas-width-input"))
        Text("×",Modifier.padding(horizontal = 8.dp),style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(height,onHeightChange,label = { Text(stringResource(R.string.ui_canvas_height)) },
            suffix = { Text("px") },singleLine = true,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("canvas-height-input"))
    }
}
