package com.kkps.kdesktopsuite.notepad

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kkps.kdesktopsuite.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LauncherIconTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun changingIconAfterLauncherStartKeepsTheSameWindowAndDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = readAppIconChoice(context)
        context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)))
        var launched: NotepadActivity? = null
        compose.waitUntil(15_000) {
            instrumentation.runOnMainSync {
                launched = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<NotepadActivity>().firstOrNull()
            }
            launched?.notepad?.loading == false
        }
        val activity = requireNotNull(launched)
        val model = activity.notepad
        compose.runOnIdle { model.newDocument(); model.active!!.editor.edit { append("Launcher icon draft") }; model.showSettings(true) }
        val document = model.active!!
        try {
            val style = if (original.style == AppIconStyle.Classic) AppIconStyle.Glass else AppIconStyle.Classic
            compose.onNodeWithTag("icon-style-$style").performScrollTo().performClick()
            // Package-change notifications and launcher task cleanup are asynchronous.
            SystemClock.sleep(2500)
            assertFalse("Switching the launcher icon must not finish its editor", activity.isFinishing || activity.isDestroyed)
            compose.onNodeWithTag("settings-page").assertIsDisplayed()
            assertSame(document, model.active)
            assertEquals("Launcher icon draft", document.editor.text.toString())
            assertEquals(NotepadActivity::class.java.name, activity.intent.component?.className)
            val choice = readAppIconChoice(context)
            compose.runOnIdle { activity.moveTaskToBack(true) }
            compose.waitUntil(15_000) {
                var stopped = false
                instrumentation.runOnMainSync {
                    stopped = activity in ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                }
                stopped
            }
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)))
            compose.waitUntil(15_000) {
                var resumed = false
                instrumentation.runOnMainSync {
                    resumed = activity in ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                }
                resumed
            }
            compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("settings-page").isDisplayed() }.getOrDefault(false) }
            assertEquals(choice, readAppIconChoice(context))
            assertFalse(activity.isDestroyed)
            compose.onNodeWithTag("settings-page").assertIsDisplayed()
        } finally {
            instrumentation.runOnMainSync {
                changeAppIcon(context, original)
                if (!activity.isDestroyed) {
                    model.showSettings(false)
                    model.requestClose(document.id); model.discardClose()
                    activity.finishAndRemoveTask()
                }
            }
        }
    }
}
