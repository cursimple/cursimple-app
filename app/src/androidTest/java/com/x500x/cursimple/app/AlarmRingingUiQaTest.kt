package com.x500x.cursimple.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.reminder.AlarmRingingActivity
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockIntents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Requires alarmUiQa=true on a dedicated QA emulator. */
class AlarmRingingUiQaTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun gate() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("alarmUiQa") == "true")
        // "system" restores the follow-the-device language after a capture run.
        val requested = args.getString("alarmUiLanguage")?.let { if (it == "system") "" else it }
        AppLanguage.entries.firstOrNull { it.tag == requested }?.let { language ->
            runBlocking { (context.applicationContext as ClassScheduleApplication).appContainer.userPreferencesRepository.setAppLanguage(language) }
            AppLocale.cache(context, language)
        }
    }

    private fun ringingIntent() = Intent(context, AlarmRingingActivity::class.java).apply {
        putExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY, "qa-alarm-ui")
        putExtra(AppAlarmClockIntents.EXTRA_RULE_ID, "qa-rule")
        putExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID, "")
        putExtra(AppAlarmClockIntents.EXTRA_PLAN_ID, "qa-plan")
        putExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, System.currentTimeMillis())
        putExtra(AppAlarmClockIntents.EXTRA_TITLE, "高等数学")
        putExtra(AppAlarmClockIntents.EXTRA_MESSAGE, "08:00 上课 · A101 · 还有 15 分钟")
    }

    @Test fun ringingScreenSlidesToStop() {
        ActivityScenario.launch<AlarmRingingActivity>(ringingIntent()).use { scenario ->
            // API 34 does not force edge-to-edge like API 35+; mimic it so the capture matches what current devices show.
            scenario.onActivity { activity ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                // The camera cutout would otherwise leave a black band; the release image is full-bleed without system bars.
                activity.window.attributes = activity.window.attributes.apply {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            compose.waitForIdle()
            Thread.sleep(900)
            save("alarm-ringing.png")
            compose.onNodeWithTag("alarm_slide_thumb").performTouchInput { swipeRight(startX = centerX, endX = centerX + 60f, durationMillis = 250) }
            compose.waitForIdle()
            Thread.sleep(600)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            save("alarm-ringing-partial.png")
            compose.onNodeWithTag("alarm_slide_thumb").performTouchInput { swipeRight(startX = centerX, endX = centerX + 2000f, durationMillis = 400) }
            Thread.sleep(300)
            save("alarm-ringing-after-slide.png")
            Thread.sleep(1500)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    private fun save(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
