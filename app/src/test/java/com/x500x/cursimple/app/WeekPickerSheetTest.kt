package com.x500x.cursimple.app

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekPickerSheetTest {
    @Test
    fun `week picker total weeks follows actual schedule data`() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-05-01T00:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "course-1",
                            title = "高等数学",
                            weeks = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val manualCourse = CourseItem(
            id = "manual-1",
            title = "补充课程",
            weeks = listOf(18),
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 3, endNode = 4),
        )

        val totalWeeks = resolveWeekPickerTotalWeeks(
            schedule = schedule,
            manualCourses = listOf(manualCourse),
            currentWeek = 10,
            selectedWeek = 12,
        )

        assertEquals(18, totalWeeks)
    }

    @Test
    fun `day view selection keeps the same weekday when jumping weeks`() {
        val today = LocalDate.of(2026, 5, 2)
        val termStart = LocalDate.of(2026, 2, 23)

        val dayOffset = resolveDayOffsetForSelectedWeek(
            today = today,
            currentDayOffset = 0,
            selectedWeek = 3,
            termStart = termStart,
            currentWeek = 10,
        )

        assertEquals(-49, dayOffset)
    }

    @Test
    fun `current week derives term start from today's monday`() {
        val today = LocalDate.of(2026, 5, 4)

        val termStart = deriveTermStartForCurrentWeek(today = today, currentWeek = 11)

        assertEquals(LocalDate.of(2026, 2, 23), termStart)
    }

    @Test
    fun `current week below one is treated as first week`() {
        val today = LocalDate.of(2026, 5, 6)

        val termStart = deriveTermStartForCurrentWeek(today = today, currentWeek = 0)

        assertEquals(LocalDate.of(2026, 5, 4), termStart)
    }
}
