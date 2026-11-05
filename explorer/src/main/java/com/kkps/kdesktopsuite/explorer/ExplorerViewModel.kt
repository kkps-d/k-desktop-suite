package com.kkps.kdesktopsuite.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import java.util.UUID

internal data class ExplorerTab(val id: String = UUID.randomUUID().toString(), val number: Int)

/** Each window owns its tabs; the workspace coordinates acknowledged transfers. */
internal class ExplorerViewModel(
    private val savedState: SavedStateHandle,
    val workspace: ExplorerWorkspace = ExplorerWorkspace(),
) : ViewModel() {
    val windowId: String = savedState.get<String>("windowId") ?: UUID.randomUUID().toString()
    var tabs by mutableStateOf(savedState.get<ArrayList<String>>("tabIds")?.mapIndexed { index, id ->
        ExplorerTab(id, savedState.get<ArrayList<Int>>("tabNumbers")?.getOrNull(index) ?: index + 1)
    } ?: listOf(ExplorerTab(number = 1)))
        private set
    var activeId by mutableStateOf(savedState.get<String>("active")?.takeIf { id -> tabs.any { it.id == id } }
        ?: tabs.firstOrNull()?.id.orEmpty())
        private set
    private var nextNumber = savedState.get<Int>("next") ?: 2
    var attached by mutableStateOf(false)
    var closed by mutableStateOf(false)
    var modalOpen by mutableStateOf(false)
    var bringToFront: () -> Unit = {}
    var prepareTransfer: () -> Unit = {}

    init { workspace.windows.add(this); save() }

    fun newTab() {
        val tab = ExplorerTab(number = nextNumber++)
        tabs = tabs + tab
        select(tab.id)
    }

    fun select(id: String) {
        if (tabs.none { it.id == id }) return
        activeId = id
        save()
    }

    fun selectRelative(offset: Int) {
        if (tabs.isNotEmpty()) select(tabs[Math.floorMod(tabs.indexOfFirst { it.id == activeId } + offset, tabs.size)].id)
    }

    fun close(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        tabs = tabs.filterNot { it.id == id }
        if (activeId == id) activeId = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.id.orEmpty()
        save()
    }

    fun reorder(id: String, index: Int): Boolean {
        val tab = tabs.find { it.id == id } ?: return false
        tabs = tabs.filterNot { it.id == id }.toMutableList().apply { add(index.coerceIn(0, size), tab) }
        select(id)
        return true
    }

    fun canTransfer(id: String) = !closed && !modalOpen && tabs.any { it.id == id }

    fun receive(tab: ExplorerTab, index: Int, newWindow: Boolean) {
        val current = if (newWindow) emptyList() else tabs
        tabs = current.toMutableList().apply { add(index.coerceIn(0, size), tab) }
        nextNumber = maxOf(nextNumber, tab.number + 1)
        select(tab.id)
    }

    private fun save() {
        savedState["windowId"] = windowId
        savedState["tabIds"] = ArrayList(tabs.map { it.id })
        savedState["tabNumbers"] = ArrayList(tabs.map { it.number })
        savedState["active"] = activeId
        savedState["next"] = nextNumber
    }
}
