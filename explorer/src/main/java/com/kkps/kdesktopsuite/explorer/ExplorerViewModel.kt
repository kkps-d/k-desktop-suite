package com.kkps.kdesktopsuite.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Window-local shell state. Tab IDs are stable even when neighboring tabs close. */
internal class ExplorerViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var tabs by mutableStateOf(savedState.get<ArrayList<Int>>("tabs")?.toList() ?: listOf(1))
        private set
    var activeId by mutableIntStateOf(savedState.get<Int>("active")?.takeIf { it in tabs } ?: tabs.first())
        private set
    private var nextId = savedState.get<Int>("next") ?: 2

    fun newTab() {
        val id = nextId++
        tabs = tabs + id
        activeId = id
        save()
    }

    fun select(id: Int) {
        if (id !in tabs) return
        activeId = id
        save()
    }

    fun selectRelative(offset: Int) {
        select(tabs[Math.floorMod(tabs.indexOf(activeId) + offset, tabs.size)])
    }

    /** Returns true when closing the final tab should close its task. */
    fun close(id: Int): Boolean {
        val index = tabs.indexOf(id)
        if (index < 0) return false
        if (tabs.size == 1) return true
        tabs = tabs.filterNot { it == id }
        if (activeId == id) activeId = tabs[index.coerceAtMost(tabs.lastIndex)]
        save()
        return false
    }

    private fun save() {
        savedState["tabs"] = ArrayList(tabs)
        savedState["active"] = activeId
        savedState["next"] = nextId
    }
}
