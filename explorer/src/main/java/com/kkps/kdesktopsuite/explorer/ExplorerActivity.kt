package com.kkps.kdesktopsuite.explorer

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras

class ExplorerActivity : AppCompatActivity() {
    internal lateinit var explorer: ExplorerViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 35) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
            )
        }
        explorer = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                ExplorerViewModel(extras.createSavedStateHandle()) as T
        })[ExplorerViewModel::class.java]
        setContent {
            ExplorerTheme {
                SideEffect { title = "${getString(R.string.tab_title, explorer.activeId)} — ${getString(R.string.app_name)}" }
                ExplorerScreen(explorer, ::newWindow, ::closeTab, ::finishAndRemoveTask)
            }
        }
    }

    internal fun newWindow() {
        try {
            startActivity(Intent(this, ExplorerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            })
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.error_new_window, Toast.LENGTH_LONG).show()
        }
    }

    private fun closeTab(id: Int) {
        if (explorer.close(id)) finishAndRemoveTask()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
            val handled = when (event.keyCode) {
                KeyEvent.KEYCODE_T -> {
                    if (event.isShiftPressed) return super.dispatchKeyEvent(event)
                    explorer.newTab()
                    true
                }
                KeyEvent.KEYCODE_N -> { newWindow(); true }
                KeyEvent.KEYCODE_W -> {
                    if (event.isShiftPressed) finishAndRemoveTask() else closeTab(explorer.activeId)
                    true
                }
                KeyEvent.KEYCODE_TAB -> { explorer.selectRelative(if (event.isShiftPressed) -1 else 1); true }
                in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                    val index = if (event.keyCode == KeyEvent.KEYCODE_9) explorer.tabs.lastIndex
                        else event.keyCode - KeyEvent.KEYCODE_1
                    explorer.tabs.getOrNull(index)?.let(explorer::select)
                    true
                }
                else -> false
            }
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }
}
