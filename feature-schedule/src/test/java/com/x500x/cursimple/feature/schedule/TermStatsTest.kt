package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.data.attendance.AttendanceData
import com.x500x.cursimple.core.data.attendance.AttendanceStatus
import com.x500x.cursimple.core.data.attendance.withMark
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TermStatsTest {
    // Term starts on a Monday, so week 1 is that week.
    private val termStart = LocalDate.of(2026, 9, 14)
    private fun course(id: String, title: String, day: Int, weeks: List<Int>) =
        CourseItem(id, title, weeks = weeks, time = CourseTimeSlot(day, 1, 2))

    private fun stats(courses: List<CourseItem>, today: LocalDate, lastWeek: Int = 3, attendance: AttendanceData = AttendanceData()) =
        computeTermStats(courses, termStart, today, lastWeek, emptyList(), HolidayCalendarSettings.NONE, attendance)!!

    @Test
    fun aWeeklyCourseCountsOncePerTeachingWeek() {
        val s = stats(listOf(course("m", "高等数学", 1, listOf(1, 2, 3))), today = LocalDate.of(2026, 12, 31))
        assertEquals(3, s.totalSessions)
        assertEquals(listOf(1, 1, 1), s.perWeek)
        assertEquals(3, s.doneSessions)
    }

    @Test
    fun onlySessionsUpToTodayCountAsDone() {
        // Today is the Tuesday of week 2: week 1's Monday and Tuesday and week 2's Monday are done.
        val s = stats(listOf(course("m", "高等数学", 1, listOf(1, 2, 3))), today = LocalDate.of(2026, 9, 22))
        assertEquals(3, s.totalSessions)
        assertEquals(2, s.doneSessions)
    }

    @Test
    fun coursesWithTheSameNameAreGroupedAndSortedByTotal() {
        val s = stats(
            listOf(
                course("a", "大学英语", 2, listOf(1, 2, 3)),
                course("b", "高等数学", 1, listOf(1)),
                course("c", "高等数学", 3, listOf(1)),
            ),
            today = LocalDate.of(2026, 12, 31),
        )
        assertEquals(listOf("大学英语", "高等数学"), s.courses.map { it.title })
        assertEquals(3, s.courses.first().total)
        assertEquals(2, s.courses.last().total)
    }

    @Test
    fun attendanceCountsAndRateIgnoreUnmarkedSessions() {
        val marks = AttendanceData()
            .withMark("高等数学", "2026-09-14", AttendanceStatus.Present, 1)
            .withMark("高等数学", "2026-09-21", AttendanceStatus.Absent, 1)
            .withMark("高等数学", "2026-09-28", AttendanceStatus.Leave, 1)
        val s = stats(listOf(course("m", "高等数学", 1, listOf(1, 2, 3))), today = LocalDate.of(2026, 12, 31), attendance = marks)
        assertEquals(1, s.attended)
        assertEquals(1, s.missed)
        assertEquals(1, s.leave)
        // Leave is excluded from the rate: one attended out of two counted sessions.
        assertEquals(1.0 / 3, s.attendanceRate!!, 0.0001)
    }

    @Test
    fun nothingMarkedMeansNoAttendanceRate() {
        val s = stats(listOf(course("m", "高等数学", 1, listOf(1))), today = LocalDate.of(2026, 12, 31))
        assertNull(s.attendanceRate)
    }

    @Test
    fun noTermStartMeansNoStatistics() {
        assertNull(computeTermStats(emptyList(), null, LocalDate.of(2026, 9, 14), 3, emptyList(), HolidayCalendarSettings.NONE, AttendanceData()))
    }

    @Test
    fun theLastWeekIsTheLatestCourseWeekPlusExtraBlankWeeks() {
        val courses = listOf(course("m", "高等数学", 1, listOf(1, 16)))
        assertEquals(18, termLastWeek(courses, extraWeeks = 2, minimum = 1))
        assertEquals(16, termLastWeek(courses, extraWeeks = 0, minimum = 1))
    }
}
