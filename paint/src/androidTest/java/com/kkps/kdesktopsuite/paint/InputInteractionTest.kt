package com.kkps.kdesktopsuite.paint

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

class InputInteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private lateinit var view: android.view.View
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"input-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            val state by model.state.collectAsStateWithLifecycle()
            PaintTheme { PaintScreen(state,model) }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
        compose.runOnIdle { model.tool(Tool.Select); model.viewport(model.state.value.viewport.actual()) }
        compose.onNodeWithTag("paint-canvas").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
    }

    private fun screen(point: Point): Offset {
        val p = model.state.value.viewport.screenPoint(point)
        return Offset(p.x,p.y)
    }

    private fun space(action: Int, repeat: Int = 0) {
        val now = SystemClock.uptimeMillis()
        compose.runOnIdle { assertTrue(view.dispatchKeyEvent(AndroidKeyEvent(now,now,action,AndroidKeyEvent.KEYCODE_SPACE,repeat))) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.cacheDir,"input-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun spaceRepeatAndPointerPanDoNotPaintOrStartKeyboardStroke() {
        compose.runOnIdle { model.tool(Tool.Brush); model.viewport(model.state.value.viewport.zoomed(3f)) }
        val before = model.state.value.viewport
        space(AndroidKeyEvent.ACTION_DOWN)
        repeat(3) { space(AndroidKeyEvent.ACTION_DOWN,it+1) }
        compose.runOnIdle { assertFalse(model.state.value.dirty) }
        compose.onNodeWithTag("paint-canvas").performMouseInput {
            moveTo(center); press(); moveBy(Offset(-60f,-30f)); release()
        }
        space(AndroidKeyEvent.ACTION_UP)
        compose.runOnIdle {
            assertFalse(model.state.value.dirty)
            assertNotEquals(before.center,model.state.value.viewport.center)
            model.viewport(model.state.value.viewport.actual())
        }
        compose.onNodeWithTag("paint-canvas").performKeyInput {
            keyDown(Key.ShiftLeft)
            repeat(10) { pressKey(Key.DirectionRight); pressKey(Key.DirectionDown) }
            keyUp(Key.ShiftLeft)
            pressKey(Key.Spacebar)
        }
        compose.runOnIdle { assertFalse(model.state.value.dirty) }
        compose.onNodeWithTag("paint-canvas").performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Spacebar) }
        compose.waitUntil(15000) { !model.state.value.busy }
        compose.runOnIdle {
            assertTrue(model.state.value.dirty)
            assertEquals(WHITE,model.state.value.raster.pixel(0,0))
            assertEquals(BLACK,model.state.value.raster.pixel(100,100))
            model.undo(); assertFalse(model.state.value.dirty)
        }
    }

    @Test fun repeatedSpaceTapActivatesOnlyOnceAndEscapeCancelsKeyboardStroke() {
        compose.runOnIdle { model.tool(Tool.Brush) }
        space(AndroidKeyEvent.ACTION_DOWN)
        repeat(3) { space(AndroidKeyEvent.ACTION_DOWN,it+1) }
        space(AndroidKeyEvent.ACTION_UP)
        compose.onNodeWithTag("paint-canvas").performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Escape) }
        compose.runOnIdle { assertFalse(model.state.value.dirty) }
        compose.onNodeWithTag("paint-canvas").performKeyInput { pressKey(Key.Spacebar) }
        compose.runOnIdle { assertFalse(model.state.value.dirty) }
    }

    @Test fun secondFingerRestoresMovedSelectionAndShape() {
        for (tool in listOf(Tool.Select,Tool.Rectangle)) {
            compose.runOnIdle {
                model.clearSelection(); model.tool(tool)
                model.createPreview(Point(100f,100f),Point(200f,200f))
            }
            val original = model.state.value.preview
            compose.onNodeWithTag("paint-canvas").performTouchInput {
                down(0,screen(Point(120f,120f))); moveTo(0,screen(Point(160f,160f)))
            }
            compose.runOnIdle { assertEquals(140,model.state.value.preview!!.bounds.x) }
            compose.onNodeWithTag("paint-canvas").performTouchInput {
                down(1,screen(Point(300f,300f))); up(0); up(1)
            }
            compose.runOnIdle {
                assertEquals(original,model.state.value.preview)
                if (tool == Tool.Select) assertEquals(PixelRect(100,100,100,100),model.state.value.selection)
                assertFalse(model.state.value.dirty)
            }
        }
    }

    @Test fun secondFingerRestoresResizedFloatingSelection() {
        compose.runOnIdle { model.insert(Raster(100,100,IntArray(10000) { BLACK })); model.movePreview(PixelRect(200,180,100,100)) }
        val original = model.state.value.preview
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(0,screen(Point(300f,280f))); moveTo(0,screen(Point(340f,310f)))
        }
        compose.runOnIdle { assertEquals(140,model.state.value.preview!!.bounds.width) }
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(1,screen(Point(400f,350f))); up(0); up(1)
        }
        compose.runOnIdle { assertEquals(original,model.state.value.preview); assertFalse(model.state.value.dirty) }
    }

    @Test fun keyboardCreatesCancelsAndNudgesRectangularSelection() {
        val canvas = compose.onNodeWithTag("paint-canvas")
        canvas.performKeyInput {
            keyDown(Key.ShiftLeft)
            repeat(10) { pressKey(Key.DirectionRight); pressKey(Key.DirectionDown) }
            keyUp(Key.ShiftLeft)
            pressKey(Key.Spacebar)
            keyDown(Key.ShiftLeft)
            repeat(5) { pressKey(Key.DirectionRight); pressKey(Key.DirectionDown) }
            keyUp(Key.ShiftLeft)
            pressKey(Key.Escape)
        }
        compose.runOnIdle { assertNull(model.state.value.selection); assertNull(model.state.value.preview) }
        canvas.performKeyInput {
            pressKey(Key.Spacebar)
            keyDown(Key.ShiftLeft)
            repeat(5) { pressKey(Key.DirectionRight); pressKey(Key.DirectionDown) }
            keyUp(Key.ShiftLeft)
            pressKey(Key.Spacebar)
        }
        compose.runOnIdle { assertEquals(PixelRect(150,150,50,50),model.state.value.selection) }
        screenshot("keyboard-selection")
        canvas.performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(PixelRect(151,150,50,50),model.state.value.preview?.bounds) }
    }

    @Test fun keyboardCursorIsVisibleForEveryPositionedTool() {
        val canvas = compose.onNodeWithTag("paint-canvas")
        canvas.performKeyInput {
            keyDown(Key.ShiftLeft)
            repeat(10) { pressKey(Key.DirectionRight); pressKey(Key.DirectionDown) }
            keyUp(Key.ShiftLeft)
        }
        for (tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser,Tool.Fill,Tool.Text,Tool.Eyedropper,Tool.Select,Tool.Rectangle)) {
            compose.runOnIdle { model.tool(tool) }
            canvas.performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionLeft) }
            val p = screen(Point(100f,100f))
            val pixels = canvas.captureToImage().toPixelMap()
            assertNotEquals("Visible cursor for $tool",androidx.compose.ui.graphics.Color.White,pixels[p.x.roundToInt()+5,p.y.roundToInt()])
            if (tool == Tool.Brush) screenshot("keyboard-cursor")
        }
    }

    @Test fun enlargedSelectionResizeTargetAcceptsTouchesAwayFromTheVisualHandle() {
        compose.runOnIdle { model.createPreview(Point(100f,100f),Point(200f,200f)) }
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            val p = screen(Point(200f,200f))
            down(p+Offset(20f,20f)); moveTo(p+Offset(50f,40f)); up()
        }
        compose.runOnIdle { assertEquals(PixelRect(100,100,130,120),model.state.value.preview?.bounds) }
        screenshot("touch-resize")
    }

    @Test fun keyboardCursorRemainsInsideTheViewportWhenZoomed() {
        compose.runOnIdle { model.tool(Tool.Brush); model.viewport(model.state.value.viewport.zoomed(5f)) }
        compose.onNodeWithTag("paint-canvas").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle {
            val view = model.state.value.viewport
            val cursor = view.screenPoint(Point(1f,0f))
            assertTrue(cursor.x >= 0f && cursor.x < view.width)
            assertTrue(cursor.y >= 0f && cursor.y < view.height)
        }
    }

    @Test fun textMoveGripHasTouchSizeAndPreservesNativeEditing() {
        compose.runOnIdle { model.tool(Tool.Text); model.tap(Point(240f,260f)) }
        val text = compose.onNodeWithTag("paint-text-overlay")
        text.performTextInput("Touch-friendly text")
        compose.runOnIdle { model.textEditor(requireNotNull(model.state.value.preview)).edit { selection = TextRange(0,5) } }
        val original = requireNotNull(model.state.value.preview)
        val grip = compose.onNodeWithTag("paint-text-move-0").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        grip.performTouchInput { swipe(center,center+Offset(40f,30f)) }
        compose.runOnIdle {
            val moved = requireNotNull(model.state.value.preview)
            assertEquals(original.bounds.x+40,moved.bounds.x)
            assertEquals(original.bounds.y+30,moved.bounds.y)
            assertEquals(TextRange(0,5),model.textEditor(moved).selection)
        }
        text.performTextInput("Finger")
        compose.waitUntil(5000) { model.state.value.preview?.text == "Finger-friendly text" }
        screenshot("text-move-grip")
        grip.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        grip.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(PaintDialog.Transform,model.state.value.dialog); model.dialog(null) }
    }
}
