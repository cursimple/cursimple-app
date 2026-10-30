package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A run of periods on one day that neither person has a class in. */
data class FreeRun(val date: LocalDate, val startNode: Int, val endNode: Int)

/**
 * Periods where both timetables are empty, day by day for the week starting on [weekMonday]. Each side is read with
 * its own term start, so two people on different calendars still line up by real date. Periods are numbered
 * 1..[periodCount]; a period with no class on either side is free.
 */
fun sharedFreeRuns(
    mine: List<CourseItem>,
    myTermStart: LocalDate?,
    theirs: List<CourseItem>,
    theirTermStart: LocalDate?,
    weekMonday: LocalDate,
    periodCount: Int,
): List<FreeRun> {
    val monday = weekMonday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val runs = mutableListOf<FreeRun>()
    for (offset in 0L until 7L) {
        val date = monday.plusDays(offset)
        val busy = BooleanArray(periodCount + 2)
        markBusy(busy, date, mine, myTermStart)
        markBusy(busy, date, theirs, theirTermStart ?: myTermStart)
        var start: Int? = null
        for (node in 1..periodCount) {
            if (!busy[node]) {
                if (start == null) start = node
            } else if (start != null) {
                runs += FreeRun(date, start, node - 1)
                start = null
            }
        }
        start?.let { runs += FreeRun(date, it, periodCount) }
    }
    return runs
}

private fun markBusy(busy: BooleanArray, date: LocalDate, courses: List<CourseItem>, termStart: LocalDate?) {
    coursesScheduledOn(date, courses.filter { !it.hidden && !it.reminderOnly }, emptyList(), HolidayCalendarSettings.NONE, termStart)
        .forEach { course ->
            for (node in course.time.startNode..course.time.endNode) {
                if (node in busy.indices) busy[node] = true
            }
        }
}

/** Number of periods to compare: the timing profile's highest period, or [DEFAULT_PERIOD_COUNT] when none is set. */
fun periodCountOf(maxNodeFromProfile: Int?): Int = (maxNodeFromProfile ?: 0).takeIf { it > 0 } ?: DEFAULT_PERIOD_COUNT

const val DEFAULT_PERIOD_COUNT = 12
