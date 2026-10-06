package com.x500x.cursimple.core.kernel.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Teaching weeks use the persisted Monday anchor, independent of display order; pre-term weeks
 * are nonpositive.
 */
fun resolveTermWeekNumber(termStart: LocalDate, date: LocalDate): Int {
    val termStartMonday = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return ChronoUnit.WEEKS.between(termStartMonday, dateMonday).toInt() + 1
}

fun isTermWeekNumberStarted(weekNumber: Int): Boolean = weekNumber >= 1

fun isTermWeekNumberActive(weekNumber: Int, weeks: List<Int>): Boolean {
    if (!isTermWeekNumberStarted(weekNumber)) return false
    return weeks.isEmpty() || weekNumber in weeks
}

fun CourseItem.isActiveInTermWeekNumber(weekNumber: Int): Boolean =
    isTermWeekNumberActive(weekNumber, weeks)

fun isCurrentTermWeek(termStart: LocalDate?, displayedWeekIndex: Int, currentWeekIndex: Int): Boolean =
    termStart != null &&
        isTermWeekNumberStarted(currentWeekIndex) &&
        displayedWeekIndex == currentWeekIndex
