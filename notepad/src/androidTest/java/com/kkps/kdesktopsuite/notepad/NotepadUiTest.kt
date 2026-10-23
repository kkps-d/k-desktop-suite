@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
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
    val compose = createAndroidComposeRule<ComponentActivity>()
    private var windowSize by mutableStateOf(DpSize(1200.dp, 900.dp))
    private var caption by mutableStateOf(DesktopCaption())
    private val compactCaptionWidth = DesktopCaptionDimensions.minTabStripWidth.value - 12f
    private lateinit var notepad: NotepadViewModel
    private lateinit var inputModeManager: InputModeManager

    private fun showEditor(largeFontRtl: Boolean = false, separatingFold: Boolean = false) {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                it.window.insetsController?.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                    android.view.WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                )
            }
        }
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
                    inputModeManager = LocalInputModeManager.current
                    val adaptive = currentWindowAdaptiveInfoV2()
                    val posture = if (separatingFold) Posture(hingeList = listOf(
                        HingeInfo(Rect(580f, 0f, 620f, 900f), isFlat = false, isVertical = true, isSeparating = true, isOccluding = true),
                    )) else adaptive.windowPosture
                    NotepadTheme {
                        val density = LocalDensity.current.density
                        NotepadScreen(notepad, contentInsets = WindowInsets(0), adaptive = WindowAdaptiveInfo(adaptive.windowSizeClass, posture),
                            caption = DesktopCaption(caption.height * density, caption.obstructions.map {
                                Rect(it.left * density, it.top * density, it.right * density, it.bottom * density)
                            }))
                    }
                }
            }
        }
        compose.waitUntil(5_000) { !notepad.loading }
    }

    private fun desktopCaption(freeWidth: Float = 1032f, height: Float = 56f) = DesktopCaption(height, listOf(
        Rect(0f, 0f, 48f, height), Rect(48f + freeWidth, 0f, 1200f, height),
    ))

    @Test
    fun tabsHaveContinuousSlotsAndIndependentHoverFeedback() {
        caption = desktopCaption()
        showEditor()
        compose.onNodeWithContentDescription("New document").performClick()
        val inactive = compose.onNodeWithContentDescription("Untitled 1")
        val active = compose.onNodeWithContentDescription("Untitled 2")
        val first = inactive.getUnclippedBoundsInRoot()
        val second = active.getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(0f, second.left.value - first.right.value, 1f)
        org.junit.Assert.assertEquals(first.bottom.value, second.bottom.value, 1f)
        org.junit.Assert.assertEquals(second.bottom.value,
            compose.onNodeWithTag("command-bar").getUnclippedBoundsInRoot().top.value, 1f)

        fun background(tab: SemanticsNodeInteraction): androidx.compose.ui.graphics.Color {
            val pixels = tab.captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 6]
        }
        val resting = background(inactive)
        inactive.performMouseInput { enter(center); moveTo(center) }
        compose.mainClock.advanceTimeBy(500)
        org.junit.Assert.assertNotEquals(resting, background(inactive))
        inactive.performMouseInput { exit() }
        compose.mainClock.advanceTimeBy(500)
        val selected = background(active)
        active.performMouseInput { enter(center); moveTo(center) }
        compose.mainClock.advanceTimeBy(500)
        org.junit.Assert.assertEquals(selected, background(active))
    }

    @Test
    fun tabsCompressEquallyBeforeScrollingAndNewRemainsReachable() {
        caption = desktopCaption()
        showEditor()
        val new = compose.onNodeWithContentDescription("New document")
        val first = compose.onNodeWithContentDescription("Untitled 1")
        first.assertWidthIsEqualTo(240.dp)
        new.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        org.junit.Assert.assertEquals(8f, first.getUnclippedBoundsInRoot().top.value -
            compose.onNodeWithTag("document-header").getUnclippedBoundsInRoot().top.value, 1f)
        org.junit.Assert.assertEquals(first.getUnclippedBoundsInRoot().right.value, new.getUnclippedBoundsInRoot().left.value, 1f)

        repeat(4) { new.performClick() }
        val viewport = compose.onNodeWithTag("document-tabs")
        (1..5).forEach { index ->
            val bounds = compose.onNodeWithContentDescription("Untitled $index").getUnclippedBoundsInRoot()
            org.junit.Assert.assertEquals(976f / 5, (bounds.right - bounds.left).value, 1f)
            compose.onNodeWithContentDescription("Close Untitled $index").assertIsDisplayed().assertWidthIsEqualTo(48.dp)
        }
        compose.runOnIdle { repeat(15) { notepad.newDocument() } }
        org.junit.Assert.assertEquals(120f,
            (compose.onNodeWithContentDescription("Untitled 20").getUnclippedBoundsInRoot().let { it.right - it.left }).value, 1f)
        val pinned = new.getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(viewport.getUnclippedBoundsInRoot().right.value, pinned.left.value, 1f)
        org.junit.Assert.assertEquals(1024f, pinned.right.value - viewport.getUnclippedBoundsInRoot().left.value, 1f)
        viewport.performTouchInput { swipeRight() }
        org.junit.Assert.assertEquals(pinned, new.getUnclippedBoundsInRoot())
        compose.runOnIdle { notepad.select(notepad.documents.first().id) }
        compose.waitForIdle()
        compose.runOnIdle { notepad.select(notepad.documents.last().id) }
        listOf(200f, 1032f).forEach { width ->
            compose.runOnIdle { caption = desktopCaption(width) }
            val tab = compose.onNodeWithContentDescription("Untitled 20").getUnclippedBoundsInRoot()
            val bounds = viewport.getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue(tab.left >= bounds.left && tab.right <= bounds.right)
            compose.onNodeWithContentDescription("Close Untitled 20").assertIsDisplayed().assertWidthIsEqualTo(48.dp)
        }
    }

    @Test
    fun selectionAndDirtyStateDoNotMoveTitleOrClose() {
        showEditor()
        compose.onNodeWithContentDescription("New document").performClick()
        lateinit var firstId: String
        compose.runOnIdle { firstId = notepad.documents.first().id }
        val title = compose.onNodeWithTag("tab-title-$firstId", useUnmergedTree = true)
        val close = compose.onNodeWithContentDescription("Close Untitled 1")
        val titleBounds = title.getUnclippedBoundsInRoot()
        val closeBounds = close.getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("Untitled 1").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Unsaved draft")
        org.junit.Assert.assertEquals(titleBounds, title.getUnclippedBoundsInRoot())
        org.junit.Assert.assertEquals(closeBounds, close.getUnclippedBoundsInRoot())
        compose.runOnIdle { notepad.reportError(R.string.error_save, firstId) }
        compose.onNodeWithContentDescription("Untitled 1, unsaved changes").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Document needs attention"))
        org.junit.Assert.assertEquals(closeBounds, close.getUnclippedBoundsInRoot())
    }

    @Test
    fun rtlTabsKeepNewAdjacentAndSelectedTabVisibleWithLargeFonts() {
        caption = desktopCaption()
        showEditor(largeFontRtl = true)
        val new = compose.onNodeWithContentDescription("New document")
        org.junit.Assert.assertEquals(compose.onNodeWithContentDescription("Untitled 1").getUnclippedBoundsInRoot().left.value,
            new.getUnclippedBoundsInRoot().right.value, 1f)
        repeat(8) { new.performClick() }
        val viewport = compose.onNodeWithTag("document-tabs")
        org.junit.Assert.assertEquals(viewport.getUnclippedBoundsInRoot().left.value, new.getUnclippedBoundsInRoot().right.value, 1f)
        compose.runOnIdle { caption = desktopCaption(200f) }
        val selected = compose.onNodeWithContentDescription("Untitled 9").getUnclippedBoundsInRoot()
        val bounds = viewport.getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(selected.left >= bounds.left && selected.right <= bounds.right)
        compose.onNodeWithContentDescription("Close Untitled 9").assertIsDisplayed().assertWidthIsEqualTo(48.dp)
    }

    @Test
    fun compactSelectorKeepsNewAndIndividualCloseActionsAvailable() {
        windowSize = DpSize(160.dp, 800.dp)
        showEditor()
        compose.onNodeWithTag("document-tabs").assertDoesNotExist()
        val switcher = compose.onNodeWithTag("document-switcher").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(112f, (switcher.right - switcher.left).value, 1f)
        val new = compose.onNodeWithContentDescription("New document").assertIsDisplayed().getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(48f, (new.right - new.left).value, 1f)
        compose.onNodeWithTag("document-editor").performTextReplacement("First draft")
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("Second draft")
        compose.onNodeWithTag("document-switcher").performClick()
        compose.onNodeWithContentDescription("Untitled 1, unsaved changes").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First draft")
        compose.onNodeWithTag("document-switcher").performClick()
        // Closing an inactive entry must preserve the active document, including on cancellation.
        compose.onNodeWithContentDescription("Close Untitled 2").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First draft")
        compose.onNodeWithTag("document-switcher").performClick()
        compose.onNodeWithContentDescription("Close Untitled 2").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First draft")
        compose.runOnIdle { org.junit.Assert.assertEquals(1, notepad.documents.size) }
    }

    @Test
    fun captionAndCompactModeChangesPreserveSelectionAndScrollAndDismissMenus() {
        caption = desktopCaption()
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement((1..150).joinToString("\n") { "Line $it" })
        compose.onNodeWithTag("document-editor").performTouchInput { swipeUp() }
        var scroll = 0
        var selection = androidx.compose.ui.text.TextRange.Zero
        compose.runOnIdle {
            scroll = notepad.active!!.scroll.value
            selection = notepad.active!!.editor.selection
            org.junit.Assert.assertTrue(scroll > 0)
            caption = desktopCaption(compactCaptionWidth)
        }
        // Insufficient caption space uses a full-width tab row below the system controls.
        val header = compose.onNodeWithTag("document-header").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(56f, header.top.value, 1f)
        compose.onNodeWithTag("document-tabs").assertIsDisplayed()
        compose.runOnIdle { windowSize = DpSize(160.dp, 800.dp) }
        compose.onNodeWithTag("document-switcher").performClick()
        compose.onNodeWithContentDescription("Close Untitled 1").assertIsDisplayed()
        compose.runOnIdle { windowSize = DpSize(1200.dp, 900.dp); caption = desktopCaption() }
        compose.onNodeWithTag("document-switcher").assertDoesNotExist()
        compose.onNodeWithTag("document-tabs").assertIsDisplayed()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(scroll, notepad.active!!.scroll.value)
            org.junit.Assert.assertEquals(selection, notepad.active!!.editor.selection)
            caption = DesktopCaption()
        }
        compose.onNodeWithTag("document-tabs").assertIsDisplayed()
        compose.onNodeWithTag("document-editor").assertTextContains("Line 150", substring = true)
    }

    @Test
    fun shortCaptionFallsBackBelowControlsWithoutShrinkingTouchTargets() {
        caption = desktopCaption(height = 40f)
        showEditor(largeFontRtl = true)
        val header = compose.onNodeWithTag("document-header").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(header.top >= 40.dp)
        compose.onNodeWithContentDescription("New document").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("مرحبا မြန်မာ 👋")
        compose.runOnIdle { caption = desktopCaption() }
        compose.onNodeWithTag("document-editor").assertTextContains("مرحبا မြန်မာ 👋")
        compose.onNodeWithTag("document-tabs").assertIsDisplayed()
    }

    @Test
    fun arrowNavigationMovesFocusWithoutSelectingUntilActivation() {
        showEditor()
        compose.onNodeWithContentDescription("New document").performClick()
        val first = compose.onNodeWithContentDescription("Untitled 1")
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        first.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        first.assertIsFocused()
        first.performKeyInput { pressKey(Key.DirectionRight) }
        val second = compose.onNodeWithContentDescription("Untitled 2")
        second.assertIsFocused()
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents[1].id, notepad.activeId) }
        second.performKeyInput { pressKey(Key.DirectionLeft) }
        first.assertIsFocused()
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents[1].id, notepad.activeId) }
        first.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents[0].id, notepad.activeId) }
        compose.onNodeWithTag("document-editor").assertIsFocused()
        compose.runOnIdle {
            repeat(18) { notepad.newDocument() }
            notepad.select(notepad.documents.first().id)
        }
        first.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        first.performKeyInput { pressKey(Key.DirectionLeft) }
        val last = compose.onNodeWithContentDescription("Untitled 20")
        last.assertIsFocused()
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents.first().id, notepad.activeId) }
        last.performKeyInput { pressKey(Key.Spacebar) }
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents.last().id, notepad.activeId) }
    }

    @Test
    fun horizontalAndVerticalWheelsScrollTabsWithoutChangingSelection() {
        showEditor()
        compose.runOnIdle {
            repeat(19) { notepad.newDocument() }
            notepad.select(notepad.documents.first().id)
        }
        val viewport = compose.onNodeWithTag("document-tabs")
        fun position() = viewport.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value()
        val start = position()
        viewport.performMouseInput { enter(center); scroll(2f, ScrollWheel.Horizontal) }
        compose.waitForIdle()
        val horizontal = position()
        org.junit.Assert.assertTrue("Horizontal wheel scrolls the tab viewport", horizontal > start)
        viewport.performMouseInput { scroll(2f) }
        compose.waitForIdle()
        org.junit.Assert.assertTrue("Vertical wheel scrolls the tab viewport", position() > horizontal)
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents.first().id, notepad.activeId) }
        compose.onNodeWithContentDescription("New document").assertIsDisplayed()
    }

    @Test
    fun separatorsAvoidSelectionAndSelectedTabMatchesMenuSurface() {
        caption = desktopCaption()
        showEditor()
        repeat(3) { compose.onNodeWithContentDescription("New document").performClick() }
        compose.onNodeWithContentDescription("Untitled 2").performClick()
        val first = compose.onNodeWithContentDescription("Untitled 1").captureToImage().toPixelMap()
        val selected = compose.onNodeWithContentDescription("Untitled 2").captureToImage().toPixelMap()
        val third = compose.onNodeWithContentDescription("Untitled 3").captureToImage().toPixelMap()
        val menu = compose.onNodeWithTag("command-bar").captureToImage().toPixelMap()
        val strip = third[third.width / 2, 2]
        org.junit.Assert.assertEquals(strip, first[first.width - 1, first.height / 2])
        org.junit.Assert.assertNotEquals(strip, third[third.width - 1, third.height / 2])
        org.junit.Assert.assertEquals(menu[menu.width - 1, menu.height / 2], selected[selected.width / 2, 2])
        val screenshot = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "tab-strip-verification.png")
        screenshot.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
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
