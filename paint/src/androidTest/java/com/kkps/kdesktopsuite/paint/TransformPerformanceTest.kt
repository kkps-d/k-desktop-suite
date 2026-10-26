package com.kkps.kdesktopsuite.paint

import android.os.Debug
import android.os.Build
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
        report.put("environment",JSONObject().put("stage",stage).put("sdk",Build.VERSION.SDK_INT)
            .put("fingerprint",Build.FINGERPRINT).put("abis",JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("densityDpi",context.resources.displayMetrics.densityDpi)
            .put("widthPixels",context.resources.displayMetrics.widthPixels)
            .put("heightPixels",context.resources.displayMetrics.heightPixels)
            .put("maxHeapBytes",Runtime.getRuntime().maxMemory()).put("debug",BuildConfig.DEBUG))
    }

    @After fun cleanup() {
        if (!::model.isInitialized) return
        compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
        File(context.cacheDir,"transform-performance-$stage-${testName.methodName}.json").writeText(report.toString(2))
        println("TRANSFORM_PERFORMANCE $stage $report")
    }

    private fun fixture(side: Int, translucent: Boolean = false) = Raster(side,side,IntArray(side*side) { index ->
        val alpha = if (translucent) (index%side*255/(side-1)) shl 24 else BLACK
        alpha or ((index%side*255/side) shl 16) or ((index/side*255/side) shl 8) or 85
    })

    private fun memory() = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.let {
        JSONObject().put("totalPssKb",it.totalPss).put("nativeHeapBytes",Debug.getNativeHeapAllocatedSize())
    }

    private fun measure(name: String, family: String, work: () -> Raster) {
        repeat(2) { work() }
        val memoryBefore = memory()
        val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
        val samples = mutableListOf<Double>()
        var checksum = 0
        repeat(7) {
            val start = System.nanoTime()
            val output = work()
            samples += (System.nanoTime()-start)/1e6
            checksum = checksum xor output.pixels[it%output.pixels.size]
        }
        val sorted = samples.sorted()
        report.put(name,JSONObject().put("family",family).put("medianMs",sorted[3]).put("samplesMs",JSONArray(samples))
            .put("checksum",checksum).put("allocatedBytes",Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()-allocated)
            .put("memoryBefore",memoryBefore).put("memoryAfter",memory()))
        println("TRANSFORM_CASE $stage $name ${sorted[3]} ms")
    }

    @Test fun rasterTimings() {
        for (side in listOf(1024,2048)) for (translucent in listOf(false,true)) {
            val source = fixture(side,translucent)
            val tag = "${if (side == 1024) "1MP" else "4MP"} ${if (translucent) "alpha" else "opaque"}"
            for (angle in listOf(90,-90,180)) measure("$tag rotate$angle","quarterTurns") {
                source.transformed(RasterTransform.rotation(angle.toDouble()))
            }
            for (horizontal in listOf(true,false)) measure("$tag flip${if (horizontal) "H" else "V"}","flips") {
                source.transformed(RasterTransform.scale(if (horizontal) -1.0 else 1.0,if (horizontal) 1.0 else -1.0))
            }
            for (scale in listOf(1.25,0.75)) measure("$tag resize${(scale*100).toInt()}","resize") {
                source.transformed(RasterTransform.scale(scale,scale))
            }
            val area = PixelRect(0,0,side,side)
            for ((name,transform) in listOf(
                "rotate33" to RasterTransform.rotation(33.0),
                "skewH" to resizeSkewTransform(area,side,side,15,0),
                "skewV" to resizeSkewTransform(area,side,side,0,-15),
                "skewBoth" to resizeSkewTransform(area,side,side,12,-10),
                "resizeSkew" to resizeSkewTransform(area,side*5/4,side*3/4,12,-10),
            )) measure("$tag $name","affine") { source.transformed(transform) }
            val selection = EditPreview(Tool.Select,PixelRect(0,0,side,side),source = PixelRect(0,0,side,side),payload = source).rotatedSelection(33.0)
            measure("$tag apply33","affine") { source.render(selection,ToolOptions()) }
            val clipped = selection.resizedSelection(selection.bounds.copy(x = -side/2,y = -side/2))
            measure("$tag clippedApply33","affine") { source.render(clipped,ToolOptions()) }
            val small = EditPreview(Tool.Select,PixelRect(side/2+12,side/2,64,64),source = PixelRect(side/2,side/2,64,64),
                payload = source.extract(PixelRect(0,0,64,64)))
            measure("$tag smallApply","quarterTurns") { source.render(small,ToolOptions()) }
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
                val memoryBefore = memory()
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
                    .put("memoryBefore",memoryBefore).put("memoryAfter",memory())
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
                val clippedSamples = mutableListOf<Double>()
                repeat(5) {
                    val rotated = original.rotatedSelection(33.0)
                    compose.runOnIdle { model.setPreview(rotated.resizedSelection(rotated.bounds.copy(x = -side/2,y = -side/2))) }
                    Thread.sleep(2000)
                    val startTime = System.nanoTime()
                    instrumentation.runOnMainSync { model.applyPreview() }
                    while (model.state.value.busy) Thread.sleep(1)
                    clippedSamples += (System.nanoTime()-startTime)/1e6
                    compose.runOnIdle { model.undo() }
                }
                report.put("$tag clippedCommandApply",JSONObject().put("medianMs",clippedSamples.sorted()[2]).put("samplesMs",JSONArray(clippedSamples)))
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
