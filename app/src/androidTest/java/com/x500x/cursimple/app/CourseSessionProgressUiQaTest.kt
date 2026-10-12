package com.x500x.cursimple.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.*
import com.x500x.cursimple.feature.schedule.CourseDetailDialog
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** In-memory details only; no saved courses or system state are modified. */
class CourseSessionProgressUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val start = LocalDate.of(2026, 9, 7)
    private val monday = CourseItem("monday", "Algorithms", weeks = (1..18).toList(), time = CourseTimeSlot(1, 1, 2))
    private val friday = monday.copy(id = "friday", time = CourseTimeSlot(5, 3, 4))

    @Test fun headingShowsTheSelectedSessionAcrossSameNamedWeekdayArrangements() {
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Light) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    CourseDetailDialog(listOf(friday), timingProfile = null, visibleWeekNumber = 1,
                        isManual = { true }, targetDate = start.plusDays(4), existingCourses = listOf(monday, friday),
                        termStartDate = start, onDismiss = {}, onSetReminder = {}, onDelete = {})
                }
            }
        }
        compose.onNodeWithTag("course-session-progress").assertIsDisplayed().assertTextContains("2/36")
    }

    @Test fun movedCourseCountsAtTheNewPosition() {
        val moved = monday.copy(time = CourseTimeSlot(4, 5, 6))
        val move = TemporaryScheduleOverride("move", TemporaryScheduleOverrideType.MoveCourse, sourceDate = start.toString(),
            targetDate = start.plusDays(3).toString(), moveCourseId = monday.id, moveToStartNode = 5, moveToEndNode = 6)
        compose.setContent {
            ClassScheduleTheme(ThemeMode.Dark) {
                CourseDetailDialog(listOf(moved), timingProfile = null, visibleWeekNumber = 1,
                    isManual = { true }, targetDate = start.plusDays(3), existingCourses = listOf(monday), termStartDate = start,
                    temporaryScheduleOverrides = listOf(move), onDismiss = {}, onSetReminder = {}, onDelete = {})
            }
        }
        compose.onNodeWithTag("course-session-progress").assertIsDisplayed().assertTextContains("1/18")
    }
}
