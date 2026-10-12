package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

/** Opt-in verification of the main app's About page, independent of component pages. */
class MainAboutChannelUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun enabled() = args.getString("mainAboutQa") == "true"
    private val language get() = if (args.getString("qaLanguage") == "en") AppLanguage.English else AppLanguage.Chinese
    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            instrumentation.runOnMainSync { android.graphics.HardwareRenderer.setDrawingEnabled(true) }
            runBlocking { DataStoreUserPreferencesRepository(context).apply {
                setDisclaimerAccepted(true); setFirstRunGuideCompleted(true); setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                markIslandStartupPromptShown(); markNotificationPermissionStartupAsked(); setTermStartUserDecided(true)
                setAdvancedToolsEnabled(false); setAutoUpdateEnabled(false); setBetaUpdatesEnabled(false)
                setAppLanguage(language)
                setThemeMode(if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light)
                setThemeAccent(if (args.getString("qaDark") == "true") ThemeAccent.Blue else ThemeAccent.Green)
            } }
            AppLocale.cache(context, language)
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @Test fun testingBuildShowsTestingBadgeWhenBetaUpdatesAreOff() {
        assumeTrue(enabled())
        assumeTrue(BuildConfig.RELEASE_CHANNEL == "beta")
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_about)).performClick()
        compose.onAllNodesWithText(activity.getString(R.string.about_channel_beta)).onFirst().assertIsDisplayed()
        compose.onAllNodesWithText(activity.getString(R.string.about_channel_stable)).assertCountEquals(0)
        compose.onAllNodesWithText("v${BuildConfig.VERSION_NAME}").onFirst().assertIsDisplayed()
        compose.waitForIdle(); instrumentation.waitForIdleSync(); Thread.sleep(300)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.filesDir, "main-about/main-about-${args.getString("qaVariant") ?: "normal"}.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
