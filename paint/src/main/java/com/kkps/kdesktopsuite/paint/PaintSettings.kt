@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalResources
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
import com.kkps.kdesktopsuite.ui.AppLanguageSettingsPage
import kotlin.math.roundToInt

@Composable
internal fun PaintSettingsScreen(s: PaintUiState, model: PaintViewModel) {
    val strings = LocalResources.current
    val preferences = s.preferences
    var width by rememberSaveable { mutableStateOf(preferences.defaultCanvasWidth.toString()) }
    var height by rememberSaveable { mutableStateOf(preferences.defaultCanvasHeight.toString()) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    fun saveDefaultCanvasSize(widthText: String, heightText: String) {
        width = widthText
        height = heightText
        try {
            val w = widthText.toIntOrNull() ?: run { error = R.string.error_width_number; return }
            val h = heightText.toIntOrNull() ?: run { error = R.string.error_height_number; return }
            checkImageSize(w,h)
            error = null
            if (w != preferences.defaultCanvasWidth || h != preferences.defaultCanvasHeight) {
                model.preferences { it.copy(defaultCanvasWidth = w,defaultCanvasHeight = h) }
            }
        } catch (invalid: IllegalArgumentException) { error = if (invalid is PaintValidationException) invalid.problem.messageResource() else R.string.error_operation }
    }
    val quality = rememberSliderState(value = preferences.jpegQuality.toFloat(),trackRange = 1f..100f)
    quality.value = preferences.jpegQuality.toFloat()
    AppLanguageSettingsPage(stringResource(R.string.ui_settings),stringResource(R.string.ui_back),{ model.showSettings(false) }) { availableWidth ->
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
                        Text(stringResource(when (appearance) { "Light" -> R.string.ui_theme_light; "Dark" -> R.string.ui_theme_dark; else -> R.string.ui_theme_auto }))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            Text(stringResource(R.string.ui_on_startup),Modifier.padding(bottom = 8.dp).semantics { heading() },style = MaterialTheme.typography.titleMedium)
            listOf(
                PaintStartupBehavior.ReopenLastEdit to R.string.ui_reopen_last_edit,
                PaintStartupBehavior.NewImage to R.string.ui_start_new_image,
                PaintStartupBehavior.AskEveryTime to R.string.ui_ask_every_time,
            ).forEach { (behavior,label) ->
                val selected = preferences.startupBehavior == behavior
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("startup-$behavior")
                    .selectable(selected = selected,role = Role.RadioButton,
                        onClick = { model.preferences { it.copy(startupBehavior = behavior) } })
                    .padding(horizontal = 8.dp,vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RadioButton(selected = selected,onClick = null)
                    Text(stringResource(label))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_default_canvas_size),Modifier.semantics { heading() },style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                CanvasSizeFields(width,height,{ saveDefaultCanvasSize(it,height) },{ saveDefaultCanvasSize(width,it) },
                    modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val defaults = PaintPreferences()
                    saveDefaultCanvasSize(defaults.defaultCanvasWidth.toString(),defaults.defaultCanvasHeight.toString())
                },modifier = Modifier.testTag("reset-default-canvas-size")) { Text(stringResource(R.string.ui_reset)) }
            }
            error?.let { Text(stringResource(it),color = MaterialTheme.colorScheme.error) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_jpeg_quality,preferences.jpegQuality),Modifier.semantics { heading() },style = MaterialTheme.typography.titleMedium)
            Slider(state = quality,onValueChange = { value -> model.preferences { it.copy(jpegQuality = value.roundToInt()) } },
                modifier = Modifier.semantics { contentDescription = strings.getString(R.string.ui_jpeg_quality_label) })
        }
    }
}

@Composable
internal fun CanvasSizeFields(width: String, height: String, onWidthChange: (String) -> Unit, onHeightChange: (String) -> Unit,
    modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp),verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(width,onWidthChange,label = { Text(stringResource(R.string.ui_canvas_width)) },
            suffix = { Text("px") },singleLine = true,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("canvas-width-input"))
        Text("×",Modifier.padding(horizontal = 8.dp),style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(height,onHeightChange,label = { Text(stringResource(R.string.ui_canvas_height)) },
            suffix = { Text("px") },singleLine = true,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("canvas-height-input"))
    }
}
