package com.kkps.kdesktopsuite.paint

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PaintStartupTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(15000) { while (!condition()) delay(20) }
    private val snapshot get() = RecoverySnapshot(Raster.blank(4,3,BLACK),ToolOptions(tool = Tool.Line),
        EditPreview(Tool.Line,PixelRect(0,0,2,2)),PixelRect(0,0,3,2),Viewport(),
        "content://images/last","Last image.png","image/png","fingerprint",true)

    private suspend fun withSession(
        behavior: PaintStartupBehavior, recoverySnapshot: RecoverySnapshot? = snapshot,
        savedState: SavedStateHandle = SavedStateHandle(),
        canvasWidth: Int = 12, canvasHeight: Int = 8,
        block: suspend (PaintViewModel, PaintRecovery) -> Unit,
    ) {
        val settings = (context.applicationContext as PaintApplication).settings
        val original = settings.preferences.first()
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val root = File(context.cacheDir,"startup-${UUID.randomUUID()}")
        val recovery = PaintRecovery(root)
        var model: PaintViewModel? = null
        try {
            settings.save(original.copy(startupBehavior = behavior,defaultCanvasWidth = canvasWidth,defaultCanvasHeight = canvasHeight))
            assertEquals(behavior,settings.preferences.first().startupBehavior)
            recoverySnapshot?.let { recovery.write(it.copy(sequence = recovery.nextSequence())) }
            main { model = PaintViewModel(ImageRepository(context),recovery,settings,savedState,scope) }
            block(requireNotNull(model),recovery)
        } finally {
            main { model?.viewModelScope?.cancel() }
            scope.coroutineContext.job.cancelAndJoin()
            settings.save(original)
            root.deleteRecursively()
        }
    }

    @Test fun defaultReopensLastEditIncludingPendingEdits() = runBlocking {
        assertEquals(PaintStartupBehavior.ReopenLastEdit,PaintPreferences().startupBehavior)
        withSession(PaintStartupBehavior.ReopenLastEdit) { model,_ ->
            waitFor { !model.state.value.loading }
            val state = model.state.value
            assertNull(state.dialog)
            assertEquals(BLACK,state.raster.pixel(0,0))
            assertEquals("content://images/last",state.uri)
            assertEquals("Last image.png",state.title)
            assertEquals(Tool.Line,state.preview?.tool)
            assertEquals(PixelRect(0,0,3,2),state.selection)
            assertTrue(state.dirty)
        }
    }

    @Test fun newImageStartsAtConfiguredSizeWithoutRestoringDraft() = runBlocking {
        withSession(PaintStartupBehavior.NewImage) { model,_ ->
            waitFor { !model.state.value.loading }
            val state = model.state.value
            assertNull(state.dialog)
            assertNull(state.uri)
            assertNull(state.preview)
            assertEquals("Untitled",state.title)
            assertEquals(12,state.raster.width)
            assertEquals(8,state.raster.height)
            assertTrue(state.raster.pixels.all { it == WHITE })
            assertFalse(state.dirty)
        }
    }

    @Test fun freshStartupUsesActualSizeOrShrinksAfterFirstLayout() = runBlocking {
        for ((width,height,scale) in listOf(Triple(1200f,900f,1f),Triple(400f,300f,0.42f))) {
            withSession(PaintStartupBehavior.NewImage,canvasWidth = 800,canvasHeight = 600) { model,_ ->
                waitFor { !model.state.value.loading }
                main { model.resizeViewport(width,height,24f) }
                val view = model.state.value.viewport
                assertEquals(scale,view.scale,0.001f)
                assertEquals(Point(400f,300f),view.center)
                assertEquals(scale < 1f,view.fit)
            }
        }
    }

    @Test fun unusableFirstMeasurementsDoNotConsumeStartupZoom() = runBlocking {
        withSession(PaintStartupBehavior.NewImage,canvasWidth = 800,canvasHeight = 600) { model,_ ->
            waitFor { !model.state.value.loading }
            main {
                model.resizeViewport(0f,0f,24f)
                model.resizeViewport(1200f,48f,24f)
            }
            assertEquals(1f,model.state.value.viewport.scale,0f)
            main { model.resizeViewport(1200f,900f,24f) }
            assertEquals(1f,model.state.value.viewport.scale,0f)
            assertFalse(model.state.value.viewport.fit)
        }
    }

    @Test fun startingFreshAfterMeasuredStartupPromptUsesConfiguredImageBounds() = runBlocking {
        withSession(PaintStartupBehavior.AskEveryTime) { model,_ ->
            waitFor { model.state.value.dialog == PaintDialog.ReopenLastEdit }
            main {
                model.resizeViewport(400f,300f,24f)
                model.resolveStartup(false)
            }
            val view = model.state.value.viewport
            assertEquals(1f,view.scale,0f)
            assertEquals(Point(6f,4f),view.center)
            assertFalse(view.fit)
        }
    }

    @Test fun newImageResetsExplicitZoomAndKeepsActualSizeDuringResize() = runBlocking {
        withSession(PaintStartupBehavior.NewImage,canvasWidth = 800,canvasHeight = 600) { model,_ ->
            waitFor { !model.state.value.loading }
            main {
                model.resizeViewport(1200f,900f,24f)
                model.viewport(model.state.value.viewport.zoomed(0.1f))
                model.newImage()
                model.resizeViewport(1400f,1000f,24f)
            }
            assertEquals(1f,model.state.value.viewport.scale,0f)
            assertFalse(model.state.value.viewport.fit)
        }
    }

    @Test fun recoveredZoomAndPanAreNotReinitializedAtFirstLayout() = runBlocking {
        val view = Viewport(imageWidth = 1600,imageHeight = 1200,scale = 2f,center = Point(900f,700f),fit = false)
        val recovered = snapshot.copy(raster = Raster.blank(1600,1200),viewport = view)
        withSession(PaintStartupBehavior.ReopenLastEdit,recoverySnapshot = recovered) { model,_ ->
            waitFor { !model.state.value.loading }
            main { model.resizeViewport(400f,300f,24f) }
            assertEquals(2f,model.state.value.viewport.scale,0f)
            assertEquals(view.center,model.state.value.viewport.center)
            assertFalse(model.state.value.viewport.fit)
        }
    }

    @Test fun recoveredFitRecalculatesAtFirstLayoutAndDuringResize() = runBlocking {
        val recovered = snapshot.copy(raster = Raster.blank(800,600),viewport = Viewport())
        withSession(PaintStartupBehavior.ReopenLastEdit,recoverySnapshot = recovered) { model,_ ->
            waitFor { !model.state.value.loading }
            main { model.resizeViewport(400f,300f,24f) }
            assertEquals(0.42f,model.state.value.viewport.scale,0.001f)
            main { model.resizeViewport(1200f,900f,24f) }
            assertEquals(1.42f,model.state.value.viewport.scale,0.001f)
            assertTrue(model.state.value.viewport.fit)
        }
    }

    @Test fun askDoesNotOverwriteDraftBeforeChoiceAndCanReopen() = runBlocking {
        withSession(PaintStartupBehavior.AskEveryTime) { model,recovery ->
            waitFor { model.state.value.dialog == PaintDialog.ReopenLastEdit }
            assertTrue(model.state.value.loading)
            main { model.flush() }
            delay(600)
            assertEquals(BLACK,requireNotNull(recovery.read()).raster.pixel(0,0))
            main { model.resolveStartup(true) }
            assertFalse(model.state.value.loading)
            assertNull(model.state.value.dialog)
            assertEquals("Last image.png",model.state.value.title)
            assertEquals(Tool.Line,model.state.value.preview?.tool)
            assertTrue(model.state.value.dirty)
        }
    }

    @Test fun askCanStartFreshAndBackDismissesToNewImage() = runBlocking {
        for (useBack in listOf(false,true)) {
            withSession(PaintStartupBehavior.AskEveryTime) { model,_ ->
                waitFor { model.state.value.dialog == PaintDialog.ReopenLastEdit }
                main { if (useBack) assertTrue(model.back()) else model.resolveStartup(false) }
                assertFalse(model.state.value.loading)
                assertNull(model.state.value.dialog)
                assertNull(model.state.value.preview)
                assertNull(model.state.value.uri)
                assertEquals(12,model.state.value.raster.width)
                assertTrue(model.state.value.raster.pixels.all { it == WHITE })
                assertEquals(PaintStartupBehavior.AskEveryTime,model.state.value.preferences.startupBehavior)
            }
        }
    }

    @Test fun askWithoutPreviousSessionStartsWithoutPrompt() = runBlocking {
        withSession(PaintStartupBehavior.AskEveryTime,recoverySnapshot = null) { model,_ ->
            waitFor { !model.state.value.loading }
            assertNull(model.state.value.dialog)
            assertEquals(12,model.state.value.raster.width)
        }
    }

    @Test fun processRecreationResumesSessionForEveryStartupPreference() = runBlocking {
        for (behavior in PaintStartupBehavior.entries) {
            withSession(behavior,savedState = SavedStateHandle(mapOf("sessionStarted" to true))) { model,_ ->
                waitFor { !model.state.value.loading }
                assertNull(model.state.value.dialog)
                assertEquals("Last image.png",model.state.value.title)
                assertEquals(BLACK,model.state.value.raster.pixel(0,0))
            }
        }
    }

    @Test fun startupDialogAndSettingsOfferAllChoices() = runBlocking {
        withSession(PaintStartupBehavior.AskEveryTime) { model,_ ->
            compose.setContent {
                val state by model.state.collectAsStateWithLifecycle()
                PaintTheme { PaintScreen(state,model) }
            }
            compose.waitUntil(15000) { model.state.value.dialog == PaintDialog.ReopenLastEdit }
            compose.onNodeWithText("Reopen").performClick()
            compose.runOnIdle { model.showSettings(true) }
            for (behavior in PaintStartupBehavior.entries) {
                compose.onNodeWithTag("startup-$behavior").performScrollTo().performClick().assertIsSelected()
                compose.runOnIdle { assertEquals(behavior,model.state.value.preferences.startupBehavior) }
            }
            compose.waitUntil(15000) {
                runBlocking { (context.applicationContext as PaintApplication).settings.preferences.first().startupBehavior == PaintStartupBehavior.AskEveryTime }
            }
        }
    }
}
