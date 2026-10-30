package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.data.attendance.AttendanceData
import com.x500x.cursimple.core.data.attendance.AttendanceStatus
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One course's share of the term: how many sessions it has in total and how many already happened. */
data class CourseTermStat(val title: String, val total: Int, val done: Int)

/**
 * The term's numbers for the statistics page. Sessions come from the same scheduling rules as the timetable
 * (moves, cancellations, holidays), so the totals match what the timetable shows.
 */
data class TermStats(
    val totalSessions: Int,
    val doneSessions: Int,
    /** Sessions per teaching week, index 0 is week 1. */
    val perWeek: List<Int>,
    val courses: List<CourseTermStat>,
    val attended: Int,
    val missed: Int,
    val leave: Int,
) {
    /** Share of marked sessions that were attended; null before any session has been marked. */
    val attendanceRate: Double? get() {
        val marked = attended + missed + leave
        return if (marked == 0) null else attended.toDouble() / marked
    }
}

/** Counts every session of the term up to [lastWeek]; [today] decides which sessions count as done. */
fun computeTermStats(
    courses: List<CourseItem>,
    termStart: LocalDate?,
    today: LocalDate,
    lastWeek: Int,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    attendance: AttendanceData,
): TermStats? {
    if (termStart == null || lastWeek <= 0) return null
    val active = courses.filter { !it.hidden && !it.reminderOnly }
    val firstDay = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val perWeek = IntArray(lastWeek)
    val totals = LinkedHashMap<String, Int>()
    val done = LinkedHashMap<String, Int>()
    var totalSessions = 0
    var doneSessions = 0
    for (offset in 0 until lastWeek * 7) {
        val date = firstDay.plusDays(offset.toLong())
        // Holidays are not class days, so they never count as a session.
        if (resolveScheduleDay(date, overrides, holidayCalendar).isHoliday) continue
        val sessions = coursesScheduledOn(date, active, overrides, holidayCalendar, termStart)
            .distinctBy { Triple(it.id, it.time.startNode, it.time.endNode) }
        if (sessions.isEmpty()) continue
        val week = (offset / 7)
        for (course in sessions) {
            val title = course.title.trim()
            totalSessions++
            perWeek[week]++
            totals[title] = (totals[title] ?: 0) + 1
            if (!date.isAfter(today)) {
                doneSessions++
                done[title] = (done[title] ?: 0) + 1
            }
        }
    }
    val courseStats = totals.map { (title, total) -> CourseTermStat(title, total, done[title] ?: 0) }
        .sortedWith(compareByDescending<CourseTermStat> { it.total }.thenBy { it.title })
    val marks = attendance.records.groupingBy { it.status }.eachCount()
    return TermStats(
        totalSessions = totalSessions,
        doneSessions = doneSessions,
        perWeek = perWeek.toList(),
        courses = courseStats,
        attended = marks[AttendanceStatus.Present] ?: 0,
        missed = marks[AttendanceStatus.Absent] ?: 0,
        leave = marks[AttendanceStatus.Leave] ?: 0,
    )
}

/** Teaching week count from the courses' own weeks, with the term's extra blank weeks. */
fun termLastWeek(courses: List<CourseItem>, extraWeeks: Int, minimum: Int): Int {
    val fromCourses = courses.filter { !it.hidden && !it.reminderOnly }.flatMap { it.weeks }.maxOrNull() ?: 0
    return maxOf(fromCourses, minimum) + extraWeeks.coerceAtLeast(0)
}

/** Notebook keys of the courses in [stats], so the page can link a row to its course. */
internal fun statKey(title: String): String = memoCourseKey(title)
