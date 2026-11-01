@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SearchInput(val document: DocumentSession?, val text: String, val query: String, val matchCase: Boolean)
internal data class SearchResults(val input: SearchInput, val matches: List<LiteralMatch>)
internal data class SearchReveal(val documentId: String, val range: LiteralMatch, val sequence: Int)
private enum class SearchAction { First, Next, Previous, Replace, ReplaceAll }
private data class SearchRequest(val input: SearchInput, val action: SearchAction, val anchor: Int)
private data class SearchSavedState(val query: String, val replacement: String, val matchCase: Boolean, val visible: Boolean, val expanded: Boolean)

internal class NotepadSearch(
    private val active: () -> DocumentSession?,
    private val savedState: SavedStateHandle,
    scope: CoroutineScope,
    ready: suspend () -> Unit,
) {
    val query = TextFieldState(savedState.get<String>("searchQuery").orEmpty())
    val replacement = TextFieldState(savedState.get<String>("searchReplacement").orEmpty())
    var matchCase by mutableStateOf(savedState.get<Boolean>("searchMatchCase") == true)
    var visible by mutableStateOf(savedState.get<Boolean>("searchVisible") == true)
        private set
    var expanded by mutableStateOf(savedState.get<Boolean>("searchExpanded") == true)
        private set
    var focusRequest by mutableIntStateOf(0)
        private set
    var reveal by mutableStateOf<SearchReveal?>(null)
        private set
    private var scanned by mutableStateOf<SearchResults?>(null)
    private var pending: SearchRequest? = null
    private var replacementNotice by mutableStateOf<Pair<SearchInput, Int>?>(null)
    private var revealSequence = 0
    private fun input(): SearchInput {
        val doc = active()
        return SearchInput(doc, doc?.editor?.text?.toString().orEmpty(), query.text.toString(), matchCase)
    }
    val results: SearchResults? get() = scanned?.takeIf { it.input == input() }
    val replacementCount: Int? get() = replacementNotice?.takeIf { it.first == input() }?.second
    val currentIndex: Int get() {
        val selection = active()?.editor?.selection ?: return -1
        return results?.matches?.indexOfFirst { it.start == selection.min && it.end == selection.max } ?: -1
    }

    init {
        scope.launch {
            snapshotFlow { SearchSavedState(query.text.toString(), replacement.text.toString(), matchCase, visible, expanded) }
                .collect { state ->
                    savedState["searchQuery"] = state.query
                    savedState["searchReplacement"] = state.replacement
                    savedState["searchMatchCase"] = state.matchCase
                    savedState["searchVisible"] = state.visible
                    savedState["searchExpanded"] = state.expanded
                }
        }
        scope.launch {
            ready()
            var previous: SearchInput? = null
            snapshotFlow { input() }.collectLatest { captured ->
                val old = previous
                previous = captured
                if (captured.document == null) close()
                if (old?.document !== captured.document) reveal = null
                if (pending?.input != captured) pending = null
                if (old?.document === captured.document && old != null &&
                    (old.query != captured.query || old.matchCase != captured.matchCase) && pending == null) {
                    pending = SearchRequest(captured, SearchAction.First, captured.document?.editor?.selection?.min ?: 0)
                }
                val matches = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    LiteralSearch.find(captured.text, captured.query, captured.matchCase) { context.ensureActive() }
                }
                if (input() == captured) {
                    scanned = SearchResults(captured, matches)
                    pending?.takeIf { it.input == captured }?.let { request ->
                        pending = null
                        perform(request, matches)
                    }
                }
            }
        }
    }

    fun open(replace: Boolean = false) {
        val doc = active() ?: return
        val selection = doc.editor.selection
        val seed = doc.editor.text.subSequence(selection.min, selection.max).toString()
        if (seed.isNotEmpty() && seed.none { it in "\r\n\u0085\u2028\u2029" }) {
            query.edit { replace(0, length, seed); this.selection = TextRange(0, length) }
        }
        visible = true
        expanded = replace
        focusRequest++
        request(SearchAction.First)
    }

    fun close() { visible = false }
    fun navigate(backwards: Boolean = false) {
        if (query.text.isEmpty()) open() else request(if (backwards) SearchAction.Previous else SearchAction.Next)
    }
    fun replace() = request(SearchAction.Replace)
    fun replaceAll() = request(SearchAction.ReplaceAll)

    private fun request(action: SearchAction) {
        val captured = input()
        if (captured.document == null || captured.query.isEmpty()) return
        replacementNotice = null
        val request = SearchRequest(captured, action, captured.document.editor.selection.min)
        val current = results
        if (current == null) pending = request else perform(request, current.matches)
    }

    private fun select(doc: DocumentSession, match: LiteralMatch?) {
        if (match == null || active() !== doc) return
        doc.editor.edit { selection = TextRange(match.start, match.end) }
        reveal = SearchReveal(doc.id, match, ++revealSequence)
    }

    private fun perform(request: SearchRequest, matches: List<LiteralMatch>) {
        val captured = request.input
        if (input() != captured) return
        val doc = captured.document ?: return
        val selection = doc.editor.selection
        when (request.action) {
            SearchAction.First -> select(doc, LiteralSearch.atOrAfter(matches, request.anchor))
            SearchAction.Next, SearchAction.Previous -> select(doc,
                LiteralSearch.navigate(matches, selection.min, selection.max, request.action == SearchAction.Previous))
            SearchAction.Replace, SearchAction.ReplaceAll -> {
                if (doc.readOnly) return
                val all = request.action == SearchAction.ReplaceAll
                val targets = if (all) matches else matches.filter { it.start == selection.min && it.end == selection.max }
                if (targets.isEmpty()) {
                    if (all) replacementNotice = captured to 0
                    else select(doc, LiteralSearch.navigate(matches, selection.min, selection.max, false))
                    return
                }
                val inserted = replacement.text.toString()
                val caret = targets.last().end + targets.sumOf { inserted.length - (it.end - it.start) }
                doc.editor.edit {
                    targets.asReversed().forEach { match ->
                        if (captured.text.substring(match.start, match.end) != inserted) replace(match.start, match.end, inserted)
                    }
                    this.selection = TextRange(caret)
                }
                val updated = input()
                replacementNotice = updated to targets.size
                if (!all) {
                    pending = SearchRequest(updated, SearchAction.First, caret)
                    if (updated == captured) {
                        pending = null
                        select(doc, LiteralSearch.atOrAfter(matches, caret))
                    }
                } else reveal = SearchReveal(doc.id, LiteralMatch(caret, caret), ++revealSequence)
            }
        }
    }
}
