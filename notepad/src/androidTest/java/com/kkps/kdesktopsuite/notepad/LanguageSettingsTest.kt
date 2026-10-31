package com.kkps.kdesktopsuite.notepad

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.os.LocaleListCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LanguageSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<NotepadActivity>()

    private fun windowActivity(window: NotepadWindow): NotepadActivity = Stage.values().flatMap {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
    }.filterIsInstance<NotepadActivity>().first { it.notepad.window === window }

    private fun clickLanguageLink(tag: String) {
        val node = compose.onNodeWithTag("language-help").performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val link = layout.layoutInput.text.getLinkAnnotations(0, layout.layoutInput.text.length)
            .first { (it.item as LinkAnnotation.Clickable).tag == tag }
        val point = layout.getBoundingBox(link.start).center
        node.performTouchInput { click(point) }
    }

    @Test
    fun pickerPreservesDraftAndNavigationThroughBurmeseAndFollowSystem() {
        compose.waitUntil(10_000) { !compose.activity.notepad.loading }
        val original = AppCompatDelegate.getApplicationLocales()
        try {
            if (original.toLanguageTags() != "en") {
                compose.runOnIdle { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
                // An explicit override matching the device language may not recreate the activity.
                compose.waitUntil(10_000) { compose.activity.resources.configuration.locales[0].toLanguageTag() == "en" }
            compose.waitForIdle()
            }
            val model = compose.activity.notepad
            compose.runOnIdle { model.newDocument() }
            compose.onNodeWithTag("document-editor").performTextReplacement("မူကြမ်း · draft")
            val document = model.active!!
            compose.runOnIdle { model.showSettings(true) }
            clickLanguageLink("supported")
            compose.onNode(hasText("English") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText("မြန်မာ") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            clickLanguageLink("picker")
            val englishActivity = compose.activity
            compose.onNodeWithTag("language-my").performClick()
            compose.waitUntil(10_000) { compose.activity !== englishActivity && englishActivity.isDestroyed }
            compose.waitForIdle()
            compose.onNodeWithTag("language-my").assertIsSelected()
            assertSame(model, compose.activity.notepad)
            assertSame(document, model.active)
            assertEquals("မူကြမ်း · draft", document.editor.text.toString())
            assertEquals("ဆက်တင်များ", compose.activity.getString(R.string.settings))
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("language-my").assertIsSelected()
            val burmeseActivity = compose.activity
            compose.onNodeWithTag("language-system").performClick()
            compose.waitUntil(10_000) { AppCompatDelegate.getApplicationLocales().isEmpty }
            if (android.content.res.Resources.getSystem().configuration.locales[0].language != "my") {
                compose.waitUntil(10_000) { burmeseActivity.isDestroyed && compose.activity !== burmeseActivity }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("language-system").assertIsSelected()
            compose.onNodeWithTag("language-en").performClick()
            compose.onNodeWithTag("language-en").assertIsSelected()
        } finally {
            compose.runOnIdle { AppCompatDelegate.setApplicationLocales(original) }
            compose.waitForIdle()
        }
    }

    @Test
    fun languageChangeRetainsBothWindowIdentitiesAndDocuments() {
        compose.waitUntil(10_000) { !compose.activity.notepad.loading }
        val model = compose.activity.notepad
        val workspace = model.workspace
        val original = AppCompatDelegate.getApplicationLocales()
        val target = if (compose.activity.resources.configuration.locales[0].language == "my") "en" else "my"
        lateinit var second: NotepadWindow
        compose.runOnIdle {
            model.newDocument()
            second = workspace.acquireWindow(null, true)
            workspace.addBlank(second)
            TabWindows.showWindow(compose.activity, second.id)
        }
        compose.waitUntil(10_000) { second.attached }
        try {
            lateinit var secondActivity: NotepadActivity
            val firstActivity = compose.activity
            val firstDocument = model.active!!
            val secondDocument = second.documents.single()
            compose.runOnIdle {
                secondActivity = windowActivity(second)
                firstDocument.editor.edit { append("First window · မူကြမ်း") }
                secondDocument.editor.edit { append("Second window · မူကြမ်း") }
            }
            val windowIds = workspace.windows.map { it.id }
            val secondModel = secondActivity.notepad
            compose.runOnIdle { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target)) }
            compose.waitUntil(10_000) {
                firstActivity.isDestroyed && secondActivity.isDestroyed && model.window.attached && second.attached
            }
            compose.runOnIdle {
                assertEquals(windowIds, workspace.windows.map { it.id })
                assertSame(model, windowActivity(model.window).notepad)
                assertSame(secondModel, windowActivity(second).notepad)
                assertEquals(target, windowActivity(model.window).resources.configuration.locales[0].language)
                assertEquals(target, windowActivity(second).resources.configuration.locales[0].language)
                assertSame(firstDocument, model.active)
                assertSame(secondDocument, secondModel.active)
                assertEquals("First window · မူကြမ်း", firstDocument.editor.text.toString())
                assertEquals("Second window · မူကြမ်း", secondDocument.editor.text.toString())
            }
        } finally {
            compose.runOnIdle {
                workspace.abandonWindow(second)
                AppCompatDelegate.setApplicationLocales(original)
            }
            compose.waitUntil(10_000) { model.window.attached }
            compose.runOnIdle { workspace.bringToFront(model.window) }
        }
    }
}
