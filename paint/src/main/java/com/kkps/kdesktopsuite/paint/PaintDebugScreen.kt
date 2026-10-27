@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kkps.kdesktopsuite.paint

import android.view.PointerIcon as AndroidPointerIcon
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.R as UiR
import kotlin.math.roundToInt

@Composable
internal fun PaintDebugScreen(onBack: () -> Unit) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(Modifier.fillMaxSize().testTag("paint-debug-screen").onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            onBack()
            true
        } else false
    }) {
        TopAppBar(title = { Text(stringResource(R.string.ui_debug)) },navigationIcon = {
            IconButton(onClick = onBack,modifier = Modifier.focusRequester(backFocus)) {
                Icon(painterResource(UiR.drawable.ic_back),stringResource(R.string.ui_back))
            }
        },windowInsets = WindowInsets(0))
        PrimaryTabRow(selectedTabIndex = 0) {
            Tab(selected = true,onClick = {},text = { Text(stringResource(R.string.ui_components)) })
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Column(Modifier.widthIn(max = 480.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.ui_drag_adjustable_field),style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ui_drag_adjustable_field_hint),style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScrubbableNumberField(stringResource(R.string.ui_brush_size),"px",24,1..100,"size")
                ScrubbableNumberField(stringResource(R.string.ui_opacity),"%",80,0..100,"opacity")
            }
        }
    }
}

@Composable
private fun ScrubbableNumberField(label: String, unit: String, initial: Int, range: IntRange, tag: String) {
    var value by rememberSaveable { mutableIntStateOf(initial) }
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val focusManager = LocalFocusManager.current
    val valid = text.toIntOrNull()?.let { it in range } == true

    fun changeValue(next: Int) {
        value = next.coerceIn(range)
        text = value.toString()
    }

    OutlinedTextField(text,onValueChange = { entry ->
        if (entry.length <= 3 && entry.all(Char::isDigit)) {
            text = entry
            entry.toIntOrNull()?.takeIf { it in range }?.let { value = it }
        }
    },label = { Text(label) },singleLine = true,isError = !valid,suffix = { Text(unit) },
        leadingIcon = { ScrubTarget(label,value,range,{ changeValue(it) },
            Modifier.testTag("paint-debug-$tag-scrub")) },
        supportingText = if (!valid) {{ Text(stringResource(R.string.ui_numeric_range,range.first,range.last)) }} else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number,imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && !valid) text = value.toString() }
            .testTag("paint-debug-$tag-input"))
}

@Composable
private fun ScrubTarget(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cursor = remember(context) { PointerIcon(AndroidPointerIcon.getSystemIcon(context,AndroidPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)) }
    val stepPx = with(LocalDensity.current) { 2.dp.toPx() }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val interaction = modifier.pointerHoverIcon(cursor).pointerInput(range,stepPx) {
        var dragValue = 0f
        detectHorizontalDragGestures(onDragStart = {
            dragValue = currentValue.toFloat()
        },onHorizontalDrag = { change,distance ->
            // Discard movement beyond the limits so reversing direction responds immediately.
            dragValue = (dragValue + distance / stepPx).coerceIn(range.first.toFloat(),range.last.toFloat())
            currentOnValueChange(dragValue.roundToInt())
            change.consume()
        })
    }.onKeyEvent { event ->
        val step = if (event.type == KeyEventType.KeyDown) when (event.key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            else -> 0
        } else 0
        if (step == 0) false else {
            onValueChange((value + step).coerceIn(range))
            true
        }
    }.semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(),range.first.toFloat()..range.last.toFloat())
        setProgress { target -> onValueChange(target.roundToInt().coerceIn(range)); true }
    }.focusable()

    Box(Modifier.size(48.dp).then(interaction),contentAlignment = Alignment.Center) {
        Icon(PaintIcon.Swap.vector,null,Modifier.size(20.dp),tint = MaterialTheme.colorScheme.primary)
    }
}
