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
        block: suspend (PaintViewModel, PaintRecovery) -> Unit,
    ) {
        val settings = (context.applicationContext as PaintApplication).settings
        val original = settings.preferences.first()
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val root = File(context.cacheDir,"startup-${UUID.randomUUID()}")
        val recovery = PaintRecovery(root)
        var model: PaintViewModel? = null
        try {
            settings.save(original.copy(startupBehavior = behavior,defaultCanvasWidth = 12,defaultCanvasHeight = 8))
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
