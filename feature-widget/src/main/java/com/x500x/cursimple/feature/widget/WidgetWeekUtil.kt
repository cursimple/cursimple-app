package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.endLocalTime
import com.x500x.cursimple.core.kernel.model.isActiveInTermWeekNumber
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberStarted
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.startLocalTime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

internal fun resolveWeekIndex(
    targetDate: LocalDate,
    termStartDate: LocalDate?,
): Int? {
    val termStart = termStartDate ?: return null
    return resolveTermWeekNumber(termStart, targetDate)
}

internal fun isBeforeTermStart(weekIndex: Int?): Boolean =
    weekIndex != null && !isTermWeekNumberStarted(weekIndex)

internal fun CourseItem.activeOnWeek(weekIndex: Int?): Boolean =
    weekIndex != null && isActiveInTermWeekNumber(weekIndex)

internal enum class CourseStatus {
    Past,

    Live,

    Soon,

    Upcoming,
}

internal const val SOON_THRESHOLD_MINUTES: Long = 30

internal data class NextCourseEntry(
    val course: CourseItem,
    val status: CourseStatus,
)

internal fun visibleNextCourseEntries(
    courses: List<CourseItem>,
    today: LocalDate,
    targetDate: LocalDate,
    now: LocalTime,
    timingProfile: TermTimingProfile?,
): List<NextCourseEntry> =
    courses
        .map { course ->
            NextCourseEntry(
                course = course,
                status = resolveCourseStatus(
                    course = course,
                    today = today,
                    targetDate = targetDate,
                    now = now,
                    timingProfile = timingProfile,
                ),
            )
        }
        .filter { it.status != CourseStatus.Past }

internal fun resolveCourseStatus(
    course: CourseItem,
    today: LocalDate,
    targetDate: LocalDate,
    now: LocalTime,
    timingProfile: TermTimingProfile?,
): CourseStatus {
    if (targetDate.isBefore(today)) return CourseStatus.Past
    if (targetDate.isAfter(today)) return CourseStatus.Upcoming

    val startTime = timingProfile?.courseStartTime(course)
    val endTime = timingProfile?.courseEndTime(course)
    return when {
        startTime == null || endTime == null -> CourseStatus.Upcoming
        !now.isBefore(endTime) -> CourseStatus.Past
        !now.isBefore(startTime) -> CourseStatus.Live
        Duration.between(now, startTime).toMinutes() <= SOON_THRESHOLD_MINUTES -> CourseStatus.Soon
        else -> CourseStatus.Upcoming
    }
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

/**
 * Switch to tomorrow after [advanceTime] only once today's courses end; null disables early
 * switching.
 */
internal fun shouldShowNextDayAtNight(
    now: LocalTime,
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    advanceTime: LocalTime? = NIGHT_ADVANCE_TIME,
): Boolean {
    if (advanceTime == null || now.isBefore(advanceTime)) return false
    if (courses.isEmpty()) return true
    return courses.all { course ->
        val endTime = timingProfile?.courseEndTime(course)
        endTime != null && !now.isBefore(endTime)
    }
}

private val NIGHT_ADVANCE_TIME: LocalTime? = null
