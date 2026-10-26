package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ResizeSkewDialog(s: PaintUiState, model: PaintViewModel) {
    val selection = s.preview?.takeIf { it.payload != null }
    val original = selection?.bounds ?: s.selection ?: PixelRect(0,0,s.raster.width,s.raster.height)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var percentage by rememberSaveable { mutableStateOf(true) }
    var horizontal by rememberSaveable { mutableStateOf("100") }
    var vertical by rememberSaveable { mutableStateOf("100") }
    var locked by rememberSaveable { mutableStateOf(true) }
    var skewX by rememberSaveable { mutableStateOf("0") }
    var skewY by rememberSaveable { mutableStateOf("0") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    fun pixels(value: String, size: Int): Int {
        val number = requireNotNull(value.toIntOrNull()) { "Enter whole numbers for dimensions." }
        require(number > 0) { "Dimensions must be greater than zero." }
        val result = if (percentage) size*number.toDouble()/100 else number.toDouble()
        require(result <= 8192) { "Each dimension must be at most 8192 pixels." }
        return result.roundToInt().coerceAtLeast(1)
    }
    val dimensions = runCatching {
        val width = pixels(horizontal,original.width); val height = pixels(vertical,original.height)
        val x = requireNotNull(skewX.toIntOrNull()) { "Enter a whole number for horizontal skew." }
        val y = requireNotNull(skewY.toIntOrNull()) { "Enter a whole number for vertical skew." }
        val transform = resizeSkewTransform(original,width,height,x,y)
        val result = if (selection != null) selection.selectionTransform.then(transform)
            .bounds(requireNotNull(selection.payload).width,selection.payload.height)
        else RasterTransform.translation(original.x.toDouble(),original.y.toDouble()).then(transform)
            .bounds(original.width,original.height)
        checkImageSize(result.width,result.height)
        result
    }
    fun changeHorizontal(value: String) {
        horizontal = value
        if (locked) {
            if (percentage) vertical = value
            else value.toIntOrNull()?.let { vertical = (it.toDouble()*original.height/original.width).roundToInt().coerceAtLeast(1).toString() }
        }
    }
    fun changeVertical(value: String) {
        vertical = value
        if (locked) {
            if (percentage) horizontal = value
            else value.toIntOrNull()?.let { horizontal = (it.toDouble()*original.width/original.height).roundToInt().coerceAtLeast(1).toString() }
        }
    }
    fun changeUnits(toPercentage: Boolean) {
        if (percentage == toPercentage) return
        fun convert(value: String, size: Int) = value.toIntOrNull()?.let {
            (if (toPercentage) it*100.0/size else it.toDouble()*size/100).roundToInt().coerceAtLeast(1).toString()
        } ?: value
        horizontal = convert(horizontal,original.width); vertical = convert(vertical,original.height)
        percentage = toPercentage
        if (locked) changeHorizontal(horizontal)
    }
    AlertDialog(onDismissRequest = { model.dialog(null) },modifier = Modifier.testTag("resize-skew-dialog"),title = { Text(stringResource(R.string.ui_resize_and_skew)) },text = {
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (selection != null || s.selection != null) R.string.ui_target_selection else R.string.ui_target_image))
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0,{ tab = 0 },text = { Text(stringResource(R.string.ui_resize)) })
                Tab(tab == 1,{ tab = 1 },text = { Text(stringResource(R.string.ui_skew)) })
            }
            if (tab == 0) {
                Row(Modifier.selectableGroup()) {
                    listOf(true to R.string.ui_percentage,false to R.string.ui_pixels).forEach { (value,label) ->
                        Row(Modifier.weight(1f).heightIn(min = 48.dp).selectable(percentage == value,
                            onClick = { changeUnits(value) },role = Role.RadioButton),verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(percentage == value,null); Text(stringResource(label))
                        }
                    }
                }
                OutlinedTextField(horizontal,::changeHorizontal,label = { Text(stringResource(R.string.ui_horizontal)) },
                    suffix = { Text(if (percentage) "%" else "px") },singleLine = true,modifier = Modifier.fillMaxWidth().testTag("resize-horizontal"))
                OutlinedTextField(vertical,::changeVertical,label = { Text(stringResource(R.string.ui_vertical)) },
                    suffix = { Text(if (percentage) "%" else "px") },singleLine = true,modifier = Modifier.fillMaxWidth().testTag("resize-vertical"))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(locked,onClick = {
                    locked = !locked; if (locked) changeHorizontal(horizontal)
                },role = Role.Checkbox),verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(locked,null); Text(stringResource(R.string.ui_maintain_aspect_ratio))
                }
            } else {
                OutlinedTextField(skewX,{ skewX = it },label = { Text(stringResource(R.string.ui_horizontal_degrees)) },singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("skew-horizontal"))
                OutlinedTextField(skewY,{ skewY = it },label = { Text(stringResource(R.string.ui_vertical_degrees)) },singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("skew-vertical"))
                Text(stringResource(R.string.ui_skew_range),style = MaterialTheme.typography.bodySmall)
            }
            dimensions.getOrNull()?.let { Text(stringResource(R.string.ui_transform_dimensions,it.width,it.height),Modifier.testTag("transform-dimensions")) }
            if (attempted) dimensions.exceptionOrNull()?.message?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        }
    },confirmButton = { TextButton(onClick = {
        attempted = true
        if (dimensions.isSuccess) {
            model.resizeImage(pixels(horizontal,original.width),pixels(vertical,original.height),skewX.toInt(),skewY.toInt())
            model.dialog(null)
        }
    },enabled = !s.busy && !s.loading) { Text(stringResource(R.string.ui_ok)) } },
        dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_cancel)) } })
}
