@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalResources
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
internal fun TransformDialog(s: PaintUiState, model: PaintViewModel) {
    val strings = LocalResources.current
    val selection = s.preview?.takeIf { it.canTransform }
    val original = s.preview?.bounds ?: s.selection ?: PixelRect(0,0,s.raster.width,s.raster.height)
    val canMove = s.preview != null || s.selection != null
    val canRotate = selection != null || s.selection != null
    val isText = s.options.tool == Tool.Text || s.preview?.tool == Tool.Text
    var tab by rememberSaveable { mutableIntStateOf(if (canMove) 0 else 1) }
    var positionX by rememberSaveable { mutableStateOf(original.x.toString()) }
    var positionY by rememberSaveable { mutableStateOf(original.y.toString()) }
    var angle by rememberSaveable { mutableStateOf((s.preview?.rotationDegrees ?: 0.0).toString()) }
    var percentage by rememberSaveable { mutableStateOf(true) }
    var horizontal by rememberSaveable { mutableStateOf("100") }
    var vertical by rememberSaveable { mutableStateOf("100") }
    var locked by rememberSaveable { mutableStateOf(true) }
    var skewX by rememberSaveable { mutableStateOf("0") }
    var skewY by rememberSaveable { mutableStateOf("0") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    fun pixels(value: String, size: Int): Int {
        val number = requireNotNull(value.toIntOrNull()) { strings.getString(R.string.error_dimensions_number) }
        require(number > 0) { strings.getString(R.string.error_dimensions_positive) }
        val result = if (percentage) size*number.toDouble()/100 else number.toDouble()
        require(result <= 8192) { strings.getString(R.string.error_dimension_limit) }
        return result.roundToInt().coerceAtLeast(1)
    }
    val requestedBounds = runCatching {
        val x = requireNotNull(positionX.toIntOrNull()) { strings.getString(R.string.error_x_number) }
        val y = requireNotNull(positionY.toIntOrNull()) { strings.getString(R.string.error_y_number) }
        require(x in -8192..8192 && y in -8192..8192) { strings.getString(R.string.error_position_range) }
        PixelRect(x,y,pixels(horizontal,original.width),pixels(vertical,original.height))
    }
    val dimensions = runCatching {
        val bounds = requestedBounds.getOrThrow()
        val x = requireNotNull(skewX.toIntOrNull()) { strings.getString(R.string.error_horizontal_skew_number) }
        val y = requireNotNull(skewY.toIntOrNull()) { strings.getString(R.string.error_vertical_skew_number) }
        val degrees = requireNotNull(angle.toDoubleOrNull()) { strings.getString(R.string.error_rotation_number) }
        require(degrees.isFinite()) { strings.getString(R.string.error_rotation_number) }
        val floating = selection
        val result = if (floating != null) {
            val moved = floating.withBounds(bounds).rotatedSelection(degrees-floating.rotationDegrees)
            moved.transformedSelection(resizeSkewTransform(moved.bounds,moved.bounds.width,moved.bounds.height,x,y)).bounds
        } else {
            var transform = RasterTransform.translation(original.x.toDouble(),original.y.toDouble())
                .then(resizeSkewTransform(original,bounds.width,bounds.height,0,0))
                .then(RasterTransform.translation((bounds.x-original.x).toDouble(),(bounds.y-original.y).toDouble()))
            if (canRotate && degrees != 0.0) transform = transform.then(
                RasterTransform.around(Point(bounds.x+bounds.width/2f,bounds.y+bounds.height/2f),RasterTransform.rotation(degrees)))
            val area = transform.bounds(original.width,original.height)
            transform.then(resizeSkewTransform(area,area.width,area.height,x,y)).bounds(original.width,original.height)
        }
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
    AlertDialog(onDismissRequest = { model.dialog(null) },modifier = Modifier.testTag("transform-dialog"),title = { Text(stringResource(if (isText) R.string.ui_move_resize else R.string.ui_move_resize_skew)) },text = {
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (s.preview?.tool == Tool.Text) strings.getString(R.string.ui_target_text) else if (s.preview?.tool?.isShape == true) strings.getString(R.string.ui_target_shape)
                else stringResource(if (canMove) R.string.ui_target_selection else R.string.ui_target_image))
            PrimaryTabRow(selectedTabIndex = tab) {
                val tabs = if (isText) listOf(R.string.ui_move,R.string.ui_resize)
                    else listOf(R.string.ui_move,R.string.ui_resize,R.string.ui_skew)
                tabs.forEachIndexed { index,label ->
                    Tab(selected = tab == index,onClick = { tab = index },enabled = index != 0 || canMove,
                        text = { Text(stringResource(label),maxLines = 1) })
                }
            }
            if (tab == 0) {
                OutlinedTextField(positionX,{ positionX = it },label = { Text(strings.getString(R.string.ui_x_image_pixels)) },singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("move-x"))
                OutlinedTextField(positionY,{ positionY = it },label = { Text(strings.getString(R.string.ui_y_image_pixels)) },singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("move-y"))
                if (canRotate) OutlinedTextField(angle,{ angle = it },label = { Text(stringResource(R.string.ui_rotation_degrees)) },singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("move-angle"))
            } else if (tab == 1) {
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
                if (s.preview?.tool == Tool.Text) Text(strings.getString(R.string.ui_text_skew_warning),
                    style = MaterialTheme.typography.bodySmall)
            }
            dimensions.getOrNull()?.let { Text(stringResource(R.string.ui_transform_dimensions,it.width,it.height),Modifier.testTag("transform-dimensions")) }
            if (attempted) dimensions.exceptionOrNull()?.let { if (it is PaintValidationException) strings.getString(it.problem.messageResource()) else it.message }?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        }
    },confirmButton = { TextButton(onClick = {
        attempted = true
        if (dimensions.isSuccess) {
            if (model.updateTransform(requestedBounds.getOrThrow(),skewX.toInt(),skewY.toInt(),
                    if (canRotate) angle.toDouble() else null)) model.dialog(null)
        }
    },enabled = !s.busy && !s.loading) { Text(stringResource(R.string.ui_ok)) } },
        dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.ui_cancel)) } })
}
