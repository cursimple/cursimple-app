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

/** 第 [termWeek] 教学周里星期 [dayOfWeek] 对应的日期，第 1 周从开学日所在周的周一算起。 */
/** 教学周编号的逆运算，锚点必须与 resolveTermWeekNumber 一致，同样固定为周一。 */
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
            if (isCourseTemporarilyCancelled(date, course, temporaryScheduleOverrides)) return@filter false
            // 被单独挪走的课，这天不再提醒
            if (isCourseMovedAwayFrom(date, course, temporaryScheduleOverrides)) return@filter false
            // 被挪到这天的课要提醒；该不该上按它原本那天判。
            // 这一步排在放假判断之前：调课可以推翻放假，挪到休息日的课照样提醒。
            val movedHere = coursesMovedToWithOrigin(
                date = date,
                overrides = temporaryScheduleOverrides,
                courseById = { id -> course.takeIf { it.id == id } },
            ).firstOrNull()
            if (movedHere != null) {
                return@filter course.isActiveOnSourceDate(termStart, movedHere.second)
            }
            if (dayPolicy.suppresses(date, day)) return@filter false
            // 只调某几节时，这门课当天到底算哪一天的安排由节次决定，不是整天一刀切
            val courseSource = temporaryScheduleCourseSourceDate(
                date = date,
                course = course,
                sourceDate = day.sourceDate,
                overrides = temporaryScheduleOverrides,
            ) ?: return@filter false
            course.isActiveOnSourceDate(termStart, courseSource)
        }
}
