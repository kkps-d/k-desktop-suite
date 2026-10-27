package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Outside-edge targets leave the native editor's caret and character selection untouched. */
@Composable
internal fun TextMoveFrame(state: PaintUiState, preview: EditPreview, model: PaintViewModel, active: Boolean) {
    if (!active || state.busy || state.loading || state.saving) return
    val density = LocalDensity.current
    val context = LocalContext.current
    val cursor = remember(context) { PointerIcon(android.view.PointerIcon.getSystemIcon(context,android.view.PointerIcon.TYPE_ALL_SCROLL)) }
    val origin = state.viewport.screenPoint(Point(preview.bounds.x.toFloat(),preview.bounds.y.toFloat()))
    val width = preview.bounds.width * state.viewport.scale
    val height = preview.bounds.height * state.viewport.scale
    val edge = with(density) { 12.dp.toPx() }
    // Leave the bottom-right resize target available to the canvas gesture handler.
    val resizeGap = with(density) { 18.dp.toPx() }
    val targets = listOf(
        Rect(origin.x,origin.y-edge,origin.x+width,origin.y),
        Rect(origin.x-edge,origin.y,origin.x,origin.y+height),
        Rect(origin.x,origin.y+height,origin.x+(width-resizeGap).coerceAtLeast(0f),origin.y+height+edge),
        Rect(origin.x+width,origin.y,origin.x+width+edge,origin.y+(height-resizeGap).coerceAtLeast(0f)),
    )
    targets.forEachIndexed { index,area ->
        if (area.width <= 0 || area.height <= 0) return@forEachIndexed
        Box(Modifier.absoluteOffset { IntOffset(area.left.roundToInt(),area.top.roundToInt()) }
            .size(with(density) { area.width.toDp() },with(density) { area.height.toDp() })
            .testTag("paint-text-move-$index").pointerHoverIcon(cursor)
            .semantics {
                contentDescription = "Move text frame"
                onClick("Enter text position") { model.dialog(PaintDialog.Transform); true }
            }.pointerInput(preview.id,index) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (currentEvent.buttons.isSecondaryPressed || currentEvent.buttons.isTertiaryPressed) return@awaitEachGesture
                    val initial = model.state.value
                    val original = initial.preview?.takeIf { it.id == preview.id }?.bounds ?: return@awaitEachGesture
                    if (initial.busy || initial.loading || initial.saving) return@awaitEachGesture
                    var delta = Offset.Zero
                    var completed = false
                    var moved = false
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                            delta += change.positionChange()
                            if (delta.getDistance() > viewConfiguration.touchSlop || moved) {
                                moved = true
                                if (model.state.value.preview?.id != preview.id) break
                                model.movePreview(original.copy(x = original.x+(delta.x/initial.viewport.scale).roundToInt(),
                                    y = original.y+(delta.y/initial.viewport.scale).roundToInt()))
                            }
                            change.consume()
                            if (!change.pressed) { completed = true; break }
                        }
                    } finally {
                        if (!completed && moved && model.state.value.preview?.id == preview.id) model.movePreview(original)
                    }
                }
            })
    }
}
