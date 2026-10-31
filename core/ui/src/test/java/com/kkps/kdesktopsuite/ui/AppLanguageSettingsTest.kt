package com.kkps.kdesktopsuite.ui

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Annotation
import android.text.Spanned
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AppLanguageSettingsTest {
    class LocaleActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
            super.onCreate(savedInstanceState)
        }
    }

    @After
    fun resetLanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        ShadowLooper.idleMainLooper()
    }

    @Test
    @Config(sdk = [32])
    fun legacyPickerAppliesBurmeseAcrossActivitiesAndCanFollowSystemAgain() {
        val first = Robolectric.buildActivity(LocaleActivity::class.java).setup()
        val second = Robolectric.buildActivity(LocaleActivity::class.java).setup()
        try {
            val languages = supportedAppLanguages(first.get())
            assertEquals(listOf("en", "my"), languages.map { it.toLanguageTag() })
            assertEquals("en", languages.matchingAppLanguage("en-US")?.language)
            assertEquals("my", languages.matchingAppLanguage("my-MM,en-US")?.language)
            assertNull(languages.matchingAppLanguage(""))
            assertNull(languages.matchingAppLanguage("fr"))
            assertFalse(openAppLanguageSettings(first.get()))
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("my"))
            ShadowLooper.idleMainLooper()
            first.recreate()
            second.recreate()
            assertEquals("my", first.get().resources.configuration.locales[0].language)
            assertEquals("my", second.get().resources.configuration.locales[0].language)
            assertEquals("ဘာသာစကား", first.get().getString(R.string.core_ui_language_title))
            val storedLocale = first.get().filesDir.listFiles().orEmpty()
                .firstOrNull { it.name.endsWith("application_locales_record_file") }
            assertNotNull("Automatic legacy locale storage must be enabled by the merged manifest", storedLocale)
            assertTrue(requireNotNull(storedLocale).readText().contains("locales=\"my\""))
            // Robolectric shares configuration objects; restore the device preference explicitly.
            RuntimeEnvironment.setQualifiers("en-rUS")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            ShadowLooper.idleMainLooper()
            first.recreate()
            assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
            assertEquals("en", first.get().resources.configuration.locales[0].language)
        } finally {
            second.pause().stop().destroy()
            first.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [33])
    fun unavailableOrRestrictedSystemSettingsFallsBackToPicker() {
        val application = RuntimeEnvironment.getApplication()
        listOf(ActivityNotFoundException(), SecurityException()).forEach { failure ->
            val context = object : ContextWrapper(application) {
                override fun startActivity(intent: Intent) { throw failure }
            }
            assertFalse(openAppLanguageSettings(context))
        }
        var launched: Intent? = null
        val available = object : ContextWrapper(application) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        assertTrue(openAppLanguageSettings(available))
        assertEquals("android.settings.APP_LOCALE_SETTINGS", launched?.action)
        assertEquals("package:${application.packageName}", launched?.data.toString())
    }

    @Test
    @Config(sdk = [32])
    fun bothTranslationsHaveTwoDistinctCompiledHyperlinkActions() {
        val application = RuntimeEnvironment.getApplication()
        listOf("en", "my").forEach { tag ->
            val configuration = Configuration(application.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            val resources = application.createConfigurationContext(configuration).resources
            val text = resources.getText(R.string.core_ui_language_links) as Spanned
            val links = text.getSpans(0, text.length, Annotation::class.java)
            assertEquals(2, links.size)
            assertTrue(links.all { it.key == "action" })
            assertEquals(setOf("supported", "picker"), links.map { it.value }.toSet())
        }
    }
}
