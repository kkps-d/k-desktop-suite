package com.kkps.kdesktopsuite.paint

import android.app.Application
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

internal class PaintApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PaintFonts.load(this)
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val recovery by lazy { PaintRecovery(java.io.File(noBackupFilesDir, "paint-recovery")) }
    val windows by lazy { PaintWindows(java.io.File(noBackupFilesDir, "paint-windows"), recovery) }
    val settings by lazy { PaintSettings(this) }
    val images by lazy { ImageRepository(this) }
}

class PaintActivity : AppCompatActivity() {
    internal lateinit var paintModel: PaintViewModel
        private set
    private var paintWindow: PaintWindow? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 35) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
            )
        }
        val application = application as PaintApplication
        lifecycleScope.launch {
            try {
                val preferences = application.settings.preferences.first()
                val window = application.windows.acquire(
                    savedInstanceState?.getString(PaintWindows.windowExtra) ?: intent.getStringExtra(PaintWindows.windowExtra),
                    resume = savedInstanceState != null,
                    newWindow = intent.getBooleanExtra(PaintWindows.newWindowExtra, false) ||
                        (savedInstanceState == null && intent.getStringExtra(PaintWindows.windowExtra) == null &&
                            application.windows.windows.values.any { it.activity != null }),
                    behavior = preferences.startupBehavior,
                )
                if (window.closed) {
                    finishAndRemoveTask()
                    return@launch
                }
                if (window.activity != null && window.activity !== this@PaintActivity) {
                    window.activity?.bringWindowToFront()
                    finishAndRemoveTask()
                    return@launch
                }
                paintWindow = window
                window.activity = this@PaintActivity
                paintModel = ViewModelProvider(this@PaintActivity, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
                        PaintViewModel(application.images, window.recovery, application.settings,
                            extras.createSavedStateHandle(), application.applicationScope,
                            windows = application.windows, window = window) as T
                })[PaintViewModel::class.java]
                showEditor(application, window)
                if (hasWindowFocus()) recordWindowFocus()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                android.util.Log.e("PaintActivity", "The Paint window could not be initialized", error)
                setContent {
                    PaintTheme {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
                            Text(androidx.compose.ui.res.stringResource(R.string.error_window_startup))
                        }
                    }
                }
            }
        }
    }

    private fun showEditor(application: PaintApplication, window: PaintWindow) {
        val model = paintModel
        lifecycleScope.launch {
            model.state.first { it.closed }
            finishAndRemoveTask()
        }
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.loading) {
                if (!state.loading && !state.closed) application.windows.restoreOthers(this@PaintActivity, window) { model.failure(it, R.string.error_restore_window) }
            }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data = result.data
                val uri = data?.data?.takeIf { result.resultCode == RESULT_OK }
                if (uri != null) {
                    try {
                        if (data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                            val flags = if (data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            else Intent.FLAG_GRANT_READ_URI_PERMISSION
                            contentResolver.takePersistableUriPermission(uri, flags)
                        }
                    } catch (_: SecurityException) { /* Temporary grants still allow importing into recovery. */ }
                }
                model.pickerResult(uri?.toString())
            }
            LaunchedEffect(state.picker?.id) {
                state.picker?.takeIf { !it.launched }?.let { request ->
                    val intent = Intent(if (request.kind == PickerKind.Save) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = if (request.kind == PickerKind.Save) request.mime else "image/*"
                        if (request.kind != PickerKind.Save) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/png", "image/jpeg"))
                        if (request.kind == PickerKind.Save) {
                            val stem = state.title.substringBeforeLast('.', state.title)
                            putExtra(Intent.EXTRA_TITLE, "$stem.${if (request.mime == "image/jpeg") "jpg" else "png"}")
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                    model.pickerLaunched(request.id)
                    try { picker.launch(intent) } catch (error: Exception) { model.pickerResult(null); model.failure(error) }
                }
            }
            LaunchedEffect(state.platform?.id) {
                state.platform?.let { request ->
                    var succeeded = false; var pasteUri: String? = null
                    try {
                        val clipboard = getSystemService(ClipboardManager::class.java)
                        if (request.action == "paste") {
                            val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                            val uri = item?.uri?.takeIf { it.scheme == "content" }
                                ?: error("The clipboard does not contain a readable image.")
                            pasteUri = uri.toString()
                        } else {
                            val uri = FileProvider.getUriForFile(this@PaintActivity, "$packageName.images", requireNotNull(request.file))
                            if (request.action == "share") {
                                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = request.mime; putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newUri(contentResolver, getString(R.string.ui_image_properties), uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, getString(R.string.ui_share_image)))
                            } else {
                                clipboard.setPrimaryClip(ClipData.newUri(contentResolver, getString(R.string.ui_copy_selection), uri))
                                if (Build.VERSION.SDK_INT < 33) model.message(R.string.message_selection_copied)
                            }
                        }
                        succeeded = true
                    } catch (error: Exception) { model.failure(error) }
                    finally { model.platformDone(request.id, succeeded, pasteUri) }
                }
            }
            val displayTitle = paintDisplayTitle(state)
            SideEffect { title = "$displayTitle — ${getString(R.string.app_name)}" }
            PaintTheme(state.preferences.appearance) {
                val lightCaption = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                SideEffect {
                    if (Build.VERSION.SDK_INT >= 35) this@PaintActivity.window.insetsController?.setSystemBarsAppearance(
                        if (lightCaption) WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS else 0,
                        WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS,
                    )
                }
                PaintScreen(state, model)
            }
        }
    }

    internal fun bringWindowToFront() {
        @Suppress("DEPRECATION")
        val task = getSystemService(ActivityManager::class.java).appTasks.find {
            val info = it.taskInfo
            (if (Build.VERSION.SDK_INT >= 29) info?.taskId else info?.id) == taskId
        }
        task?.moveToFront()
    }

    override fun onKeyShortcut(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (::paintModel.isInitialized && event.action == android.view.KeyEvent.ACTION_DOWN &&
            event.isCtrlPressed && !event.isAltPressed) {
            val state = paintModel.state.value
            if (state.dialog == null && !state.settingsOpen) {
                when {
                    keyCode == android.view.KeyEvent.KEYCODE_N && event.isShiftPressed -> {
                        if (event.repeatCount == 0) paintModel.newWindow()
                        return true
                    }
                    keyCode == android.view.KeyEvent.KEYCODE_W && !event.isShiftPressed -> {
                        if (event.repeatCount == 0) paintModel.closeImage()
                        return true
                    }
                }
            }
        }
        return super.onKeyShortcut(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        paintWindow?.let { outState.putString(PaintWindows.windowExtra, it.id) }
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) recordWindowFocus()
    }

    private fun recordWindowFocus() {
        paintWindow?.let { window ->
            lifecycleScope.launch {
                try { (application as PaintApplication).windows.focus(window) }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { if (::paintModel.isInitialized) paintModel.failure(error, R.string.error_window_recovery) }
            }
        }
    }

    override fun onStop() {
        if (::paintModel.isInitialized) paintModel.flush()
        super.onStop()
    }

    override fun onDestroy() {
        paintWindow?.takeIf { it.activity === this }?.activity = null
        super.onDestroy()
    }
}
