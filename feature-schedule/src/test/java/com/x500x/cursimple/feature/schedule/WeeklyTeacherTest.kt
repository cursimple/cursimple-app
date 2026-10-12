package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeeklyTeacherTest {
    @Test fun individualMoveRetainsSourceWeekTeacherWithoutAffectingOtherCourse() {
        val start = LocalDate.of(2026, 9, 7)
        val target = start.plusWeeks(1)
        val original = CourseItem("lab", "Lab", teacher = "Default", weeks = listOf(1, 2),
            weekTeachers = mapOf(1 to "Teacher A", 2 to "Teacher B"), time = CourseTimeSlot(1, 1, 2))
        val overrides = listOf(TemporaryScheduleOverride("move", TemporaryScheduleOverrideType.MoveCourse,
            targetDate = target.toString(), sourceDate = start.toString(), moveCourseId = original.id,
            moveToStartNode = 3, moveToEndNode = 4))
        val moved = original.copy(time = CourseTimeSlot(1, 3, 4))
        val week = detailCourseWeekNumber(target, moved, start, overrides, HolidayCalendarSettings.NONE)
        assertEquals(1, week)
        assertEquals("Teacher A", moved.teacherForWeek(week))
        val other = original.copy(id = "other", title = original.title)
        assertEquals(2, detailCourseWeekNumber(target, other, start, overrides, HolidayCalendarSettings.NONE))
        assertEquals(null, detailCourseWeekNumber(target, moved, null, overrides, HolidayCalendarSettings.NONE))
    }
}
