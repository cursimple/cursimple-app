package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WidgetCourseDetailsTest {
    private val start = LocalDate.of(2026, 9, 7)
    private val course = CourseItem("math", "高等数学", teacher = "默认老师", location = "默认教室", weeks = listOf(1, 2),
        weekTeachers = mapOf(1 to "老师一", 2 to "老师二"), weekLocations = mapOf(1 to "教室一", 2 to "教室二"),
        time = CourseTimeSlot(1, 1, 2))

    @Test fun clickingEitherPeriodOfAContinuousClassReturnsItsWeeklyDetails() {
        for (node in 1..2) {
            val detail = widgetCourseDetailsForSlot(start.plusWeeks(1), node, node, listOf(course), start, emptyList(), HolidayCalendarSettings.NONE)
            assertEquals("老师二", detail.courses.single().teacher)
            assertEquals("教室二", detail.courses.single().location)
            assertEquals(2, detail.sourceWeeks[detail.courses.single()])
        }
    }

    @Test fun aMovedClassUsesTheOriginalWeeksTeacherAndLocationAtItsNewPosition() {
        val move = TemporaryScheduleOverride("move", TemporaryScheduleOverrideType.MoveCourse, sourceDate = start.toString(),
            targetDate = start.plusDays(8).toString(), moveCourseId = course.id, moveToStartNode = 5, moveToEndNode = 6)
        val detail = widgetCourseDetailsForSlot(start.plusDays(8), 6, 6, listOf(course), start, listOf(move), HolidayCalendarSettings.NONE)
        assertEquals(CourseTimeSlot(2, 5, 6), detail.courses.single().time)
        assertEquals("老师一", detail.courses.single().teacher)
        assertEquals("教室一", detail.courses.single().location)
        assertEquals(1, detail.sourceWeeks[detail.courses.single()])
        assertTrue(widgetCourseDetailsForSlot(start, 1, 2, listOf(course), start, listOf(move), HolidayCalendarSettings.NONE).courses.isEmpty())
    }

    @Test fun differentCellsAndCancelledMeetingsNeverReturnAnUnrelatedClass() {
        val cancel = TemporaryScheduleOverride("cancel", TemporaryScheduleOverrideType.CancelCourse, targetDate = start.toString(),
            cancelCourseId = course.id, cancelStartNode = 1, cancelEndNode = 2)
        assertTrue(widgetCourseDetailsForSlot(start, 1, 2, listOf(course), start, listOf(cancel), HolidayCalendarSettings.NONE).courses.isEmpty())
        assertTrue(widgetCourseDetailsForSlot(start, 3, 4, listOf(course), start, emptyList(), HolidayCalendarSettings.NONE).courses.isEmpty())
        assertTrue(widgetCourseDetailsForSlot(start.plusDays(1), 1, 2, listOf(course), start, emptyList(), HolidayCalendarSettings.NONE).courses.isEmpty())
    }

    @Test fun overlappingClassesRemainIndividuallyAvailableToTheReadOnlyPager() {
        val second = course.copy(id = "physics", title = "物理")
        val detail = widgetCourseDetailsForSlot(start, 1, 1, listOf(course, second), start, emptyList(), HolidayCalendarSettings.NONE)
        assertEquals(setOf("math", "physics"), detail.courses.map { it.id }.toSet())
        assertEquals(setOf(1), detail.sourceWeeks.values.toSet())
        assertEquals("默认老师", course.teacher)
    }
}
