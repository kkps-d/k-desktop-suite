package com.kkps.kdesktopsuite.explorer

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.core.net.toUri
import androidx.compose.ui.draganddrop.DragAndDropTransferData

internal object ExplorerWindows {
    const val mimeType = "application/vnd.com.kkps.kdesktopsuite.explorer.tab"
    const val transferExtra = "explorer.tabTransfer"
    const val windowExtra = "explorer.windowId"

    fun showWindow(context: Context, id: String? = null) {
        context.startActivity(Intent(context, ExplorerActivity::class.java).apply {
            putExtra(windowExtra, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        })
    }

    fun supported(activity: ExplorerActivity): Boolean = activity.isInMultiWindowMode ||
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    private fun launchIntent(context: Context, token: String) = Intent(context, ExplorerActivity::class.java).apply {
        data = "explorer-tab://transfer/$token".toUri()
        putExtra(transferExtra, token)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
    }

    fun launch(activity: ExplorerActivity, explorer: ExplorerViewModel, id: String) {
        if (explorer.tabs.size == 1 || !supported(activity)) return
        val transfer = explorer.workspace.beginTransfer(explorer, id) ?: return
        if (!launchTransfer(activity, explorer, transfer.token)) explorer.workspace.cancelTransfer(transfer.token)
    }

    fun launchTransfer(activity: ExplorerActivity?, explorer: ExplorerViewModel, token: String): Boolean {
        val transfer = explorer.workspace.transfer(token) ?: return false
        if (activity == null || transfer.sourceId != explorer.windowId || explorer.tabs.size == 1 ||
            !explorer.canTransfer(transfer.tabId) || !supported(activity)) return false
        return try {
            activity.startActivity(launchIntent(activity, token))
            true
        } catch (_: RuntimeException) {
            android.widget.Toast.makeText(activity, R.string.error_new_window, android.widget.Toast.LENGTH_LONG).show()
            false
        }
    }

    fun dragData(activity: ExplorerActivity?, explorer: ExplorerViewModel, id: String): DragAndDropTransferData? {
        val transfer = explorer.workspace.beginTransfer(explorer, id) ?: return null
        var flags = if (Build.VERSION.SDK_INT >= 35) View.DRAG_FLAG_GLOBAL_SAME_APPLICATION else View.DRAG_FLAG_GLOBAL
        val item = if (Build.VERSION.SDK_INT >= 35 && activity != null && explorer.tabs.size > 1 && supported(activity)) {
            val pending = PendingIntent.getActivity(activity, 0, launchIntent(activity, transfer.token),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            flags = flags or View.DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG
            ClipData.Item.Builder().setText(transfer.token).setIntentSender(pending.intentSender).build()
        } else ClipData.Item(transfer.token)
        return DragAndDropTransferData(
            clipData = ClipData("KD Explorer tab", arrayOf(mimeType, ClipDescription.MIMETYPE_TEXT_INTENT), item),
            flags = flags,
        )
    }
}
