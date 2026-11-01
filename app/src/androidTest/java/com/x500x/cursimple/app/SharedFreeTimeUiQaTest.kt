package com.x500x.cursimple.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.ScheduleSharePayload
import com.x500x.cursimple.app.util.ScheduleShareCodec
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.app.ai.AiImportConfig
import com.x500x.cursimple.app.ai.AiScheduleImportClient
import com.x500x.cursimple.app.webdav.WebDavClient
import com.x500x.cursimple.app.webdav.WebDavConfig
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** Opt-in (`-e freeTimeQa true`): paste a real share code and read the shared free periods on the real screen. */
class SharedFreeTimeUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun enabled() = InstrumentationRegistry.getArguments().getString("freeTimeQa") == "true"
    private val setup = object : ExternalResource() {
        override fun before() {
            if (!enabled()) return
            runBlocking {
                DataStoreUserPreferencesRepository(context).apply {
                    setDebugForcedDateTime(null)
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

    // My own week: Monday 1-2, Wednesday 3-4 and Friday 5-6, so both grids show real course blocks.
    private val myCourses = listOf(
        CourseItem("mine-math", "高等数学", teacher = "", location = "", weeks = (1..16).toList(), time = CourseTimeSlot(1, 1, 2)),
        CourseItem("mine-english", "大学英语", teacher = "", location = "", weeks = (1..16).toList(), time = CourseTimeSlot(3, 3, 4)),
        CourseItem("mine-physics", "大学物理", teacher = "", location = "", weeks = (1..16).toList(), time = CourseTimeSlot(5, 5, 6)),
    )

    /** The entry lives on the share page now: show that page, then tap the row. */
    private fun openSharedFreeTime() {
        showSharePage()
        compose.onNodeWithText(compose.activity.getString(ScheduleR.string.free_time_entry)).performScrollTo().performClick()
        compose.onNodeWithTag("shared-free-dialog").assertIsDisplayed()
    }

    private fun showSharePage() {
        compose.activity.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = ThemeAccent.Green) {
                ImportExportScreen(schedule = null, manualCourses = myCourses, termName = null, termStartDate = java.time.LocalDate.of(2026, 9, 14),
                    timingProfile = null, temporaryScheduleOverrides = emptyList(),
                    webDavConfig = WebDavConfig("", "", ""), webDavClient = WebDavClient(),
                    aiImportConfig = AiImportConfig("", "", ""), aiImportClient = AiScheduleImportClient(),
                    onApplyImport = { _, _, _, _ -> }, onApplyTermStartDate = {},
                    onCreateAppBackup = { error("Not used") }, onRestoreAppBackup = {},
                    onOpenWebDavSettings = {}, onOpenAiImportSettings = {}, onBack = {})
            }
        }
    }

    private fun shot(name: String) {
        // Escape drops the keyboard the code field left open, so the grids are not hidden behind it.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 111").close()
        Thread.sleep(600)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "free-time-qa/$name.png"))
    }

    @Test fun theEntryIsOnTheSharePage() {
        assumeTrue(enabled())
        showSharePage()
        compose.onNodeWithText(compose.activity.getString(ScheduleR.string.free_time_entry)).performScrollTo().assertIsDisplayed()
        shot("share-page")
    }

    @Test fun anInvalidCodeIsRejectedWithAClearMessage() {
        assumeTrue(enabled())
        openSharedFreeTime()
        compose.onNodeWithTag("shared-free-code").performTextInput("这不是课表")
        compose.onNodeWithTag("shared-free-decode").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("shared-free-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shared-free-error").assertIsDisplayed()
        shot("invalid-code")
    }

    @Test fun aValidCodeShowsTheSharedFreePeriods() {
        assumeTrue(enabled())
        // A classmate's timetable: a Tuesday class in periods 1-2 and a Thursday class in periods 3-4.
        val theirs = ScheduleSharePayload(
            termStartDate = "2026-09-14",
            manualCourses = listOf(
                CourseItem("their-tue", "别人的课", weeks = (1..18).toList(), time = CourseTimeSlot(2, 1, 2)),
                CourseItem("their-thu", "别人的课二", weeks = (1..18).toList(), time = CourseTimeSlot(4, 3, 4)),
            ),
        )
        val code = ScheduleShareCodec.encode(theirs)
        openSharedFreeTime()
        compose.onNodeWithTag("shared-free-code").performTextInput(code)
        compose.onNodeWithTag("shared-free-decode").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("shared-free-run").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("shared-free-none").fetchSemanticsNodes().isNotEmpty() }
        // The code decoded: no error, and the count of their classes is shown.
        assertTrue("valid code is not rejected", compose.onAllNodesWithTag("shared-free-error").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(compose.activity.getString(ScheduleR.string.free_time_their_count, 2)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("shared-free-week-grid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("shared-free-mine").assertIsDisplayed()
        compose.onNodeWithTag("shared-free-theirs").assertIsDisplayed()
        shot("valid-code")
        // Release-note shot: back to the top of the page so the code box, both timetables and the title are in view.
        compose.onNodeWithTag("shared-free-code").performScrollTo()
        shot("announce")
    }
}
