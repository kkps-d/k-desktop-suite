package com.kkps.kdesktopsuite.paint

import android.os.Build
import android.os.Debug
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in command timings: real model/history work, without display uploads or recovery PNGs. */
class TransformCommandPerformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val recoveryScope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private lateinit var model: PaintViewModel
    private val report = JSONObject()
    private val stage = InstrumentationRegistry.getArguments().getString("performanceStage") ?: "measurement"

    @Before fun setup() {
        assumeTrue("Pass performanceStage to run command timings",InstrumentationRegistry.getArguments().containsKey("performanceStage"))
        // Recovery is outside this metric; prevent deferred PNG work competing with the next sample.
        recoveryScope.cancel()
        instrumentation.runOnMainSync {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"commands-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),recoveryScope)
        }
        awaitReady()
        report.put("environment",JSONObject().put("stage",stage).put("sdk",Build.VERSION.SDK_INT)
            .put("fingerprint",Build.FINGERPRINT).put("abis",JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("densityDpi",context.resources.displayMetrics.densityDpi)
            .put("widthPixels",context.resources.displayMetrics.widthPixels)
            .put("heightPixels",context.resources.displayMetrics.heightPixels)
            .put("maxHeapBytes",Runtime.getRuntime().maxMemory()).put("debug",BuildConfig.DEBUG)
            .put("quarterTurnsCanvas",RasterTransform.rotation(90.0).usesCanvas())
            .put("affineCanvas",RasterTransform.rotation(33.0).usesCanvas()))
    }

    @After fun cleanup() {
        if (!::model.isInitialized) return
        instrumentation.runOnMainSync { model.viewModelScope.cancel() }
        File(context.cacheDir,"transform-performance-$stage-commandTimings.json").writeText(report.toString(2))
        println("TRANSFORM_COMMAND_PERFORMANCE $stage $report")
    }

    private fun awaitReady() {
        val deadline = System.nanoTime()+30_000_000_000L
        while (model.state.value.loading || model.state.value.busy) {
            check(System.nanoTime() < deadline) { "Timed out waiting for the transform command." }
            Thread.sleep(1)
        }
    }

    private fun fixture(side: Int, alpha: Boolean = false) = Raster(side,side,IntArray(side*side) { index ->
        val opacity = if (alpha) (index%side*255/(side-1)) shl 24 else BLACK
        opacity or ((index%side*255/side) shl 16) or ((index/side*255/side) shl 8) or 85
    })

    private fun memory() = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.let {
        JSONObject().put("totalPssKb",it.totalPss).put("nativeHeapBytes",Debug.getNativeHeapAllocatedSize())
    }

    @Test fun commandTimings() {
        for (side in listOf(1024,2048)) {
            val area = PixelRect(0,0,side,side)
            instrumentation.runOnMainSync { model.clearSelection(); model.resizeCanvas(side,side) }
            awaitReady()
            instrumentation.runOnMainSync {
                model.setPreview(EditPreview(Tool.Select,area,payload = fixture(side)))
                model.applyPreview()
            }
            awaitReady()
            val base = model.state.value.raster
            val smallArea = PixelRect(side/2,side/2,64,64)
            val smallPayload = base.extract(smallArea)
            val size = if (side == 1024) "1MP" else "4MP"
            for (target in listOf("canvas","selection","floatingAlpha","clipped","composed33","small64")) {
                val payload = if (target == "floatingAlpha") fixture(side,true) else base
                System.gc()
                for (command in listOf("rotate90","rotate-90","rotate180","flipH","flipV")) {
                    val samples = mutableListOf<Double>()
                    var allocated = 0L
                    var memoryBefore: JSONObject? = null
                    repeat(7) { sample ->
                        instrumentation.runOnMainSync {
                            model.clearSelection()
                            when (target) {
                                "selection" -> model.selectAll()
                                "floatingAlpha" -> model.setPreview(EditPreview(Tool.Select,area,payload = payload))
                                "clipped" -> model.setPreview(EditPreview(Tool.Select,area.copy(x = -side/2,y = -side/2),source = area,payload = payload))
                                "composed33" -> model.setPreview(EditPreview(Tool.Select,area,source = area,payload = payload).rotatedSelection(33.0))
                                "small64" -> model.setPreview(EditPreview(Tool.Select,smallArea,source = smallArea,payload = smallPayload))
                            }
                        }
                        if (sample == 2) {
                            memoryBefore = memory()
                            allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
                        }
                        val before = model.state.value.raster
                        val start = System.nanoTime()
                        instrumentation.runOnMainSync {
                            when (command) {
                                "flipH" -> model.flip(true)
                                "flipV" -> model.flip(false)
                                else -> model.rotate(command.removePrefix("rotate").toInt())
                            }
                            if (target != "canvas") model.applyPreview()
                        }
                        awaitReady()
                        val elapsed = (System.nanoTime()-start)/1e6
                        if (sample >= 2) samples += elapsed
                        val committed = model.state.value.raster
                        assertNotSame("$size $target $command sample $sample: ${model.state.value.message}",before,committed)
                        assertNull(model.state.value.preview)
                        assertEquals(side,committed.width)
                        assertEquals(side,committed.height)
                        assertTrue("Expected an undoable edit",model.state.value.canUndo)
                        instrumentation.runOnMainSync { model.undo() }
                        // Undo and validation are outside the timed command, but their allocations are cumulative below.
                        assertTrue("Undo must restore the fixture",base.pixels.contentEquals(model.state.value.raster.pixels))
                    }
                    val name = "$size $target $command"
                    report.put(name,JSONObject().put("medianMs",samples.sorted()[2]).put("samplesMs",JSONArray(samples))
                        .put("allocatedBytes",Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()-allocated)
                        .put("memoryBefore",memoryBefore).put("memoryAfter",memory()))
                    println("TRANSFORM_COMMAND $stage $name ${samples.sorted()[2]} ms")
                }
            }
        }
    }
}
