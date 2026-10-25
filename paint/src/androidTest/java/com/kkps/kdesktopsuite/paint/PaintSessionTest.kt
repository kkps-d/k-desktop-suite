package com.kkps.kdesktopsuite.paint

import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PaintSessionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(15000) { while (!condition()) delay(20) }

    @Test fun saveDuringEditMarksOnlyEncodedRevisionClean() = runBlocking {
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val root = File(context.cacheDir,"save-session-${UUID.randomUUID()}")
        val images = ImageRepository(context)
        lateinit var model: PaintViewModel
        main { model = PaintViewModel(images,PaintRecovery(root),(context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope) }
        try {
            waitFor { !model.state.value.loading }
            main { model.stroke(listOf(Point(20f,20f))) }
            waitFor { !model.state.value.busy && model.state.value.dirty }
            val savedPixels = model.state.value.raster.pixels.copyOf()
            val target = images.stage(Raster.blank(),"image/png",100).file
            val uri = FileProvider.getUriForFile(context,"${context.packageName}.images",target).toString()
            main {
                model.save(true); model.saveFormat("image/png"); model.pickerResult(uri)
                model.stroke(listOf(Point(100f,100f)))
            }
            waitFor { !model.state.value.saving && !model.state.value.busy }
            assertEquals(uri,model.state.value.uri)
            assertTrue(model.state.value.dirty)
            assertArrayEquals(savedPixels,images.read(uri).raster.pixels)
            assertEquals(BLACK,model.state.value.raster.pixel(100,100))
            main { model.undo() }
            assertFalse(model.state.value.dirty)
        } finally { main { model.viewModelScope.cancel() }; scope.cancel(); root.deleteRecursively() }
    }

    @Test fun cancelledSaveBlocksReplacementAndReleasesSavingState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val root = File(context.cacheDir,"cancel-session-${UUID.randomUUID()}")
        lateinit var model: PaintViewModel
        main { model = PaintViewModel(ImageRepository(context),PaintRecovery(root),(context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope) }
        try {
            waitFor { !model.state.value.loading }
            main { model.stroke(listOf(Point(20f,20f))) }
            waitFor { !model.state.value.busy && model.state.value.dirty }
            main { model.newImage(); assertEquals(PaintDialog.Unsaved,model.state.value.dialog)
                model.saveThenContinue(); model.saveFormat("image/png"); model.pickerResult(null) }
            assertTrue(model.state.value.dirty); assertFalse(model.state.value.saving)
            assertEquals(BLACK,model.state.value.raster.pixel(20,20))
            main { model.newImage(); model.discard() }
            assertFalse(model.state.value.dirty); assertTrue(model.state.value.raster.pixels.all { it == WHITE })
        } finally { main { model.viewModelScope.cancel() }; scope.cancel(); root.deleteRecursively() }
    }

    @Test fun liftingCancelsExactlyAndSuccessfulCutClearsOriginalSource() = runBlocking {
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val root = File(context.cacheDir,"selection-session-${UUID.randomUUID()}")
        lateinit var model: PaintViewModel
        main { model = PaintViewModel(ImageRepository(context),PaintRecovery(root),(context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope) }
        try {
            waitFor { !model.state.value.loading }
            main { model.stroke(listOf(Point(20f,20f))) }
            waitFor { !model.state.value.busy && model.state.value.dirty }
            val original = model.state.value.raster.pixels.copyOf()
            main { model.tool(Tool.Select); model.createPreview(Point(15f,15f),Point(25f,25f))
                model.movePreview(PixelRect(100,100,10,10)); model.cancelPreview() }
            assertArrayEquals(original,model.state.value.raster.pixels)
            main { model.movePreview(PixelRect(100,100,10,10)); model.copy(true) }
            waitFor { model.state.value.platform != null }
            main { val request = requireNotNull(model.state.value.platform); model.platformDone(request.id,true) }
            waitFor { !model.state.value.busy && model.state.value.preview == null }
            assertEquals(WHITE,model.state.value.raster.pixel(20,20))
            main { model.undo() }; assertArrayEquals(original,model.state.value.raster.pixels)
        } finally { main { model.viewModelScope.cancel() }; scope.cancel(); root.deleteRecursively() }
    }
}
