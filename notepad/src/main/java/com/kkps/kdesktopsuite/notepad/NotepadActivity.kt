package com.kkps.kdesktopsuite.notepad

import android.app.Application
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import java.io.File

internal class NotepadApplication : Application() {
    val sessions by lazy { SessionStore(File(filesDir, "notepad-session.json")) }
    val workspace by lazy {
        NotepadWorkspace(sessions).apply { showWindow = { TabWindows.showWindow(this@NotepadApplication, it.id) } }
    }
}

class NotepadActivity : AppCompatActivity() {
    internal lateinit var notepad: NotepadViewModel
        private set
    private var boundWindow = false
    private val documentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data?.takeIf { result.resultCode == RESULT_OK }
        if (uri != null) retainPermission(uri, data.flags)
        notepad.pickerResult(uri?.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 35) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
            )
        }
        notepad = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val state = extras.createSavedStateHandle()
                if (savedInstanceState == null) intent.getStringExtra(TabWindows.windowExtra)?.let { state["windowId"] = it }
                val workspace = (application as NotepadApplication).workspace
                return NotepadViewModel(
                    AndroidDocumentRepository(applicationContext.contentResolver),
                    workspace,
                    state,
                    newWindow = savedInstanceState == null && workspace.windows.any { it.attached && !it.closed },
                ) as T
            }
        })[NotepadViewModel::class.java]

        if (notepad.window.attached) {
            notepad.window.bringToFront()
            finishAndRemoveTask()
            return
        }
        boundWindow = true
        notepad.window.attached = true
        notepad.window.bringToFront = {
            @Suppress("DEPRECATION")
            val task = getSystemService(ActivityManager::class.java).appTasks.find {
                val info = it.taskInfo
                (if (Build.VERSION.SDK_INT >= 29) info?.taskId else info?.id) == taskId
            }
            task?.moveToFront()
        }

        setContent {
            LaunchedEffect(notepad.loading, notepad.window.closed) {
                if (notepad.window.closed) {
                    finishAndRemoveTask()
                } else if (!notepad.loading) {
                    // The launch intent can outlive an acknowledged transfer across recreation.
                    val token = intent.getStringExtra(TabWindows.transferExtra).takeIf { savedInstanceState == null }
                    if (token != null) {
                        intent.removeExtra(TabWindows.transferExtra)
                        val transfer = notepad.workspace.transfer(token)
                        if (transfer != null && !notepad.workspace.move(token, notepad.window, 0, newWindow = true)) {
                            notepad.workspace.cancelTransfer(token)
                            notepad.workspace.abandonWindow(notepad.window)
                            finishAndRemoveTask()
                        } else if (transfer == null && notepad.workspace.windows.count { !it.closed } > 1) {
                            notepad.workspace.abandonWindow(notepad.window)
                            notepad.workspace.windows.firstOrNull { !it.closed }?.let { notepad.workspace.bringToFront(it) }
                            finishAndRemoveTask()
                        }
                        // An old token after process death leaves the merged recovery window intact.
                    }
                }
            }
            val active = notepad.active
            val title = active?.let { documentTitle(it) }
                ?: if (!notepad.loading) getString(R.string.welcome) else null
            SideEffect {
                this.title = if (title == null) getString(R.string.app_name) else "$title — ${getString(R.string.app_name)}"
            }
            val picker = notepad.pickerRequest
            LaunchedEffect(picker) {
                if (picker != null) {
                    val intent = when (picker.kind) {
                        PickerKind.Open -> Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "text/*" }
                        PickerKind.SaveAs -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "text/plain"
                            val doc = notepad.documents.find { it.id == picker.documentId }
                            val name = doc?.name ?: getString(R.string.untitled) + ".txt"
                            putExtra(Intent.EXTRA_TITLE, name)
                        }
                    }.apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                    notepad.pickerLaunched()
                    try {
                        documentPicker.launch(intent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        notepad.pickerResult(null)
                        notepad.reportError(R.string.error_open)
                    }
                }
            }
            NotepadTheme(notepad.appTheme) {
                val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.argb(230, 255, 255, 255),
                            android.graphics.Color.argb(128, 27, 27, 27),
                        ) { dark },
                    )
                }
                NotepadScreen(notepad, activity = this)
            }
        }
    }

    internal fun retainPermission(uri: Uri, flags: Int) {
        val access = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            if (access != 0) contentResolver.takePersistableUriPermission(uri, access)
        } catch (_: SecurityException) {
            // Dropped files and some providers grant only temporary access. Recovery still works.
        }
    }

    override fun onStop() {
        if (::notepad.isInitialized) notepad.checkpoint()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && boundWindow) notepad.workspace.focus(notepad.window)
    }

    override fun onDestroy() {
        if (boundWindow) {
            notepad.window.attached = false
            notepad.window.bringToFront = {}
            notepad.window.prepareTransfer = {}
            notepad.window.modalOpen = false
        }
        super.onDestroy()
    }
}
