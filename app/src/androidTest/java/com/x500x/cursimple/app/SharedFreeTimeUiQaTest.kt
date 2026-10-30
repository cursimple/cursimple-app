package com.x500x.cursimple.app

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

    private fun openSharedFreeTime() {
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.main_add_to_schedule)).performClick()
        compose.onNodeWithTag("menu-shared-free-time").performClick()
        compose.onNodeWithTag("shared-free-dialog").assertIsDisplayed()
    }

    private fun shot(name: String) {
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "free-time-qa/$name.png"))
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
        shot("valid-code")
    }
}
