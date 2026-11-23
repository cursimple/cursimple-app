package com.x500x.cursimple.core.kernel.model

import com.x500x.cursimple.core.kernel.time.WeekStartDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CourseWeekPreviewTest {
    @Test
    fun previewStartsAtTheFirstVisibleTeachingWeekAndKeepsGaps() {
        val preview = courseWeekPreview(listOf(course("late", listOf(5)), course("early", listOf(3)),
            course("deleted", listOf(1)).copy(hidden = true), course("alarm", listOf(1)).copy(reminderOnly = true)))
        assertEquals(3, preview.sourceWeek(1))
        assertEquals(4, preview.sourceWeek(2))
        assertEquals(3, preview.displayWeek(5))
    }

    @Test
    fun anUnrestrictedCourseStartsInWeekOne() {
        assertEquals(1, courseWeekPreview(listOf(course("weekly", emptyList()), course("later", listOf(3)))).sourceWeek(1))
        assertEquals(1, courseWeekPreview(emptyList()).sourceWeek(1))
    }

    @Test
    fun dayAndWeekPagesAgreeAcrossASundayBoundary() {
        val preview = courseWeekPreview(listOf(course("weekly", listOf(3))))
        val today = LocalDate.of(2026, 11, 20)
        val sunday = today.plusDays(2)
        assertEquals(3, preview.sourceWeekForPageDate(today, sunday, WeekStartDay.Monday))
        assertEquals(4, preview.sourceWeekForPageDate(today, sunday, WeekStartDay.Sunday))
    }

    private fun course(id: String, weeks: List<Int>) = CourseItem(id, id, weeks = weeks, time = CourseTimeSlot(1, 1, 1))
}
