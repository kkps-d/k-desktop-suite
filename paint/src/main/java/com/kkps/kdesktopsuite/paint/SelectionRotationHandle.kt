package com.kkps.kdesktopsuite.paint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
internal fun SelectionRotationHandle(state: PaintUiState, model: PaintViewModel, active: Boolean, cancelSignal: Int, onDraggingChange: (Boolean) -> Unit) {
    val current by rememberUpdatedState(state)
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<EditPreview?>(null) }
    var cancelGeneration by remember { mutableIntStateOf(0) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val enabled = active && !state.busy && !state.loading && !state.saving
    fun cancel() { if (dragging) model.setPreview(original); dragging = false; onDraggingChange(false); cancelGeneration++ }
    BackHandler(dragging) { cancel() }
    val preview = state.preview?.takeIf { it.payload != null }
    val area = preview?.bounds ?: state.selection ?: return
    val center = preview?.selectionCenter ?: Point(area.x+area.width/2f,area.y+area.height/2f)
    val corner = preview?.let { it.selectionTransform.point(requireNotNull(it.payload).width.toDouble(),0.0) }
        ?: Point(area.right.toFloat(),area.y.toFloat())
    val screenCorner = state.viewport.screenPoint(corner)
    val screenCenter = state.viewport.screenPoint(center)
    val dx = screenCorner.x-screenCenter.x; val dy = screenCorner.y-screenCenter.y
    val length = hypot(dx,dy).coerceAtLeast(1f)
    val distance = with(density) { 32.dp.toPx() }
    val radius = with(density) { 24.dp.toPx() }
    val position = Point(
        (screenCorner.x+dx/length*distance).coerceIn(radius,max(radius,state.viewport.width-radius)),
        (screenCorner.y+dy/length*distance).coerceIn(radius,max(radius,state.viewport.height-radius)))
    val label = stringResource(R.string.ui_rotation_handle)
    val description = stringResource(R.string.ui_rotation_state,preview?.rotationDegrees ?: 0.0)
    Box(Modifier.absoluteOffset {
        IntOffset((position.x-radius).roundToInt(),(position.y-radius).roundToInt())
    }.size(48.dp).onGloballyPositioned { coordinates = it }.testTag("selection-rotation-handle").pointerHoverIcon(PointerIcon.Crosshair)
        .focusRequester(focus).onFocusChanged { focused = it.isFocused }.focusable(enabled)
        .semantics {
            contentDescription = label; stateDescription = description; role = Role.Button
            if (!enabled) disabled()
            onClick("Enter selection rotation") { if (enabled) model.dialog(PaintDialog.Rotation); enabled }
        }.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !enabled) false
            else when (event.key) {
                Key.Escape -> if (dragging) { cancel(); true } else false
                Key.Enter, Key.Spacebar -> { model.dialog(PaintDialog.Rotation); true }
                Key.DirectionLeft, Key.DirectionRight -> {
                    model.rotateSelection((if (event.key == Key.DirectionLeft) -1.0 else 1.0)*(if (event.isShiftPressed) 15 else 1)); true
                }
                else -> false
            }
        }.pointerInput(enabled,cancelGeneration,cancelSignal) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                if (currentEvent.buttons.isSecondaryPressed || currentEvent.buttons.isTertiaryPressed) return@awaitEachGesture
                val initial = current
                val selection = model.floatingSelection() ?: return@awaitEachGesture
                original = initial.preview
                val pivot = selection.selectionCenter
                val view = initial.viewport
                fun angle(point: androidx.compose.ui.geometry.Offset): Double {
                    val layout = requireNotNull(coordinates)
                    val position = requireNotNull(layout.parentLayoutCoordinates).localPositionOf(layout,point)
                    val image = view.imagePoint(Point(position.x,position.y))
                    return atan2((image.y-pivot.y).toDouble(),(image.x-pivot.x).toDouble())*180/PI
                }
                var previous = angle(down.position)
                var total = 0.0
                var released = false
                focus.requestFocus(); dragging = true; onDraggingChange(true); down.consume()
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1 || change.isConsumed) {
                            model.setPreview(original)
                            // Retain gesture ownership until all fingers lift, so the canvas cannot start an edit.
                            event.changes.forEach { it.consume() }
                            while (currentEvent.changes.any { it.pressed }) awaitPointerEvent().changes.forEach { it.consume() }
                            break
                        }
                        val next = angle(change.position)
                        total += (next-previous+540)%360-180
                        previous = next
                        if (total != 0.0) model.rotateSelection(total,selection)
                        change.consume()
                        if (!change.pressed) { released = true; break }
                    }
                } finally {
                    if (!released && dragging) model.setPreview(original)
                    dragging = false; onDraggingChange(false)
                }
            }
        },contentAlignment = Alignment.Center) {
        Icon(PaintIcon.Rotate.vector,null,Modifier.size(28.dp)
            .background(if (focused || dragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,CircleShape)
            .border(1.dp,MaterialTheme.colorScheme.primary,CircleShape).padding(4.dp),tint = MaterialTheme.colorScheme.primary)
    }
}
