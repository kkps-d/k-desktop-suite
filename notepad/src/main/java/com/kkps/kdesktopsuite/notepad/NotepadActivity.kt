package com.kkps.kdesktopsuite.notepad

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import java.io.File

internal class NotepadApplication : Application() {
    val sessions by lazy { SessionStore(File(filesDir, "notepad-session.json")) }
}

class NotepadActivity : ComponentActivity() {
    private lateinit var notepad: NotepadViewModel
    private val documentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data?.takeIf { result.resultCode == RESULT_OK }
        if (uri != null) retainPermission(uri, data.flags)
        notepad.pickerResult(uri?.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notepad = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                NotepadViewModel(
                    AndroidDocumentRepository(applicationContext.contentResolver),
                    (application as NotepadApplication).sessions,
                    extras.createSavedStateHandle(),
                ) as T
        })[NotepadViewModel::class.java]

        setContent {
            val active = notepad.active
            val title = active?.let { documentTitle(it) }
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
                            val name = doc?.name ?: getString(R.string.untitled, doc?.untitledNumber ?: 1) + ".txt"
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
            NotepadTheme { NotepadScreen(notepad, activity = this) }
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
        notepad.checkpoint()
        super.onStop()
    }
}
