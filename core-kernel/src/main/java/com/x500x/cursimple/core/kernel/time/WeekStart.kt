package com.x500x.cursimple.core.kernel.time

import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Display week start affects ordering only; teaching-week numbering remains Monday-based. */
enum class WeekStartDay(val dayOfWeek: DayOfWeek) {
    Monday(DayOfWeek.MONDAY),
    Sunday(DayOfWeek.SUNDAY),
}

fun displayWeekStartOf(date: LocalDate, weekStart: WeekStartDay): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(weekStart.dayOfWeek))

/** Use the Monday inside the display window to identify its teaching week. */
fun displayWeekAnchorMonday(displayWeekStart: LocalDate): LocalDate =
    displayWeekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

fun displayWeekTermIndex(termStart: LocalDate, displayWeekStart: LocalDate): Int =
    resolveTermWeekNumber(termStart, displayWeekAnchorMonday(displayWeekStart))

/**
 * Return weekday values in display order, not indices; hiding weekends always starts at Monday.
 */
fun columnDayOfWeeks(
    weekStart: WeekStartDay,
    weekendVisible: Boolean,
    saturdayVisible: Boolean,
): List<Int> = when {
    !weekendVisible && !saturdayVisible -> (1..5).toList()
    !weekendVisible -> (1..6).toList()
    weekStart == WeekStartDay.Sunday -> listOf(7) + (1..6).toList()
    else -> (1..7).toList()
}

fun columnDate(displayWeekStart: LocalDate, dayOfWeek: Int): LocalDate {
    val offset = (dayOfWeek - displayWeekStart.dayOfWeek.value + 7) % 7
    return displayWeekStart.plusDays(offset.toLong())
}

enum class ScheduleRowFitMode {
    Fit,
    Scroll,
}
