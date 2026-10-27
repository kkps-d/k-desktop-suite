package com.kkps.kdesktopsuite.paint

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*

internal class PaintApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PaintFonts.load(this)
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val recovery by lazy { PaintRecovery(java.io.File(noBackupFilesDir, "paint-recovery")) }
    val settings by lazy { PaintSettings(this) }
    val images by lazy { ImageRepository(this) }
}

class PaintActivity : ComponentActivity() {
    internal lateinit var paintModel: PaintViewModel
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = application as PaintApplication
            val model: PaintViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
                    PaintViewModel(application.images, application.recovery, application.settings,
                        extras.createSavedStateHandle(), application.applicationScope) as T
            })
            val state by model.state.collectAsStateWithLifecycle()
            SideEffect { paintModel = model }
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
                    try { picker.launch(intent) } catch (error: Exception) { model.pickerResult(null); model.message(error.message) }
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
                                    type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newUri(contentResolver, "Image", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Share image"))
                            } else {
                                clipboard.setPrimaryClip(ClipData.newUri(contentResolver, "Image selection", uri))
                                if (Build.VERSION.SDK_INT < 33) model.message("Image selection copied.")
                            }
                        }
                        succeeded = true
                    } catch (error: Exception) { model.message(error.message ?: "The action could not be completed.") }
                    finally { model.platformDone(request.id, succeeded, pasteUri) }
                }
            }
            DisposableEffect(lifecycle, model) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.flush() }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer); model.flush() }
            }
            SideEffect { title = "${state.title} — KD Paint" }
            PaintTheme(state.preferences.appearance) { PaintScreen(state, model) }
        }
    }
}
