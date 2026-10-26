package com.kkps.kdesktopsuite.paint

import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File
import java.util.Collections
import java.util.UUID
import kotlin.math.*

/** Opt-in timing harness: run the same debug APK configuration before and after changes. */
class TransformPerformanceTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val testName = TestName()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private lateinit var model: PaintViewModel
    private val report = JSONObject()
    private val stage = InstrumentationRegistry.getArguments().getString("performanceStage") ?: "measurement"

    @Before fun setup() {
        assumeTrue("Pass -e performanceStage before or after to run timings",InstrumentationRegistry.getArguments().containsKey("performanceStage"))
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"performance-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            PaintTheme { Box(Modifier.requiredSize(1000.dp,640.dp)) { PaintScreen(state,model) } }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
    }

    @After fun cleanup() {
        if (!::model.isInitialized) return
        compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
        File(context.cacheDir,"transform-performance-$stage-${testName.methodName}.json").writeText(report.toString(2))
        println("TRANSFORM_PERFORMANCE $stage $report")
    }

    private fun fixture(side: Int) = Raster(side,side,IntArray(side*side) { index ->
        BLACK or ((index%side*255/side) shl 16) or ((index/side*255/side) shl 8) or 85
    })

    private fun measure(name: String, work: () -> Raster) {
        repeat(2) { work() }
        val samples = mutableListOf<Double>()
        var checksum = 0
        repeat(7) {
            val start = System.nanoTime()
            val output = work()
            samples += (System.nanoTime()-start)/1e6
            checksum = checksum xor output.pixels[it%output.pixels.size]
        }
        val sorted = samples.sorted()
        report.put(name,JSONObject().put("medianMs",sorted[3]).put("samplesMs",JSONArray(samples)).put("checksum",checksum))
    }

    @Test fun rasterTimings() {
        for (side in listOf(1024,2048)) {
            val source = fixture(side)
            val tag = if (side == 1024) "1MP" else "4MP"
            measure("$tag rotate90") { source.transformed(RasterTransform.rotation(90.0).then(RasterTransform.translation(side.toDouble(),0.0))) }
            measure("$tag flip") { source.transformed(RasterTransform.scale(-1.0,1.0).then(RasterTransform.translation(side.toDouble(),0.0))) }
            measure("$tag resize125") { source.transformed(RasterTransform.scale(1.25,1.25)) }
            val selection = EditPreview(Tool.Select,PixelRect(0,0,side,side),source = PixelRect(0,0,side,side),payload = source).rotatedSelection(33.0)
            measure("$tag apply33") { source.render(selection,ToolOptions()) }
        }
    }

    @Test fun livePreviewAndApplyTimings() {
        var window: Window? = null
        compose.runOnIdle { window = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first().window }
        val thread = HandlerThread("transform-frame-metrics").apply { start() }
        val frames = Collections.synchronizedList(mutableListOf<Double>())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)/1e6
        }
        instrumentation.runOnMainSync { window!!.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper)) }
        try {
            for (side in listOf(1024,2048)) {
                val source = fixture(side)
                compose.runOnIdle { model.resizeCanvas(side,side) }
                while (model.state.value.busy) Thread.sleep(1)
                compose.runOnIdle {
                    model.setPreview(EditPreview(Tool.Select,PixelRect(0,0,side,side),payload = source))
                }
                Thread.sleep(500)
                compose.waitForIdle()
                val original = requireNotNull(model.state.value.preview)
                val viewport = compose.onNodeWithTag("paint-viewport")
                val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot
                val handleBounds = compose.onNodeWithTag("selection-rotation-handle").fetchSemanticsNode().boundsInRoot
                val start = handleBounds.center-viewportBounds.topLeft
                val pivot = model.state.value.viewport.screenPoint(original.selectionCenter).let { Offset(it.x,it.y) }
                val vector = start-pivot
                val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
                frames.clear()
                val interactionSamples = mutableListOf<Double>()
                viewport.performTouchInput { down(start) }
                repeat(90) { index ->
                    val radians = (index+1)*0.5*PI/180
                    val target = pivot+Offset((vector.x*cos(radians)-vector.y*sin(radians)).toFloat(),
                        (vector.x*sin(radians)+vector.y*cos(radians)).toFloat())
                    val begin = System.nanoTime()
                    viewport.performTouchInput {
                        advanceEventTime(16)
                        moveTo(target)
                    }
                    interactionSamples += (System.nanoTime()-begin)/1e6
                    Thread.sleep(16)
                }
                Thread.sleep(250)
                val frameSamples = synchronized(frames) { frames.toList().sorted() }
                assertTrue("Expected rendered frames",frameSamples.isNotEmpty())
                assertEquals(45.0,requireNotNull(model.state.value.preview).rotationDegrees,1.0)
                val tag = if (side == 1024) "1MP" else "4MP"
                report.put("$tag live",JSONObject().put("frameMedianMs",frameSamples[frameSamples.size/2])
                    .put("frameP95Ms",frameSamples[((frameSamples.size-1)*0.95).toInt()])
                    .put("frames",frameSamples.size).put("allocatedBytes",Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()-allocated)
                    .put("interactionMedianMs",interactionSamples.sorted()[interactionSamples.size/2]))
                viewport.performTouchInput { up() }
                val applySamples = mutableListOf<Double>()
                repeat(5) {
                    compose.runOnIdle { model.setPreview(original.rotatedSelection(33.0)) }
                    Thread.sleep(2000)
                    val start = System.nanoTime()
                    instrumentation.runOnMainSync { model.applyPreview() }
                    while (model.state.value.busy) Thread.sleep(1)
                    applySamples += (System.nanoTime()-start)/1e6
                    compose.runOnIdle { model.undo() }
                }
                report.put("$tag commandApply",JSONObject().put("medianMs",applySamples.sorted()[2]).put("samplesMs",JSONArray(applySamples)))
                val smallSamples = mutableListOf<Double>()
                val area = PixelRect(side/2,side/2,64,64)
                val small = EditPreview(Tool.Select,area.copy(x = area.x+12),source = area,payload = source.extract(PixelRect(0,0,64,64)))
                repeat(5) {
                    compose.runOnIdle { model.setPreview(small) }
                    Thread.sleep(500)
                    val startTime = System.nanoTime()
                    instrumentation.runOnMainSync { model.applyPreview() }
                    while (model.state.value.busy) Thread.sleep(1)
                    smallSamples += (System.nanoTime()-startTime)/1e6
                    compose.runOnIdle { model.undo() }
                }
                report.put("$tag smallApply",JSONObject().put("medianMs",smallSamples.sorted()[2]).put("samplesMs",JSONArray(smallSamples)))
                compose.runOnIdle { model.clearSelection() }
            }
        } finally {
            instrumentation.runOnMainSync { window!!.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
        }
    }
}
