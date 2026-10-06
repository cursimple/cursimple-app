package com.x500x.cursimple.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

/**
 * Checks the menu update badge and beta opt-in dialog on a dedicated emulator. Restores update
 * preferences afterward.
 */
class UpdateBadgeUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun enabled() = InstrumentationRegistry.getArguments().getString("updateBadgeQa") == "true"
    private val repository get() = DataStoreUserPreferencesRepository(context)

    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            runBlocking {
                repository.apply {
                    setDisclaimerAccepted(true)
                    setFirstRunGuideCompleted(true)
                    setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                    markIslandStartupPromptShown()
                    markNotificationPermissionStartupAsked()
                    // Disable live checks so they cannot overwrite the injected update state.
                    setAutoUpdateEnabled(false)
                    setBetaUpdatesEnabled(false)
                    setUpdateNotice(BuildConfig.VERSION_CODE + 100, "9.9.9")
                }
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun restore() {
        if (!enabled()) return
        runBlocking {
            repository.setBetaUpdatesEnabled(false)
            repository.setAutoUpdateEnabled(true)
            repository.clearUpdateNotice()
        }
    }

    private fun capture(name: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val file = File(context.filesDir, name)
        file.outputStream().use { node.captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun menuButtonShowsDotAndUpdateDialogTogglesBeta() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTagCount("menu-update-dot") > 0
        }
        capture("update-menu-dot.png", compose.onNode(isRoot()))
        val dot = compose.onNodeWithTag("menu-update-dot", useUnmergedTree = true).fetchSemanticsNode()
        println("QA dot bounds=" + dot.boundsInWindow + " size=" + dot.size)
        compose.onNodeWithTag("menu-update-dot", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.main_drawer_update_shortcut)).performClick()
        val betaTitle = activity.getString(R.string.settings_beta_updates_title)
        compose.onNodeWithText(betaTitle).assertIsDisplayed()
        compose.waitForIdle()
        capture("update-dialog-beta.png", compose.onNode(androidx.compose.ui.test.isDialog()))

        compose.onNodeWithText(betaTitle).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_beta_updates_confirm_ok)).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { runBlocking { repository.preferencesFlow.first().betaUpdatesEnabled } }
        assertTrue(runBlocking { repository.preferencesFlow.first().betaUpdatesEnabled })

        compose.onNodeWithText(betaTitle).performClick()
        compose.waitUntil(5_000) { !runBlocking { repository.preferencesFlow.first().betaUpdatesEnabled } }
        assertFalse(runBlocking { repository.preferencesFlow.first().betaUpdatesEnabled })
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size
}
