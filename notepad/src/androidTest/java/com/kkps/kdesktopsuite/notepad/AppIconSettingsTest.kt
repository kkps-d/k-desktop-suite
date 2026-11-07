package com.kkps.kdesktopsuite.notepad

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
    @get:Rule val compose = createAndroidComposeRule<NotepadActivity>()

    @Test
    fun iconChoicesUpdateLauncherAndSurviveRecreationWithDraft() {
        compose.waitUntil(15_000) { !compose.activity.notepad.loading }
        val context = compose.activity.applicationContext
        val original = readAppIconChoice(context)
        val model = compose.activity.notepad
        val theme = model.appTheme
        compose.runOnIdle { model.newDocument(); model.active!!.editor.edit { append("Icon settings draft") }; model.showSettings(true) }
        val document = model.active!!
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
                assertSame(document, model.active)
                assertEquals("Icon settings draft", document.editor.text.toString())
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("icon-style-Glass").assertIsSelected()
            compose.onNodeWithTag("icon-tone-Dark").assertIsOn()
            assertSame(document, compose.activity.notepad.active)
            compose.runOnIdle { model.changeAppTheme(AppTheme.Light) }
            compose.onNodeWithTag("icon-tone-Light").performScrollTo().performClick()
            capture("notepad-settings-light.png")
            compose.runOnIdle { model.changeAppTheme(AppTheme.Dark) }
            compose.onNodeWithTag("icon-tone-Dark").performClick()
            capture("notepad-settings-dark.png")
        } finally {
            compose.runOnIdle {
                changeAppIcon(context, original)
                model.changeAppTheme(theme)
                model.showSettings(false)
                model.requestClose(document.id); model.discardClose()
            }
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
