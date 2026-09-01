package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.endLocalTime
import com.x500x.cursimple.core.kernel.model.isActiveInTermWeekNumber
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberStarted
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.startLocalTime
import java.time.LocalDate
import java.time.LocalTime

/** 开学前返回 0 或负数；未设开学日期时返回 null。 */
internal fun resolveWeekIndex(
    targetDate: LocalDate,
    termStartDate: LocalDate?,
): Int? {
    val termStart = termStartDate ?: return null
    return resolveTermWeekNumber(termStart, targetDate)
}

/** 已知开学日期且周次小于 1，即尚未开学。 */
internal fun isBeforeTermStart(weekIndex: Int?): Boolean =
    weekIndex != null && !isTermWeekNumberStarted(weekIndex)

/** 未设开学日期（周次为 null）时不显示任何课程。 */
internal fun CourseItem.activeOnWeek(weekIndex: Int?): Boolean =
    weekIndex != null && isActiveInTermWeekNumber(weekIndex)

internal enum class CourseStatus { Past, Live, Upcoming }

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

private fun resolveCourseStatus(
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
 * 到达 [advanceTime] 且当天课程全部结束时返回 true，表示改为展示次日安排。
 * [advanceTime] 为 null 表示关闭提前切换，日期只在零点自然翻页。
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

/** 夜间提前切到次日的时刻；null 表示不提前切换。 */
private val NIGHT_ADVANCE_TIME: LocalTime? = null
