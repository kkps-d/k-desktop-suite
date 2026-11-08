package com.kkps.kdesktopsuite.paint

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*

/** Capture navigation fingers before text fields and transform handles see them. */
internal fun Modifier.stylusTouchNavigation(
    navigationOnly: Boolean, active: Boolean, model: PaintViewModel, pen: PaintPenStatus,
): Modifier = pointerInput(navigationOnly,active) {
    awaitPointerEventScope {
        var sequence = false
        var suppressed = false
        var navigating = false
        var movement = Offset.Zero
        var policy = navigationOnly
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val touches = event.changes.filter { it.type == PointerType.Touch }
            if (touches.isEmpty()) continue
            val latestPolicy = pen.navigationOnly(model.state.value.preferences)
            if (latestPolicy != policy) {
                if (sequence) suppressed = true
                policy = latestPolicy
            }
            if (!sequence) {
                sequence = true
                // A policy change must not adopt fingers already resting on the screen.
                suppressed = touches.any { it.previousPressed }
            }
            if (pen.touching || event.changes.any {
                it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
            }) suppressed = true
            if (policy || suppressed) {
                if (active && policy && !suppressed) {
                    val stable = touches.filter { it.pressed && it.previousPressed }
                    if (stable.isNotEmpty() && stable.size == touches.count { it.pressed } &&
                        stable.size == touches.count { it.previousPressed }) {
                        val centroid = stable.fold(Offset.Zero) { sum, p -> sum+p.position } / stable.size.toFloat()
                        val previous = stable.fold(Offset.Zero) { sum, p -> sum+p.previousPosition } / stable.size.toFloat()
                        val pan = centroid-previous
                        movement += pan
                        if (stable.size >= 2 || movement.getDistance() > viewConfiguration.touchSlop) navigating = true
                        if (navigating) {
                            val oldRadius = stable.sumOf { (it.previousPosition-previous).getDistance().toDouble() } / stable.size
                            val newRadius = stable.sumOf { (it.position-centroid).getDistance().toDouble() } / stable.size
                            val zoom = if (oldRadius > 0) (newRadius/oldRadius).toFloat() else 1f
                            val view = model.state.value.viewport
                            model.viewport(view.zoomed(view.scale*zoom,Point(previous.x,previous.y)).panned(Point(pan.x,pan.y)))
                        }
                    }
                }
                touches.forEach { it.consume() }
            }
            if (touches.none { it.pressed }) {
                sequence = false; suppressed = false; navigating = false; movement = Offset.Zero
            }
        }
    }
}
