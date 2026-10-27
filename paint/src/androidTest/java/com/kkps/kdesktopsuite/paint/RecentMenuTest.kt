package com.kkps.kdesktopsuite.paint

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class RecentMenuTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: PaintViewModel
    private lateinit var image: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.viewModelScope.cancel() }
        scope.cancel()
        if (::image.isInitialized) image.delete()
    }

    @Test fun recentFilesStayInSubmenuAndSelectionClosesBothMenus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        image = File(context.cacheDir,"recent-${UUID.randomUUID().toString().take(8)}.png")
        val bitmap = Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        val uri = Uri.fromFile(image).toString()
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(File(context.cacheDir,"recent-${UUID.randomUUID()}")),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope)
        }
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            PaintTheme { PaintScreen(state.copy(preferences = state.preferences.copy(recent = listOf(uri))),model) }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
        compose.onNodeWithText("File").performClick()
        compose.onNodeWithText(image.name,substring = true).assertDoesNotExist()
        compose.onNodeWithText("Recent").performClick()
        compose.onNodeWithText("1: ${image.name}").assertIsDisplayed().performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText(image.name,substring = true).assertDoesNotExist()
        compose.onNodeWithText("Recent").assertIsDisplayed().performClick()
        compose.onNodeWithText("1: ${image.name}").performClick()
        compose.onNodeWithText("Recent").assertDoesNotExist()
        compose.waitUntil(15000) { model.state.value.uri == uri && !model.state.value.loading }
        compose.runOnIdle { assertEquals(16,model.state.value.raster.width) }
        compose.onNodeWithText("File").performClick()
        compose.onNodeWithText("1: ${image.name}").assertDoesNotExist()
        compose.onNodeWithText("Recent").assertIsDisplayed()
    }
}
