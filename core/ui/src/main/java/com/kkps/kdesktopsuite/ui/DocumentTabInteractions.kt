package com.kkps.kdesktopsuite.ui

import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Application callbacks for the shared tab strip; transfer tokens never contain document bodies. */
class DocumentTabInteractions(
    val dragged: DocumentTab?,
    val mimeType: String,
    val dragData: (String) -> DragAndDropTransferData?,
    val endDrag: () -> Unit,
    val cancelTransfer: (String) -> Unit,
    val move: (String, Int) -> Boolean,
    val reorder: (String, Int) -> Boolean,
    val canTransfer: (String) -> Boolean,
)

/** The platform shadow follows the pointer across windows; no document text leaves the process. */
@Composable
fun Modifier.suiteTabDragSource(interactions: DocumentTabInteractions?, id: String, title: String): Modifier {
    if (interactions == null) return this
    // Keep the native source stable when starting a drag updates application state.
    val currentInteractions by rememberUpdatedState(interactions)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val closeWidth = with(LocalDensity.current) { TabBarDimensions.buttonSize.toPx() }
    val titleStyle = TabTypography.title.copy(color = MaterialTheme.colorScheme.onSurface)
    val background = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    return onSizeChanged { size = it }.dragAndDropSource(
        // Drawing a dedicated shadow avoids caching the live tab's hover/focus feedback.
        drawDragDecoration = {
            drawRoundRect(background, cornerRadius = CornerRadius(12.dp.toPx()))
            val inset = 22.dp.toPx()
            val text = measurer.measure(title, titleStyle, overflow = TextOverflow.Ellipsis, maxLines = 1,
                constraints = Constraints(maxWidth = (this.size.width - inset * 2).toInt().coerceAtLeast(0)))
            drawText(text, topLeft = Offset(if (rtl) this.size.width - inset - text.size.width else inset,
                (this.size.height - text.size.height) / 2))
        },
        transferData = { offset ->
            val overClose = if (rtl) offset.x < closeWidth else offset.x >= size.width - closeWidth
            if (overClose) null else currentInteractions.dragData(id)
        },
    )
}

@Composable
fun Modifier.suiteTabDropTarget(
    interactions: DocumentTabInteractions?,
    moved: (Offset) -> Unit = {},
    exited: () -> Unit = {},
    dropped: (String) -> Boolean,
): Modifier {
    if (interactions == null) return this
    val currentInteractions by rememberUpdatedState(interactions)
    val currentMoved by rememberUpdatedState(moved)
    val currentExited by rememberUpdatedState(exited)
    val currentDropped by rememberUpdatedState(dropped)
    val target = remember(interactions.mimeType) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = onMoved(event)
            override fun onMoved(event: DragAndDropEvent) {
                val drag = event.toAndroidDragEvent()
                currentMoved(Offset(drag.x, drag.y))
            }
            override fun onExited(event: DragAndDropEvent) = currentExited()
            override fun onEnded(event: DragAndDropEvent) {
                currentExited()
                // SystemUI may launch the PendingIntent after this event. Keep its token until
                // destination acknowledgement; ending a gesture never removes its document.
                currentInteractions.endDrag()
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                onMoved(event)
                val data = event.toAndroidDragEvent().clipData
                val token = data?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString()
                if (token != null && !currentDropped(token)) currentInteractions.cancelTransfer(token)
                // Rejected internal drops are consumed too: they must not detach via SystemUI.
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { it.toAndroidDragEvent().clipDescription?.hasMimeType(interactions.mimeType) == true },
        target = target,
    )
}

fun tabMoveActions(
    interactions: DocumentTabInteractions, documents: List<DocumentTab>, id: String, left: String, right: String, window: String,
    moveToWindow: (String) -> Unit, rtl: Boolean = false,
): List<CustomAccessibilityAction> {
    val index = documents.indexOfFirst { it.id == id }
    if (index < 0) return emptyList()
    val leftIndex = index + if (rtl) 1 else -1
    val rightIndex = index + if (rtl) -1 else 1
    return buildList {
        if (leftIndex in documents.indices) add(CustomAccessibilityAction(left) { interactions.reorder(id, leftIndex) })
        if (rightIndex in documents.indices) add(CustomAccessibilityAction(right) { interactions.reorder(id, rightIndex) })
        if (interactions.canTransfer(id)) add(CustomAccessibilityAction(window) { moveToWindow(id); true })
    }
}

@Composable
fun TabMoveMenu(interactions: DocumentTabInteractions?, documents: List<DocumentTab>, id: String, moveToWindow: (String) -> Unit, dismiss: () -> Unit) {
    if (interactions == null) return
    val actions = tabMoveActions(interactions, documents, id, stringResource(R.string.ui_tabs_move_tab_left),
        stringResource(R.string.ui_tabs_move_tab_right), stringResource(R.string.ui_tabs_move_to_window), moveToWindow,
        LocalLayoutDirection.current == LayoutDirection.Rtl)
    actions.forEach { action ->
        AppMenuItem(text = { Text(action.label) }, onClick = { dismiss(); action.action() })
    }
}
