package com.kkps.kdesktopsuite.paint

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
    fun changingIconAfterLauncherStartKeepsTheSameWindowAndPendingEdit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = readAppIconChoice(context)
        context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)))
        var launched: PaintActivity? = null
        compose.waitUntil(15_000) {
            instrumentation.runOnMainSync {
                launched = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<PaintActivity>().firstOrNull()
            }
            launched?.let { runCatching { !it.paintModel.state.value.loading }.getOrDefault(false) } == true
        }
        val activity = requireNotNull(launched)
        val model = activity.paintModel
        compose.runOnIdle {
            model.tool(Tool.Text); model.tap(Point(30f, 30f)); model.textChanged("Launcher icon draft", 0, 0, true)
            model.showSettings(true)
        }
        val preview = model.state.value.preview!!
        try {
            val style = if (original.style == AppIconStyle.Classic) AppIconStyle.Glass else AppIconStyle.Classic
            compose.onNodeWithTag("icon-style-$style").performScrollTo().performClick()
            // Package-change notifications and launcher task cleanup are asynchronous.
            SystemClock.sleep(2500)
            assertFalse("Switching the launcher icon must not finish its editor", activity.isFinishing || activity.isDestroyed)
            compose.onNodeWithTag("settings-page").assertIsDisplayed()
            assertEquals(preview.id, model.state.value.preview?.id)
            assertEquals(preview.text, model.state.value.preview?.text)
            assertEquals(PaintActivity::class.java.name, activity.intent.component?.className)
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
                    model.showSettings(false); model.cancelPreview()
                    activity.finishAndRemoveTask()
                }
            }
        }
    }
}
