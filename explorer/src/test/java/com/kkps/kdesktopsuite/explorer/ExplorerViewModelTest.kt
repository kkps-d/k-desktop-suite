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
    @Test fun closingInactiveTabPreservesSelectionAndClosingActiveChoosesRightThenLeft() {
        val model = ExplorerViewModel(SavedStateHandle())
        repeat(3) { model.newTab() }
        model.select(2)
        assertFalse(model.close(1))
        assertEquals(2, model.activeId)
        assertFalse(model.close(2))
        assertEquals(3, model.activeId)
        model.select(4)
        assertFalse(model.close(4))
        assertEquals(3, model.activeId)
        assertTrue(model.close(3))
        assertEquals(listOf(3), model.tabs)
    }

    @Test fun savedStateRestoresStableIdsSelectionAndNextNumber() {
        val handle = SavedStateHandle()
        val model = ExplorerViewModel(handle)
        repeat(4) { model.newTab() }
        model.close(4)
        model.select(2)
        val restored = ExplorerViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        assertEquals(listOf(1, 2, 3, 5), restored.tabs)
        assertEquals(2, restored.activeId)
        restored.newTab()
        assertEquals(6, restored.activeId)
        assertEquals(listOf(1, 2, 3, 5, 6), restored.tabs)
    }

    @Test fun tabCyclingWrapsAndUnknownIdsDoNotChangeState() {
        val model = ExplorerViewModel(SavedStateHandle())
        model.newTab()
        model.selectRelative(1)
        assertEquals(1, model.activeId)
        model.selectRelative(-1)
        assertEquals(2, model.activeId)
        model.select(99)
        assertFalse(model.close(99))
        assertEquals(2, model.activeId)
        assertEquals(listOf(1, 2), model.tabs)
    }

    @Test fun windowsOwnIndependentTabSessions() {
        val first = ExplorerViewModel(SavedStateHandle())
        val second = ExplorerViewModel(SavedStateHandle())
        first.newTab()
        first.newTab()
        second.newTab()
        second.select(1)
        first.close(1)
        assertEquals(listOf(2, 3), first.tabs)
        assertEquals(3, first.activeId)
        assertEquals(listOf(1, 2), second.tabs)
        assertEquals(1, second.activeId)
    }
}
