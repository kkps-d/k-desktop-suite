package com.kkps.kdesktopsuite.notepad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.UUID

internal class NotepadWindow {
    val id: String = UUID.randomUUID().toString()
    val documents = mutableStateListOf<DocumentSession>()
    var activeId by mutableStateOf("")
    var focusRequest by mutableIntStateOf(0)
    var closed by mutableStateOf(false)
    var errorMessage by mutableStateOf<Int?>(null)
    var errorDocumentId by mutableStateOf<String?>(null)
    var blocked: () -> Boolean = { false }
    var prepareTransfer: () -> Unit = {}
    var showDocument: () -> Unit = {}
    var bringToFront: () -> Unit = {}
    var attached by mutableStateOf(false)
    var modalOpen by mutableStateOf(false)

    fun select(id: String, focusEditor: Boolean = true) {
        if (documents.none { it.id == id }) return
        activeId = id
        if (focusEditor) focusRequest++
    }
}

internal data class TabTransfer(val token: String, val sourceId: String, val documentId: String)

/** Owns documents and the single recovery writer independently of Activity lifetimes. */
@OptIn(FlowPreview::class)
internal class NotepadWorkspace(private val store: SessionStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val ready = CompletableDeferred<Unit>()
    val windows = mutableStateListOf<NotepadWindow>()
    val recentFiles = mutableStateListOf<RecentFile>()
    var showWindow: (NotepadWindow) -> Unit = {}
    private val main = NotepadWindow().also { windows.add(it) }
    private var claimed = false
    private var focusedId by mutableStateOf(main.id)
    private val transfers = mutableMapOf<String, TabTransfer>()
    private val writeTargets = mutableMapOf<String, String>()
    var dragging by mutableStateOf<TabTransfer?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var zoom by mutableIntStateOf(100)
    var wordWrap by mutableStateOf(true)
    var showStatusBar by mutableStateOf(true)
    var appTheme by mutableStateOf(AppTheme.Auto)
    var fontStyle by mutableStateOf(NoteFontStyle.Monospace)

    init {
        scope.launch {
            try {
                store.read()?.let { snapshot ->
                    main.documents.addAll(snapshot.documents.filter { it.text.isNotEmpty() }.map(::DocumentSession))
                    main.activeId = snapshot.activeId.takeIf { id -> main.documents.any { it.id == id } }
                        ?: main.documents.firstOrNull()?.id.orEmpty()
                    zoom = snapshot.zoom
                    wordWrap = snapshot.wordWrap
                    showStatusBar = snapshot.showStatusBar
                    appTheme = snapshot.appTheme
                    fontStyle = snapshot.fontStyle
                    recentFiles.addAll(snapshot.recentFiles)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                main.errorMessage = R.string.error_restore
            }
            main.focusRequest++
            loading = false
            ready.complete(Unit)
            snapshotFlow { snapshot() }.debounce(500).collect { checkpoint(it) }
        }
    }

    fun acquireWindow(restoredId: String?, newWindow: Boolean): NotepadWindow {
        windows.find { it.id == restoredId && !it.closed }?.let { return it }
        // Stale saved IDs belong to a previous process: merge into the first recovery window.
        if (restoredId != null || !claimed || !newWindow) {
            claimed = true
            return windows.first { !it.closed }
        }
        return NotepadWindow().also { windows.add(it) }
    }

    fun focus(window: NotepadWindow) { if (!window.closed) focusedId = window.id }

    fun rememberFile(uri: String, name: String?) {
        recentFiles.removeAll { it.uri == uri }
        recentFiles.add(0, RecentFile(uri, name))
        while (recentFiles.size > 5) recentFiles.removeAt(recentFiles.lastIndex)
        checkpoint()
    }

    fun addBlank(window: NotepadWindow) {
        val doc = DocumentSession(DocumentSnapshot())
        window.documents.add(doc)
        window.select(doc.id)
    }

    fun ownerOf(id: String): NotepadWindow? = windows.find { window -> window.documents.any { it.id == id } }
    fun findUri(uri: String): DocumentSession? = windows.flatMap { it.documents }
        .find { it.uri == uri || it.id == writeTargets[uri] }

    fun reserveWrite(uri: String, id: String): Boolean {
        if (findUri(uri)?.let { it.id != id } == true) return false
        writeTargets[uri] = id
        return true
    }

    fun releaseWrite(uri: String) { writeTargets.remove(uri) }
    fun document(transfer: TabTransfer): DocumentSession? = windows.find { it.id == transfer.sourceId }
        ?.documents?.find { it.id == transfer.documentId }

    fun selectDocument(doc: DocumentSession) {
        ownerOf(doc.id)?.let { window ->
            window.showDocument()
            window.select(doc.id)
            focus(window)
            bringToFront(window)
        }
    }

    fun bringToFront(window: NotepadWindow) {
        try {
            if (window.attached) window.bringToFront() else showWindow(window)
        } catch (_: RuntimeException) {
            window.errorMessage = R.string.error_window
        }
    }

    fun reorder(window: NotepadWindow, id: String, index: Int): Boolean {
        val from = window.documents.indexOfFirst { it.id == id }
        if (from < 0 || window.closed) return false
        Snapshot.withMutableSnapshot {
            val doc = window.documents.removeAt(from)
            window.documents.add(index.coerceIn(0, window.documents.size), doc)
            window.select(id)
        }
        checkpoint()
        return true
    }

    fun canTransfer(window: NotepadWindow, id: String): Boolean = !loading && !window.closed &&
        !window.blocked() && !window.modalOpen && window.documents.any { it.id == id && !it.saving }

    fun beginTransfer(window: NotepadWindow, id: String): TabTransfer? {
        if (!canTransfer(window, id)) return null
        // A new gesture invalidates any abandoned launch tokens from earlier gestures.
        transfers.clear()
        return TabTransfer(UUID.randomUUID().toString(), window.id, id).also {
            transfers[it.token] = it
            dragging = it
            checkpoint()
        }
    }

    fun transfer(token: String): TabTransfer? = transfers[token]

    fun endDrag() { dragging = null }

    fun cancelTransfer(token: String) {
        transfers.remove(token)
        if (dragging?.token == token) dragging = null
    }

    fun move(token: String, destination: NotepadWindow, index: Int, newWindow: Boolean = false): Boolean {
        val transfer = transfers[token] ?: return false
        val source = windows.find { it.id == transfer.sourceId } ?: return false
        if (!canTransfer(source, transfer.documentId) || destination.closed || destination.blocked() || destination.modalOpen) return false
        if (source === destination) {
            cancelTransfer(token)
            return reorder(source, transfer.documentId, index)
        }
        if (newWindow && source.documents.size == 1) return false
        val doc = document(transfer) ?: return false
        source.prepareTransfer()
        destination.prepareTransfer()
        Snapshot.withMutableSnapshot {
            val sourceIndex = source.documents.indexOf(doc)
            source.documents.remove(doc)
            if (source.activeId == doc.id) {
                source.documents.getOrNull(sourceIndex.coerceAtMost(source.documents.lastIndex))?.let { source.select(it.id) }
            }
            // A newly launched transfer destination has no documents of its own.
            if (newWindow) destination.documents.clear()
            destination.documents.add(index.coerceIn(0, destination.documents.size), doc)
            destination.showDocument()
            destination.select(doc.id)
            if (source.errorDocumentId == doc.id) {
                destination.errorDocumentId = doc.id
                destination.errorMessage = source.errorMessage
                source.errorDocumentId = null
                source.errorMessage = null
            }
            if (source.documents.isEmpty()) source.closed = true
        }
        focus(destination)
        cancelTransfer(token)
        checkpoint()
        bringToFront(destination)
        return true
    }

    fun abandonWindow(window: NotepadWindow) {
        window.closed = true
        window.documents.clear()
    }

    fun snapshot(): SessionSnapshot {
        val documents = windows.filter { !it.closed }.flatMap { it.documents }
        val active = windows.find { it.id == focusedId && !it.closed }?.activeId
            ?.takeIf { id -> documents.any { it.id == id } } ?: documents.firstOrNull()?.id.orEmpty()
        return SessionSnapshot(documents.map { it.snapshot() }, active, zoom,
            wordWrap, showStatusBar, appTheme, fontStyle, recentFiles.toList())
    }

    fun checkpoint() { if (!loading) checkpoint(snapshot()) }

    private fun checkpoint(snapshot: SessionSnapshot) {
        store.checkpoint(snapshot) { succeeded ->
            if (!succeeded) windows.filter { !it.closed && it.errorMessage == null }.forEach {
                it.errorMessage = R.string.error_recovery
            }
        }
    }

    fun close() = scope.cancel()
}
