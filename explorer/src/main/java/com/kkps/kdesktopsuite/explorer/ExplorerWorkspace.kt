package com.kkps.kdesktopsuite.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import java.util.UUID

internal data class ExplorerTransfer(val token: String, val sourceId: String, val tabId: String)

internal class ExplorerWorkspace {
    val windows = mutableStateListOf<ExplorerViewModel>()
    private val transfers = mutableMapOf<String, ExplorerTransfer>()
    var dragging by mutableStateOf<ExplorerTransfer?>(null)
        private set

    fun beginTransfer(source: ExplorerViewModel, id: String): ExplorerTransfer? {
        if (!source.canTransfer(id)) return null
        transfers.clear()
        return ExplorerTransfer(UUID.randomUUID().toString(), source.windowId, id).also {
            transfers[it.token] = it
            dragging = it
        }
    }

    fun transfer(token: String) = transfers[token]
    fun source(transfer: ExplorerTransfer) = windows.find { it.windowId == transfer.sourceId && !it.closed }
    fun tab(transfer: ExplorerTransfer) = source(transfer)?.tabs?.find { it.id == transfer.tabId }
    fun endDrag() { dragging = null }
    fun cancelTransfer(token: String) {
        transfers.remove(token)
        if (dragging?.token == token) dragging = null
    }

    fun move(token: String, destination: ExplorerViewModel, index: Int, newWindow: Boolean = false): Boolean {
        val transfer = transfers[token] ?: return false
        val source = source(transfer) ?: return false
        if (!source.canTransfer(transfer.tabId) || destination.closed || destination.modalOpen) return false
        if (source === destination) {
            cancelTransfer(token)
            return source.reorder(transfer.tabId, index)
        }
        if (newWindow && source.tabs.size == 1) return false
        val tab = tab(transfer) ?: return false
        source.prepareTransfer()
        destination.prepareTransfer()
        Snapshot.withMutableSnapshot {
            source.close(tab.id)
            destination.receive(tab, index, newWindow)
            if (source.tabs.isEmpty()) source.closed = true
        }
        cancelTransfer(token)
        if (destination.attached) destination.bringToFront()
        return true
    }
}
