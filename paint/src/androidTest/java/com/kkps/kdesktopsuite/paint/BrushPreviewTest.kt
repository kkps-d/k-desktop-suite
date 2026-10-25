package com.kkps.kdesktopsuite.paint

import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class BrushPreviewTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()
    private val compute = executor.asCoroutineDispatcher()
    private val commitGate = CountDownLatch(1)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"brush-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope,compute)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            PaintTheme { PaintScreen(state,model) }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
        compose.runOnIdle {
            model.brush(BrushStyle.Solid)
            model.strokeWidth(100)
        }
        // Hold the commit so the test can inspect the exact handoff frame.
        executor.submit { commitGate.await() }
    }

    @After fun cleanup() {
        commitGate.countDown()
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        compute.close()
        scope.cancel()
    }

    private fun screen(point: Point): Offset {
        val p = model.state.value.viewport.screenPoint(point)
        return Offset(p.x,p.y)
    }

    private fun pixel(point: Point): Int {
        val p = screen(point)
        return compose.onNodeWithTag("paint-canvas").captureToImage().asAndroidBitmap()
            .getPixel(p.x.roundToInt(),p.y.roundToInt())
    }

    @Test fun strokeStaysVisibleThroughReleaseAndCommitThenUndoRestoresCanvas() {
        val sample = Point(300f,200f)
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screen(Point(100f,200f)))
            moveTo(screen(Point(650f,200f)))
        }
        compose.waitUntil(5000) { pixel(sample) == BLACK }
        compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
        compose.waitUntil(5000) { model.state.value.busy }
        assertEquals(BLACK,pixel(sample))
        commitGate.countDown()
        compose.waitUntil(5000) { !model.state.value.busy }
        assertEquals(BLACK,pixel(sample))
        compose.runOnIdle { model.undo() }
        assertEquals(WHITE,pixel(sample))
    }

    @Test fun quickTouchStrokeStillRendersWhenReleasedBeforeTheFirstPreview() {
        val sample = Point(275f,200f)
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screen(Point(250f,200f)))
            moveTo(screen(Point(350f,200f)))
            up()
        }
        compose.waitUntil(5000) { model.state.value.busy }
        // No pause for a preview before finger-up, and commit work is still blocked.
        compose.waitUntil(5000) { pixel(sample) == BLACK }
        commitGate.countDown()
        compose.waitUntil(5000) { !model.state.value.busy }
        assertEquals(BLACK,pixel(sample))
    }

    @Test fun longTouchStrokeKeepsItsFinalSegmentsVisibleWhileCommitIsQueued() {
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screen(Point(100f,150f)))
            for (x in 102..650 step 2) moveTo(screen(Point(x.toFloat(),150f)))
            for (x in 648 downTo 100 step 2) moveTo(screen(Point(x.toFloat(),300f)))
            for (x in 102..650 step 2) moveTo(screen(Point(x.toFloat(),450f)))
            up()
        }
        compose.waitUntil(5000) { model.state.value.busy }
        val tail = Point(550f,450f)
        compose.waitUntil(5000) { pixel(tail) == BLACK }
        val start = System.nanoTime()
        commitGate.countDown()
        compose.waitUntil(5000) { !model.state.value.busy }
        println("Long 100px touch stroke commit after preview: ${(System.nanoTime()-start)/1_000_000.0} ms")
        assertEquals(BLACK,pixel(tail))
        compose.runOnIdle { model.undo() }
        assertEquals(WHITE,pixel(tail))
    }

    @Test fun cancellingCommitClearsThePreviewWithoutChangingTheImage() {
        val sample = Point(300f,200f)
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screen(Point(100f,200f)))
            moveTo(screen(Point(650f,200f)))
        }
        compose.waitUntil(5000) { pixel(sample) == BLACK }
        compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
        compose.waitUntil(5000) { model.state.value.busy }
        compose.runOnIdle { model.cancelOperation() }
        assertEquals(WHITE,pixel(sample))
        assertTrue(model.state.value.raster.pixels.all { it == WHITE })
        assertFalse(model.state.value.canUndo)
    }

    @Test fun translucentBrushDoesNotDarkenWhenItsPreviewIsCommitted() {
        compose.runOnIdle { model.brush(BrushStyle.Marker); model.strokeWidth(100) }
        val sample = Point(300f,200f)
        compose.onNodeWithTag("paint-canvas").performTouchInput {
            down(screen(Point(100f,200f)))
            moveTo(screen(Point(400f,200f)))
        }
        compose.waitUntil(5000) { pixel(sample) != WHITE }
        val previewColor = pixel(sample)
        assertNotEquals(BLACK,previewColor)
        compose.onNodeWithTag("paint-canvas").performTouchInput { moveTo(screen(Point(650f,200f))) }
        compose.waitUntil(5000) { pixel(Point(500f,200f)) != WHITE }
        assertEquals(previewColor,pixel(sample))
        compose.onNodeWithTag("paint-canvas").performTouchInput { up() }
        compose.waitUntil(5000) { model.state.value.busy }
        assertEquals(previewColor,pixel(sample))
        commitGate.countDown()
        compose.waitUntil(5000) { !model.state.value.busy }
        assertEquals(previewColor,pixel(sample))
    }
}
