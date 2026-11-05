package com.kkps.kdesktopsuite.explorer

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ExplorerViewModelTest {
    @Test fun closingKeepsActiveIdentityChoosesRightThenLeftAndReturnsToWelcome() {
        val model = ExplorerViewModel(SavedStateHandle())
        repeat(3) { model.newTab() }
        val tabs = model.tabs.toList()
        model.select(tabs[1].id)
        model.close(tabs[0].id)
        assertEquals(tabs[1].id, model.activeId)
        model.close(tabs[1].id)
        assertEquals(tabs[2].id, model.activeId)
        model.select(tabs[3].id)
        model.close(tabs[3].id)
        assertEquals(tabs[2].id, model.activeId)
        model.close(tabs[2].id)
        assertTrue(model.tabs.isEmpty())
        assertEquals("", model.activeId)
        assertFalse(model.closed)
        model.selectRelative(1)
        model.newTab()
        assertEquals(5, model.tabs.single().number)
    }

    @Test fun savedStateRestoresStableIdsSelectionOrderAndNumbering() {
        val state = SavedStateHandle()
        val model = ExplorerViewModel(state)
        repeat(4) { model.newTab() }
        model.close(model.tabs[3].id)
        model.reorder(model.tabs.last().id, 0)
        model.select(model.tabs[1].id)
        val restored = ExplorerViewModel(SavedStateHandle(state.keys().associateWith { state.get<Any>(it) }))
        assertEquals(model.tabs, restored.tabs)
        assertEquals(model.activeId, restored.activeId)
        assertEquals(model.windowId, restored.windowId)
        restored.newTab()
        assertEquals(6, restored.tabs.last().number)
    }

    @Test fun cyclingWrapsAndUnknownIdsLeaveStateIntact() {
        val model = ExplorerViewModel(SavedStateHandle())
        model.newTab()
        model.selectRelative(1)
        assertEquals(model.tabs.first().id, model.activeId)
        model.selectRelative(-1)
        assertEquals(model.tabs.last().id, model.activeId)
        val before = model.tabs
        model.select("unknown")
        model.close("unknown")
        assertFalse(model.reorder("unknown", 0))
        assertEquals(before, model.tabs)
    }

    @Test fun transferMovesTheSameTabOnceAndClosesOnlyAnEmptySource() {
        val workspace = ExplorerWorkspace()
        val source = ExplorerViewModel(SavedStateHandle(), workspace)
        val destination = ExplorerViewModel(SavedStateHandle(), workspace)
        source.newTab()
        val moved = source.tabs.first()
        val transfer = workspace.beginTransfer(source, moved.id)!!
        assertTrue(workspace.move(transfer.token, destination, 0))
        assertSame(moved, destination.tabs.first())
        assertEquals(moved.id, destination.activeId)
        assertEquals(1, source.tabs.size)
        assertFalse(source.closed)
        assertFalse(workspace.move(transfer.token, destination, 0))
        val last = workspace.beginTransfer(source, source.tabs.single().id)!!
        assertTrue(workspace.move(last.token, destination, destination.tabs.size))
        assertTrue(source.closed)
        assertFalse(destination.closed)
        assertEquals(3, destination.tabs.size)
    }

    @Test fun cancelledStaleBlockedAndSingleTabDetachNeverRemoveSource() {
        val workspace = ExplorerWorkspace()
        val source = ExplorerViewModel(SavedStateHandle(), workspace)
        val destination = ExplorerViewModel(SavedStateHandle(), workspace)
        val tab = source.tabs.single()
        val single = workspace.beginTransfer(source, tab.id)!!
        assertFalse(workspace.move(single.token, destination, 0, newWindow = true))
        workspace.endDrag()
        assertNotNull(workspace.transfer(single.token))
        workspace.cancelTransfer(single.token)
        assertFalse(workspace.move(single.token, destination, 0))
        source.newTab()
        val stale = workspace.beginTransfer(source, tab.id)!!
        val current = workspace.beginTransfer(source, tab.id)!!
        assertFalse(workspace.move(stale.token, destination, 0))
        destination.modalOpen = true
        assertFalse(workspace.move(current.token, destination, 0))
        destination.modalOpen = false
        workspace.cancelTransfer(current.token)
        assertEquals(tab, source.tabs.first())
        assertEquals(2, source.tabs.size)
        assertEquals(1, destination.tabs.size)
    }

    @Test fun newWindowAcknowledgementReplacesOnlyItsInitialBlankTab() {
        val workspace = ExplorerWorkspace()
        val source = ExplorerViewModel(SavedStateHandle(), workspace)
        val destination = ExplorerViewModel(SavedStateHandle(), workspace)
        source.newTab()
        val tab = source.tabs.last()
        val transfer = workspace.beginTransfer(source, tab.id)!!
        assertTrue(workspace.move(transfer.token, destination, 0, newWindow = true))
        assertEquals(listOf(tab), destination.tabs)
        assertEquals(1, source.tabs.size)
        assertFalse(source.closed)
        assertNull(workspace.dragging)
    }
}
