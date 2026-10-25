package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ClassicAdaptationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @After fun cleanup() { if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }; scope.cancel() }

    @Test fun narrowAndShortLayoutsKeepClassicCommandsAndDocument() {
        var size by mutableStateOf(DpSize(1200.dp,800.dp))
        var largeText by mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread { model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"adaptive-${UUID.randomUUID()}")),
            (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope) }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if (largeText) 2f else 1f)) {
                PaintTheme { Box(Modifier.requiredSize(size)) { PaintScreen(state,model) } }
            }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
        compose.runOnIdle { model.stroke(listOf(Point(20f,20f))) }
        compose.waitUntil(15000) { model.state.value.dirty && !model.state.value.busy }
        compose.runOnIdle { size = DpSize(360.dp,640.dp); largeText = true }
        compose.onNodeWithText("File").assertIsDisplayed()
        compose.onNodeWithText("View").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose drawing tool").performClick()
        compose.onNodeWithText("Pencil").assertExists()
        compose.onNodeWithText("Pencil").performClick()
        compose.onNodeWithTag("paint-canvas").assertIsDisplayed()
        compose.runOnIdle { assertEquals(BLACK,model.state.value.raster.pixel(20,20)); size = DpSize(1200.dp,400.dp); largeText = false }
        compose.onNodeWithText("Collapse ribbon").performClick()
        compose.onNodeWithText("Show drawing tools").assertIsDisplayed()
        compose.onNodeWithTag("paint-canvas").assertIsDisplayed()
    }
}
