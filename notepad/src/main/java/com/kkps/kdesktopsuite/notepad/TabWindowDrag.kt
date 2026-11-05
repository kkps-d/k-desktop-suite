package com.kkps.kdesktopsuite.notepad

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.core.net.toUri
import com.kkps.kdesktopsuite.ui.DocumentTabInteractions
import com.kkps.kdesktopsuite.ui.suiteTabDropTarget

internal object TabWindows {
    const val mimeType = "application/vnd.com.kkps.kdesktopsuite.notepad.tab"
    const val transferExtra = "notepad.tabTransfer"
    const val windowExtra = "notepad.windowId"

    fun showWindow(context: Context, id: String? = null) {
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

/** Android window launch policy stays in Notepad; gestures and visuals are shared. */
@Composable
internal fun Modifier.tabDropTarget(
    notepad: NotepadViewModel?, moved: (Offset) -> Unit = {}, exited: () -> Unit = {},
    dropped: (String) -> Boolean,
): Modifier {
    if (notepad == null) return this
    val interactions = DocumentTabInteractions(null, TabWindows.mimeType, { null },
        notepad.workspace::endDrag, notepad.workspace::cancelTransfer,
        { token, index -> notepad.workspace.move(token, notepad.window, index) }, notepad::reorder, notepad::canTransfer)
    return suiteTabDropTarget(interactions, moved, exited, dropped)
}
