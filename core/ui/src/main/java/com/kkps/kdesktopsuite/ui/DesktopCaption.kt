package com.kkps.kdesktopsuite.ui

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlin.math.ceil
import kotlin.math.floor

// Geometry is in window pixels, including the physical positions of system controls in RTL.
data class DesktopCaption(
    val height: Float = 0f,
    val obstructions: List<Rect> = emptyList(),
    val safeTop: Float = 0f,
)

// Returns the widest physical span clear of system controls and display cutouts.
fun desktopCaptionSpan(caption: DesktopCaption, region: Rect): Rect? {
    if (caption.height <= 0f || region.top >= caption.height || caption.obstructions.isEmpty()) return null
    val header = Rect(region.left, region.top, region.right, minOf(region.bottom, caption.height))
    var spans = listOf(header)
    caption.obstructions.filter { it.overlaps(header) }.forEach { obstacle ->
        spans = spans.flatMap { span ->
            if (!span.overlaps(obstacle)) listOf(span) else listOf(
                span.copy(right = obstacle.left.coerceIn(span.left, span.right)),
                span.copy(left = obstacle.right.coerceIn(span.left, span.right)),
            ).filter { it.width > 0f }
        }
    }
    return spans.maxByOrNull { it.width }
}

fun captionGestureRects(controls: Collection<Rect>, clip: Rect?, hostOrigin: Offset): List<Rect> =
    if (clip == null) emptyList() else controls.mapNotNull { control ->
        val bounds = control.intersect(clip)
        if (bounds.isEmpty) null else bounds.translate(-hostOrigin)
    }

@Composable
fun rememberDesktopCaption(enabled: Boolean): DesktopCaption {
    val view = LocalView.current
    var caption by remember(view) { mutableStateOf(DesktopCaption()) }
    DisposableEffect(view, enabled) {
        // Observe rather than replace Compose's inset listener. Reading on pre-draw also catches
        // caption changes during maximize/restore and display/density transitions.
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            caption = if (enabled && Build.VERSION.SDK_INT >= 35) {
                val insets = view.rootWindowInsets
                val type = WindowInsets.Type.captionBar()
                val transparent = (view.windowInsetsController?.systemBarsAppearance ?: 0)
                    .and(WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND) != 0
                if (insets != null && transparent && insets.isVisible(type)) {
                    DesktopCaption(
                        insets.getInsets(type).top.toFloat(),
                        (insets.getBoundingRects(type) + (insets.displayCutout?.boundingRects ?: emptyList())).map {
                            Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                        },
                        insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top.toFloat(),
                    )
                } else DesktopCaption()
            } else DesktopCaption()
            true
        }
        observer.addOnPreDrawListener(listener)
        onDispose { if (observer.isAlive) observer.removeOnPreDrawListener(listener) }
    }
    return caption
}

class CaptionInput(private val view: View) {
    private val original = if (Build.VERSION.SDK_INT >= 29) view.systemGestureExclusionRects.map { android.graphics.Rect(it) } else emptyList()
    private val controls = mutableMapOf<Any, Rect>()
    var clip: Rect? = null
        set(value) { field = value; apply() }

    fun update(key: Any, bounds: Rect?) {
        if (bounds == null) controls.remove(key) else controls[key] = bounds
        apply()
    }

    private fun apply() {
        if (Build.VERSION.SDK_INT < 29) return
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rects = captionGestureRects(controls.values, clip, Offset(location[0].toFloat(), location[1].toFloat()))
            .mapNotNull {
                // Round inward so fractional layout pixels cannot exclude an adjacent system button.
                android.graphics.Rect(ceil(it.left).toInt(), ceil(it.top).toInt(), floor(it.right).toInt(), floor(it.bottom).toInt())
                    .takeUnless { rect -> rect.isEmpty }
            }
        val exclusions = original + rects
        if (view.systemGestureExclusionRects != exclusions) view.systemGestureExclusionRects = exclusions
    }

    fun dispose() {
        controls.clear()
        if (Build.VERSION.SDK_INT >= 29) view.systemGestureExclusionRects = original
    }
}

@Composable
fun rememberCaptionInput(clip: Rect?): CaptionInput {
    // Own exclusions on the decor view, leaving Compose's own gesture exclusions untouched.
    val view = LocalView.current.rootView
    val input = remember(view) { CaptionInput(view) }
    SideEffect { input.clip = clip }
    DisposableEffect(input) { onDispose { input.dispose() } }
    return input
}

@Composable
fun Modifier.captionControl(input: CaptionInput?, key: Any): Modifier {
    DisposableEffect(input, key) { onDispose { input?.update(key, null) } }
    return if (input == null) this else onGloballyPositioned { input.update(key, it.boundsInWindow()) }
}
