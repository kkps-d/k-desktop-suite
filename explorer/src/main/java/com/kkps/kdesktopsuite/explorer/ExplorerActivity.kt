package com.kkps.kdesktopsuite.explorer

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.launch

internal class ExplorerApplication : Application() {
    val workspace = ExplorerWorkspace()
}

class ExplorerActivity : AppCompatActivity() {
    internal lateinit var explorer: ExplorerViewModel
        private set
    private var boundWindow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 35) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
            )
        }
        val workspace = (application as ExplorerApplication).workspace
        val requested = intent.getStringExtra(ExplorerWindows.windowExtra)
        workspace.windows.find { it.windowId == requested && it.attached && !it.closed }?.let {
            it.bringToFront()
            finishAndRemoveTask()
            return
        }
        explorer = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val state = extras.createSavedStateHandle()
                if (savedInstanceState == null && requested != null) state["windowId"] = requested
                return ExplorerViewModel(state, workspace) as T
            }
        })[ExplorerViewModel::class.java]
        boundWindow = true
        explorer.attached = true
        explorer.bringToFront = {
            @Suppress("DEPRECATION")
            getSystemService(ActivityManager::class.java).appTasks.find {
                val info = it.taskInfo
                (if (Build.VERSION.SDK_INT >= 29) info?.taskId else info?.id) == taskId
            }?.moveToFront()
        }
        // A transfer owns no destination tab until acknowledgement succeeds.
        val token = intent.getStringExtra(ExplorerWindows.transferExtra).takeIf { savedInstanceState == null }
        if (token != null) {
            intent.removeExtra(ExplorerWindows.transferExtra)
            if (!workspace.move(token, explorer, 0, newWindow = true)) {
                workspace.cancelTransfer(token)
                explorer.closed = true
                finishAndRemoveTask()
                return
            }
        }
        lifecycleScope.launch {
            snapshotFlow { explorer.closed }.collect { if (it) finishAndRemoveTask() }
        }
        setContent {
            ExplorerTheme {
                SideEffect {
                    val tab = explorer.tabs.find { it.id == explorer.activeId }
                    val label = tab?.let { getString(R.string.tab_title, it.number) }
                        ?: getString(com.kkps.kdesktopsuite.ui.R.string.ui_tabs_welcome)
                    title = "$label — ${getString(R.string.app_name)}"
                }
                ExplorerScreen(explorer, ::newWindow, explorer::close, ::finishAndRemoveTask, activity = this)
            }
        }
    }

    internal fun newWindow() {
        try { ExplorerWindows.showWindow(this) }
        catch (_: RuntimeException) { Toast.makeText(this, R.string.error_new_window, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        if (boundWindow) {
            explorer.attached = false
            explorer.bringToFront = {}
            explorer.prepareTransfer = {}
            explorer.modalOpen = false
            if (isFinishing) {
                explorer.closed = true
                explorer.workspace.windows.remove(explorer)
            }
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::explorer.isInitialized) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            explorer.workspace.dragging?.let {
                explorer.workspace.cancelTransfer(it.token)
                window.decorView.cancelDragAndDrop()
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_N -> if (event.isShiftPressed) newWindow() else explorer.newTab()
                KeyEvent.KEYCODE_W -> explorer.close(explorer.activeId)
                KeyEvent.KEYCODE_TAB -> explorer.selectRelative(if (event.isShiftPressed) -1 else 1)
                else -> return super.dispatchKeyEvent(event)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
