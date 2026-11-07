package com.kkps.kdesktopsuite.paint

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.kkps.kdesktopsuite.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppIconSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<PaintActivity>()

    @Test
    fun iconChoicesUpdateLauncherAndSurviveRecreationWithPendingEdit() {
        compose.waitUntil(15_000) { runCatching { !compose.activity.paintModel.state.value.loading }.getOrDefault(false) }
        val context = compose.activity.applicationContext
        val original = readAppIconChoice(context)
        val model = compose.activity.paintModel
        val appearance = model.state.value.preferences.appearance
        compose.runOnIdle { model.tool(Tool.Text); model.tap(Point(30f, 30f)); model.textChanged("Icon settings draft", 0, 0, true); model.showSettings(true) }
        val preview = model.state.value.preview!!
        try {
            for (style in AppIconStyle.entries) for (tone in AppIconTone.entries) {
                compose.onNodeWithTag("icon-style-$style").performScrollTo().performClick()
                compose.onNodeWithTag("icon-tone-$tone").performScrollTo().performClick()
                compose.onNodeWithTag("icon-style-$style").assertIsSelected()
                compose.onNodeWithTag("icon-tone-$tone").assertIsOn()
                assertEquals(AppIconChoice(style, tone), readAppIconChoice(context))
                val launchers = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
                assertEquals(listOf("${context.packageName}.${AppIconChoice(style, tone).alias}"), launchers.map { it.activityInfo.name })
                assertEquals(preview.id, model.state.value.preview?.id)
                assertEquals(preview.text, model.state.value.preview?.text)
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("icon-style-Glass").assertIsSelected()
            compose.onNodeWithTag("icon-tone-Dark").assertIsOn()
            assertEquals(preview.id, compose.activity.paintModel.state.value.preview?.id)
            compose.runOnIdle { model.preferences { it.copy(appearance = "Light") } }
            compose.onNodeWithTag("icon-tone-Light").performScrollTo().performClick()
            capture("paint-settings-light.png")
            compose.runOnIdle { model.preferences { it.copy(appearance = "Dark") } }
            compose.onNodeWithTag("icon-tone-Dark").performClick()
            capture("paint-settings-dark.png")
        } finally {
            compose.runOnIdle { changeAppIcon(context, original); model.preferences { it.copy(appearance = appearance) }; model.showSettings(false); model.cancelPreview() }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(800) // Let the platform ripple finish before capturing.
        File(compose.activity.cacheDir, name).outputStream().use {
            compose.onNodeWithTag("settings-page").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
