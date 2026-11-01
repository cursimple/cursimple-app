package com.x500x.cursimple.app

import android.app.UiAutomation
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

/** Opt-in (`-e drawerLandscapeQa true`) landscape drawer width and scroll check on a dedicated emulator. */
class DrawerLandscapeUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun enabled() = InstrumentationRegistry.getArguments().getString("drawerLandscapeQa") == "true"

    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            runBlocking {
                DataStoreUserPreferencesRepository(context).apply {
                    setDisclaimerAccepted(true)
                    setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                    markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked()
                }
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun restore() {
        if (enabled()) instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
    }

    @Test fun landscapeDrawerKeepsItsWidthAndScrolls() {
        assumeTrue(enabled())
        instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.waitForIdle()
        Thread.sleep(600)
        save("drawer-top")
        val settings = compose.onNodeWithText(activity.getString(R.string.screen_settings)).performScrollTo()
        compose.waitForIdle()
        save("drawer-bottom")
        val bounds = settings.fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("drawer rows must stay within 360dp: $bounds", bounds.right <= 360 * density + 1f)
        assertTrue("last drawer row must scroll into view: $bounds", bounds.bottom <= context.resources.displayMetrics.heightPixels)
    }

    private fun save(name: String) {
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "drawer-landscape-qa/$name.png"))
    }
}
