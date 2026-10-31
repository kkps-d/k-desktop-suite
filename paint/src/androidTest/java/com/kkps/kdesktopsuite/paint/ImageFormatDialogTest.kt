package com.kkps.kdesktopsuite.paint

import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ImageFormatDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private lateinit var root: File
    private lateinit var model: PaintViewModel
    private var darkRtl by mutableStateOf(false)

    @Before fun setup() {
        root = File(context.cacheDir,"format-dialog-${UUID.randomUUID()}")
        compose.runOnUiThread {
            model = PaintViewModel(ImageRepository(context),PaintRecovery(root),
                (context.applicationContext as PaintApplication).settings,SavedStateHandle(),scope)
        }
        compose.waitUntil(15000) { !model.state.value.loading }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalLayoutDirection provides if (darkRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PaintTheme(if (darkRtl) "Dark" else "Light") { PaintDialogs(state,model,{}, {}) }
            }
        }
    }

    @After fun cleanup() {
        compose.runOnUiThread { model.viewModelScope.cancel() }
        scope.cancel()
        root.deleteRecursively()
    }

    @Test fun saveOffersBothFormatsAndKeepsJpegConfirmation() {
        compose.runOnIdle { model.save(true) }
        compose.onNodeWithText("Choose a format to save your image as below").assertIsDisplayed()
        compose.onNodeWithText("Smaller image").assertIsDisplayed()
        compose.onNodeWithText("Better quality").assertIsDisplayed()
        screenshot("save-image-format")
        compose.onNodeWithText(".png").performClick()
        compose.runOnIdle {
            assertEquals("image/png",model.state.value.picker?.mime)
            model.pickerResult(null)
            model.save(true)
        }
        compose.onNodeWithText(".jpg").performClick()
        compose.onNodeWithText("Save a JPEG image?").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle {
            assertEquals("image/jpeg",model.state.value.picker?.mime)
            model.pickerResult(null)
            assertFalse(model.state.value.saving)
        }
    }

    @Test fun shareWaitsForChoiceAndExportsTheSelectedEncodingWithoutSaving() = runBlocking {
        compose.runOnIdle { model.stroke(listOf(Point(20f,20f),Point(35f,35f))) }
        compose.waitUntil(15000) { !model.state.value.busy && model.state.value.dirty }
        val original = model.state.value
        for ((extension,mime) in listOf(".png" to "image/png",".jpg" to "image/jpeg")) {
            compose.runOnIdle { model.share(); assertNull(model.state.value.platform) }
            compose.onNodeWithText("Choose a format to share your image as below").assertIsDisplayed()
            compose.onNodeWithText(extension).performClick()
            compose.waitUntil(15000) { model.state.value.platform != null && !model.state.value.busy }
            val request = requireNotNull(model.state.value.platform)
            assertEquals("share",request.action)
            assertEquals(mime,request.mime)
            val file = requireNotNull(request.file)
            assertEquals(extension.drop(1),file.extension)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path,bounds)
            assertEquals(mime,bounds.outMimeType)
            assertEquals(original.raster.width,bounds.outWidth)
            assertEquals(original.raster.height,bounds.outHeight)
            val uri = FileProvider.getUriForFile(context,"${context.packageName}.images",file)
            val exported = ImageRepository(context).read(uri.toString())
            if (mime == "image/png") assertArrayEquals(original.raster.pixels,exported.raster.pixels)
            val expected = ImageRepository(context).stage(original.raster,mime,original.preferences.jpegQuality)
            assertArrayEquals(expected.file.readBytes(),file.readBytes())
            compose.runOnIdle {
                assertEquals(original.uri,model.state.value.uri)
                assertEquals(original.mime,model.state.value.mime)
                assertEquals(original.dirty,model.state.value.dirty)
                assertArrayEquals(original.raster.pixels,model.state.value.raster.pixels)
                model.platformDone(request.id,true)
            }
        }
    }

    @Test fun cancellingShareDoesNotExportOrOpenASavePicker() {
        compose.runOnIdle { model.share() }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertNull(model.state.value.dialog)
            assertNull(model.state.value.platform)
            assertNull(model.state.value.picker)
            assertFalse(model.state.value.saving)
        }
        compose.runOnIdle { model.share(); assertTrue(model.back()) }
        compose.onNodeWithText(".png").assertDoesNotExist()
    }

    @Test fun choicesRemainReachableInDarkThemeAndRtl() {
        compose.runOnIdle { darkRtl = true; model.share() }
        compose.onNodeWithText(".png").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(".jpg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Smaller image").assertIsDisplayed()
        compose.onNodeWithText("Better quality").assertIsDisplayed()
        screenshot("share-image-format-dark-rtl")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(model.state.value.platform) }
    }

    private fun screenshot(name: String) {
        File(context.cacheDir,"$name.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
