package com.x500x.cursimple.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.courseWeekPreview
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import com.x500x.cursimple.feature.schedule.ScheduleViewMode
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class CourseWeekPreviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun weekPickerUsesRelativeLabelsAndReturnsTheOriginalWeek() {
        val preview = courseWeekPreview(listOf(
            CourseItem("preview-first", "Physics Lab", weeks = listOf(3, 5), time = CourseTimeSlot(2, 1, 2)),
        ))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var pickedWeek: Int? = null
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light) {
                WeekPickerSheet(
                    termStart = null,
                    currentWeek = preview.firstCourseWeek,
                    selectedWeek = preview.firstCourseWeek,
                    totalWeeks = 5,
                    onSelectWeek = { pickedWeek = it },
                    onSetSelectedAsCurrent = {},
                    onDismiss = {},
                    preview = preview,
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.week_picker_no_term_start)).assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("4").assertDoesNotExist()
        compose.onNodeWithText("2").performClick()
        compose.runOnIdle { assertEquals(4, pickedWeek) }
    }

    @Test
    fun unsetTermStartsAtTheFirstCourseAndPagesThroughTheOriginalWeeks() {
        var weekOffset by mutableIntStateOf(0)
        var dayOffset by mutableIntStateOf(0)
        var viewMode by mutableStateOf(ScheduleViewMode.Week)
        val courses = listOf(
            CourseItem("preview-first", "Physics Lab", weeks = listOf(3), time = CourseTimeSlot(2, 1, 2)),
            CourseItem("preview-next", "Workshop", weeks = listOf(4), time = CourseTimeSlot(2, 1, 2)),
        )
        val today = BeijingTime.todayIn(BeijingTime.zone)
        val firstTuesdayOffset = 2 - today.dayOfWeek.value
        val dark = InstrumentationRegistry.getArguments().getString("previewDark") == "true"
        compose.setContent {
            ClassScheduleTheme(
                themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (dark) ThemeAccent.Blue else ThemeAccent.Green,
            ) {
                ScheduleScreen(
                    state = ScheduleUiState(initialized = true, manualCourses = courses),
                    onCreateCourseReminder = { _, _, _ -> },
                    onMuteExamReminder = {}, onRestoreExamReminder = {},
                    onRemoveReminderRule = {}, onRemoveManualCourse = {},
                    onCreateBulkReminder = { _, _, _ -> },
                    onPrevWeek = {}, onNextWeek = {},
                    onPrevDay = {}, onNextDay = {}, onResetDay = {},
                    onOpenPluginMarket = {},
                    weekOffset = weekOffset,
                    onWeekOffsetChange = { weekOffset = it },
                    minWeekOffset = 0,
                    maxWeekOffset = 1,
                    viewMode = viewMode,
                    dayOffset = dayOffset,
                    onDayOffsetChange = { dayOffset = it },
                    scheduleDisplay = ScheduleDisplayPreferences(
                        totalScheduleDisplayEnabled = false,
                        todayOverviewEnabled = false,
                    ),
                )
            }
        }
        currentWeekCourse("preview-first").assertIsDisplayed()
        currentWeekCourse("preview-next").assertDoesNotExist()
        compose.runOnIdle { weekOffset = 1 }
        currentWeekCourse("preview-next").assertIsDisplayed()
        currentWeekCourse("preview-first").assertDoesNotExist()

        compose.runOnIdle {
            dayOffset = firstTuesdayOffset
            viewMode = ScheduleViewMode.Day
        }
        compose.onNodeWithText("Physics Lab").assertIsDisplayed()
        compose.runOnIdle { dayOffset = firstTuesdayOffset + 7 }
        compose.onNodeWithText("Workshop").assertIsDisplayed()
    }

    private fun currentWeekCourse(id: String) = compose.onNode(
        hasTestTag("schedule-course-$id") and hasAnyAncestor(hasTestTag("schedule-current-week")),
        useUnmergedTree = true,
    )
}
