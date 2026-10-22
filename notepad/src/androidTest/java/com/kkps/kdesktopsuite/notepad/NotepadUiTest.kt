@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class NotepadUiTest {
    @get:Rule
    val compose = createComposeRule()
    private var windowSize by mutableStateOf(DpSize(1200.dp, 900.dp))
    private lateinit var notepad: NotepadViewModel

    private fun showEditor(largeFontRtl: Boolean = false, separatingFold: Boolean = false) {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ui-${UUID.randomUUID()}.json")
        compose.setContent {
            notepad = remember { NotepadViewModel(FakeDocuments(), SessionStore(file), SavedStateHandle()) }
            DisposableEffect(notepad) {
                val owner = ViewModelStore().apply { put("notepad", notepad) }
                onDispose { owner.clear() }
            }
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(windowSize)) {
                val configuration = if (largeFontRtl) {
                    DeviceConfigurationOverride.FontScale(2f) then
                        DeviceConfigurationOverride.LayoutDirection(LayoutDirection.Rtl) then
                        DeviceConfigurationOverride.DarkMode(true)
                } else DeviceConfigurationOverride.FontScale(1f)
                DeviceConfigurationOverride(configuration) {
                    val adaptive = currentWindowAdaptiveInfoV2()
                    val posture = if (separatingFold) Posture(hingeList = listOf(
                        HingeInfo(Rect(580f, 0f, 620f, 900f), isFlat = false, isVertical = true, isSeparating = true, isOccluding = true),
                    )) else adaptive.windowPosture
                    NotepadTheme {
                        NotepadScreen(notepad, contentInsets = WindowInsets(0), adaptive = WindowAdaptiveInfo(adaptive.windowSizeClass, posture))
                    }
                }
            }
        }
        compose.waitUntil(5_000) { !notepad.loading }
    }

    @Test
    fun tabsPreserveIndependentTextAndCloseCanBeCancelledOrDiscarded() {
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement("First document")
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Second document")
        compose.onNodeWithContentDescription("Untitled 1, unsaved changes").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First document")
        compose.onNodeWithContentDescription("Close Untitled 1").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First document")
        compose.onNodeWithContentDescription("Close Untitled 1").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("Second document")
    }

    @Test
    fun compactShortAndExtraLargeWindowsKeepTheEditorAndCommandsAvailable() {
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement("မြန်မာ text across sizes 👋")
        listOf(DpSize(400.dp, 800.dp), DpSize(1000.dp, 400.dp), DpSize(1800.dp, 1000.dp)).forEach { size ->
            compose.runOnIdle { windowSize = size }
            compose.onNodeWithTag("document-editor").assertIsDisplayed().assertTextContains("မြန်မာ text across sizes 👋")
            compose.onNodeWithContentDescription("New document").assertIsDisplayed()
            compose.onNodeWithText(if (size.width < 600.dp || size.height < 480.dp) "Commands" else "File").performClick()
            compose.onNodeWithText("Save as…").assertIsDisplayed()
            compose.onNodeWithText("Save as…").performKeyInput { pressKey(Key.Escape) }
        }
    }

    @Test
    fun keyboardCommandsCreateCycleCloseAndZoomWithoutChangingText() {
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement("Keep this")
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.N); keyUp(Key.CtrlLeft) }
        compose.onNodeWithTag("document-editor").assertTextEquals("")
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Tab); keyUp(Key.CtrlLeft) }
        compose.onNodeWithTag("document-editor").assertTextContains("Keep this")
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Equals); keyUp(Key.CtrlLeft) }
        compose.runOnIdle { org.junit.Assert.assertEquals(110, notepad.zoom) }
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Zero); keyUp(Key.CtrlLeft) }
        compose.runOnIdle { org.junit.Assert.assertEquals(100, notepad.zoom) }
        compose.onNodeWithTag("document-editor").assertTextContains("Keep this")
    }

    @Test
    fun menuClipboardEditsParticipateInUndoAndRedo() {
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement("Copy me")
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Select all").performClick()
        compose.runOnIdle { org.junit.Assert.assertFalse(notepad.active!!.editor.selection.collapsed) }
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Copy").performClick()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            org.junit.Assert.assertEquals("Copy me", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Paste").performClick()
        compose.waitUntil(5_000) { notepad.active!!.editor.text.toString() == "Copy me" }
        compose.onNodeWithTag("document-editor").assertTextContains("Copy me")
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithTag("document-editor").assertTextEquals("")
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Redo").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("Copy me")
    }

    @Test
    fun largeFontsRtlAndDarkAppearanceRetainUsableEditorAndCommands() {
        windowSize = DpSize(400.dp, 800.dp)
        showEditor(largeFontRtl = true)
        compose.onNodeWithTag("document-editor").performTextReplacement("مرحبا မြန်မာ 👋")
        compose.onNodeWithTag("document-editor").assertIsDisplayed().assertTextContains("مرحبا မြန်မာ 👋")
        compose.onNodeWithText("Commands").performClick()
        compose.onNodeWithText("Save as…").assertIsDisplayed()
    }

    @Test
    fun separatingFoldKeepsEditorAndControlsInAnUnobstructedRegion() {
        showEditor(separatingFold = true)
        compose.onNodeWithTag("document-editor").performTextReplacement("Document next to fold")
        compose.onNodeWithTag("document-editor").assertIsDisplayed().assertTextContains("Document next to fold")
        compose.onNodeWithContentDescription("New document").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("document-editor").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(bounds.right <= 580f || bounds.left >= 620f)
    }

    @Test
    fun switchingTabsPreservesActualEditorScrollAndSelection() {
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement((1..150).joinToString("\n") { "Line $it" })
        compose.onNodeWithTag("document-editor").performTouchInput { swipeUp() }
        var scroll = 0
        var selection = androidx.compose.ui.text.TextRange.Zero
        compose.runOnIdle {
            scroll = notepad.active!!.scroll.value
            selection = notepad.active!!.editor.selection
            org.junit.Assert.assertTrue(scroll > 0)
        }
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Other tab")
        compose.onNodeWithContentDescription("Untitled 1, unsaved changes").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(scroll, notepad.active!!.scroll.value)
            org.junit.Assert.assertEquals(selection, notepad.active!!.editor.selection)
        }
    }
}
