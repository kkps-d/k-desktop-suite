package com.kkps.kdesktopsuite.paint

import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PaintStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(file: File) = FileProvider.getUriForFile(context,"${context.packageName}.images",file).toString()

    @Test fun recoveryPreservesBrushStyleAndWidthsAndReadsOlderDrafts() = runBlocking {
        val root = File(context.cacheDir,"brush-recovery-${UUID.randomUUID()}")
        try {
            val options = ToolOptions(width = 21,brushStyle = BrushStyle.Watercolor,
                brushWidths = BrushStyle.entries.associateWith { it.ordinal+12 })
            val store = PaintRecovery(root)
            store.write(RecoverySnapshot(Raster.blank(20,20),options,null,null,Viewport(),null,"Brushes","image/png",null,false))
            assertEquals(options,store.read()!!.options)
            val manifest = File(root,"current.json")
            val json = org.json.JSONObject(manifest.readText())
            json.getJSONObject("options").apply { remove("brushStyle"); remove("brushWidths") }
            manifest.writeText(json.toString())
            val restored = PaintRecovery(root).read()!!.options
            assertEquals(BrushStyle.Solid,restored.brushStyle)
            assertTrue(restored.brushWidths.isEmpty())
            assertEquals(21,restored.width)
            json.getJSONObject("options").put("tool","Zoom")
            manifest.writeText(json.toString())
            assertEquals(Tool.Pan,PaintRecovery(root).read()!!.options.tool)
        } finally { root.deleteRecursively() }
    }

    @Test fun pngRoundTripAndExternalConflictPreservePrivatePixels() = runBlocking {
        val repository = ImageRepository(context)
        val source = Raster(2,2,intArrayOf(BLACK,WHITE,0xFF123456.toInt(),0xFF789ABC.toInt()))
        val staged = repository.stage(source,"image/png",100)
        val loaded = repository.read(uri(staged.file))
        assertArrayEquals(source.pixels,loaded.raster.pixels)
        val target = repository.stage(Raster.blank(2,2),"image/png",100)
        val newData = repository.stage(source,"image/png",100)
        repository.write(uri(target.file),newData,repository.read(uri(target.file)).fingerprint)
        assertArrayEquals(source.pixels,repository.read(uri(target.file)).raster.pixels)
        val changed = repository.stage(Raster.blank(2,2,BLACK),"image/png",100)
        changed.file.copyTo(target.file,overwrite = true)
        try { repository.write(uri(target.file),newData,loaded.fingerprint); fail("Expected external conflict") }
        catch (_: ImageChangedException) { assertArrayEquals(source.pixels,loaded.raster.pixels) }
    }

    @Test fun transparentOpenFlattensWhileClipboardRetainsAlpha() = runBlocking {
        val repository = ImageRepository(context)
        val transparent = Raster(2,1,intArrayOf(0,0x8000FF00.toInt()))
        val file = repository.stage(transparent,"image/png",100).file
        val opened = repository.read(uri(file)).raster
        assertEquals(WHITE,opened.pixel(0,0)); assertEquals(255,opened.pixel(1,0) ushr 24)
        val clipboard = repository.clipboardImage(uri(file))
        assertEquals(0,clipboard.pixel(0,0) ushr 24)
        assertEquals(128,clipboard.pixel(1,0) ushr 24)
    }

    @Test fun jpegOrientationIsAppliedExactlyOnce() = runBlocking {
        val repository = ImageRepository(context)
        val file = repository.stage(Raster.blank(4,2),"image/jpeg",100).file
        ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val opened = repository.read(uri(file)).raster
        assertEquals(2,opened.width); assertEquals(4,opened.height)
        val roundTrip = repository.stage(opened,"image/png",100)
        val again = repository.read(uri(roundTrip.file)).raster
        assertEquals(2,again.width); assertEquals(4,again.height)
    }

    @Test fun recoveryRestoresPendingSelectionAndFallsBackAfterCorruption() = runBlocking {
        val root = File(context.cacheDir,"recovery-test-${UUID.randomUUID()}")
        try {
            val store = PaintRecovery(root)
            val base = Raster.blank(4,4)
            val preview = EditPreview(Tool.Select,PixelRect(-1,1,2,2),source = PixelRect(0,0,2,2),payload = Raster.blank(2,2,BLACK))
            val snapshot = RecoverySnapshot(base,ToolOptions(),preview,PixelRect(0,0,2,2),Viewport(),null,"Untitled","image/png",null,true)
            store.write(snapshot.copy(sequence = 1))
            store.write(snapshot.copy(raster = Raster.blank(4,4,BLACK),sequence = 2))
            File(root,"current.json").writeText("interrupted manifest")
            val recovered = requireNotNull(store.read())
            assertArrayEquals(base.pixels,recovered.raster.pixels)
            assertEquals(preview.bounds,recovered.preview?.bounds)
            assertEquals(preview.source,recovered.preview?.source)
            assertArrayEquals(preview.payload!!.pixels,recovered.preview!!.payload!!.pixels)
            store.write(snapshot.copy(raster = Raster.blank(4,4,0xFF00FF00.toInt()),sequence = 3))
            File(root,"current.json").writeText("interrupted again")
            assertArrayEquals(base.pixels,requireNotNull(store.read()).raster.pixels)
        } finally { root.deleteRecursively() }
    }

    @Test fun staleCheckpointCannotReplaceANewerGeneration() = runBlocking {
        val root = File(context.cacheDir,"recovery-order-${UUID.randomUUID()}")
        try {
            val store = PaintRecovery(root)
            val snapshot = RecoverySnapshot(Raster.blank(3,3),ToolOptions(),null,null,Viewport(),null,"Untitled","image/png",null,false)
            store.write(snapshot.copy(raster = Raster.blank(3,3,BLACK),sequence = 2))
            store.write(snapshot.copy(sequence = 1))
            assertEquals(BLACK,requireNotNull(store.read()).raster.pixel(0,0))
        } finally { root.deleteRecursively() }
    }

    @Test fun textAndShapeTransactionsDoNotMutateTheirSource() {
        val base = Raster.blank(160,100)
        val text = base.render(EditPreview(Tool.Text,PixelRect(4,4,145,80),text = "Hello\nမြန်မာ café"),ToolOptions(fontSize = 18))
        assertTrue(text.pixels.any { it != WHITE }); assertTrue(base.pixels.all { it == WHITE })
        val shape = base.render(EditPreview(Tool.Rectangle,PixelRect(10,10,30,20)),ToolOptions(outline = false,fill = true,background = BLACK))
        assertEquals(BLACK,shape.pixel(20,20)); assertEquals(WHITE,shape.pixel(2,2))
        val history = RasterHistory(base); history.commit(text); history.undo()
        assertArrayEquals(base.pixels,history.raster.pixels)
    }

    @Test fun everyClassicShapeRendersDistinctGeometryWithOutlineAndFill() {
        val base = Raster.blank(140,140)
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val results = mutableSetOf<List<Int>>()
        assertEquals(23,shapeTools.size)
        for (tool in shapeTools) {
            var preview = EditPreview.between(tool,Point(20f,20f),Point(120f,110f))
            if (tool == Tool.Curve) preview = preview.withShapePoint(Point(25f,100f)).withShapePoint(Point(110f,20f))
            if (tool == Tool.Polygon) preview = preview.withShapePoint(Point(25f,110f))
            preview = preview.copy(constructing = false)
            val result = base.render(preview,ToolOptions(width = 4,foreground = red,background = blue,fill = true))
            assertTrue("Outline for $tool",result.pixels.any { it == red })
            if (tool.isClosedShape) assertTrue("Fill for $tool",result.pixels.any { it == blue })
            else assertFalse("No fill for $tool",result.pixels.any { it == blue })
            assertEquals("Outside $tool",WHITE,result.pixel(5,5))
            assertTrue("Distinct geometry for $tool",results.add(result.pixels.toList()))
        }
        assertTrue(base.pixels.all { it == WHITE })
    }

    @Test fun recoveryPreservesCurveAndPolygonConstruction() = runBlocking {
        val root = File(context.cacheDir,"shape-recovery-${UUID.randomUUID()}")
        try {
            val store = PaintRecovery(root)
            for ((index,tool) in listOf(Tool.Curve,Tool.Polygon).withIndex()) {
                val preview = EditPreview.between(tool,Point(10f,20f),Point(90f,80f)).withShapePoint(Point(40f,95f))
                store.write(RecoverySnapshot(Raster.blank(100,100),ToolOptions(tool = tool),preview,null,
                    Viewport(),null,"Untitled","image/png",null,false,sequence = index + 1L))
                val restored = requireNotNull(store.read()).preview!!
                assertEquals(tool,restored.tool)
                assertEquals(preview.points,restored.points)
                assertEquals(preview.constructing,restored.constructing)
                assertEquals(preview.bounds,restored.bounds)
                assertArrayEquals(Raster.blank(100,100).render(preview,ToolOptions()).pixels,
                    Raster.blank(100,100).render(restored,ToolOptions()).pixels)
            }
        } finally { root.deleteRecursively() }
    }
}
