package com.x500x.cursimple.app

import android.app.UiAutomation
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Checks all three actions in the real sheet at constrained screen heights. */
class ThirdPartyImportQaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()

    @Before fun prepare() {
        assumeTrue(args.getString("preImportQa") == "true")
        instrumentation.uiAutomation.setRotation(if (args.getString("qaVariant").orEmpty().contains("land"))
            UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        Thread.sleep(1000)
    }

    @After fun restoreRotation() {
        instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    @Test fun allThirdPartyActionsRemainReachableAndBackDismisses() {
        val visible = mutableStateOf(true)
        var chosen = ""
        compose.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,
                args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                ClassScheduleTheme(
                    themeMode = if (args.getString("qaTheme") == "dark") ThemeMode.Dark else ThemeMode.Light,
                    themeAccent = ThemeAccent.Blue,
                ) {
                    if (visible.value) ThirdPartyImportSheet(
                        onDismiss = { visible.value = false },
                        onImportIcs = { chosen = "ics"; visible.value = false },
                        onImportWakeUp = { chosen = "wakeup"; visible.value = false },
                        onImportAiSchedule = { chosen = "aischedule"; visible.value = false },
                    )
                }
            }
        }
        val context = AppLocale.wrap(instrumentation.targetContext)
        for ((resource, key) in listOf(
            R.string.ie_import_aischedule_title to "aischedule",
            R.string.ie_import_wakeup_title to "wakeup",
            R.string.ie_import_ics_title to "ics",
        )) {
            compose.runOnIdle { visible.value = true }
            val action = compose.onNodeWithText(context.getString(resource))
            action.performScrollTo().assertIsDisplayed()
            if (key == "aischedule") {
                val file = File(instrumentation.targetContext.filesDir,
                    "pre-regression/${args.getString("qaVariant", "portrait")}/third-party-import.png")
                ScreenshotCapture.save(ScreenshotCapture.take(), file)
            }
            action.performClick()
            compose.waitForIdle()
            assertEquals(key, chosen)
            assertEquals(false, visible.value)
        }
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { !visible.value }
    }
}
