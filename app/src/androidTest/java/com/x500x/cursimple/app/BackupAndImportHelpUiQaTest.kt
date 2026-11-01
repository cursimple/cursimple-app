package com.x500x.cursimple.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.app.util.ImportSource
import com.x500x.cursimple.app.util.diagnoseImportFailure
import com.x500x.cursimple.app.webdav.WebDavAutoBackupStore
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

/** Import failure help (in memory) and the WebDAV automatic backup switch (opt-in: `-e backupHelpQa true`). */
class BackupAndImportHelpUiQaTest {
    private val composeOnly = createComposeRule()
    @get:Rule val rule = composeOnly
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun anImportFailureExplainsWhatToDoAndCanCopyItsDetails() {
        val diagnosis = diagnoseImportFailure(ImportSource.Ics, IllegalArgumentException("暂不支持此 ICS 重复频率：MONTHLY"))
        var closed = false
        composeOnly.setContent {
            ClassScheduleTheme(ThemeMode.Light) { ImportFailureDialog(diagnosis, onDismiss = { closed = true }) }
        }
        composeOnly.onNodeWithTag("import-failure-reason").assertIsDisplayed().assertTextContains(context.getString(R.string.import_diag_ics_recurrence))
        composeOnly.onNodeWithTag("import-failure-hint").assertTextContains(context.getString(R.string.import_diag_ics_recurrence_hint))
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "backup-help-qa/import-failure.png"))
        composeOnly.onNodeWithTag("import-failure-copy").performClick()
        composeOnly.onNodeWithTag("import-failure-close").performClick()
        assertTrue(closed)
    }
}

class WebDavAutoBackupSettingsUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun enabled() = InstrumentationRegistry.getArguments().getString("backupHelpQa") == "true"
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
                WebDavAutoBackupStore(context).setEnabled(false)
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:org.junit.Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun reset() {
        if (enabled()) {
            runBlocking { WebDavAutoBackupStore(context).setEnabled(false) }
            com.x500x.cursimple.app.webdav.WebDavAutoBackupWorker.apply(context, false)
        }
    }

    @Test fun theSwitchRevealsTheKeepCountAndStatusAndPersists() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).performClick()
        compose.onAllNodesWithTextOrEmpty(activity.getString(R.string.main_later))
        compose.onNodeWithText("WebDAV").performScrollTo().performClick()
        compose.onNodeWithTag("webdav-auto-backup").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.webdav_auto_title)).performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { WebDavAutoBackupStore(context).settingsFlow.first().enabled } }
        compose.onNodeWithTag("webdav-auto-status").performScrollTo().assertTextContains(activity.getString(R.string.webdav_auto_never))
        compose.onNodeWithTag("webdav-auto-now").performScrollTo().assertIsDisplayed()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "backup-help-qa/webdav-auto.png"))
        assertEquals(WebDavAutoBackupSettingsKeepDefault, runBlocking { WebDavAutoBackupStore(context).settingsFlow.first().keep })
        compose.onNodeWithText(activity.getString(R.string.webdav_auto_title)).performScrollTo().performClick()
        compose.waitUntil(5_000) { !runBlocking { WebDavAutoBackupStore(context).settingsFlow.first().enabled } }
        assertFalse(runBlocking { WebDavAutoBackupStore(context).settingsFlow.first().enabled })
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextOrEmpty(text: String) {
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().takeIf { it.isNotEmpty() }
            ?.let { onNodeWithText(text).performClick() }
    }
}

private const val WebDavAutoBackupSettingsKeepDefault = 5
