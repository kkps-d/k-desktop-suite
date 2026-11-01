package com.kkps.kdesktopsuite.paint

import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class TransparentSelectionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val key = 0xFF00FF00.toInt()
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private lateinit var root: File
    private lateinit var images: ImageRepository
    private lateinit var model: PaintViewModel
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(15000) { while (!condition()) delay(20) }

    @Before fun setup() = runBlocking {
        root = File(context.cacheDir,"transparent-selection-${UUID.randomUUID()}")
        images = ImageRepository(context)
        main { model = PaintViewModel(images,PaintRecovery(root),
            (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope) }
        waitFor { !model.state.value.loading }
    }

    @After fun cleanup() {
        main { model.viewModelScope.cancel() }
        scope.cancel()
        root.deleteRecursively()
    }

    @Test fun togglingUntouchedSelectionDoesNotLiftEditOrAddHistory() {
        val before = model.state.value
        main {
            assertFalse(before.options.transparentSelection)
            model.tool(Tool.Select)
            model.createPreview(Point(0f,0f),Point(2f,1f))
            model.transparentSelection(true)
            assertTrue(model.state.value.options.transparentSelection)
            assertEquals(PixelRect(0,0,2,1),model.state.value.selection)
            assertNull(model.state.value.preview)
            assertSame(before.raster,model.state.value.raster)
            assertFalse(model.state.value.dirty)
            assertFalse(model.state.value.canUndo)
            model.tool(Tool.Pencil)
            assertTrue(model.state.value.options.transparentSelection)
        }
    }

    @Test fun liftCapturesKeyWhileOffAndCommitClearsSourceWithOneUndoStep() = runBlocking {
        main {
            model.insert(Raster(2,1,intArrayOf(key,red)))
            model.movePreview(PixelRect(0,0,2,1))
            model.applyPreview()
        }
        waitFor { !model.state.value.busy && model.state.value.preview == null }
        val original = model.state.value.raster
        main {
            model.setColor(key,background = true)
            model.tool(Tool.Select)
            model.createPreview(Point(0f,0f),Point(2f,1f))
            model.movePreview(PixelRect(10,10,2,1))
            assertEquals(key,model.state.value.preview!!.transparencyKey)
            model.setColor(blue,background = true)
            model.transparentSelection(true)
            model.cancelPreview()
            assertSame(original,model.state.value.raster)
            model.setColor(key,background = true)
            model.movePreview(PixelRect(10,10,2,1))
            model.setColor(blue,background = true)
            model.applyPreview()
            // The dedicated command must not alter a commit already running.
            model.transparentSelection(false)
            assertTrue(model.state.value.options.transparentSelection)
        }
        waitFor { !model.state.value.busy && model.state.value.preview == null }
        val committed = model.state.value.raster
        assertEquals(blue,committed.pixel(0,0))
        assertEquals(blue,committed.pixel(1,0))
        assertEquals(WHITE,committed.pixel(10,10))
        assertEquals(red,committed.pixel(11,10))
        main { model.undo() }
        assertArrayEquals(original.pixels,model.state.value.raster.pixels)
        main { model.redo() }
        assertArrayEquals(committed.pixels,model.state.value.raster.pixels)
    }

    @Test fun insertionCapturesKeyWhileOffAndToggleRestoresOriginalAlpha() {
        val payload = Raster(2,1,intArrayOf(key,0x80FF0000.toInt()))
        main {
            model.setColor(key,background = true)
            model.insert(payload)
            val preview = requireNotNull(model.state.value.preview)
            assertEquals(key,preview.transparencyKey)
            model.setColor(blue,background = true)
            model.transparentSelection(true)
            assertArrayEquals(intArrayOf(0,0x80FF0000.toInt()),preview.selectionPixels(model.state.value.options.transparentSelection).pixels)
            model.transparentSelection(false)
            assertArrayEquals(payload.pixels,preview.selectionPixels(model.state.value.options.transparentSelection).pixels)
            assertSame(payload,model.state.value.preview!!.payload)
            model.cancelPreview()
            assertFalse(model.state.value.dirty)
            assertFalse(model.state.value.canUndo)
        }
    }

    @Test fun untouchedCopyExportsKeyedPngAndFailedCutPreservesSource() = runBlocking {
        main {
            model.insert(Raster(2,1,intArrayOf(key,red)))
            model.movePreview(PixelRect(0,0,2,1))
            model.applyPreview()
        }
        waitFor { !model.state.value.busy && model.state.value.preview == null }
        val original = model.state.value.raster
        main {
            model.setColor(key,background = true)
            model.createPreview(Point(0f,0f),Point(2f,1f))
            model.transparentSelection(true)
            model.copy(true)
        }
        waitFor { model.state.value.platform != null }
        val request = requireNotNull(model.state.value.platform)
        val uri = FileProvider.getUriForFile(context,"${context.packageName}.images",requireNotNull(request.file)).toString()
        assertArrayEquals(intArrayOf(0,red),images.clipboardImage(uri).pixels)
        main { model.platformDone(request.id,false) }
        assertSame(original,model.state.value.raster)
        assertNotNull(model.state.value.selection)
        assertNull(model.state.value.preview)
        main { model.copy(true) }
        waitFor { model.state.value.platform != null }
        main { model.platformDone(requireNotNull(model.state.value.platform).id,true) }
        waitFor { !model.state.value.busy && model.state.value.selection == null }
        assertEquals(key,model.state.value.raster.pixel(0,0))
        assertEquals(key,model.state.value.raster.pixel(1,0))
        main { model.undo() }
        assertArrayEquals(original.pixels,model.state.value.raster.pixels)
        main { model.paste(); model.platformDone(requireNotNull(model.state.value.platform).id,true,uri) }
        waitFor { model.state.value.preview != null }
        assertArrayEquals(intArrayOf(0,red),model.state.value.preview!!.payload!!.pixels)
    }

    @Test fun floatingCopyUsesCapturedKeyAndPreservesPartialAlpha() = runBlocking {
        main {
            model.setColor(key,background = true)
            model.insert(Raster(2,1,intArrayOf(key,0x80FF0000.toInt())))
            model.setColor(blue,background = true)
            model.transparentSelection(true)
            model.movePreview(PixelRect(12,15,2,1))
            model.copy()
        }
        waitFor { model.state.value.platform != null }
        val request = requireNotNull(model.state.value.platform)
        val uri = FileProvider.getUriForFile(context,"${context.packageName}.images",requireNotNull(request.file)).toString()
        assertArrayEquals(intArrayOf(0,0x80FF0000.toInt()),images.clipboardImage(uri).pixels)
        assertFalse(model.state.value.dirty)
        assertEquals(key,model.state.value.preview!!.payload!!.pixel(0,0))
    }
}
