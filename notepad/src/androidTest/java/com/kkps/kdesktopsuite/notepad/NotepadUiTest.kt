@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
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
    private val repository = FakeDocuments()

    private fun showEditor(largeFontRtl: Boolean = false, separatingFold: Boolean = false, startWithDocument: Boolean = true) {
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
            notepad = remember { NotepadViewModel(repository, SessionStore(file), SavedStateHandle()) }
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
                    NotepadTheme(notepad.appTheme) {
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
        if (startWithDocument) compose.runOnIdle { notepad.newDocument() }
    }

    @Test
    fun welcomeTabIsReplacedByNewAndReturnsAfterTheLastDocumentCloses() {
        showEditor(startWithDocument = false)
        compose.onNodeWithTag("welcome-tab").assertIsDisplayed().assertIsSelected().assertTextEquals("Welcome")
        compose.onNodeWithTag("document-editor").assertDoesNotExist()
        compose.onNodeWithText("No recent files").assertIsDisplayed()
        compose.onNodeWithTag("welcome-new").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("welcome-tab").assertDoesNotExist()
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.W); keyUp(Key.CtrlLeft) }
        compose.onNodeWithTag("welcome-tab").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("welcome-screen").assertIsDisplayed()
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
    }

    @Test
    fun welcomeCommandsSupportKeyboardMenusAndCancelledOpen() {
        showEditor(startWithDocument = false)
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("welcome-new").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onNodeWithTag("welcome-new").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.N); keyUp(Key.CtrlLeft) }
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
        compose.runOnIdle { notepad.requestClose(notepad.activeId) }
        compose.onNodeWithTag("welcome-new").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onNodeWithTag("welcome-new").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.O); keyUp(Key.CtrlLeft) }
        compose.runOnIdle {
            org.junit.Assert.assertEquals(PickerKind.Open, notepad.pickerRequest?.kind)
            notepad.pickerResult(null)
        }
        compose.onNodeWithTag("welcome-open").assertIsEnabled().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(notepad.pickerPending)
            notepad.pickerResult(null)
        }
        compose.onNodeWithText("File").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Open…").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(notepad.pickerPending)
            notepad.pickerResult(null)
        }
        compose.onNodeWithText("File").performClick()
        compose.onNode(hasText("New") and !hasTestTag("welcome-new")).performClick()
        compose.onNodeWithTag("welcome-tab").assertDoesNotExist()
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
    }

    @Test
    fun welcomeColumnsStackBelow600dpAndSurviveResizingAndSettings() {
        showEditor(startWithDocument = false)
        captureWelcome("welcome-desktop.png")
        listOf(1000, 600, 599, 400).forEach { width ->
            compose.runOnIdle { windowSize = DpSize(width.dp, 800.dp) }
            val start = compose.onNodeWithTag("welcome-start").getUnclippedBoundsInRoot()
            val recent = compose.onNodeWithTag("welcome-recent").getUnclippedBoundsInRoot()
            if (width >= 600) {
                org.junit.Assert.assertEquals(start.top.value, recent.top.value, 1f)
                org.junit.Assert.assertTrue(recent.left >= start.right)
                org.junit.Assert.assertEquals((start.right - start.left).value, (recent.right - recent.left).value, 2f)
            } else {
                org.junit.Assert.assertTrue(recent.top >= start.bottom)
            }
        }
        captureWelcome("welcome-phone.png")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("welcome-screen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("welcome-tab").assertIsDisplayed()
        compose.onNodeWithTag("welcome-screen").assertIsDisplayed()
    }

    @Test
    fun recentFilesOpenFromWelcomeAndFailedRecentLeavesWelcomeVisible() {
        repository.put("file:notes.txt", "Saved notes")
        showEditor(startWithDocument = false)
        kotlinx.coroutines.runBlocking { notepad.openUri("file:notes.txt").join() }
        compose.runOnIdle { notepad.requestClose(notepad.activeId) }
        compose.onNodeWithTag("recent-file-file:notes.txt").assertTextEquals("notes.txt").performClick()
        compose.waitUntil(5_000) { notepad.active != null }
        compose.onNodeWithTag("welcome-tab").assertDoesNotExist()
        compose.onNodeWithTag("document-editor").assertTextContains("Saved notes").assertIsNotFocused()
        compose.runOnIdle {
            notepad.requestClose(notepad.activeId)
            repository.files.clear()
        }
        compose.onNodeWithTag("recent-file-file:notes.txt").performClick()
        compose.waitUntil(5_000) { notepad.errorMessage != null }
        compose.onNodeWithTag("welcome-tab").assertIsDisplayed()
        compose.onNodeWithTag("recent-file-file:notes.txt").assertIsDisplayed()
        compose.onNodeWithTag("document-editor").assertDoesNotExist()
    }

    @Test
    fun welcomeSupportsShortWindowsLargeFontsRtlAndNarrowHeaders() {
        windowSize = DpSize(320.dp, 400.dp)
        showEditor(largeFontRtl = true, startWithDocument = false)
        compose.onNodeWithTag("welcome-tab").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("welcome-open").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("No recent files").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { windowSize = DpSize(144.dp, 800.dp) }
        compose.onNodeWithTag("document-switcher").assertDoesNotExist()
        compose.onNodeWithContentDescription("New document").assertIsDisplayed()
        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
    }

    @Test
    fun welcomeHeaderKeepsCaptionClearancesAndAcceptsAMovedDocument() {
        caption = desktopCaption(height = 40f)
        showEditor(startWithDocument = false)
        val welcome = compose.onNodeWithTag("welcome-tab").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(welcome.bottom <= 40.dp)
        lateinit var transfer: TabTransfer
        compose.runOnIdle {
            val source = notepad.workspace.acquireWindow(null, true)
            notepad.workspace.addBlank(source)
            source.documents.single().editor.edit { append("Moved into Welcome") }
            transfer = notepad.workspace.beginTransfer(source, source.activeId)!!
        }
        val area = compose.onNodeWithTag("tab-drop-area").fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            org.junit.Assert.assertTrue(compose.activity.window.decorView.systemGestureExclusionRects.any {
                it.contains((area.right - 2f).toInt(), area.center.y.toInt())
            })
            org.junit.Assert.assertTrue(notepad.workspace.move(transfer.token, notepad.window, 0))
            org.junit.Assert.assertTrue(notepad.workspace.windows.first { it.id == transfer.sourceId }.closed)
        }
        compose.onNodeWithTag("welcome-tab").assertDoesNotExist()
        compose.onNodeWithTag("document-editor").assertTextContains("Moved into Welcome")
    }

    @Test
    fun welcomeRecentListKeepsFiveFilesScrollableWithLargeText() {
        (1..6).forEach { repository.put("file:$it.txt", "Saved file $it") }
        windowSize = DpSize(320.dp, 480.dp)
        showEditor(largeFontRtl = true, startWithDocument = false)
        (1..6).forEach { index ->
            kotlinx.coroutines.runBlocking { notepad.openUri("file:$index.txt").join() }
            compose.runOnIdle { notepad.requestClose(notepad.activeId) }
        }
        compose.onNodeWithTag("recent-file-file:1.txt").assertDoesNotExist()
        compose.onNodeWithTag("recent-file-file:6.txt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recent-file-file:2.txt").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        captureWelcome("welcome-recents-rtl.png")
    }

    private fun captureWelcome(name: String) {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun compactDocumentListHasAccessibleReorderAndWindowCommands() {
        windowSize = DpSize(144.dp, 800.dp)
        showEditor()
        compose.runOnIdle { notepad.newDocument() }
        val first = notepad.documents.first()
        compose.onNodeWithTag("document-switcher").performClick()
        val actions = compose.onNodeWithTag("document-tab-${first.id}")
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle {
            org.junit.Assert.assertTrue(actions.first { it.label == "Move right" }.action())
            org.junit.Assert.assertSame(first, notepad.documents.last())
            org.junit.Assert.assertTrue(actions.first { it.label == "Move to window…" }.action())
        }
        compose.onNodeWithText("New window").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("document-switcher").assertIsDisplayed()
    }

    @Test
    fun rtlMoveLeftCommandUsesPhysicalDirection() {
        showEditor(largeFontRtl = true)
        compose.runOnIdle { notepad.newDocument() }
        val first = notepad.documents.first()
        val actions = compose.onNodeWithTag("document-tab-${first.id}")
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle {
            org.junit.Assert.assertTrue(actions.first { it.label == "Move left" }.action())
            org.junit.Assert.assertSame(first, notepad.documents.last())
        }
    }

    @Test
    fun movingTabWithAccessibleCommandAnimatesNeighborsAndKeepsTheDocument() {
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement("animated draft")
        val first = notepad.active!!
        compose.runOnIdle { notepad.newDocument(); notepad.newDocument() }
        val neighbor = notepad.documents[1]
        val title = compose.onNodeWithTag("tab-title-${neighbor.id}", useUnmergedTree = true)
        val before = title.fetchSemanticsNode().boundsInRoot.left
        val tab = compose.onNodeWithTag("document-tab-${first.id}")
        val action = tab.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            .first { it.label == "Move right" }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { org.junit.Assert.assertTrue(action.action()) }
        compose.mainClock.advanceTimeBy(64)
        val during = title.fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(1_000)
        val after = title.fetchSemanticsNode().boundsInRoot.left
        org.junit.Assert.assertTrue("The neighboring tab should slide through an intermediate position", during < before && during > after)
        org.junit.Assert.assertTrue(after < before)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("document-editor").assertTextContains("animated draft")
        compose.runOnIdle { org.junit.Assert.assertSame(first, notepad.active) }
    }

    @Test
    fun helpAboutShowsAppDetailsAndDismissalKeepsTheDraftAndSelection() {
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement("Draft behind About")
        val activeId = notepad.activeId
        compose.runOnIdle { notepad.active!!.editor.edit { selection = androidx.compose.ui.text.TextRange(2, 7) } }
        compose.onNodeWithText("Help").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("Selection after opening Help",androidx.compose.ui.text.TextRange(2,7),notepad.active!!.editor.selection) }
        compose.onNodeWithText("About KD Notepad").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("Selection while About is open",androidx.compose.ui.text.TextRange(2,7),notepad.active!!.editor.selection) }
        compose.onNodeWithTag("about-dialog").assertIsDisplayed()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText("Developed by kkps-d").assertIsDisplayed()
        compose.onNodeWithText("Source code").assertIsDisplayed()
        val screenshot = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "about-verification.png")
        screenshot.outputStream().use {
            compose.onNodeWithTag("about-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithTag("about-dialog").assertDoesNotExist()
        compose.onNodeWithTag("document-editor").assertTextContains("Draft behind About")
        compose.runOnIdle {
            org.junit.Assert.assertEquals(activeId, notepad.activeId)
            org.junit.Assert.assertEquals(androidx.compose.ui.text.TextRange(2, 7), notepad.active!!.editor.selection)
            inputModeManager.requestInputMode(InputMode.Keyboard)
        }
        compose.onNodeWithText("Help").performClick()
        compose.onNodeWithText("About KD Notepad").performClick()
        compose.onNodeWithText("Dismiss").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onNodeWithText("Dismiss").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("about-dialog").assertDoesNotExist()
        compose.runOnIdle {
            notepad.active!!.editor.undoState.undo()
            org.junit.Assert.assertEquals("", notepad.active!!.editor.text.toString())
        }
    }

    @Test
    fun compactAboutRemainsReachableWithLargeFontsRtlAndShortWindows() {
        windowSize = DpSize(320.dp, 600.dp)
        showEditor(largeFontRtl = true)
        listOf(DpSize(320.dp, 600.dp), DpSize(700.dp, 320.dp)).forEach { size ->
            compose.runOnIdle { windowSize = size }
            compose.onNodeWithText("Help").assertDoesNotExist()
            compose.onNodeWithText("Commands").performClick()
            compose.onNodeWithText("About KD Notepad").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("about-dialog").assertIsDisplayed()
            compose.onNodeWithText("Source code").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Dismiss").assertIsDisplayed()
            val screenshot = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "about-${size.width.value.toInt()}-verification.png")
            screenshot.outputStream().use {
                compose.onNodeWithTag("about-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("about-dialog").assertDoesNotExist()
            compose.onNodeWithTag("document-editor").assertIsDisplayed()
        }
    }

    @Test
    fun editorCaretContrastsWithLightAndDarkSurfaces() {
        showEditor()
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        compose.mainClock.autoAdvance = false
        listOf(AppTheme.Dark, AppTheme.Light).forEach { theme ->
            compose.runOnIdle { notepad.changeAppTheme(theme) }
            val editor = compose.onNodeWithTag("document-editor")
            editor.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            compose.mainClock.advanceTimeBy(100)
            val pixels = editor.captureToImage().toPixelMap()
            val bounds = editor.getUnclippedBoundsInRoot()
            val scale = pixels.width / (bounds.right - bounds.left).value
            val background = pixels[0, 0].luminance()
            var contrast = 1f
            // The empty editor's caret sits just inside its 16dp content padding.
            for (y in (14 * scale).toInt() until (48 * scale).toInt()) {
                for (x in (14 * scale).toInt() until (24 * scale).toInt()) {
                    val foreground = pixels[x, y].luminance()
                    contrast = maxOf(contrast, (maxOf(foreground, background) + 0.05f) / (minOf(foreground, background) + 0.05f))
                }
            }
            org.junit.Assert.assertTrue("The $theme caret should contrast with the editor surface", contrast >= 4.5f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun settingsChangesApplyAndBackKeepsTheActiveDraft() {
        windowSize = DpSize(700.dp, 800.dp)
        showEditor()
        compose.onNodeWithTag("document-editor").performTextReplacement("Keep my draft and undo history")
        val activeId = notepad.activeId
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-page").assertIsDisplayed()
        compose.onNodeWithTag("font-Monospace").assertIsSelected()
        listOf(NoteFontStyle.System, NoteFontStyle.SansSerif, NoteFontStyle.Monospace, NoteFontStyle.Serif).forEach { option ->
            compose.onNodeWithTag("font-${option.name}").performClick().assertIsSelected()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag("font-preview").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            org.junit.Assert.assertEquals(option.fontFamily, layouts.single().layoutInput.style.fontFamily)
        }
        compose.onNodeWithTag("theme-Auto").assertIsOn()
        compose.onNodeWithTag("theme-Light").performClick().assertIsOn()
        val light = compose.onNodeWithTag("settings-page").captureToImage().toPixelMap()[0, 0]
        compose.onNodeWithTag("theme-Dark").performClick().assertIsOn()
        val dark = compose.onNodeWithTag("settings-page").captureToImage().toPixelMap()[0, 0]
        org.junit.Assert.assertNotEquals(light, dark)
        compose.onNodeWithTag("font-Serif").performClick().assertIsSelected()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(AppTheme.Dark, notepad.appTheme)
            org.junit.Assert.assertEquals(NoteFontStyle.Serif, notepad.fontStyle)
        }
        val screenshot = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "settings-verification.png")
        screenshot.outputStream().use {
            compose.onNodeWithTag("settings-page").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("Keep my draft and undo history")
        compose.runOnIdle {
            org.junit.Assert.assertEquals(activeId, notepad.activeId)
            notepad.active!!.editor.undoState.undo()
            org.junit.Assert.assertEquals("", notepad.active!!.editor.text.toString())
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("theme-Auto").performClick().assertIsOn()
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithContentDescription("Back").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onNodeWithContentDescription("Back").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
    }

    @Test
    fun phoneFontChoicesWrapIntoTwoColumnsAndBundledFontsHaveDistinctMetrics() {
        windowSize = DpSize(400.dp, 800.dp)
        showEditor()
        compose.onNodeWithContentDescription("Settings").performClick()
        val system = compose.onNodeWithTag("font-System").getUnclippedBoundsInRoot()
        val mono = compose.onNodeWithTag("font-Monospace").getUnclippedBoundsInRoot()
        val serif = compose.onNodeWithTag("font-Serif").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(system.top.value, mono.top.value, 1f)
        org.junit.Assert.assertTrue(mono.left >= system.right)
        org.junit.Assert.assertTrue(serif.top >= system.bottom)
        compose.onNodeWithTag("font-System").performClick().assertIsSelected()
        compose.onNodeWithTag("font-SansSerif").performClick().assertIsSelected()

        val resources = compose.activity.resources
        val paint = android.graphics.Paint().apply {
            textSize = 32f
            typeface = resources.getFont(R.font.noto_sans_mono)
        }
        org.junit.Assert.assertEquals(paint.measureText("iiii"), paint.measureText("mmmm"), 0.01f)
        paint.typeface = resources.getFont(R.font.noto_serif)
        org.junit.Assert.assertTrue(paint.measureText("mmmm") > paint.measureText("iiii"))
        val serifWidth = paint.measureText("The quick brown fox 0123456789")
        paint.typeface = resources.getFont(R.font.noto_sans)
        org.junit.Assert.assertTrue(paint.measureText("mmmm") > paint.measureText("iiii"))
        org.junit.Assert.assertTrue(kotlin.math.abs(serifWidth - paint.measureText("The quick brown fox 0123456789")) > 1f)
    }

    @Test
    fun narrowLargeFontSettingsScrollAndSupportSystemBack() {
        windowSize = DpSize(320.dp, 600.dp)
        showEditor(largeFontRtl = true)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithTag("theme-Light").assertIsDisplayed().performClick().assertIsOn()
        compose.onNodeWithTag("font-SansSerif").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        val bounds = compose.onNodeWithTag("font-SansSerif").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(bounds.right - bounds.left > 200.dp)
        compose.onNodeWithTag("font-preview").performScrollTo().assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
    }

    @Test
    fun shortDesktopSettingsStayBelowCaptionControls() {
        windowSize = DpSize(700.dp, 320.dp)
        caption = desktopCaption(height = 40f)
        showEditor()
        compose.onNodeWithContentDescription("Settings").performClick()
        org.junit.Assert.assertTrue(compose.onNodeWithTag("settings-page").getUnclippedBoundsInRoot().top >= 40.dp)
        compose.onNodeWithTag("font-SansSerif").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.onNodeWithTag("document-editor").assertIsDisplayed()
    }

    private fun tab(number: Int): SemanticsNodeInteraction =
        compose.onNodeWithTag("document-tab-${notepad.documents[number - 1].id}")

    private fun closeTab(number: Int): SemanticsNodeInteraction {
        val id = notepad.documents[number - 1].id
        return compose.onNode(hasContentDescription("Close Untitled") and hasAnyAncestor(
            hasTestTag("document-tab-$id") or hasTestTag("document-entry-$id")))
    }

    private fun desktopCaption(freeWidth: Float = 1032f, height: Float = 56f) = DesktopCaption(height, listOf(
        Rect(0f, 0f, 48f, height), Rect(48f + freeWidth, 0f, 1200f, height),
    ))

    private fun SemanticsNodeInteraction.assertTargetSize(width: Dp = 48.dp, height: Dp = 48.dp): SemanticsNodeInteraction {
        val bounds = getUnclippedBoundsInRoot()
        // WindowSize overrides can map a dp to less than one physical pixel.
        org.junit.Assert.assertEquals(width.value, (bounds.right - bounds.left).value, 2f)
        org.junit.Assert.assertEquals(height.value, (bounds.bottom - bounds.top).value, 2f)
        return this
    }

    @Test
    fun tabsOverlapAndKeepIndependentHoverFeedback() {
        caption = desktopCaption(height = 40f)
        showEditor()
        compose.onNodeWithContentDescription("New document").performClick()
        val inactive = tab(1)
        val active = tab(2)
        val first = inactive.getUnclippedBoundsInRoot()
        val second = active.getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(-16f, second.left.value - first.right.value, 1f)
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
        val hover = inactive.captureToImage().toPixelMap()
        val selectedPixels = active.captureToImage().toPixelMap()
        val scanY = hover.height / 2
        // Exclude neighboring bodies visible inside the overlapping screenshot edges.
        val edge = (hover.width * 8f / 240f).toInt()
        val hoverBody = (edge until hover.width - edge).filter { hover[it, scanY] != resting }
        val selectedBody = (edge until selectedPixels.width - edge).filter { selectedPixels[it, scanY] != resting }
        org.junit.Assert.assertEquals(selectedBody.first().toFloat(), hoverBody.first().toFloat(), 1f)
        org.junit.Assert.assertEquals(selectedBody.last().toFloat(), hoverBody.last().toFloat(), 1f)
        org.junit.Assert.assertEquals(resting, hover[hover.width / 2, hover.height - 2])
        val screenshot = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "tab-hover-verification.png")
        screenshot.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodesWithText("Untitled").assertCountEquals(2)
        inactive.performMouseInput { exit() }
        compose.mainClock.advanceTimeBy(500)
        val selected = background(active)
        active.performMouseInput { enter(center); moveTo(center) }
        compose.mainClock.advanceTimeBy(500)
        org.junit.Assert.assertEquals(selected, background(active))
    }

    @Test
    fun incomingDragExpandsSingleTabViewportAndCaptionDropRegion() {
        caption = desktopCaption()
        showEditor()
        val viewport = compose.onNodeWithTag("document-tabs")
        val original = viewport.getUnclippedBoundsInRoot()
        lateinit var transfer: TabTransfer
        compose.runOnIdle {
            val source = notepad.workspace.acquireWindow(null, true)
            notepad.workspace.addBlank(source)
            transfer = notepad.workspace.beginTransfer(source, source.documents.single().id)!!
        }
        val expanded = viewport.getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(expanded.right - expanded.left > (original.right - original.left) * 2)
        val dropArea = compose.onNodeWithTag("tab-drop-area").fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            // Unused caption space must reach the app during a tab drag.
            val farEnd = androidx.compose.ui.geometry.Offset(dropArea.right - 2f, dropArea.center.y)
            org.junit.Assert.assertTrue(compose.activity.window.decorView.systemGestureExclusionRects.any {
                it.contains(farEnd.x.toInt(), farEnd.y.toInt())
            })
            notepad.workspace.cancelTransfer(transfer.token)
        }
        org.junit.Assert.assertEquals(original, viewport.getUnclippedBoundsInRoot())
        compose.runOnIdle {
            org.junit.Assert.assertFalse(compose.activity.window.decorView.systemGestureExclusionRects.any {
                it.contains((dropArea.right - 2f).toInt(), dropArea.center.y.toInt())
            })
            org.junit.Assert.assertEquals(1, notepad.documents.size)
        }
    }

    @Test
    fun tabsCompressEquallyBeforeScrollingAndNewRemainsReachable() {
        caption = desktopCaption()
        showEditor()
        val new = compose.onNodeWithContentDescription("New document")
        val first = tab(1)
        val firstBounds = first.getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(240f, (firstBounds.right - firstBounds.left).value, 2f)
        new.assertTargetSize()
        org.junit.Assert.assertEquals(8f, first.getUnclippedBoundsInRoot().top.value -
            compose.onNodeWithTag("document-header").getUnclippedBoundsInRoot().top.value, 1f)
        org.junit.Assert.assertEquals(first.getUnclippedBoundsInRoot().right.value, new.getUnclippedBoundsInRoot().left.value, 1f)

        repeat(4) { new.performClick() }
        val viewport = compose.onNodeWithTag("document-tabs")
        (1..5).forEach { index ->
            val bounds = tab(index).getUnclippedBoundsInRoot()
            org.junit.Assert.assertEquals((976f + 16f * 4) / 5, (bounds.right - bounds.left).value, 1f)
            closeTab(index).assertIsDisplayed().assertTargetSize()
        }
        compose.runOnIdle { repeat(15) { notepad.newDocument() } }
        org.junit.Assert.assertEquals(120f,
            (tab(20).getUnclippedBoundsInRoot().let { it.right - it.left }).value, 1f)
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
            val tab = tab(20).getUnclippedBoundsInRoot()
            val bounds = viewport.getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue(tab.left >= bounds.left && tab.right <= bounds.right)
            closeTab(20).assertIsDisplayed().assertTargetSize()
        }
    }

    @Test
    fun statusOnlyOffsetsTitleWhenDisplayedAndNeverMovesClose() {
        showEditor()
        compose.onNodeWithContentDescription("New document").performClick()
        lateinit var firstId: String
        compose.runOnIdle { firstId = notepad.documents.first().id }
        val title = compose.onNodeWithTag("tab-title-$firstId", useUnmergedTree = true)
        val close = closeTab(1)
        val titleBounds = title.getUnclippedBoundsInRoot()
        val closeBounds = close.getUnclippedBoundsInRoot()
        tab(1).performClick()
        val selectedTitleBounds = title.getUnclippedBoundsInRoot()
        val selectedCloseBounds = close.getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(titleBounds.left.value, selectedTitleBounds.left.value, 2f)
        org.junit.Assert.assertEquals(closeBounds.left.value, selectedCloseBounds.left.value, 2f)
        compose.onNodeWithTag("document-editor").performTextReplacement("Unsaved draft")
        org.junit.Assert.assertEquals(16f, (title.getUnclippedBoundsInRoot().left - selectedTitleBounds.left).value, 2f)
        org.junit.Assert.assertEquals(selectedCloseBounds, close.getUnclippedBoundsInRoot())
        compose.onNodeWithTag("document-editor").performTextReplacement("")
        org.junit.Assert.assertEquals(selectedTitleBounds, title.getUnclippedBoundsInRoot())
        compose.onNodeWithTag("document-editor").performTextReplacement("Unsaved draft")
        compose.runOnIdle { notepad.reportError(R.string.error_save, firstId) }
        tab(1).assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Document needs attention"))
        org.junit.Assert.assertEquals(selectedCloseBounds, close.getUnclippedBoundsInRoot())
    }

    @Test
    fun iconFeedbackIsSmallWhileNewAndCloseKeepFullHitTargets() {
        showEditor()
        listOf("New document" to 20f, "Close Untitled" to 16f).forEach { (label, iconSize) ->
            val button = compose.onNodeWithContentDescription(label)
            button.assertTargetSize()
            val before = button.captureToImage().toPixelMap()
            button.performMouseInput { enter(center); moveTo(center) }
            compose.mainClock.advanceTimeBy(200)
            val after = button.captureToImage().toPixelMap()
            val scale = after.width / 48f
            val inside = (after.width / 2 + (iconSize / 2 + 2) * scale).toInt()
            val outside = (after.width / 2 + (iconSize / 2 + 6) * scale).toInt()
            org.junit.Assert.assertNotEquals(before[inside, before.height / 2], after[inside, after.height / 2])
            org.junit.Assert.assertEquals(before[outside, before.height / 2], after[outside, after.height / 2])
            button.performMouseInput { exit() }
            compose.mainClock.advanceTimeBy(300)
        }
    }

    @Test
    fun iconTooltipsWaitHalfASecondAndCancelWhenPointerLeaves() {
        showEditor()
        compose.mainClock.autoAdvance = false
        val new = compose.onNodeWithContentDescription("New document")
        new.performMouseInput { enter(center); moveTo(center) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("New document").assertDoesNotExist()
        new.performMouseInput { exit() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithText("New document").assertDoesNotExist()
        new.performMouseInput { enter(center); moveTo(center) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("New document").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("New document").assertIsDisplayed()
        new.performMouseInput { exit() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("New document").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun tabsFitTheSystemCaptionAndKeepMenusBelowIt() {
        caption = desktopCaption(height = 40f)
        showEditor()
        val header = compose.onNodeWithTag("document-header").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(0f, header.top.value, 1f)
        org.junit.Assert.assertEquals(40f, (header.bottom - header.top).value, 1f)
        org.junit.Assert.assertEquals(header.bottom.value, compose.onNodeWithTag("command-bar").getUnclippedBoundsInRoot().top.value, 1f)
        compose.onNodeWithContentDescription("New document").assertTargetSize(height = 32.dp).performClick()
        val active = tab(2).getUnclippedBoundsInRoot()
        lateinit var id: String
        compose.runOnIdle { id = notepad.activeId }
        val title = compose.onNodeWithTag("tab-title-$id", useUnmergedTree = true).getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(22f, (title.left - active.left).value, 2f)
        closeTab(2).assertTargetSize(height = 32.dp)
    }

    @Test
    fun viewMenuControlsZoomWrappingAndStatusWithoutChangingDocumentContent() {
        showEditor()
        val editor = compose.onNodeWithTag("document-editor")
        val text = "Long line ".repeat(80) + "\nSecond line"
        editor.performTextReplacement(text)
        var selection = androidx.compose.ui.text.TextRange.Zero
        compose.runOnIdle { selection = notepad.active!!.editor.selection }
        fun lineCount(): Int {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            editor.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().lineCount
        }
        org.junit.Assert.assertTrue(lineCount() > 2)
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Zoom in").assertDoesNotExist()
        compose.onNodeWithText("Copy").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Zoom in").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(110, notepad.zoom) }
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Reset zoom").performClick()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Word wrap").assertIsOn().performClick()
        org.junit.Assert.assertEquals(2, lineCount())
        compose.runOnIdle {
            org.junit.Assert.assertTrue(notepad.active!!.horizontalScroll.maxValue > 0)
            org.junit.Assert.assertEquals(selection, notepad.active!!.editor.selection)
        }
        val before = editor.getUnclippedBoundsInRoot()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Status bar").assertIsOn().performClick()
        compose.onNodeWithTag("status-bar").assertDoesNotExist()
        org.junit.Assert.assertTrue(editor.getUnclippedBoundsInRoot().bottom > before.bottom)
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Word wrap").assertIsOff().performClick()
        org.junit.Assert.assertTrue(lineCount() > 2)
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Status bar").assertIsOff().performClick()
        compose.onNodeWithTag("status-bar").assertIsDisplayed()
        editor.assertTextContains(text)
        compose.runOnIdle { org.junit.Assert.assertEquals(selection, notepad.active!!.editor.selection) }
    }

    @Test
    fun dropdownItemsAre44dpAndPopupHasNoVerticalPadding() {
        showEditor()
        compose.onNodeWithText("File").performClick()
        val first = compose.onNodeWithText("New").getUnclippedBoundsInRoot()
        val last = compose.onNodeWithText("Close").getUnclippedBoundsInRoot()
        val popup = compose.onNodeWithTag("dropdown-menu").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(44f, (first.bottom - first.top).value, 2f)
        org.junit.Assert.assertEquals(popup.top.value, first.top.value, 2f)
        org.junit.Assert.assertEquals(popup.bottom.value, last.bottom.value, 2f)
        org.junit.Assert.assertEquals(6 * 44f, (popup.bottom - popup.top).value, 3f)
    }

    @Test
    fun toolbarKeepsItsHeightAndStatusBarIsShorterWithoutADivider() {
        showEditor()
        val toolbar = compose.onNodeWithTag("command-bar").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(48f, (toolbar.bottom - toolbar.top).value, 1f)
        val file = compose.onNodeWithText("File").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(8f, (file.top - toolbar.top).value, 2f)
        org.junit.Assert.assertEquals(8f, (toolbar.bottom - file.bottom).value, 2f)
        val settings = compose.onNodeWithContentDescription("Settings")
        val settingsBounds = settings.getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(8f, (toolbar.right - settingsBounds.right).value, 2f)
        settings.performClick()
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        compose.runOnIdle { org.junit.Assert.assertEquals(1, notepad.documents.size) }
        compose.onNodeWithTag("settings-page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        val statusBar = compose.onNodeWithTag("status-bar")
        val statusBounds = statusBar.getUnclippedBoundsInRoot()
        statusBar.assertHeightIsAtLeast(28.dp)
        org.junit.Assert.assertTrue(statusBounds.bottom - statusBounds.top < toolbar.bottom - toolbar.top)
        org.junit.Assert.assertEquals(16f,
            (statusBounds.top - compose.onNodeWithTag("document-editor").getUnclippedBoundsInRoot().bottom).value, 1f)
        val strip = compose.onNodeWithTag("document-header").captureToImage().toPixelMap()
        val status = statusBar.captureToImage().toPixelMap()
        org.junit.Assert.assertEquals(strip[strip.width / 2, 0], status[status.width / 2, 0])
        org.junit.Assert.assertEquals(status[status.width / 2, 0], status[status.width / 2, status.height - 1])
    }

    @Test
    fun rtlTabsKeepNewAdjacentAndSelectedTabVisibleWithLargeFonts() {
        caption = desktopCaption()
        showEditor(largeFontRtl = true)
        val new = compose.onNodeWithContentDescription("New document")
        org.junit.Assert.assertEquals(tab(1).getUnclippedBoundsInRoot().left.value,
            new.getUnclippedBoundsInRoot().right.value, 1f)
        repeat(8) { new.performClick() }
        val viewport = compose.onNodeWithTag("document-tabs")
        org.junit.Assert.assertEquals(viewport.getUnclippedBoundsInRoot().left.value, new.getUnclippedBoundsInRoot().right.value, 1f)
        compose.runOnIdle { caption = desktopCaption(200f) }
        val selected = tab(9).getUnclippedBoundsInRoot()
        val bounds = viewport.getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(selected.left >= bounds.left && selected.right <= bounds.right)
        closeTab(9).assertIsDisplayed().assertTargetSize()
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
        tab(1).performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First draft")
        compose.onNodeWithTag("document-switcher").performClick()
        // Closing an inactive entry must preserve the active document, including on cancellation.
        closeTab(2).performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First draft")
        compose.onNodeWithTag("document-switcher").performClick()
        closeTab(2).performClick()
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
        closeTab(1).assertIsDisplayed()
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
        caption = desktopCaption(height = 28f)
        showEditor(largeFontRtl = true)
        val header = compose.onNodeWithTag("document-header").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(28f, header.top.value, 2f)
        compose.onNodeWithContentDescription("New document").assertTargetSize().performClick()
        compose.onNodeWithTag("document-editor").performTextReplacement("مرحبا မြန်မာ 👋")
        compose.runOnIdle { caption = desktopCaption() }
        compose.onNodeWithTag("document-editor").assertTextContains("مرحبا မြန်မာ 👋")
        compose.onNodeWithTag("document-tabs").assertIsDisplayed()
    }

    @Test
    fun arrowNavigationMovesFocusWithoutSelectingUntilActivation() {
        showEditor()
        compose.onNodeWithContentDescription("New document").performClick()
        val first = tab(1)
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        first.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        first.assertIsFocused()
        first.performKeyInput { pressKey(Key.DirectionRight) }
        val second = tab(2)
        second.assertIsFocused()
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents[1].id, notepad.activeId) }
        second.performKeyInput { pressKey(Key.DirectionLeft) }
        first.assertIsFocused()
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents[1].id, notepad.activeId) }
        first.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { org.junit.Assert.assertEquals(notepad.documents[0].id, notepad.activeId) }
        first.assertIsFocused()
        compose.onNodeWithTag("document-editor").assertIsNotFocused()
        compose.runOnIdle {
            repeat(18) { notepad.newDocument() }
            notepad.select(notepad.documents.first().id)
        }
        first.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        first.performKeyInput { pressKey(Key.DirectionLeft) }
        val last = tab(20)
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
        tab(2).performClick()
        val first = tab(1).captureToImage().toPixelMap()
        val selected = tab(2).captureToImage().toPixelMap()
        val third = tab(3).captureToImage().toPixelMap()
        val menu = compose.onNodeWithTag("command-bar").captureToImage().toPixelMap()
        val strip = third[third.width / 2, 2]
        val separatorX = (third.width * (1f - 8f / 240f)).toInt() - 1
        val separatorPixels = separatorX - 1..separatorX + 1
        org.junit.Assert.assertTrue(separatorPixels.all { first[it, first.height / 2] == strip })
        org.junit.Assert.assertTrue(separatorPixels.any { third[it, third.height / 2] != strip })
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
        compose.onAllNodesWithText("Untitled").assertCountEquals(2)
        tab(1).assertContentDescriptionEquals("Untitled, unsaved changes")
        tab(2).assertContentDescriptionEquals("Untitled, unsaved changes")
        tab(1).performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First document")
        closeTab(1).performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("document-editor").assertTextContains("First document")
        closeTab(1).performClick()
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
        compose.onNodeWithTag("document-editor").performClick().assertIsFocused()
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.N); keyUp(Key.CtrlLeft) }
        compose.onNodeWithTag("document-editor").assertTextEquals("").assertIsNotFocused()
        compose.onNodeWithTag("document-editor").performClick().assertIsFocused()
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Tab); keyUp(Key.CtrlLeft) }
        compose.onNodeWithTag("document-editor").assertTextContains("Keep this").assertIsNotFocused()
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Equals); keyUp(Key.CtrlLeft) }
        compose.runOnIdle { org.junit.Assert.assertEquals(110, notepad.zoom) }
        compose.onNodeWithTag("document-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Zero); keyUp(Key.CtrlLeft) }
        compose.runOnIdle { org.junit.Assert.assertEquals(100, notepad.zoom) }
        compose.onNodeWithTag("document-editor").assertTextContains("Keep this")

        repository.put("file:opened.txt", "Opened file")
        compose.onNodeWithTag("document-editor").performClick().assertIsFocused()
        compose.runOnIdle {
            notepad.requestOpen()
            notepad.pickerResult("file:opened.txt")
        }
        compose.waitUntil(5_000) { notepad.active?.uri == "file:opened.txt" }
        compose.onNodeWithTag("document-editor").assertTextContains("Opened file").assertIsNotFocused()
    }

    @Test
    fun toolbarActionsSaveAndFollowTheActiveDocumentUndoHistory() {
        showEditor(startWithDocument = false)
        val save = compose.onNodeWithContentDescription("Save (Ctrl+S)")
        val undo = compose.onNodeWithContentDescription("Undo (Ctrl+Z)")
        val redo = compose.onNodeWithContentDescription("Redo (Ctrl+Y)")
        save.assertIsNotEnabled()
        undo.assertIsNotEnabled()
        redo.assertIsNotEnabled()
        compose.onNodeWithTag("welcome-new").performClick()
        save.assertIsEnabled()
        compose.onNodeWithTag("document-editor").performTextInput("Toolbar draft")
        undo.assertIsEnabled().assertIsDisplayed().performClick()
        compose.onNodeWithTag("document-editor").assertTextEquals("").assertIsFocused()
        redo.assertIsEnabled().performClick()
        compose.onNodeWithTag("document-editor").assertTextEquals("Toolbar draft").assertIsFocused()
        save.performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(PickerKind.SaveAs, notepad.pickerRequest?.kind)
            notepad.pickerResult(null)
        }
        compose.onNodeWithContentDescription("New document").performClick()
        undo.assertIsNotEnabled()
        redo.assertIsNotEnabled()
    }

    @Test
    fun readOnlyBlocksEditsButAllowsSelectionCopyAndScrolling() {
        showEditor()
        val text = (1..150).joinToString("\n") { "Line $it" }
        val editor = compose.onNodeWithTag("document-editor")
        editor.performTextReplacement(text)
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Read-only").assertIsOff().performClick()
        compose.onNodeWithText("Read-only enabled").assertIsDisplayed()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Read-only").assertIsOn().performKeyInput { pressKey(Key.Escape) }

        editor.performClick()
        editor.performKeyInput {
            pressKey(Key.Q)
            pressKey(Key.Backspace)
            pressKey(Key.Delete)
            pressKey(Key.Enter)
            keyDown(Key.CtrlLeft)
            pressKey(Key.A)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            org.junit.Assert.assertEquals(androidx.compose.ui.text.TextRange(0, text.length), notepad.active!!.editor.selection)
        }
        compose.onNodeWithText("Edit").performClick()
        listOf("Undo", "Redo", "Cut", "Paste").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        compose.onNodeWithText("Select all").assertIsEnabled()
        compose.onNodeWithText("Copy").assertIsEnabled().performClick()
        compose.runOnIdle {
            val clipboard = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.content.ClipboardManager::class.java)
            org.junit.Assert.assertEquals(text, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
        editor.performClick()
        editor.performKeyInput {
            keyDown(Key.CtrlLeft)
            listOf(Key.X, Key.V, Key.Z, Key.Y).forEach { pressKey(it) }
            keyUp(Key.CtrlLeft)
        }
        editor.assertTextEquals(text)
        editor.performTouchInput { swipeUp() }
        compose.runOnIdle { org.junit.Assert.assertTrue(notepad.active!!.scroll.value > 0) }

        compose.onNodeWithContentDescription("New document").performClick()
        compose.onNodeWithText("Read-only enabled").assertDoesNotExist()
        editor.performTextInput("Editable tab")
        tab(1).performClick()
        compose.onNodeWithText("Read-only enabled").assertIsDisplayed()
        editor.assertTextEquals(text)
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Read-only").performClick()
        compose.onNodeWithText("Read-only enabled").assertDoesNotExist()
        editor.performTextInput("!")
        editor.assertTextContains("!", substring = true)
        compose.onNodeWithContentDescription("Undo (Ctrl+Z)").assertIsEnabled()
    }

    @Test
    fun statusLabelAndToolbarIconDisableReadOnlyWithoutChangingTheDocument() {
        showEditor()
        val editor = compose.onNodeWithTag("document-editor")
        val unlock = compose.onNodeWithContentDescription("Disable read-only")
        editor.performTextReplacement("Keep this text")
        unlock.assertDoesNotExist()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Read-only").performClick()
        unlock.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val redo = compose.onNodeWithContentDescription("Redo (Ctrl+Y)").getUnclippedBoundsInRoot()
        val icon = unlock.getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("Read-only follows Redo with room for a divider", icon.left > redo.right)
        compose.onNodeWithText("Read-only enabled").assertHasClickAction().performClick()
        unlock.assertDoesNotExist()
        compose.onNodeWithText("Read-only enabled").assertDoesNotExist()
        editor.assertTextEquals("Keep this text")
        editor.performTextInput("!")
        editor.assertTextContains("!", substring = true)

        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Read-only").performClick()
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        unlock.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        unlock.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        unlock.assertDoesNotExist()
        compose.onNodeWithText("Read-only enabled").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertFalse(notepad.active!!.readOnly) }
        editor.performTextInput("?")
        editor.assertTextContains("?", substring = true)
    }

    @Test
    fun compactCommandsToggleReadOnlyAndKeepItsStatusVisible() {
        windowSize = DpSize(400.dp, 800.dp)
        showEditor(largeFontRtl = true)
        compose.onNodeWithTag("document-editor").performTextReplacement("مرحبا မြန်မာ 👋")
        compose.onNodeWithText("Commands").performClick()
        compose.onNodeWithText("Read-only").performScrollTo().performClick()
        compose.onNodeWithText("Read-only enabled").assertIsDisplayed()
        compose.onNodeWithText("Commands").performClick()
        compose.onNodeWithText("Read-only").performScrollTo().assertIsOn().performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Read-only enabled").assertHasClickAction().performClick()
        compose.onNodeWithText("Read-only enabled").assertDoesNotExist()
        compose.onNodeWithTag("document-editor").performTextInput("!")
        compose.onNodeWithTag("document-editor").assertTextContains("!", substring = true)
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
        tab(1).performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(scroll, notepad.active!!.scroll.value)
            org.junit.Assert.assertEquals(selection, notepad.active!!.editor.selection)
        }
    }
}
