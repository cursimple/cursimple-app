package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.isActiveInTermWeekNumber
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberStarted
import com.x500x.cursimple.core.kernel.model.coursesMovedToWithOrigin
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.targetDates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private const val MAX_TERM_WEEK = 60

/** Resolve a weekday within the teaching week anchored to the term's first Monday. */
/** Use the same fixed Monday anchor as resolveTermWeekNumber. */
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
    dayPolicy: ReminderDayPolicy = ReminderDayPolicy(),
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
            if (dayPolicy.suppresses(date, day)) return@filter false
            if (isCourseTemporarilyCancelled(date, course, temporaryScheduleOverrides)) return@filter false
            if (isCourseMovedAwayFrom(date, course, temporaryScheduleOverrides)) return@filter false
            // Moved courses retain original-day coverage; explicit workdays override holidays, manual muting does not.
            val movedHere = coursesMovedToWithOrigin(
                date = date,
                overrides = temporaryScheduleOverrides,
                courseById = { id -> course.takeIf { it.id == id } },
            ).firstOrNull()
            if (movedHere != null) {
                return@filter course.isActiveOnSourceDate(termStart, movedHere.second)
            }
            // Partial swaps resolve source dates by each course's periods.
            val courseSource = temporaryScheduleCourseSourceDate(
                date = date,
                course = course,
                sourceDate = day.sourceDate,
                overrides = temporaryScheduleOverrides,
            ) ?: return@filter false
            course.isActiveOnSourceDate(termStart, courseSource)
        }
}
