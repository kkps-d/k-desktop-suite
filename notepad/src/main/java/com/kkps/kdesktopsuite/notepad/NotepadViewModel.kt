package com.kkps.kdesktopsuite.notepad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class PickerKind { Open, SaveAs }
internal data class PickerRequest(val kind: PickerKind, val documentId: String)
internal data class SaveConflict(val documentId: String, val closeAfter: Boolean)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal class NotepadViewModel(
    private val repository: DocumentRepository,
    val workspace: NotepadWorkspace,
    private val savedState: SavedStateHandle,
    newWindow: Boolean = false,
    private val ownsWorkspace: Boolean = false,
) : ViewModel() {
    constructor(repository: DocumentRepository, store: SessionStore, savedState: SavedStateHandle) :
        this(repository, NotepadWorkspace(store), savedState, ownsWorkspace = true)

    val window = workspace.acquireWindow(savedState["windowId"], newWindow)
    val documents get() = window.documents
    val activeId get() = window.activeId
    val active: DocumentSession? get() = documents.firstOrNull { it.id == activeId }
    val loading get() = workspace.loading
    var opening by mutableIntStateOf(0)
        private set
    val zoom get() = workspace.zoom
    val wordWrap get() = workspace.wordWrap
    val showStatusBar get() = workspace.showStatusBar
    val appTheme get() = workspace.appTheme
    val fontStyle get() = workspace.fontStyle
    var settingsOpen by mutableStateOf(savedState.get<Boolean>("settingsOpen") == true)
        private set
    val focusRequest get() = window.focusRequest
    var closeConfirmation by mutableStateOf<String?>(null)
        private set
    var conflict by mutableStateOf<SaveConflict?>(null)
        private set
    var pickerRequest by mutableStateOf<PickerRequest?>(null)
        private set
    val errorMessage get() = window.errorMessage
    val errorDocumentId get() = window.errorDocumentId
    private val ready get() = workspace.ready
    var pickerPending by mutableStateOf(savedState.get<String>("pickerKind") != null)
        private set

    init {
        savedState["windowId"] = window.id
        if (!window.attached) {
            window.blocked = { pickerPending || closeConfirmation != null || conflict != null || opening > 0 }
            window.showDocument = { showSettings(false) }
        }
        viewModelScope.launch {
            ready.await()
            // Recovery copies are authoritative; validate access without replacing contents.
            documents.toList().forEach { doc ->
                val uri = doc.uri ?: return@forEach
                try {
                    repository.read(uri)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    reportError(R.string.error_access, doc.id)
                }
            }
        }
    }

    fun snapshot() = workspace.snapshot()

    fun checkpoint() = workspace.checkpoint()

    fun newDocument() {
        if (loading) return
        addBlank()
        checkpoint()
    }

    private fun addBlank() = workspace.addBlank(window)

    fun select(id: String, focusEditor: Boolean = true) {
        window.select(id, focusEditor)
        workspace.focus(window)
    }

    fun reorder(id: String, index: Int) = workspace.reorder(window, id, index)

    fun canTransfer(id: String) = workspace.canTransfer(window, id)

    fun moveToWindow(id: String, destination: NotepadWindow): Boolean {
        if (!destination.attached) return false
        val transfer = workspace.beginTransfer(window, id) ?: return false
        return workspace.move(transfer.token, destination, destination.documents.size).also {
            workspace.cancelTransfer(transfer.token)
        }
    }

    fun cycleTab(direction: Int) {
        if (documents.isEmpty()) return
        val index = documents.indexOfFirst { it.id == activeId }
        select(documents[Math.floorMod(index + direction, documents.size)].id)
    }

    fun changeZoom(value: Int) {
        workspace.zoom = value.coerceIn(50, 300)
    }

    fun toggleWordWrap() { workspace.wordWrap = !wordWrap }

    fun toggleStatusBar() { workspace.showStatusBar = !showStatusBar }

    fun showSettings(show: Boolean) {
        settingsOpen = show
        savedState["settingsOpen"] = show
    }

    fun changeAppTheme(theme: AppTheme) {
        workspace.appTheme = theme
        checkpoint()
    }

    fun changeFontStyle(style: NoteFontStyle) {
        workspace.fontStyle = style
        checkpoint()
    }

    fun requestClose(id: String) {
        val doc = documents.find { it.id == id } ?: return
        if (doc.saving || savedState.get<String>("pickerTargetId") == id) return
        if (doc.dirty) closeConfirmation = id else closeDocument(id)
    }

    fun cancelClose() { closeConfirmation = null }

    fun discardClose() {
        closeConfirmation?.let(::closeDocument)
        closeConfirmation = null
    }

    fun saveBeforeClose() {
        val id = closeConfirmation ?: return
        closeConfirmation = null
        save(id, closeAfter = true)
    }

    private fun closeDocument(id: String) {
        val index = documents.indexOfFirst { it.id == id }
        if (index == -1) return
        documents.removeAt(index)
        if (documents.isEmpty()) addBlank()
        else if (activeId == id) select(documents[index.coerceAtMost(documents.lastIndex)].id)
        checkpoint()
    }

    fun requestOpen() {
        if (loading || pickerPending) return
        launchPicker(PickerKind.Open, activeId)
    }

    fun requestSaveAs(id: String = activeId, closeAfter: Boolean = false) {
        val doc = documents.find { it.id == id } ?: return
        if (pickerPending || doc.saving) return
        savedState["pickerCloseAfter"] = closeAfter
        launchPicker(PickerKind.SaveAs, id)
    }

    private fun launchPicker(kind: PickerKind, id: String) {
        pickerPending = true
        savedState["pickerKind"] = kind.name
        savedState["pickerTargetId"] = id
        pickerRequest = PickerRequest(kind, id)
        checkpoint()
    }

    fun pickerLaunched() { pickerRequest = null }

    fun pickerResult(uri: String?) {
        val kind = savedState.get<String>("pickerKind")
        val id = savedState.get<String>("pickerTargetId")
        val closeAfter = savedState.get<Boolean>("pickerCloseAfter") == true
        savedState.remove<String>("pickerKind")
        savedState.remove<String>("pickerTargetId")
        savedState.remove<Boolean>("pickerCloseAfter")
        pickerPending = false
        pickerRequest = null
        if (uri == null || id == null) return
        viewModelScope.launch {
            ready.await()
            when (kind) {
                PickerKind.Open.name -> openUri(uri, id).join()
                PickerKind.SaveAs.name -> {
                    if (workspace.findUri(uri)?.let { it.id != id } == true) {
                        reportError(R.string.error_already_open, id)
                    } else saveTo(id, uri, closeAfter, isSaveAs = true)
                }
            }
        }
    }

    fun openUri(uri: String, originatingId: String = activeId): Job = viewModelScope.launch {
        ready.await()
        workspace.findUri(uri)?.let { workspace.selectDocument(it); return@launch }
        opening++
        try {
            val loaded = repository.read(uri)
            workspace.findUri(uri)?.let { workspace.selectDocument(it); return@launch }
            val doc = DocumentSession(DocumentSnapshot(
                name = loaded.name, uri = uri, text = loaded.text, savedText = loaded.text,
                diskFingerprint = loaded.fingerprint, format = loaded.format,
            ))
            val blankIndex = documents.indexOfFirst {
                it.id == originatingId && it.uri == null && it.editor.text.isEmpty() && !it.saving &&
                    !it.editor.undoState.canUndo && !it.editor.undoState.canRedo
            }
            if (blankIndex >= 0) documents[blankIndex] = doc else documents.add(doc)
            select(doc.id)
            checkpoint()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            reportError(if (error is UnsupportedTextException) R.string.error_encoding else R.string.error_open)
        } finally {
            opening--
        }
    }

    fun save(id: String = activeId, closeAfter: Boolean = false) {
        val doc = documents.find { it.id == id } ?: return
        val uri = doc.uri
        if (uri == null) requestSaveAs(id, closeAfter)
        else saveTo(id, uri, closeAfter)
    }

    private fun saveTo(
        id: String, uri: String, closeAfter: Boolean, isSaveAs: Boolean = false, overwrite: Boolean = false,
    ) {
        val doc = documents.find { it.id == id } ?: return
        if (doc.saving) return
        if (!workspace.reserveWrite(uri, id)) {
            reportError(R.string.error_already_open, id)
            return
        }
        val snapshot = doc.snapshot()
        doc.saving = true
        viewModelScope.launch {
            try {
                val saved = repository.write(
                    uri, snapshot.text, snapshot.format,
                    if (isSaveAs || overwrite) null else snapshot.diskFingerprint,
                )
                doc.uri = uri
                doc.name = saved.name
                doc.savedText = snapshot.text
                doc.diskFingerprint = saved.fingerprint
                if (errorDocumentId == id) dismissError()
                if (closeAfter && !doc.dirty) closeDocument(id)
                checkpoint()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is FileChangedException) conflict = SaveConflict(id, closeAfter)
                else reportError(R.string.error_save, id)
            } finally {
                doc.saving = false
                workspace.releaseWrite(uri)
            }
        }
    }

    fun cancelConflict() { conflict = null }

    fun overwriteConflict() {
        val pending = conflict ?: return
        conflict = null
        val uri = documents.find { it.id == pending.documentId }?.uri ?: return
        saveTo(pending.documentId, uri, pending.closeAfter, overwrite = true)
    }

    fun saveConflictAs() {
        val pending = conflict ?: return
        conflict = null
        requestSaveAs(pending.documentId, pending.closeAfter)
    }

    fun reportError(message: Int, documentId: String? = null) {
        window.errorMessage = message
        window.errorDocumentId = documentId
    }

    fun dismissError() {
        window.errorMessage = null
        window.errorDocumentId = null
    }

    override fun onCleared() {
        if (ownsWorkspace) workspace.close()
    }
}
