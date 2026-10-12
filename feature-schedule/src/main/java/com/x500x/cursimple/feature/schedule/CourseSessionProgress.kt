package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class CourseSessionProgress(val current: Int?, val total: Int)

private data class CountedCourseSession(val date: LocalDate, val course: CourseItem)

/** Count meetings, not periods, through the same rescheduling rules as the timetable. */
internal fun courseSessionProgress(
    selected: CourseItem,
    selectedDate: LocalDate,
    courses: List<CourseItem>,
    termStartDate: LocalDate?,
    maxWeekCount: Int,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    includeHolidays: Boolean = true,
): CourseSessionProgress? {
    if (termStartDate == null) return null
    val title = selected.title.trim()
    val matching = courses.filter { !it.hidden && !it.reminderOnly && it.title.trim() == title && it.category == selected.category }
        .distinctBy { it.id }
    val lastWeek = maxOf(maxWeekCount, matching.flatMap { it.weeks }.maxOrNull() ?: 1).coerceAtLeast(1)
    val sourceCourses = matching.map { if (it.weeks.isEmpty()) it.copy(weeks = (1..lastWeek).toList()) else it }
    val firstDay = termStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val calendar = if (includeHolidays) HolidayCalendarSettings.NONE else holidayCalendar
    val sessions = (0 until lastWeek * 7).flatMap { offset ->
        val date = firstDay.plusDays(offset.toLong())
        if (!includeHolidays && resolveScheduleDay(date, overrides, holidayCalendar).isHoliday) emptyList()
        else coursesScheduledOn(date, sourceCourses, overrides, calendar, termStartDate)
            .distinctBy { Triple(it.id, it.time.startNode, it.time.endNode) }
            .map { CountedCourseSession(date, it) }
    }.sortedWith(compareBy<CountedCourseSession> { it.date }
        .thenBy { it.course.time.startNode }.thenBy { it.course.time.endNode }.thenBy { it.course.id })
    val index = sessions.indexOfFirst { it.date == selectedDate && it.course.id == selected.id &&
        it.course.time.startNode == selected.time.startNode && it.course.time.endNode == selected.time.endNode }
    return CourseSessionProgress((index + 1).takeIf { index >= 0 }, sessions.size)
}
