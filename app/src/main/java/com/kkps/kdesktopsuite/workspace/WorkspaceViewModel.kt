package com.kkps.kdesktopsuite.workspace

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

data class StarterDocument(val id: String, val title: String, val subtitle: String, val body: String)

val starterDocuments = listOf(
    StarterDocument(
        id = "welcome",
        title = "Welcome to your workspace",
        subtitle = "Start with a little room to think",
        body = "K Desktop Suite\n\nA starting point for your next adaptive Android app.\n\n" +
            "Try editing this document, then resize the window. The list and editor sit side by " +
            "side when there is enough space. In a narrow window, open a document and use Back " +
            "to return to the list.",
    ),
    StarterDocument(
        id = "ideas",
        title = "Ideas for the suite",
        subtitle = "A few possibilities to build on",
        body = "What would you like to build?\n\n" +
            "• A notes workspace\n• A task planner\n• A document library\n\n" +
            "Replace these sample documents with your own data source as the project grows.",
    ),
    StarterDocument(
        id = "desktop",
        title = "Try the desktop experience",
        subtitle = "Resize, focus, select, and type",
        body = "Desktop checklist\n\n" +
            "Resize the app from wide to narrow.\n" +
            "Use Tab to move focus and Enter to open a document.\n" +
            "Select text with your mouse and try standard copy/paste shortcuts.\n" +
            "Change the system theme and increase the font size.\n" +
            "Rotate or recreate the activity and check that your edits remain.",
    ),
)

class WorkspaceViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val drafts = mutableStateMapOf<String, String>().apply {
        starterDocuments.forEach { document ->
            put(document.id, savedState[document.id] ?: document.body)
        }
    }

    fun textFor(id: String): String = drafts.getValue(id)

    fun updateText(id: String, text: String) {
        drafts[id] = text
        savedState[id] = text
    }
}
