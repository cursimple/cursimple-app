package com.x500x.cursimple.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Opt-in check: a Monday course stays under the Monday header whichever weekday the week starts on. */
class WeekStartAlignmentUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val args get() = InstrumentationRegistry.getArguments()
    private val monday = LocalDate.of(2026, 9, 14)
    @Before fun clock() { BeijingTime.setForcedNow(monday.atTime(9, 0)) }
    @After fun restore() { BeijingTime.setForcedNow(null) }
    private val timing = TermTimingProfile(monday.minusWeeks(1).toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40")))

    private fun render(start: WeekStartDay) {
        Thread.sleep(1200)
        BeijingTime.setForcedNow(monday.atTime(9, 0))
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale)) {
                ClassScheduleTheme(ThemeMode.Light) {
                    ScheduleScreen(
                        state = ScheduleUiState(initialized = true, timingProfile = timing,
                            manualCourses = listOf(CourseItem("c1", "高数", location = "A1", weeks = (1..8).toList(), time = CourseTimeSlot(1, 1, 2)))),
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                        onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                        onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                        onOpenPluginMarket = {}, overrideTermStart = monday.minusWeeks(1),
                        weekOffset = 0, minWeekOffset = 0, maxWeekOffset = 2, termWeekCount = 8,
                        scheduleDisplay = ScheduleDisplayPreferences(weekStartDay = start),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun left(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
    private fun headerLeft(label: String) = compose.onAllNodes(hasText(label), useUnmergedTree = true)
        .fetchSemanticsNodes().minOf { it.boundsInRoot.left }

    @Test fun sundayFirstKeepsMondayCourseUnderMonday() {
        assumeTrue(args.getString("weekStartQa") == "true")
        render(WeekStartDay.Sunday)
        val course = left("schedule-course-c1")
        val mondayHeader = headerLeft("一")
        assertTrue("course($course) should sit at Monday header($mondayHeader)", kotlin.math.abs(course - mondayHeader) < 90f)
    }
}
