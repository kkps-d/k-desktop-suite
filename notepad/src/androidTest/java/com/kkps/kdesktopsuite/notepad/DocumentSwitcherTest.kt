@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kkps.kdesktopsuite.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DocumentSwitcherTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var size by mutableStateOf(DpSize(400.dp, 800.dp))
    private var documents by mutableStateOf((1..5).map { DocumentTab("$it", "Document $it") })
    private var activeId by mutableStateOf("1")
    private var nativeDrags = 0
    private var reorders = 0
    private var nextId = 6
    private var movedId: String? = null
    private lateinit var inputMode: InputModeManager
    private var caption by mutableStateOf(DesktopCaption())

    private fun show(largeRtl: Boolean = false) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(size)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (largeRtl) 2f else 1f) then
                    DeviceConfigurationOverride.LayoutDirection(if (largeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                    NotepadTheme(if (largeRtl) AppTheme.Dark else AppTheme.Light) {
                        val density = LocalDensity.current
                        inputMode = LocalInputModeManager.current
                        val region = with(density) { Rect(0f, 0f, size.width.toPx(), size.height.toPx()) }
                        Column(Modifier.fillMaxSize()) {
                            SuiteTabHeader(documents, activeId, false, { id, _ -> activeId = id },
                                close = { id ->
                                    documents = documents.filterNot { it.id == id }
                                    if (id == activeId) activeId = documents.firstOrNull()?.id.orEmpty()
                                }, new = {
                                    val id = "${nextId++}"
                                    documents = documents + DocumentTab(id, "Document $id")
                                    activeId = id
                                }, caption = caption, region = region,
                                interactions = DocumentTabInteractions(null, "test/tabs", { nativeDrags++; null }, {}, {},
                                    { _, _ -> error("Local reordering must not transfer tabs") },
                                    { id, index ->
                                        val tab = documents.first { it.id == id }
                                        documents = documents.filterNot { it.id == id }.toMutableList().apply { add(index, tab) }
                                        activeId = id
                                        reorders++
                                        true
                                    }, { true }), moveToWindow = { movedId = it })
                        }
                    }
                }
            }
        }
    }

    private fun open() = compose.onNodeWithTag("document-switcher").performClick()
    private fun tab(id: String) = compose.onNodeWithTag("document-tab-$id")
    private fun capturePanel(name: String) {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        file.outputStream().use { compose.onNodeWithTag("document-switcher-panel").captureToImage()
            .asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun dragHandle(id: String, position: Offset) {
        val handle = compose.onNodeWithTag("document-handle-$id").fetchSemanticsNode().boundsInRoot.center
        val bounds = compose.onNodeWithTag("document-switcher-list").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("document-switcher-list").performTouchInput {
            swipe(handle - bounds.topLeft, position - bounds.topLeft, durationMillis = 600)
        }
    }

    @Test fun panelFitsContentUpToSixtyPercentWithStickyFooterAndBothNewActions() {
        show()
        compose.onNodeWithContentDescription("New document").performClick()
        compose.runOnIdle { assertEquals("6", activeId) }
        open()
        compose.onNodeWithTag("document-switcher-panel").assertHeightIsEqualTo(337.dp)
        compose.onNodeWithTag("document-switcher-new").assertIsDisplayed()
        tab("6").assertIsDisplayed().assertIsSelected()
        capturePanel("tabs-dropdown-light.png")
        compose.onNodeWithTag("document-switcher-new").performClick()
        compose.onNodeWithTag("document-switcher-panel").assertDoesNotExist()
        compose.runOnIdle { assertEquals("7", activeId); documents = listOf(documents.last()) }
        open()
        compose.onNodeWithTag("document-switcher-panel").assertHeightIsEqualTo(97.dp)
        compose.onNodeWithTag("document-switcher-new").assertIsDisplayed()
        compose.runOnIdle { documents = (1..30).map { DocumentTab("$it", "Document $it") }; activeId = "1" }
        compose.onNodeWithTag("document-switcher-panel").assertHeightIsEqualTo(480.dp)
        val footer = compose.onNodeWithTag("document-switcher-new").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("document-switcher-list").performScrollToIndex(29)
        tab("30").assertIsDisplayed()
        assertEquals(footer, compose.onNodeWithTag("document-switcher-new").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun breakpointResizePreservesSelectionAndDismissesPopup() {
        size = DpSize(599.dp, 800.dp)
        show()
        open()
        tab("3").performClick()
        open()
        compose.runOnIdle { size = DpSize(600.dp, 800.dp) }
        compose.onNodeWithTag("document-switcher-panel").assertDoesNotExist()
        compose.onNodeWithTag("document-tabs").assertIsDisplayed()
        tab("3").assertIsSelected()
        compose.runOnIdle { size = DpSize(400.dp, 400.dp) }
        open()
        compose.onNodeWithTag("document-switcher-panel").assertHeightIsEqualTo(240.dp)
        tab("3").assertIsSelected().assertIsDisplayed()
    }

    @Test fun welcomeUsesTheSameDropdownAndHeaderLeavesSpaceBelowTheTrigger() {
        documents = emptyList()
        activeId = ""
        show()
        val trigger = compose.onNodeWithTag("document-switcher")
            .assertContentDescriptionEquals("Switch document: Welcome").getUnclippedBoundsInRoot()
        val header = compose.onNodeWithTag("document-header").getUnclippedBoundsInRoot()
        assertEquals(8f, (header.bottom - trigger.bottom).value, 1f)
        open()
        compose.onNodeWithTag("welcome-tab").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("document-switcher-panel").assertHeightIsEqualTo(97.dp)
        compose.onNodeWithTag("document-switcher-new").performClick()
        compose.runOnIdle { assertEquals("6", activeId); assertEquals(1, documents.size) }
    }

    @Test fun activeHighlightHasNoCheckmarkAndRowActionsStillReorder() {
        show()
        open()
        tab("1").assertIsSelected()
        compose.onAllNodesWithText("✓").assertCountEquals(0)
        compose.onNodeWithTag("document-actions-2").performClick()
        compose.onNodeWithTag("document-tab-actions-menu").assertIsDisplayed()
        compose.onNodeWithText("Move up").performClick()
        compose.runOnIdle { assertEquals("2", documents.first().id) }
        compose.onNodeWithTag("document-switcher-panel").assertIsDisplayed()
    }

    @Test fun draggingLiftsTheRowWhileNeighborsAnimateWithoutABlueBorder() {
        show()
        open()
        val listNode = compose.onNodeWithTag("document-switcher-list")
        val viewport = listNode.fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("document-handle-1").fetchSemanticsNode().boundsInRoot.center - viewport.topLeft
        val target = compose.onNodeWithTag("document-entry-3").fetchSemanticsNode().boundsInRoot
        val before = tab("2").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        listNode.performTouchInput { down(handle); moveTo(Offset(handle.x, target.bottom - viewport.top - 4), delayMillis = 100) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("document-drag-preview", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("tab-drag-placeholder").assertDoesNotExist()
        val during = tab("2").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(1_000)
        val after = tab("2").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Neighbor must animate toward its new position", after < during && during < before)
        listNode.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(listOf("1", "2", "3", "4", "5"), documents.map { it.id }) }
    }

    @Test fun holdingADragAtEitherEdgeScrollsAndCommitsBeyondTheOriginalViewport() {
        documents = (1..30).map { DocumentTab("$it", "Document $it") }
        show()
        open()
        val listNode = compose.onNodeWithTag("document-switcher-list")
        fun edgeDrag(bottom: Boolean) {
            val viewport = listNode.fetchSemanticsNode().boundsInRoot
            val handle = compose.onNodeWithTag("document-handle-1").fetchSemanticsNode().boundsInRoot.center - viewport.topLeft
            compose.mainClock.autoAdvance = false
            listNode.performTouchInput { down(handle); moveTo(Offset(handle.x, if (bottom) viewport.height - 4 else 4f), delayMillis = 100) }
            compose.mainClock.advanceTimeBy(1_000)
            listNode.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
        }
        edgeDrag(true)
        var lowerIndex = 0
        compose.runOnIdle { lowerIndex = documents.indexOfFirst { it.id == "1" }; assertTrue(lowerIndex > 5) }
        listNode.performScrollToIndex(lowerIndex)
        edgeDrag(false)
        compose.runOnIdle { assertTrue(documents.indexOfFirst { it.id == "1" } < lowerIndex - 3); assertEquals(0, nativeDrags) }
    }

    @Test fun handleReordersLocallyAndOutsideReleaseCancels() {
        show()
        open()
        val third = compose.onNodeWithTag("document-entry-3").fetchSemanticsNode().boundsInRoot
        dragHandle("1", Offset(third.center.x, third.bottom - 4))
        compose.runOnIdle { assertEquals(listOf("2", "3", "1", "4", "5"), documents.map { it.id }); assertEquals(1, reorders) }
        compose.onNodeWithTag("document-switcher-panel").assertIsDisplayed()
        val before = documents
        val viewport = compose.onNodeWithTag("document-switcher-list").fetchSemanticsNode().boundsInRoot
        dragHandle("2", Offset(viewport.right + 60, viewport.center.y))
        compose.runOnIdle { assertEquals(before, documents); assertEquals(1, reorders); assertEquals(0, nativeDrags) }
        compose.onNodeWithTag("document-switcher-panel").assertIsDisplayed()
    }

    @Test fun mouseHandleReordersButTriggerDragNeverStartsTransfer() {
        show()
        compose.onNodeWithTag("document-switcher").performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(0f, 200f), delayMillis = 200)
            release()
        }
        compose.runOnIdle { assertEquals(0, nativeDrags); assertEquals(0, reorders) }
        open()
        val listNode = compose.onNodeWithTag("document-switcher-list")
        val viewport = listNode.fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("document-handle-1").fetchSemanticsNode().boundsInRoot.center
        val third = compose.onNodeWithTag("document-entry-3").fetchSemanticsNode().boundsInRoot
        listNode.performMouseInput {
            moveTo(handle - viewport.topLeft)
            press()
            moveTo(Offset(handle.x, third.bottom - 4) - viewport.topLeft, delayMillis = 200)
            release()
        }
        compose.runOnIdle {
            assertEquals(listOf("2", "3", "1", "4", "5"), documents.map { it.id })
            assertEquals(1, reorders)
            assertEquals(0, nativeDrags)
        }
    }

    @Test fun keyboardFocusAndVerticalActionsWorkWithLargeFontsAndRtl() {
        show(largeRtl = true)
        compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        open()
        tab("1").performKeyInput { pressKey(Key.DirectionDown) }
        tab("2").assertIsFocused()
        tab("1").assertIsSelected()
        tab("2").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("2", activeId) }
        open()
        val actions = tab("2").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first { it.label == "Move up" }.action()) }
        compose.runOnIdle { assertEquals("2", documents.first().id) }
        tab("2").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("document-switcher-panel").assertDoesNotExist()
        compose.onNodeWithTag("document-switcher").assertIsFocused()
        open()
        val windowAction = tab("2").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Move to window…" }
        compose.runOnIdle { assertTrue(windowAction.action()) }
        compose.runOnIdle { assertEquals("2", movedId); assertEquals(0, nativeDrags) }
    }

    @Test fun compactCaptionKeepsTouchTargetsAndNarrowRtlStatusActions() {
        size = DpSize(200.dp, 500.dp)
        caption = DesktopCaption(40f, listOf(Rect(0f, 0f, 30f, 40f), Rect(170f, 0f, 200f, 40f)))
        documents = listOf(
            DocumentTab("1", "Long title မြန်မာစာ.txt", "အပြောင်းအလဲများကို သိမ်းဆည်းနေသည်…", dirty = true, saving = true),
            DocumentTab("2", "Long title မြန်မာစာ.txt", "Save failed", dirty = true, error = true),
        )
        show(largeRtl = true)
        compose.onNodeWithTag("document-header").assertTopPositionInRootIsEqualTo(40.dp)
        compose.onNodeWithContentDescription("New document").assertHeightIsAtLeast(48.dp)
        open()
        tab("1").assertContentDescriptionEquals("Long title မြန်မာစာ.txt, unsaved changes")
        compose.onNode(hasContentDescription("Close Long title မြန်မာစာ.txt") and
            hasAnyAncestor(hasTestTag("document-entry-1"))).assertIsNotEnabled()
        capturePanel("tabs-dropdown-rtl-dark.png")
        compose.onNodeWithTag("document-handle-1").performClick()
        compose.onNodeWithText("Move down").performClick()
        compose.runOnIdle { assertEquals("1", documents.last().id) }
    }

    @Test fun handleEdgeScrollsAndCancelledGestureDoesNotCommit() {
        documents = (1..30).map { DocumentTab("$it", "Document $it") }
        show()
        open()
        val listNode = compose.onNodeWithTag("document-switcher-list")
        val viewport = listNode.fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("document-handle-1").fetchSemanticsNode().boundsInRoot.center - viewport.topLeft
        compose.mainClock.autoAdvance = false
        listNode.performTouchInput {
            down(handle)
            moveTo(Offset(handle.x, viewport.height - 4), delayMillis = 100)
        }
        compose.mainClock.advanceTimeBy(1_500)
        listNode.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals((1..30).map { "$it" }, documents.map { it.id })
            assertEquals(0, reorders)
            assertEquals(0, nativeDrags)
        }
        tab("1").assertIsNotDisplayed()
        compose.onNodeWithTag("document-switcher-new").assertIsDisplayed()
    }
}
