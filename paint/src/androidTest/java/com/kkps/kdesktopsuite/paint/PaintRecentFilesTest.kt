package com.kkps.kdesktopsuite.paint

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.content.FileProvider
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

class PaintRecentFilesTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings get() = (context.applicationContext as PaintApplication).settings
    private val images get() = ImageRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var original: PaintPreferences
    private lateinit var model: PaintViewModel
    private val files = mutableListOf<File>()
    private val recovery by lazy { File(context.cacheDir,"recent-${UUID.randomUUID()}") }

    @Before fun setup() {
        runBlocking {
            original = settings.preferences.first()
            settings.save(PaintPreferences(defaultCanvasWidth = 32,defaultCanvasHeight = 24))
        }
        compose.runOnUiThread { model = PaintViewModel(images,PaintRecovery(recovery),settings,SavedStateHandle(),scope) }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            PaintTheme { PaintScreen(state,model) }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        runBlocking {
            scope.coroutineContext.job.cancelAndJoin()
            if (::original.isInitialized) settings.save(original)
        }
        files.forEach { it.delete() }
        recovery.deleteRecursively()
    }

    private fun image(name: String): String = runBlocking {
        val file = images.stage(Raster.blank(4,3),"image/png",100).file
        files.add(file)
        FileProvider.getUriForFile(context,"${context.packageName}.images",file,name).toString()
    }

    private fun seed(uris: List<String>, names: Map<String,String> = emptyMap()) {
        runBlocking { settings.update { it.copy(recent = uris,recentNames = names) } }
        compose.waitUntil(15000) { model.state.value.preferences.recent == uris }
    }

    private fun menu() {
        compose.onNodeWithText("File").performClick()
        compose.onNodeWithText("Recent").performClick()
    }

    @Test fun legacyUrisResolveProviderNamesWithoutOpeningOrChangingTheDraft() {
        val name = "ပန်းချီ 🌿 final.png"
        val uri = image(name)
        seed(listOf(uri))
        val before = model.state.value
        menu()
        compose.waitUntil(15000) { model.state.value.preferences.recentNames[uri] == name }
        compose.onNodeWithTag("paint-recent-0").assertTextEquals(name)
        compose.runOnIdle {
            assertSame(before.raster,model.state.value.raster)
            assertEquals(before.uri,model.state.value.uri)
            assertEquals(before.dirty,model.state.value.dirty)
        }
        runBlocking { withTimeout(15000) { settings.preferences.first { it.recentNames[uri] == name } } }
    }

    @Test fun staleEntriesKeepCachedNamesAndCanBeRemovedWithoutDeletingFiles() {
        val uri = image("Keep me.png")
        val missing = "content://missing.paint.provider/document/42"
        seed(listOf(missing,uri),mapOf(missing to "Unavailable.png",uri to "Keep me.png"))
        menu()
        compose.onNodeWithTag("paint-recent-0").assertTextEquals("Unavailable.png")
        compose.onNodeWithTag("paint-remove-recent-0").performClick()
        compose.waitUntil(15000) { model.state.value.preferences.recent == listOf(uri) }
        compose.onNodeWithTag("paint-recent-0").assertTextEquals("Keep me.png")
        compose.onNodeWithText("Clear recent files").performClick()
        compose.waitUntil(15000) { model.state.value.preferences.recent.isEmpty() }
        compose.runOnIdle { assertTrue(model.state.value.preferences.recentNames.isEmpty()) }
        runBlocking {
            withTimeout(15000) { settings.preferences.first { it.recent.isEmpty() && it.recentNames.isEmpty() } }
            assertEquals("Keep me.png",images.read(uri).name)
        }
    }

    @Test fun unreadableLegacyUrisUseAFriendlyFallbackInsteadOfProviderIds() {
        seed(listOf("content://missing.paint.provider/document/opaque%3A1234"))
        menu()
        compose.onNodeWithTag("paint-recent-0").assertTextEquals("Image 1")
        compose.onNodeWithTag("paint-remove-recent-0").performClick()
        compose.waitUntil(15000) { model.state.value.preferences.recent.isEmpty() }
        compose.onNodeWithText("Recent").assertDoesNotExist()
    }

    @Test fun openingARecentFileCachesItsNameAndKeepsTheOpenCommand() {
        val name = "Real document name.jpg.png"
        val uri = image(name)
        seed(listOf(uri))
        menu()
        compose.onNodeWithTag("paint-recent-0").performClick()
        compose.waitUntil(15000) { model.state.value.uri == uri && !model.state.value.busy }
        compose.runOnIdle {
            assertEquals(name,model.state.value.title)
            assertEquals(name,model.state.value.preferences.recentNames[uri])
            assertFalse(model.state.value.dirty)
        }
    }

    @Test fun saveAsCachesTheProviderNameAndClearingHistoryKeepsTheImageOpen() {
        val name = "Saved landscape.png"
        val uri = image(name)
        compose.runOnIdle { model.stroke(listOf(Point(2f,2f))) }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.dirty }
        compose.runOnIdle { model.save(true); model.saveFormat("image/png"); model.pickerResult(uri) }
        compose.waitUntil(15000) { !model.state.value.saving && model.state.value.uri == uri }
        val before = model.state.value
        compose.runOnIdle {
            assertEquals(name,before.preferences.recentNames[uri])
            model.clearRecent()
        }
        compose.waitUntil(15000) { model.state.value.preferences.recent.isEmpty() }
        compose.runOnIdle {
            assertEquals(uri,model.state.value.uri)
            assertSame(before.raster,model.state.value.raster)
            assertEquals(before.canUndo,model.state.value.canUndo)
            assertEquals(before.dirty,model.state.value.dirty)
        }
        runBlocking { withTimeout(15000) { settings.preferences.first { it.recent.isEmpty() } } }
    }
}
