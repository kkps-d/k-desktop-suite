package com.kkps.kdesktopsuite.paint

import android.os.Build
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.kkps.kdesktopsuite.ui.DesktopCaption
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PaintCaptionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: PaintViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var width by mutableStateOf(1000.dp)
    private var fontScale by mutableFloatStateOf(1f)
    private var rtl by mutableStateOf(false)
    private var captionHeight by mutableStateOf(40.dp)
    private var documentTitle by mutableStateOf<String?>(null)
    private var origin = Offset.Zero
    private var caption = DesktopCaption()

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            if (Build.VERSION.SDK_INT >= 35) compose.activity.window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
            )
            model = PaintViewModel(ImageRepository(context), PaintRecovery(File(context.cacheDir, "caption-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings, SavedStateHandle(), scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            var position by remember { mutableStateOf(Offset.Zero) }
            // Place simulated captions below the real system bars and keep oversized content at the top.
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .wrapContentSize(Alignment.TopStart, unbounded = true)) {
                Box(Modifier.requiredSize(width, 600.dp).onGloballyPositioned { position = it.positionInWindow() }) {
                    val height = with(density) { captionHeight.toPx() }
                    val left = position.x
                    val right = left + with(density) { width.toPx() }
                    val top = position.y
                    val fakeCaption = DesktopCaption(top + height, listOf(
                        Rect(left, top, left + with(density) { 48.dp.toPx() }, top + height),
                        Rect(right - with(density) { 120.dp.toPx() }, top, right, top + height),
                    ))
                    SideEffect { origin = position; caption = fakeCaption }
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        PaintTheme("Light") {
                            PaintScreen(documentTitle?.let { state.copy(title = it, dirty = true) } ?: state,
                                model, fakeCaption, WindowInsets(0))
                        }
                    }
                }
            }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
    }

    @Test fun headerFitsCaptionAndMenusWorkInBothDirections() {
        for (direction in listOf(false, true)) {
            compose.runOnIdle { rtl = direction }
            val header = compose.onNodeWithTag("paint-header").fetchSemanticsNode().boundsInWindow
            assertEquals(origin.y, header.top, 1f)
            assertEquals(caption.height, header.bottom, 1f)
            val toolbar = compose.onNodeWithTag("paint-primary-toolbar").fetchSemanticsNode().boundsInWindow
            assertTrue(toolbar.top >= caption.height)
            for (node in listOf(compose.onNodeWithText("File"), compose.onNodeWithContentDescription("Save (Ctrl+S)"),
                compose.onNodeWithContentDescription("Settings"))) {
                val bounds = node.fetchSemanticsNode().boundsInWindow
                assertTrue("Control $bounds overlaps ${caption.obstructions}; header=$header, rtl=$direction",
                    caption.obstructions.none { it.overlaps(bounds) })
                assertTrue(bounds.top >= origin.y && bounds.bottom <= caption.height + 1f)
            }
            compose.onNodeWithText("File").performClick()
            compose.onNodeWithText("Image properties").assertIsDisplayed()
            compose.onNodeWithTag("dropdown-menu").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        }
    }

    @Test fun resizingLargeTextAndShortCaptionsFallBackWithoutLosingEdits() {
        compose.runOnIdle { model.tool(Tool.Rectangle); model.createPreview(Point(10f, 10f), Point(60f, 50f)) }
        val preview = model.state.value.preview
        for ((newWidth, scale, height) in listOf(Triple(667.dp, 1f, 40.dp), Triple(1000.dp, 2f, 40.dp), Triple(1000.dp, 1f, 24.dp))) {
            compose.runOnIdle { width = newWidth; fontScale = scale; captionHeight = height }
            val header = compose.onNodeWithTag("paint-header").fetchSemanticsNode().boundsInWindow
            assertTrue("Constrained header stays below caption", header.top >= caption.height - 1f)
            compose.onNodeWithContentDescription("Save (Ctrl+S)").assertIsDisplayed()
            compose.runOnIdle { assertEquals(preview, model.state.value.preview) }
        }
        compose.runOnIdle { width = 1000.dp; fontScale = 1f; captionHeight = 40.dp }
        assertEquals(origin.y, compose.onNodeWithTag("paint-header").fetchSemanticsNode().boundsInWindow.top, 1f)
        compose.runOnIdle { assertEquals(preview, model.state.value.preview) }
    }

    @Test fun longTitleEllipsizesAtFiveHundredClearDpWithoutCrowdingControls() {
        for (scale in listOf(1f, 1.5f)) {
            for (direction in listOf(false, true)) {
                compose.runOnIdle {
                    fontScale = scale
                    rtl = direction
                    width = (500 * scale + 168).dp
                    captionHeight = 64.dp
                    documentTitle = "A very long painting document name that should truncate with an ellipsis.png"
                }
                val header = compose.onNodeWithTag("paint-header").fetchSemanticsNode().boundsInWindow
                assertEquals(origin.y, header.top, 1f)
                assertEquals(caption.height, header.bottom, 1f)
                val title = compose.onNodeWithTag("paint-document-title")
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                title.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.single().lineCount)
                assertTrue("Long title should end with an ellipsis", layouts.single().isLineEllipsized(0))
                val titleBounds = title.fetchSemanticsNode().boundsInWindow
                assertTrue(titleBounds.width > 0)
                compose.onNodeWithTag("paint-unsaved-indicator").assertIsDisplayed()
                for (label in listOf("Save (Ctrl+S)", "Undo (Ctrl+Z)", "Redo (Ctrl+Shift+Z)", "Settings")) {
                    val control = compose.onNodeWithContentDescription(label)
                    control.assertIsDisplayed().assertWidthIsEqualTo(48.dp)
                    val bounds = control.fetchSemanticsNode().boundsInWindow
                    assertFalse(bounds.overlaps(titleBounds))
                    assertTrue(caption.obstructions.none { it.overlaps(bounds) })
                    assertTrue(bounds.top >= header.top && bounds.bottom <= header.bottom + 1f)
                }
            }
        }
    }
}
