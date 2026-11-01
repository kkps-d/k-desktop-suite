package com.kkps.kdesktopsuite.notepad

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotepadSearchImeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun composingEnterDoesNotNavigateOrReplaceUntilCompositionFinishes() {
        lateinit var search: NotepadSearch
        lateinit var document: DocumentSession
        var connection: InputConnection? = null
        compose.setContent {
            val scope = rememberCoroutineScope()
            document = remember { DocumentSession(DocumentSnapshot(text = "cat cat")) }
            search = remember { NotepadSearch({ document }, SavedStateHandle(), scope) {} }
            val window = LocalWindowInfo.current
            val focusedWindow = remember(window) { object : WindowInfo by window { override val isWindowFocused = true } }
            val interceptor = remember {
                PlatformTextInputInterceptor { request, _ ->
                    connection = request.createInputConnection(EditorInfo())
                    try { awaitCancellation() } finally { connection = null }
                }
            }
            LaunchedEffect(Unit) { search.open(replace = true) }
            CompositionLocalProvider(LocalWindowInfo provides focusedWindow) {
                InterceptPlatformTextInput(interceptor) {
                    NotepadTheme {
                        FindReplacePanel(search, false, {}, {}, Modifier.heightIn(max = 600.dp))
                    }
                }
            }
        }
        compose.waitUntil(5_000) { connection != null }
        compose.runOnIdle { assertTrue(connection!!.setComposingText("cat", 1)) }
        compose.waitUntil(5_000) { search.results?.matches?.size == 2 }
        compose.runOnIdle { assertNotNull(search.query.composition); assertEquals(TextRange(0, 3), document.editor.selection) }
        compose.onNodeWithTag("search-query").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(TextRange(0, 3), document.editor.selection) }
        compose.runOnIdle { connection!!.finishComposingText() }
        compose.onNodeWithTag("search-query").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(TextRange(4, 7), document.editor.selection) }
        val queryConnection = connection
        compose.onNodeWithTag("search-replacement").performClick()
        compose.waitUntil(5_000) { connection != null && connection !== queryConnection }
        compose.runOnIdle { connection!!.setComposingText("dog", 1); assertNotNull(search.replacement.composition) }
        compose.onNodeWithTag("search-replacement").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("cat cat", document.editor.text.toString()) }
        compose.runOnIdle { connection!!.finishComposingText() }
        compose.onNodeWithTag("search-replacement").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("cat dog", document.editor.text.toString()) }
    }
}
