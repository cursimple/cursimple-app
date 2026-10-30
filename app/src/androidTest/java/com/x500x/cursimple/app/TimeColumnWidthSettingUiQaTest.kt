package com.x500x.cursimple.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

/** Opt-in (`-e timeColumnQa true`): the real settings page; the width is reset to automatic afterwards. */
class TimeColumnWidthSettingUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun enabled() = InstrumentationRegistry.getArguments().getString("timeColumnQa") == "true"
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
                    setTimeColumnWidthDp(0)
                }
            }
            com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(context)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    @After fun reset() {
        if (enabled()) runBlocking { DataStoreUserPreferencesRepository(context).setTimeColumnWidthDp(0) }
    }

    private fun stored() = runBlocking { DataStoreUserPreferencesRepository(context).preferencesFlow.first().scheduleDisplay.timeColumnWidthDp }

    @Test fun theSliderDefaultsToAutomaticStoresAWidthAndResets() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_display)).performScrollTo().performClick()
        // A fresh emulator has no term start date, which raises a reminder dialog over the page.
        compose.onAllNodesWithText(activity.getString(R.string.main_later)).fetchSemanticsNodes().takeIf { it.isNotEmpty() }
            ?.let { compose.onNodeWithText(activity.getString(R.string.main_later)).performClick() }
        compose.onNodeWithTag("settings-time-column-width").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.settings_time_column_width_auto)).assertIsDisplayed()
        compose.onNodeWithTag("settings-time-column-reset").assertDoesNotExist()
        assertEquals("untouched means automatic", 0, stored())
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "time-column-qa/auto.png"))

        // A real drag across the hand-drawn slider reaches the far end.
        compose.onNodeWithTag("settings-time-column-slider").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { stored() >= 64 }
        // Accessibility services set the progress directly.
        compose.onNodeWithTag("settings-time-column-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(48f) }
        compose.waitUntil(5_000) { stored() == 48 }
        assertEquals("the slider stores the chosen width", 48, stored())
        compose.onNodeWithTag("settings-time-column-reset").assertIsDisplayed()
        // The right-hand label is a ratio of the screen width, not a dp number.
        val widthPx = context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density
        compose.onNodeWithTag("app-slider-value").assertTextEquals("${Math.round(48f * 100f / Math.round(widthPx).coerceAtLeast(1))}%")
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "time-column-qa/custom.png"))

        compose.onNodeWithTag("settings-time-column-reset").performClick()
        compose.waitUntil(5_000) { stored() == 0 }
        compose.onNodeWithText(activity.getString(R.string.settings_time_column_width_auto)).assertIsDisplayed()
    }

    @Test fun aSettingsGroupFoldsAndUnfolds() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_display)).performScrollTo().performClick()
        compose.onAllNodesWithText(activity.getString(R.string.main_later)).fetchSemanticsNodes().takeIf { it.isNotEmpty() }
            ?.let { compose.onNodeWithText(activity.getString(R.string.main_later)).performClick() }
        val group = activity.getString(R.string.settings_subgroup_cell_info)
        val inside = activity.getString(R.string.settings_display_node_time_title)
        compose.onNodeWithText(inside).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-group-toggle-$group").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(inside).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("settings-group-toggle-$group").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(inside).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun previewCardLeft(): Float = compose.onNode(
        hasTestTag("schedule-course-preview-english") and hasAnyAncestor(hasTestTag("schedule-appearance-preview")) and
            hasAnyAncestor(hasTestTag("schedule-current-week")),
        useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInWindow.left

    @Test fun theDisplayPagePreviewIsTheRealTimetableAndFollowsTheSlider() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_display)).performScrollTo().performClick()
        compose.onAllNodesWithText(activity.getString(R.string.main_later)).fetchSemanticsNodes().takeIf { it.isNotEmpty() }
            ?.let { compose.onNodeWithText(activity.getString(R.string.main_later)).performClick() }
        compose.onNodeWithTag("schedule-appearance-preview").assertIsDisplayed()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("schedule-course-preview-english") and hasAnyAncestor(hasTestTag("schedule-appearance-preview")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "time-column-qa/display-preview.png"))
        val startLeft = previewCardLeft()
        compose.onNodeWithTag("settings-time-column-width").performScrollTo()
        compose.onNodeWithTag("settings-time-column-slider")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(24f) }
        compose.waitUntil(5_000) { stored() == 24 }
        // The preference reaches the screen through a flow; wait for the preview to settle on it.
        var narrow = previewCardLeft()
        compose.waitUntil(8_000) { previewCardLeft().also { narrow = it } != startLeft }
        compose.onNodeWithTag("settings-time-column-slider")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(60f) }
        compose.waitUntil(5_000) { stored() == 60 }
        var wide = narrow
        compose.waitUntil(8_000) { previewCardLeft().also { wide = it } > narrow + 20f }
        val density = context.resources.displayMetrics.density
        // The sample timetable shifts by the width difference, so the preview really is the live grid.
        // 31dp of real shift, drawn at the preview's scale (a 392x640dp page shrunk into ~260dp of height).
        val shift = (wide - narrow) / density
        assertTrue("preview grid follows the slider (moved ${shift}dp)", shift in 8f..18f)
    }

    @Test fun theDisplayPageLeadsWithLayoutAndKeepsRareGroupsFolded() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_display)).performScrollTo().performClick()
        compose.onAllNodesWithText(activity.getString(R.string.main_later)).fetchSemanticsNodes().takeIf { it.isNotEmpty() }
            ?.let { compose.onNodeWithText(activity.getString(R.string.main_later)).performClick() }
        // "One screen or scrolling" is the first thing under the preview, before the card contents.
        val layoutTop = compose.onNodeWithText(activity.getString(R.string.settings_display_row_fit_title)).performScrollTo().fetchSemanticsNode().positionInRoot.y
        val locationTop = compose.onNodeWithText(activity.getString(R.string.settings_display_location_title)).performScrollTo().fetchSemanticsNode().positionInRoot.y
        assertTrue("layout ($layoutTop) comes before the card contents ($locationTop)", layoutTop < locationTop)
        // Gestures are rarely changed, so that group starts folded and opens on a tap.
        val rare = activity.getString(R.string.settings_subgroup_interaction)
        val dragRow = activity.getString(R.string.settings_display_course_drag_title)
        assertTrue(compose.onAllNodesWithText(dragRow).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("settings-group-toggle-$rare").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(dragRow).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "time-column-qa/display-regrouped.png"))
    }

    @Test fun theStylePageLeadsWithCardsAndTextAndFoldsTheRest() {
        assumeTrue(enabled())
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_open_drawer)).performClick()
        compose.onNodeWithText(activity.getString(R.string.screen_settings)).performClick()
        compose.waitForIdle()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "time-column-qa/root.png"))
        compose.onNodeWithText(activity.getString(R.string.settings_dest_schedule_style)).performScrollTo().performClick()
        compose.onAllNodesWithText(activity.getString(R.string.main_later)).fetchSemanticsNodes().takeIf { it.isNotEmpty() }
            ?.let { compose.onNodeWithText(activity.getString(R.string.main_later)).performClick() }
        val cardTop = compose.onNodeWithText(activity.getString(R.string.settings_subgroup_card)).performScrollTo().fetchSemanticsNode().positionInRoot.y
        val textTop = compose.onNodeWithText(activity.getString(R.string.settings_subgroup_course_text)).performScrollTo().fetchSemanticsNode().positionInRoot.y
        assertTrue("cards ($cardTop) come before course text ($textTop)", cardTop < textTop)
        val headerRow = activity.getString(R.string.settings_header_text_size)
        assertTrue("header settings start folded", compose.onAllNodesWithText(headerRow).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("settings-group-toggle-${activity.getString(R.string.settings_header_style)}").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(headerRow).fetchSemanticsNodes().isNotEmpty() }
    }
}
