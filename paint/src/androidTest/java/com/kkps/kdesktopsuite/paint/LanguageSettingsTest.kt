package com.kkps.kdesktopsuite.paint

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LanguageSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<PaintActivity>()

    @Test
    fun languageChangeRecreatesAllWindowsWithoutLosingPixelsOrPendingText() {
        compose.waitUntil(15_000) { runCatching { !compose.activity.paintModel.state.value.loading }.getOrDefault(false) }
        val original = AppCompatDelegate.getApplicationLocales()
        compose.runOnIdle { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        compose.waitUntil(15_000) { compose.activity.resources.configuration.locales[0].toLanguageTag() == "en" && runCatching { !compose.activity.paintModel.state.value.loading }.getOrDefault(false) }
        val application = compose.activity.application as PaintApplication
        val firstActivity = compose.activity
        val first = firstActivity.paintModel
        val firstWindow = application.windows.windows.values.first { it.activity === firstActivity }
        val before = application.windows.windows.keys.toSet()
        var secondWindow: PaintWindow? = null
        try {
            compose.runOnIdle { first.stroke(listOf(Point(20f, 20f))); first.newWindow() }
            compose.waitUntil(15_000) { application.windows.windows.values.any { it.id !in before && it.activity != null } }
            val window = application.windows.windows.values.first { it.id !in before && it.activity != null }
            secondWindow = window
            val secondActivity = requireNotNull(window.activity)
            compose.waitUntil(15_000) { runCatching { !secondActivity.paintModel.state.value.loading }.getOrDefault(false) && !first.state.value.busy }
            val second = secondActivity.paintModel
            compose.runOnIdle {
                second.tool(Tool.Text)
                second.tap(Point(30f, 30f))
                second.textChanged("မသိမ်းရသေး · draft", 2, 5, true)
                second.showSettings(true)
            }
            val pixels = first.state.value.raster.pixels.copyOf()
            val preview = second.state.value.preview!!
            compose.runOnIdle { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("my")) }
            compose.waitUntil(15_000) {
                firstWindow.activity?.let {
                    it !== firstActivity && it.resources.configuration.locales[0].language == "my" &&
                        runCatching { it.paintModel === first }.getOrDefault(false)
                } == true && window.activity?.let {
                    it !== secondActivity && it.resources.configuration.locales[0].language == "my" &&
                        runCatching { it.paintModel === second }.getOrDefault(false)
                } == true
            }
            assertSame(first, firstWindow.activity!!.paintModel)
            assertSame(second, window.activity!!.paintModel)
            assertEquals(before + window.id, application.windows.windows.keys)
            assertArrayEquals(pixels, first.state.value.raster.pixels)
            assertEquals(preview.id, second.state.value.preview?.id)
            assertEquals(preview.text, second.state.value.preview?.text)
            assertEquals(preview.textSelectionStart, second.state.value.preview?.textSelectionStart)
            assertTrue(second.state.value.settingsOpen)
            assertEquals("ဆက်တင်များ", window.activity!!.getString(R.string.ui_settings))
            compose.runOnIdle { first.undo() }
            assertFalse(first.state.value.dirty)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                AppCompatDelegate.setApplicationLocales(original)
            }
            compose.waitUntil(15_000) { firstWindow.activity != null && secondWindow?.activity != null }
            secondWindow?.activity?.let { activity ->
                compose.runOnIdle {
                    activity.paintModel.closeImage()
                    if (activity.paintModel.state.value.dialog == PaintDialog.Unsaved) activity.paintModel.discard()
                }
                compose.waitUntil(15_000) { secondWindow?.closed == true && secondWindow?.activity == null }
            }
            compose.runOnIdle { firstWindow.activity?.bringWindowToFront() }
        }
    }
}
