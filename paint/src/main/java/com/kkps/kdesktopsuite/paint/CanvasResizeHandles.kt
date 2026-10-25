package com.kkps.kdesktopsuite.paint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun CanvasResizeHandles(
    state: PaintUiState, model: PaintViewModel, bounds: PixelRect?, active: Boolean,
    onBoundsChange: (PixelRect?) -> Unit,
) {
    val current by rememberUpdatedState(state)
    val density = LocalDensity.current
    val context = LocalContext.current
    val enabled = active && !state.loading && !state.busy && !state.saving
    var cancelGeneration by remember { mutableIntStateOf(0) }
    fun cancel() { onBoundsChange(null); cancelGeneration++ }
    BackHandler(bounds != null) { cancel() }
    val area = bounds ?: PixelRect(0,0,state.raster.width,state.raster.height)

    CanvasResizeHandle.entries.forEach { handle ->
        val focus = remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        val cursorType = when {
            handle.vertical == 0 -> android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
            handle.horizontal == 0 -> android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
            handle.horizontal == handle.vertical -> android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
            else -> android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
        }
        val cursor = remember(cursorType) { PointerIcon(android.view.PointerIcon.getSystemIcon(context,cursorType)) }
        val position = state.viewport.screenPoint(Point(area.x + area.width * (handle.horizontal + 1) / 2f,
            area.y + area.height * (handle.vertical + 1) / 2f))
        Box(Modifier.absoluteOffset {
            val radius = with(density) { 24.dp.toPx() }
            IntOffset((position.x - radius).roundToInt(),(position.y - radius).roundToInt())
        }.size(48.dp).testTag("canvas-resize-${handle.name}").pointerHoverIcon(cursor)
            .focusRequester(focus).onFocusChanged { focused = it.isFocused }.focusable(enabled)
            .semantics {
                contentDescription = "${handle.label} canvas resize handle"
                stateDescription = "${area.width} x ${area.height} px"
                role = Role.Button
                if (!enabled) disabled()
                onClick("Enter canvas dimensions") { if (enabled) model.dialog(PaintDialog.CanvasSize); enabled }
            }.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !enabled || event.isCtrlPressed || event.isAltPressed) false
                else if (event.key == Key.Escape && bounds != null) { cancel(); true }
                else if (event.key == Key.Enter || event.key == Key.Spacebar) { model.dialog(PaintDialog.CanvasSize); true }
                else {
                    val step = if (event.isShiftPressed) 10f else 1f
                    val dx = when (event.key) { Key.DirectionLeft -> -step; Key.DirectionRight -> step; else -> 0f }
                    val dy = when (event.key) { Key.DirectionUp -> -step; Key.DirectionDown -> step; else -> 0f }
                    if (dx * handle.horizontal == 0f && dy * handle.vertical == 0f) false
                    else { model.resizeCanvas(handle.bounds(state.raster.width,state.raster.height,Point(dx,dy))); true }
                }
            }.pointerInput(enabled,state.raster,cancelGeneration) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (currentEvent.buttons.isSecondaryPressed || currentEvent.buttons.isTertiaryPressed) return@awaitEachGesture
                    val initial = current
                    val original = PixelRect(0,0,initial.raster.width,initial.raster.height)
                    var target = original
                    var delta = Offset.Zero
                    focus.requestFocus()
                    dragging = true
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                            delta += change.positionChange()
                            target = handle.bounds(original.width,original.height,
                                Point(delta.x / initial.viewport.scale,delta.y / initial.viewport.scale))
                            onBoundsChange(target)
                            change.consume()
                            if (!change.pressed) {
                                if (target != original) model.resizeCanvas(target)
                                break
                            }
                        }
                    } finally { dragging = false; onBoundsChange(null) }
                }
            },contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).background(
                if (focused || dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,CircleShape)
                .border(1.5.dp,MaterialTheme.colorScheme.primary,CircleShape))
        }
    }
}
