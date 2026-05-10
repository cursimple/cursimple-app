package com.kebiao.viewer.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TemporaryScheduleOverrideTest {
    @Test
    fun `explicit date override resolves source date`() {
        val target = LocalDate.of(2026, 5, 6)
        val source = LocalDate.of(2026, 5, 11)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                targetDate = target.toString(),
                sourceDate = source.toString(),
            ),
        )

        assertEquals(source, resolveTemporaryScheduleSourceDate(target, overrides))
        assertEquals(1, resolveTemporaryScheduleDayOfWeek(target, overrides))
        assertTrue(isTemporaryScheduleOverridden(target, overrides))
    }

    @Test
    fun `single day override resolves source weekday`() {
        val date = LocalDate.of(2026, 5, 6)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                startDate = "2026-05-06",
                endDate = "2026-05-06",
                sourceDayOfWeek = 1,
            ),
        )

        assertEquals(1, resolveTemporaryScheduleDayOfWeek(date, overrides))
        assertTrue(isTemporaryScheduleOverridden(date, overrides))
    }

    @Test
    fun `date range override covers every date in range`() {
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "holiday",
                startDate = "2026-05-04",
                endDate = "2026-05-06",
                sourceDayOfWeek = 5,
            ),
        )

        assertTrue(overrides.first().containsDate(LocalDate.of(2026, 5, 4)))
        assertTrue(overrides.first().containsDate(LocalDate.of(2026, 5, 5)))
        assertTrue(overrides.first().containsDate(LocalDate.of(2026, 5, 6)))
        assertFalse(overrides.first().containsDate(LocalDate.of(2026, 5, 7)))
    }

    @Test
    fun `overlapping overrides prefer the last rule`() {
        val date = LocalDate.of(2026, 5, 5)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "first",
                startDate = "2026-05-04",
                endDate = "2026-05-06",
                sourceDayOfWeek = 1,
            ),
            TemporaryScheduleOverride(
                id = "second",
                startDate = "2026-05-05",
                endDate = "2026-05-05",
                sourceDayOfWeek = 3,
            ),
        )

        assertEquals("second", matchingTemporaryScheduleOverride(date, overrides)?.id)
        assertEquals(3, resolveTemporaryScheduleDayOfWeek(date, overrides))
    }

    @Test
    fun `invalid override dates and weekdays are ignored`() {
        val date = LocalDate.of(2026, 5, 6)
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "bad-date",
                startDate = "not-a-date",
                endDate = "2026-05-06",
                sourceDayOfWeek = 1,
            ),
            TemporaryScheduleOverride(
                id = "bad-weekday",
                startDate = "2026-05-06",
                endDate = "2026-05-06",
                sourceDayOfWeek = 9,
            ),
        )

        assertNull(matchingTemporaryScheduleOverride(date, overrides))
        assertEquals(date.dayOfWeek.value, resolveTemporaryScheduleDayOfWeek(date, overrides))
    }

    @Test
    fun `cancel override cancels matching node range on target date`() {
        val date = LocalDate.of(2026, 5, 6)
        val course = CourseItem(
            id = "math",
            title = "高等数学",
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )
        val override = TemporaryScheduleOverride(
            id = "cancel",
            type = TemporaryScheduleOverrideType.CancelCourse,
            targetDate = "2026-05-06",
            cancelStartNode = 2,
            cancelEndNode = 2,
        )

        assertTrue(isCourseTemporarilyCancelled(date, course, listOf(override)))
        assertFalse(isCourseTemporarilyCancelled(date.plusDays(1), course, listOf(override)))
    }

    @Test
    fun `cancel override can target a single course id`() {
        val date = LocalDate.of(2026, 5, 6)
        val target = CourseItem(
            id = "math",
            title = "高等数学",
            time = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2),
        )
        val other = target.copy(id = "english", title = "大学英语")
        val override = TemporaryScheduleOverride(
            id = "cancel",
            type = TemporaryScheduleOverrideType.CancelCourse,
            targetDate = "2026-05-06",
            cancelStartNode = 1,
            cancelEndNode = 2,
            cancelCourseId = "math",
        )

        assertEquals(listOf(other), filterTemporaryCancelledCourses(date, listOf(target, other), listOf(override)))
    }
}
