package com.kebiao.viewer.feature.widget

import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.endLocalTime
import com.kebiao.viewer.core.kernel.model.startLocalTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

internal fun resolveWeekIndex(
    targetDate: LocalDate,
    termStartDate: LocalDate?,
): Int? {
    val termStart = termStartDate ?: return null
    val termStartMonday = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val targetMonday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = ChronoUnit.WEEKS.between(termStartMonday, targetMonday).toInt() + 1
    return max(1, weeks)
}

internal fun CourseItem.activeOnWeek(weekIndex: Int?): Boolean {
    if (weekIndex == null) return false
    if (weeks.isEmpty()) return true
    return weeks.contains(weekIndex)
}

/** Slot whose [startNode]/[endNode] together cover [startNode]; used to derive course start time. */
internal fun TermTimingProfile.startSlotFor(startNode: Int): ClassSlotTime? =
    slotTimes.firstOrNull { it.startNode <= startNode && startNode <= it.endNode }

/** Slot covering [endNode]; used to derive course end time. */
internal fun TermTimingProfile.endSlotFor(endNode: Int): ClassSlotTime? =
    slotTimes.firstOrNull { it.startNode <= endNode && endNode <= it.endNode }

/** Real-clock start of a course using the configured node range. */
internal fun TermTimingProfile.courseStartTime(course: CourseItem): LocalTime? =
    runCatching { startSlotFor(course.time.startNode)?.startLocalTime() }.getOrNull()

/** Real-clock end of a course using the configured node range. */
internal fun TermTimingProfile.courseEndTime(course: CourseItem): LocalTime? =
    runCatching { endSlotFor(course.time.endNode)?.endLocalTime() }.getOrNull()

/** "08:00 – 09:35" formatted clock range for a course, or null if timing data is missing. */
internal fun TermTimingProfile.courseClockRange(course: CourseItem, separator: String = "–"): String? {
    val start = startSlotFor(course.time.startNode)?.startTime ?: return null
    val end = endSlotFor(course.time.endNode)?.endTime ?: return null
    return "$start$separator$end"
}

internal fun shouldShowNextDayAtNight(
    now: LocalTime,
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
): Boolean {
    if (now.isBefore(NIGHT_ADVANCE_TIME)) return false
    if (courses.isEmpty()) return true
    val endTimes = courses.map { course -> timingProfile?.courseEndTime(course) }
    return endTimes.all { endTime -> endTime != null && !now.isBefore(endTime) }
}

private val NIGHT_ADVANCE_TIME: LocalTime = LocalTime.of(22, 0)
