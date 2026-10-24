package com.kkps.kdesktopsuite.notepad

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.LayoutDirection

internal fun tabMoveActions(
    notepad: NotepadViewModel, id: String, left: String, right: String, window: String,
    moveToWindow: (String) -> Unit, rtl: Boolean = false,
): List<CustomAccessibilityAction> {
    val index = notepad.documents.indexOfFirst { it.id == id }
    if (index < 0) return emptyList()
    val leftIndex = index + if (rtl) 1 else -1
    val rightIndex = index + if (rtl) -1 else 1
    return buildList {
        if (leftIndex in notepad.documents.indices) add(CustomAccessibilityAction(left) { notepad.reorder(id, leftIndex) })
        if (rightIndex in notepad.documents.indices) add(CustomAccessibilityAction(right) { notepad.reorder(id, rightIndex) })
        if (notepad.canTransfer(id)) add(CustomAccessibilityAction(window) { moveToWindow(id); true })
    }
}

@Composable
internal fun TabMoveMenu(notepad: NotepadViewModel?, id: String, moveToWindow: (String) -> Unit, dismiss: () -> Unit) {
    if (notepad == null) return
    val actions = tabMoveActions(notepad, id, stringResource(R.string.move_tab_left),
        stringResource(R.string.move_tab_right), stringResource(R.string.move_to_window), moveToWindow,
        LocalLayoutDirection.current == LayoutDirection.Rtl)
    actions.forEach { action ->
        NotepadMenuItem(text = { Text(action.label) }, onClick = { dismiss(); action.action() })
    }
}
