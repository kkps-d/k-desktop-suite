package com.kkps.kdesktopsuite.notepad

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.core.net.toUri
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

internal object TabWindows {
    const val mimeType = "application/vnd.com.kkps.kdesktopsuite.notepad.tab"
    const val transferExtra = "notepad.tabTransfer"
    const val windowExtra = "notepad.windowId"

    fun showWindow(context: Context, id: String) {
        context.startActivity(Intent(context, NotepadActivity::class.java).apply {
            putExtra(windowExtra, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        })
    }

    fun supported(activity: NotepadActivity): Boolean = activity.isInMultiWindowMode ||
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    private fun launchIntent(context: Context, token: String) = Intent(context, NotepadActivity::class.java).apply {
        data = "notepad-tab://transfer/$token".toUri()
        putExtra(transferExtra, token)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
    }

    fun launch(activity: NotepadActivity, notepad: NotepadViewModel, id: String) {
        if (notepad.documents.size == 1 || !supported(activity)) return
        val transfer = notepad.workspace.beginTransfer(notepad.window, id) ?: return
        if (!launchTransfer(activity, notepad, transfer.token)) notepad.workspace.cancelTransfer(transfer.token)
    }

    fun launchTransfer(activity: NotepadActivity?, notepad: NotepadViewModel, token: String): Boolean {
        val transfer = notepad.workspace.transfer(token) ?: return false
        if (activity == null || transfer.sourceId != notepad.window.id || notepad.documents.size == 1 ||
            !notepad.canTransfer(transfer.documentId) || !supported(activity)) return false
        return try {
            activity.startActivity(launchIntent(activity, token))
            true
        } catch (_: RuntimeException) {
            notepad.reportError(R.string.error_window)
            false
        }
    }

    fun dragData(activity: NotepadActivity?, notepad: NotepadViewModel, id: String): DragAndDropTransferData? {
        val transfer = notepad.workspace.beginTransfer(notepad.window, id) ?: return null
        var flags = if (Build.VERSION.SDK_INT >= 35) View.DRAG_FLAG_GLOBAL_SAME_APPLICATION else View.DRAG_FLAG_GLOBAL
        val item = if (Build.VERSION.SDK_INT >= 35 && activity != null && notepad.documents.size > 1 && supported(activity)) {
            val pending = PendingIntent.getActivity(activity, 0, launchIntent(activity, transfer.token),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            flags = flags or View.DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG
            ClipData.Item.Builder().setText(transfer.token).setIntentSender(pending.intentSender).build()
        } else ClipData.Item(transfer.token)
        return DragAndDropTransferData(
            clipData = ClipData("KD Notepad tab", arrayOf(mimeType, ClipDescription.MIMETYPE_TEXT_INTENT), item),
            flags = flags,
        )
    }
}

/** The platform shadow follows the pointer across windows; no document text leaves the process. */
@Composable
internal fun Modifier.tabDragSource(notepad: NotepadViewModel?, activity: NotepadActivity?, id: String): Modifier {
    if (notepad == null) return this
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val closeWidth = with(LocalDensity.current) { TabBarDimensions.buttonSize.toPx() }
    val doc = notepad.documents.find { it.id == id }
    val title = doc?.let { documentTitle(it) }.orEmpty()
    val titleStyle = NotepadTabTypography.title.copy(color = MaterialTheme.colorScheme.onSurface)
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
            if (overClose) null else TabWindows.dragData(activity, notepad, id)
        },
    )
}

@Composable
internal fun Modifier.tabDropTarget(
    notepad: NotepadViewModel?,
    moved: (Offset) -> Unit = {},
    exited: () -> Unit = {},
    dropped: (String) -> Boolean,
): Modifier {
    if (notepad == null) return this
    val currentMoved by rememberUpdatedState(moved)
    val currentExited by rememberUpdatedState(exited)
    val currentDropped by rememberUpdatedState(dropped)
    val target = remember(notepad) {
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
                notepad.workspace.endDrag()
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                onMoved(event)
                val data = event.toAndroidDragEvent().clipData
                val token = data?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString()
                if (token != null && !currentDropped(token)) notepad.workspace.cancelTransfer(token)
                // Rejected internal drops are consumed too: they must not detach via SystemUI.
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { it.toAndroidDragEvent().clipDescription?.hasMimeType(TabWindows.mimeType) == true },
        target = target,
    )
}
