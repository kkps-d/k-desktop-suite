package com.kkps.kdesktopsuite.notepad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

internal enum class PickerKind { Open, SaveAs }
internal data class PickerRequest(val kind: PickerKind, val documentId: String)
internal data class SaveConflict(val documentId: String, val closeAfter: Boolean)

@OptIn(FlowPreview::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
internal class NotepadViewModel(
    private val repository: DocumentRepository,
    private val store: SessionStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val documents = mutableStateListOf<DocumentSession>()
    var activeId by mutableStateOf("")
        private set
    val active: DocumentSession? get() = documents.firstOrNull { it.id == activeId }
    var loading by mutableStateOf(true)
        private set
    var opening by mutableIntStateOf(0)
        private set
    var zoom by mutableIntStateOf(100)
        private set
    var wordWrap by mutableStateOf(true)
        private set
    var showStatusBar by mutableStateOf(true)
        private set
    var focusRequest by mutableIntStateOf(0)
        private set
    var closeConfirmation by mutableStateOf<String?>(null)
        private set
    var conflict by mutableStateOf<SaveConflict?>(null)
        private set
    var pickerRequest by mutableStateOf<PickerRequest?>(null)
        private set
    var errorMessage by mutableStateOf<Int?>(null)
        private set
    var errorDocumentId by mutableStateOf<String?>(null)
        private set
    private var nextUntitled = 1
    private val ready = CompletableDeferred<Unit>()
    var pickerPending by mutableStateOf(savedState.get<String>("pickerKind") != null)
        private set

    init {
        viewModelScope.launch {
            try {
                store.read()?.let { snapshot ->
                    documents.addAll(snapshot.documents.filter { it.text.isNotEmpty() }.map(::DocumentSession))
                    activeId = snapshot.activeId.takeIf { id -> documents.any { it.id == id } }
                        ?: documents.firstOrNull()?.id.orEmpty()
                    zoom = snapshot.zoom
                    nextUntitled = snapshot.nextUntitled
                    wordWrap = snapshot.wordWrap
                    showStatusBar = snapshot.showStatusBar
                    focusRequest++
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportError(R.string.error_restore)
            }
            if (documents.isEmpty()) addBlank()
            loading = false
            ready.complete(Unit)
            launch {
                snapshotFlow { snapshot() }.debounce(500).collect { checkpoint(it) }
            }
            // Recovery copies are authoritative; validate access without replacing their contents.
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

    fun snapshot() = SessionSnapshot(documents.map { it.snapshot() }, activeId, zoom, nextUntitled, wordWrap, showStatusBar)

    fun checkpoint() {
        if (!loading) checkpoint(snapshot())
    }

    private fun checkpoint(snapshot: SessionSnapshot) {
        store.checkpoint(snapshot) { succeeded ->
            if (!succeeded && errorMessage == null) reportError(R.string.error_recovery)
        }
    }

    fun newDocument() {
        if (loading) return
        addBlank()
        checkpoint()
    }

    private fun addBlank() {
        val doc = DocumentSession(DocumentSnapshot(untitledNumber = nextUntitled++))
        documents.add(doc)
        activeId = doc.id
        focusRequest++
    }

    fun select(id: String, focusEditor: Boolean = true) {
        if (documents.none { it.id == id }) return
        activeId = id
        if (focusEditor) focusRequest++
    }

    fun cycleTab(direction: Int) {
        if (documents.isEmpty()) return
        val index = documents.indexOfFirst { it.id == activeId }
        select(documents[Math.floorMod(index + direction, documents.size)].id)
    }

    fun changeZoom(value: Int) {
        zoom = value.coerceIn(50, 300)
    }

    fun toggleWordWrap() { wordWrap = !wordWrap }

    fun toggleStatusBar() { showStatusBar = !showStatusBar }

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
                    if (documents.any { it.uri == uri && it.id != id }) {
                        reportError(R.string.error_already_open, id)
                    } else saveTo(id, uri, closeAfter, isSaveAs = true)
                }
            }
        }
    }

    fun openUri(uri: String, originatingId: String = activeId): Job = viewModelScope.launch {
        ready.await()
        documents.find { it.uri == uri }?.let { select(it.id); return@launch }
        opening++
        try {
            val loaded = repository.read(uri)
            documents.find { it.uri == uri }?.let { select(it.id); return@launch }
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
        errorMessage = message
        errorDocumentId = documentId
    }

    fun dismissError() {
        errorMessage = null
        errorDocumentId = null
    }
}
