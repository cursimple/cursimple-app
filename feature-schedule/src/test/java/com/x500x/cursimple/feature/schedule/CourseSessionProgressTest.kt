package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CourseSessionProgressTest {
    private val start = LocalDate.of(2026, 9, 7)
    private val course = CourseItem("a", "算法", weeks = (1..18).toList(), time = CourseTimeSlot(1, 1, 2))
    private fun count(selected: CourseItem = course, date: LocalDate = start, courses: List<CourseItem> = listOf(course),
        rules: List<TemporaryScheduleOverride> = emptyList(), holidays: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        includeHolidays: Boolean = true, maxWeek: Int = 20) =
        courseSessionProgress(selected, date, courses, start, maxWeek, rules, holidays, includeHolidays)!!
    private fun move(from: LocalDate, to: LocalDate, nodes: IntRange = 3..4) = TemporaryScheduleOverride("move", TemporaryScheduleOverrideType.MoveCourse,
        sourceDate = from.toString(), targetDate = to.toString(), moveCourseId = course.id, moveToStartNode = nodes.first, moveToEndNode = nodes.last)

    @Test fun oneMeetingSpanningTwoPeriodsIsOneOfEighteenSessions() {
        assertEquals(CourseSessionProgress(1, 18), count())
        assertEquals(CourseSessionProgress(3, 18), count(date = start.plusWeeks(2)))
    }

    @Test fun sameNamedWeekdayAndPeriodArrangementsAreMergedButExamsAreSeparate() {
        val friday = course.copy(id = "b", time = CourseTimeSlot(5, 5, 6))
        val exam = course.copy(id = "exam", category = CourseCategory.Exam)
        assertEquals(CourseSessionProgress(2, 36), count(friday, start.plusDays(4), listOf(course, friday, exam)))
    }

    @Test fun movingACourseRemovesTheOriginalPositionAndCountsItsNewMeetingOnce() {
        val rule = move(start, start.plusDays(3))
        assertEquals(CourseSessionProgress(null, 18), count(rules = listOf(rule)))
        val moved = course.copy(time = CourseTimeSlot(4, 3, 4))
        assertEquals(CourseSessionProgress(1, 18), count(moved, start.plusDays(3), rules = listOf(rule)))
    }

    @Test fun movingBeyondTheSemesterRemovesItFromThisSemester() {
        assertEquals(CourseSessionProgress(null, 17), count(rules = listOf(move(start, start.plusWeeks(21)))))
    }

    @Test fun cancellationsReduceTheTotalAndRenumberLaterMeetings() {
        val rule = TemporaryScheduleOverride("cancel", TemporaryScheduleOverrideType.CancelCourse,
            targetDate = start.toString(), cancelCourseId = course.id, cancelStartNode = 1, cancelEndNode = 2)
        assertEquals(CourseSessionProgress(null, 17), count(rules = listOf(rule)))
        assertEquals(CourseSessionProgress(1, 17), count(date = start.plusWeeks(1), rules = listOf(rule)))
    }

    @Test fun holidayCountingDefaultsOnAndCanExcludeEvenMovedInMeetings() {
        val holidays = HolidayCalendarSettings(false, listOf(HolidayCalendarEntry(start.toString())))
        assertEquals(CourseSessionProgress(1, 18), count(holidays = holidays))
        assertEquals(CourseSessionProgress(null, 17), count(holidays = holidays, includeHolidays = false))
        val target = start.plusDays(3)
        val moved = course.copy(time = CourseTimeSlot(4, 3, 4))
        val holidayTarget = holidays.copy(entries = listOf(HolidayCalendarEntry(target.toString())))
        assertEquals(CourseSessionProgress(null, 17), count(moved, target, rules = listOf(move(start, target)),
            holidays = holidayTarget, includeHolidays = false))
    }

    @Test fun wholeDayAndPartialSwapsUseTheTimetablesResolvedSourceWeek() {
        val wednesday = start.plusDays(2)
        val swap = listOf(TemporaryScheduleOverride("forward", targetDate = wednesday.toString(), sourceDate = start.toString()),
            TemporaryScheduleOverride("back", targetDate = start.toString(), sourceDate = wednesday.toString()))
        assertEquals(CourseSessionProgress(null, 18), count(rules = swap))
        assertEquals(CourseSessionProgress(1, 18), count(date = wednesday, rules = swap))
        val partial = swap.map { it.copy(makeUpStartNode = 1, makeUpEndNode = 2) }
        assertEquals(CourseSessionProgress(1, 18), count(date = wednesday, rules = partial))
    }

    @Test fun hiddenCoursesPlaceholdersAndEmptyWeeksDoNotInflateTotals() {
        val everyWeek = course.copy(weeks = emptyList())
        assertEquals(CourseSessionProgress(1, 20), count(everyWeek, courses = listOf(everyWeek,
            course.copy(id = "hidden", hidden = true), course.copy(id = "placeholder", reminderOnly = true))))
        assertEquals(CourseSessionProgress(1, 18), count(courses = listOf(course, course)))
    }

    @Test fun sameDayDifferentSessionsGetDistinctSequentialPositions() {
        val later = course.copy(id = "later", time = CourseTimeSlot(1, 5, 6))
        assertEquals(CourseSessionProgress(2, 36), count(later, courses = listOf(course, later)))
    }

    @Test fun missingTermStartDoesNotInventACurrentSessionNumber() {
        assertNull(courseSessionProgress(course, start, listOf(course), null, 20, emptyList(), HolidayCalendarSettings.NONE))
    }

    @Test fun explicitlyAddedTermWeeksExtendCoursesWithEveryWeekCoverage() {
        val everyWeek = course.copy(weeks = emptyList())
        assertEquals(CourseSessionProgress(24, 24), count(everyWeek, start.plusWeeks(23), listOf(everyWeek), maxWeek = 24))
    }
}
