package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.isActiveInTermWeekNumber
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberStarted
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.targetDates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private const val MAX_TERM_WEEK = 60

/** 第 [termWeek] 教学周里星期 [dayOfWeek] 对应的日期，第 1 周从开学日所在周的周一算起。 */
internal fun termWeekDate(termStart: LocalDate, termWeek: Int, dayOfWeek: Int): LocalDate =
    termStart
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusWeeks((termWeek - 1).toLong())
        .plusDays((dayOfWeek - 1).toLong())

internal fun resolveTermWeek(termStart: LocalDate, date: LocalDate): Int =
    resolveTermWeekNumber(termStart, date)

internal fun isTermWeekStarted(termWeek: Int): Boolean = isTermWeekNumberStarted(termWeek)

internal fun CourseItem.isActiveInTermWeek(termWeek: Int): Boolean =
    isActiveInTermWeekNumber(termWeek)

internal fun CourseItem.isActiveOnSourceDate(termStart: LocalDate, sourceDate: LocalDate): Boolean =
    isActiveInTermWeek(resolveTermWeek(termStart, sourceDate))

internal fun CourseItem.termWeekNumbers(): List<Int> = weeks.ifEmpty { (1..MAX_TERM_WEEK).toList() }

internal fun courseOccurrenceDates(
    course: CourseItem,
    termStart: LocalDate,
    fromDate: LocalDate,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    dayPolicy: ReminderDayPolicy = ReminderDayPolicy.ALWAYS,
): List<LocalDate> {
    val regularDates = course.termWeekNumbers().map { week ->
        termWeekDate(termStart, week, course.time.dayOfWeek)
    }
    val overrideTargetDates = temporaryScheduleOverrides.flatMap { it.targetDates() }
    return (regularDates + overrideTargetDates)
        .distinct()
        .filterNot { it.isBefore(fromDate) }
        .filter { date ->
            val day = resolveScheduleDay(date, temporaryScheduleOverrides, holidayCalendar)
            !dayPolicy.suppresses(date, day) &&
                day.sourceDate.dayOfWeek.value == course.time.dayOfWeek &&
                course.isActiveOnSourceDate(termStart, day.sourceDate) &&
                !isCourseTemporarilyCancelled(date, course, temporaryScheduleOverrides)
        }
}
