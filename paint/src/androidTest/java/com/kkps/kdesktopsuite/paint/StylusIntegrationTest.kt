package com.kkps.kdesktopsuite.paint

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PointerIcon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class StylusIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pen = PaintPenStatus()
    private lateinit var model: PaintViewModel
    private lateinit var view: android.view.View
    private lateinit var original: PaintPreferences
    private var downTime = 0L
    private var size by mutableStateOf(DpSize(1200.dp,800.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private var rtl by mutableStateOf(false)
    private var appearance by mutableStateOf("Light")

    @Before fun setup() {
        val settings = (context.applicationContext as PaintApplication).settings
        original = runBlocking { settings.preferences.first() }
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"stylus-${UUID.randomUUID()}")),
                settings,SavedStateHandle(),scope)
            pen.start(context)
            pen.updateDevices(emptySet(),emptySet())
        }
        compose.setContent {
            view = LocalView.current
            val state by model.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalPaintPenStatus provides pen, LocalDensity provides Density(density,fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PaintTheme(appearance) { Box(Modifier.requiredSize(size)) { PaintScreen(state,model) } }
            }
        }
        compose.waitUntil(15_000) { !model.state.value.loading }
        compose.runOnIdle {
            model.preferences { it.copy(fingerInput = FingerInput.Automatic,stylusHoverCursor = true,stylusButtonEraser = true) }
            model.tool(Tool.Brush)
            model.viewport(model.state.value.viewport.actual())
        }
    }

    @After fun cleanup() {
        compose.runOnIdle { pen.stop(context); model.viewModelScope.cancel() }
        scope.cancel()
        runBlocking { (context.applicationContext as PaintApplication).settings.save(original) }
    }

    @Test fun persistentMenuTracksCapabilityAndSavesAllOptions() {
        val icon = compose.onNodeWithTag("paint-stylus-menu")
        icon.assertIsDisplayed().assertIsNotEnabled()
        val bounds = icon.fetchSemanticsNode().boundsInRoot
        capture("stylus-unsupported.png",compose.onNodeWithTag("paint-status-bar"))
        compose.runOnIdle { pen.updateDevices(setOf(42),setOf(42)) }
        icon.assertIsEnabled()
        assertEquals(bounds,icon.fetchSemanticsNode().boundsInRoot)
        icon.performClick()
        capture("stylus-menu.png",compose.onNodeWithTag("dropdown-menu"))
        compose.onNodeWithTag("stylus-finger-Automatic").assertIsSelected()
        compose.onNodeWithTag("stylus-finger-Navigate").performClick()
        icon.performClick()
        compose.onNodeWithTag("stylus-hover-cursor").performClick().assertIsOff()
        compose.onNodeWithTag("stylus-button-eraser").performClick().assertIsOff()
        compose.waitUntil {
            runBlocking { (context.applicationContext as PaintApplication).settings.preferences.first() }.let {
                it.fingerInput == FingerInput.Navigate && !it.stylusHoverCursor && !it.stylusButtonEraser
            }
        }
        compose.runOnIdle { pen.updateDevices(emptySet(),emptySet()) }
        icon.assertIsNotEnabled()
        compose.onNodeWithTag("stylus-hover-cursor").assertDoesNotExist()
        assertEquals(bounds,icon.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun detachedFingersNavigateOverSelectionAndTextWithoutEditing() {
        detached()
        compose.runOnIdle {
            model.viewport(model.state.value.viewport.zoomed(2f))
            model.tool(Tool.Select); model.createPreview(Point(100f,100f),Point(220f,220f))
        }
        val originalPreview = model.state.value.preview
        val before = model.state.value
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            val start = screen(Point(150f,150f))
            down(start); moveBy(Offset(-80f,-40f)); up()
        }
        compose.runOnIdle {
            assertEquals(originalPreview,model.state.value.preview)
            assertSame(before.raster,model.state.value.raster)
            assertEquals(before.canUndo,model.state.value.canUndo)
            assertNotEquals(before.viewport,model.state.value.viewport)
            model.cancelPreview(); model.tool(Tool.Text); model.tap(Point(100f,100f))
            model.textChanged("ABCDE FGHIJ",0,0,true)
        }
        compose.waitForIdle()
        val text = model.state.value.preview
        compose.onNodeWithTag("paint-text-overlay").performTouchInput {
            down(center); moveBy(Offset(-70f,-35f)); up()
        }
        compose.runOnIdle { assertTextUnchanged(text); assertFalse(model.state.value.dirty) }
        compose.onNodeWithTag("paint-text-move-0").performTouchInput {
            down(center); moveBy(Offset(-70f,-35f)); up()
        }
        compose.runOnIdle { assertTextUnchanged(text) }
    }

    @Test fun navigationOverResizeAndRotationHandlesAndPinchPreservesPixels() {
        detached()
        val originalRaster = model.state.value.raster
        compose.runOnIdle { model.viewport(model.state.value.viewport.zoomed(2f)) }
        compose.onNodeWithTag("canvas-resize-BottomRight").performTouchInput {
            down(center); moveBy(Offset(-60f,-30f)); up()
        }
        compose.runOnIdle {
            assertSame(originalRaster,model.state.value.raster)
            model.tool(Tool.Select); model.createPreview(Point(100f,100f),Point(200f,200f))
        }
        val preview = model.state.value.preview
        compose.onNodeWithTag("selection-rotation-handle").performTouchInput {
            down(center); moveBy(Offset(-80f,-30f)); up()
        }
        val scale = model.state.value.viewport.scale
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            pinch(center-Offset(50f,0f),center+Offset(50f,0f),center-Offset(100f,0f),center+Offset(100f,0f))
        }
        compose.runOnIdle {
            assertEquals(preview,model.state.value.preview)
            assertTrue(model.state.value.viewport.scale > scale)
            assertSame(originalRaster,model.state.value.raster)
        }
    }

    @Test fun attachmentPolicyChangeCancelsTouchEditAndDrawChoiceOverridesIt() {
        attached(true)
        compose.runOnIdle { pen.updateDevices(setOf(42),setOf(42)); model.tool(Tool.Rectangle) }
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screen(Point(100f,100f))); moveTo(screen(Point(200f,200f)))
        }
        attached(false)
        compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
        compose.runOnIdle {
            assertNull(model.state.value.preview)
            assertFalse(model.state.value.dirty)
            model.preferences { it.copy(fingerInput = FingerInput.Draw) }
            model.tool(Tool.Pencil)
        }
        compose.onNodeWithTag("paint-canvas").performTouchInput { click(screen(Point(120f,120f))) }
        idleStroke()
        compose.runOnIdle { assertEquals(BLACK,model.state.value.raster.pixel(120,120)) }
    }

    @Test fun sharedSettingsUpdateAnotherWindowWithoutChangingItsDocument() {
        val settings = (context.applicationContext as PaintApplication).settings
        lateinit var other: PaintViewModel
        compose.runOnIdle {
            other = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"stylus-other-${UUID.randomUUID()}")),
                settings,SavedStateHandle(),scope)
        }
        try {
            compose.waitUntil(15_000) { !other.state.value.loading }
            val raster = other.state.value.raster
            compose.runOnIdle { model.preferences { it.copy(fingerInput = FingerInput.Draw,stylusHoverCursor = false,stylusButtonEraser = false) } }
            compose.waitUntil {
                other.state.value.preferences.let { it.fingerInput == FingerInput.Draw && !it.stylusHoverCursor && !it.stylusButtonEraser }
            }
            compose.runOnIdle { assertSame(raster,other.state.value.raster); assertFalse(other.state.value.dirty) }
        } finally { compose.runOnIdle { other.viewModelScope.cancel() } }
    }

    @Test fun heldButtonLatchesAtTipDownAndRestoresTheSelectedTool() {
        compose.runOnIdle {
            model.tool(Tool.Pencil); model.stroke(listOf(Point(100f,100f),Point(140f,100f)))
        }
        idleStroke()
        send(MotionEvent.ACTION_HOVER_ENTER,Point(110f,100f),MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(MotionEvent.ACTION_DOWN,Point(110f,100f),MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(MotionEvent.ACTION_MOVE,Point(120f,100f)) // Release changes the next stroke, not this one.
        send(MotionEvent.ACTION_UP,Point(120f,100f))
        idleStroke()
        compose.runOnIdle {
            assertEquals(WHITE,model.state.value.raster.pixel(110,100))
            assertEquals(WHITE,model.state.value.raster.pixel(120,100))
            assertEquals(Tool.Pencil,model.state.value.options.tool)
            model.undo()
        }
        compose.runOnIdle { assertEquals(BLACK,model.state.value.raster.pixel(110,100)) }
        send(MotionEvent.ACTION_DOWN,Point(100f,140f))
        send(MotionEvent.ACTION_MOVE,Point(120f,140f),MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(MotionEvent.ACTION_UP,Point(120f,140f),MotionEvent.BUTTON_STYLUS_PRIMARY)
        idleStroke()
        compose.runOnIdle { assertEquals(BLACK,model.state.value.raster.pixel(110,140)) }
    }

    @Test fun firstPenContactReplacesUncommittedFingerStrokeAndRequiresFreshNavigation() {
        val origin = compose.onNodeWithTag("paint-viewport").fetchSemanticsNode().boundsInRoot.topLeft
        val finger = origin+screen(Point(100f,100f))
        val tip = origin+screen(Point(200f,200f))
        pointers(MotionEvent.ACTION_DOWN,listOf(finger),listOf(MotionEvent.TOOL_TYPE_FINGER))
        pointers(MotionEvent.ACTION_MOVE,listOf(finger+Offset(20f,0f)),listOf(MotionEvent.TOOL_TYPE_FINGER))
        pointers(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(finger+Offset(20f,0f),tip),listOf(MotionEvent.TOOL_TYPE_FINGER,MotionEvent.TOOL_TYPE_STYLUS))
        pointers(MotionEvent.ACTION_MOVE,listOf(finger+Offset(30f,0f),tip+Offset(20f,0f)),
            listOf(MotionEvent.TOOL_TYPE_FINGER,MotionEvent.TOOL_TYPE_STYLUS))
        pointers(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(finger+Offset(30f,0f),tip+Offset(20f,0f)),listOf(MotionEvent.TOOL_TYPE_FINGER,MotionEvent.TOOL_TYPE_STYLUS))
        idleStroke()
        val viewport = model.state.value.viewport
        pointers(MotionEvent.ACTION_MOVE,listOf(finger+Offset(90f,0f)),listOf(MotionEvent.TOOL_TYPE_FINGER))
        pointers(MotionEvent.ACTION_UP,listOf(finger+Offset(90f,0f)),listOf(MotionEvent.TOOL_TYPE_FINGER))
        compose.runOnIdle {
            assertEquals(WHITE,model.state.value.raster.pixel(110,100))
            assertEquals(BLACK,model.state.value.raster.pixel(210,200))
            assertEquals(viewport,model.state.value.viewport)
            model.undo()
        }
        compose.runOnIdle { assertFalse(model.state.value.dirty) }
    }

    @Test fun penCursorTracksButtonToolAndPreferenceWhileHovering() {
        send(MotionEvent.ACTION_HOVER_ENTER,Point(150f,150f))
        assertEquals(PointerIcon.getSystemIcon(context,PointerIcon.TYPE_NULL),resolve(Point(150f,150f)))
        compose.runOnIdle { model.tool(Tool.Text) }
        assertEquals(PointerIcon.getSystemIcon(context,PointerIcon.TYPE_TEXT),resolve(Point(150f,150f)))
        send(MotionEvent.ACTION_BUTTON_PRESS,Point(150f,150f),MotionEvent.BUTTON_STYLUS_PRIMARY)
        compose.runOnIdle { assertTrue(pen.buttonHeld); assertEquals(Tool.Text,model.state.value.options.tool) }
        assertEquals(PointerIcon.getSystemIcon(context,PointerIcon.TYPE_NULL),resolve(Point(150f,150f)))
        compose.runOnIdle { model.preferences { it.copy(stylusHoverCursor = false) } }
        assertNotEquals(PointerIcon.getSystemIcon(context,PointerIcon.TYPE_NULL),resolve(Point(150f,150f)))
        compose.runOnIdle { model.preferences { it.copy(stylusHoverCursor = true,stylusButtonEraser = false) }; model.tool(Tool.Text) }
        assertEquals(PointerIcon.getSystemIcon(context,PointerIcon.TYPE_TEXT),resolve(Point(150f,150f)))
    }

    @Test fun firstHoverResolvesTheBrushCursorBeforeRecomposition() {
        val viewport = compose.onNodeWithTag("paint-viewport").fetchSemanticsNode().boundsInRoot
        val origin = viewport.topLeft
        val point = origin+screen(Point(150f,150f))
        val outside = origin+Offset(8f,viewport.height/2)
        assertTrue(model.state.value.viewport.toString(),model.state.value.viewport.imagePoint(Point(8f,viewport.height/2)).x < 0)
        repeat(2) {
            compose.runOnUiThread {
                val event = event(MotionEvent.ACTION_HOVER_ENTER,point,0)
                try {
                    pen.onMotionEvent(event)
                    view.dispatchGenericMotionEvent(event)
                    // Resolve in the same event turn: waiting for idle would conceal a one-frame flash.
                    assertEquals(PointerIcon.getSystemIcon(context,PointerIcon.TYPE_NULL),view.onResolvePointerIcon(event,0))
                } finally { event.recycle() }
            }
            compose.waitForIdle()
            compose.runOnUiThread {
                val event = event(MotionEvent.ACTION_HOVER_MOVE,outside,0)
                try {
                    pen.onMotionEvent(event)
                    view.dispatchGenericMotionEvent(event)
                } finally { event.recycle() }
            }
            compose.waitForIdle()
            compose.runOnUiThread {
                val event = event(MotionEvent.ACTION_HOVER_MOVE,outside,0)
                try {
                    assertEquals(PointerIcon.getSystemIcon(context,PointerIcon.TYPE_CROSSHAIR),view.onResolvePointerIcon(event,0))
                } finally { event.recycle() }
                val enterImage = event(MotionEvent.ACTION_HOVER_MOVE,point,0)
                try {
                    pen.onMotionEvent(enterImage)
                    view.dispatchGenericMotionEvent(enterImage)
                    assertEquals(PointerIcon.getSystemIcon(context,PointerIcon.TYPE_NULL),view.onResolvePointerIcon(enterImage,0))
                } finally { enterImage.recycle() }
            }
            compose.waitForIdle()
            sendAt(MotionEvent.ACTION_HOVER_EXIT,point)
        }
    }

    @Test fun imagePenCursorRegionPreservesMouseCursors() {
        val origin = compose.onNodeWithTag("paint-viewport").fetchSemanticsNode().boundsInRoot.topLeft
        val point = origin+screen(Point(150f,150f))
        for ((tool,icon) in listOf(Tool.Brush to PointerIcon.TYPE_CROSSHAIR,Tool.Pan to PointerIcon.TYPE_GRAB)) {
            compose.runOnIdle { model.tool(tool) }
            compose.waitForIdle()
            compose.runOnUiThread {
                val event = event(MotionEvent.ACTION_HOVER_ENTER,point,0,MotionEvent.TOOL_TYPE_MOUSE)
                try {
                    view.dispatchGenericMotionEvent(event)
                    assertEquals(PointerIcon.getSystemIcon(context,icon),view.onResolvePointerIcon(event,0))
                } finally { event.recycle() }
            }
            compose.waitForIdle()
        }
    }

    @Test fun penHoverShowsTooltipAndContactDismissesIt() {
        val button = compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot.center
        sendAt(MotionEvent.ACTION_HOVER_ENTER,button)
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Settings").assertIsDisplayed()
        sendAt(MotionEvent.ACTION_HOVER_EXIT,button)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test fun observedPenFallbackAndDeviceRemovalUpdateCapability() {
        compose.runOnIdle { assertFalse(pen.supported); assertFalse(pen.usedInSession) }
        send(MotionEvent.ACTION_HOVER_ENTER,Point(100f,100f))
        compose.runOnIdle {
            assertTrue(pen.supported)
            assertTrue(pen.navigationOnly(model.state.value.preferences))
            pen.updateDevices(setOf(42),emptySet())
            assertTrue(pen.supported)
            pen.updateDevices(emptySet(),emptySet())
            assertFalse(pen.supported)
            assertFalse(pen.navigationOnly(model.state.value.preferences))
        }
    }

    @Test fun compactIconRemainsVisibleWithLargeTextRtlAndBothThemes() {
        compose.runOnIdle { size = DpSize(360.dp,800.dp); fontScale = 1.6f }
        for (direction in listOf(false,true)) for (theme in listOf("Light","Dark")) {
            compose.runOnIdle { rtl = direction; appearance = theme }
            compose.onNodeWithTag("paint-stylus-menu").assertIsDisplayed().assertIsNotEnabled()
            capture("stylus-compact-$theme-$direction.png",compose.onNodeWithTag("paint-status-bar"))
        }
    }

    private fun assertTextUnchanged(original: EditPreview?) {
        val current = model.state.value.preview
        assertEquals(original?.id,current?.id)
        assertEquals(original?.bounds,current?.bounds)
        assertEquals(original?.text,current?.text)
        assertEquals(original?.textSelectionStart,current?.textSelectionStart)
        assertEquals(original?.textSelectionEnd,current?.textSelectionEnd)
    }

    private fun capture(name: String, node: SemanticsNodeInteraction) {
        File(context.cacheDir,name).outputStream().use {
            node.captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    private fun screen(point: Point): Offset = model.state.value.viewport.screenPoint(point).let { Offset(it.x,it.y) }
    private fun idleStroke() { compose.waitUntil(15_000) { !model.state.value.busy }; compose.waitForIdle() }
    private fun detached() {
        compose.runOnIdle { pen.updateDevices(setOf(42),setOf(42)) }
        attached(false)
    }
    private fun attached(value: Boolean) {
        context.sendBroadcast(Intent("com.samsung.pen.INSERT").setPackage(context.packageName).putExtra("penInsert",value))
        compose.waitUntil { pen.attached == value }
    }
    private fun send(action: Int, point: Point, buttons: Int = 0) {
        val origin = compose.onNodeWithTag("paint-viewport").fetchSemanticsNode().boundsInRoot.topLeft
        sendAt(action,origin+screen(point),buttons)
    }
    private fun event(action: Int, point: Offset, buttons: Int, tool: Int = MotionEvent.TOOL_TYPE_STYLUS): MotionEvent {
        val time = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = time
        return MotionEvent.obtain(downTime.coerceAtMost(time),time,action,1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y }),
            0,buttons,1f,1f,42,0,if (tool == MotionEvent.TOOL_TYPE_MOUSE) InputDevice.SOURCE_MOUSE else InputDevice.SOURCE_STYLUS,0)
    }
    private fun sendAt(action: Int, point: Offset, buttons: Int = 0) {
        compose.runOnUiThread {
            val event = event(action,point,buttons)
            try {
                pen.onMotionEvent(event)
                if (action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL))
                    view.dispatchTouchEvent(event)
                else view.dispatchGenericMotionEvent(event)
            } finally { event.recycle() }
        }
        compose.waitForIdle()
    }
    private fun pointers(action: Int, points: List<Offset>, tools: List<Int>) {
        compose.runOnUiThread {
            val time = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) downTime = time
            val event = MotionEvent.obtain(downTime,time,action,points.size,
                Array(points.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = tools[i] } },
                Array(points.size) { i -> MotionEvent.PointerCoords().apply { x = points[i].x; y = points[i].y } },
                0,0,1f,1f,42,0,InputDevice.SOURCE_STYLUS,0)
            try { pen.onMotionEvent(event); view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        compose.waitForIdle()
    }
    private fun resolve(point: Point): PointerIcon? {
        val origin = compose.onNodeWithTag("paint-viewport").fetchSemanticsNode().boundsInRoot.topLeft
        var result: PointerIcon? = null
        compose.runOnUiThread {
            val event = event(MotionEvent.ACTION_HOVER_MOVE,origin+screen(point),0)
            try { result = view.onResolvePointerIcon(event,0) } finally { event.recycle() }
        }
        return result
    }
}
