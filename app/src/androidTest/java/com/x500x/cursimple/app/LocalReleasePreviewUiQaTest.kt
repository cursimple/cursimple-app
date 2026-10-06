package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.app.update.buildReleaseAnnouncement
import com.x500x.cursimple.app.update.loadLocalReleasePreview
import com.x500x.cursimple.app.update.releaseImageLoader
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in offline preview checks for a dedicated QA emulator. */
class LocalReleasePreviewUiQaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun localAnnouncementOpensFromDeveloperSettingsWithAllImagesAvailable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localReleasePreviewQa") == "true")
        val activity = compose.activity
        val notes = runBlocking {
            DataStoreUserPreferencesRepository(activity).apply {
                setDisclaimerAccepted(true)
                setFirstRunGuideCompleted(true)
                setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                markIslandStartupPromptShown()
                markNotificationPermissionStartupAsked()
                setAdvancedToolsEnabled(true)
            }
            requireNotNull(loadLocalReleasePreview(activity))
        }
        val announcement = requireNotNull(buildReleaseAnnouncement(notes.markdown))
        assertEquals(10, announcement.highlights.size)
        val loader = releaseImageLoader(activity, notes.localDir, notes.localAssetDir)
        runBlocking {
            for (highlight in announcement.highlights) {
                assertNotNull("Missing local image: ${highlight.image.url}", loader.load(highlight.image.url))
            }
        }
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_dest_dev_data)).performScrollTo().performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_dev_release_preview_title)).performScrollTo().performClick()
        compose.onNodeWithText(announcement.highlights.first().title).assertIsDisplayed()
        capture("announcement-first.png")
        compose.onNodeWithContentDescription(activity.getString(R.string.update_announcement_prev)).performClick()
        compose.onNodeWithText(activity.getString(R.string.update_announcement_more_title)).assertIsDisplayed()
        capture("announcement-summary.png")
        compose.onNodeWithText(activity.getString(R.string.update_announcement_dismiss)).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_dev_release_preview_title)).assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val painted = CountDownLatch(1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            compose.activity.window.decorView.postOnAnimation {
                compose.activity.window.decorView.postOnAnimation { painted.countDown() }
            }
        }
        check(painted.await(5, TimeUnit.SECONDS))
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "release-preview-qa")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
