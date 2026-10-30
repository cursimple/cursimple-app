package com.x500x.cursimple.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.attendance.AttendanceStatus
import com.x500x.cursimple.core.data.attendance.withAllowance
import com.x500x.cursimple.core.data.attendance.withMark
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import com.x500x.cursimple.feature.schedule.R as ScheduleR

/** In-memory only: the course detail's homework list and attendance marks, and what they draw on the card. */
class CourseTodosAndAttendanceUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private val monday = LocalDate.of(2026, 9, 14)
    private val termStart = monday.minusWeeks(6)
    @Before fun clock() { BeijingTime.setForcedNow(monday.atTime(9, 45)) }
    @After fun restore() { BeijingTime.setForcedNow(null) }

    private val math = CourseItem("math", "高等数学", weeks = (1..18).toList(), time = CourseTimeSlot(1, 1, 2), location = "理工楼110")
    private val state = mutableStateOf(
        ScheduleUiState(
            initialized = true,
            timingProfile = TermTimingProfile(termStart.toString(), listOf(ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40"))),
            manualCourses = listOf(math),
        ),
    )

    private fun show(weekOffset: Int = 0) = compose.setContent {
        ClassScheduleTheme(themeMode = ThemeMode.Light, themeAccent = ThemeAccent.Green) {
            val current = state.value
            ScheduleScreen(
                state = current,
                onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                onOpenPluginMarket = {}, overrideTermStart = termStart,
                weekOffset = weekOffset, minWeekOffset = -6, maxWeekOffset = 11, termWeekCount = 18,
                onSaveMemo = { memo -> state.value = state.value.copy(memos = state.value.memos.filterNot { it.id == memo.id } + memo) },
                onMarkAttendance = { key, date, status ->
                    state.value = state.value.copy(attendance = state.value.attendance.withMark(key, date, status, 1L))
                },
                onSetAbsenceAllowance = { key, allowance ->
                    state.value = state.value.copy(attendance = state.value.attendance.withAllowance(key, allowance))
                },
            )
        }
    }

    private fun openCourse() {
        compose.onNode(hasTestTag("schedule-course-math") and hasAnyAncestor(hasTestTag("schedule-current-week")), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    @Test fun homeworkAddedFromTheCourseDetailLandsInItsNotebookAndShowsOnTheCard() {
        show()
        openCourse()
        compose.onNodeWithTag("course-todos-section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("course-todo-add").performScrollTo().performClick()
        compose.onNodeWithTag("course-todo-title").performTextInput("交实验报告")
        compose.onNodeWithTag("course-todo-due-tomorrow").performClick()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "todo-qa/dialog.png"))
        compose.onNodeWithTag("course-todo-save").performClick()
        compose.waitForIdle()
        val saved = state.value.memos.single()
        assertEquals("交实验报告", saved.title)
        assertEquals(memoCourseKey("高等数学"), saved.courseKey)
        assertEquals(monday.plusDays(1).atTime(23, 59).toString(), saved.dueAt)
        compose.onNodeWithTag("course-todos-count").assertExists()
        compose.onNode(hasTestTag("course-todo-badge-math") and hasAnyAncestor(hasTestTag("schedule-current-week")), useUnmergedTree = true).assertExists()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "todo-qa/detail.png"))
        // Ticking the row off completes it and clears the card badge.
        compose.onNodeWithTag("course-todo-row-${saved.id}").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(state.value.memos.single().completed)
        compose.onAllNodes(hasTestTag("course-todo-badge-math"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun markingAMissedClassCountsItAgainstTheLimitAndFlagsTheCard() {
        show()
        openCourse()
        compose.onNodeWithTag("course-attendance-section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("attendance-allowance-set").performScrollTo().performClick()
        compose.onNodeWithTag("attendance-allowance-less").performScrollTo().performClick()
        compose.onNodeWithTag("attendance-allowance-value").assertTextEquals(text(ScheduleR.string.attendance_allowance_value).format(2))
        compose.onNodeWithTag("attendance-mark-Absent").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(AttendanceStatus.Absent, state.value.attendance.statusOf(memoCourseKey("高等数学"), monday.toString()))
        compose.onNodeWithTag("attendance-totals").assertTextContains("1", substring = true)
        compose.onNodeWithTag("attendance-remaining").assertExists()
        compose.onNode(hasTestTag("course-attendance-badge-math") and hasAnyAncestor(hasTestTag("schedule-current-week")), useUnmergedTree = true).assertExists()
        Thread.sleep(300)
        ScreenshotCapture.save(ScreenshotCapture.take(), File(context.filesDir, "todo-qa/attendance.png"))
        // Tapping the same mark again clears it.
        compose.onNodeWithTag("attendance-mark-Absent").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(state.value.attendance.records.isEmpty())
        compose.onAllNodes(hasTestTag("course-attendance-badge-math"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun aFutureClassCannotBeMarkedYet() {
        // Looking a week ahead of the clock, that Monday's class is still to come.
        show(weekOffset = 1)
        openCourse()
        compose.onNodeWithTag("course-attendance-section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("attendance-mark-Absent").performScrollTo().assertIsNotEnabled()
        assertNotNull(state.value.manualCourses.firstOrNull())
    }
}
