package com.x500x.cursimple.app.util

import androidx.annotation.StringRes
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.model.filterTemporaryCancelledCourses
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberActive
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.targetDates
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** 一门课在某一天的一次上课。[displaced] 表示这一次来自调课，不在原本的每周节奏上。 */
data class CourseOccurrence(
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val displaced: Boolean,
)

/** 一门课及其整学期的全部上课时间。 */
data class PlannedCourse(
    val course: CourseItem,
    val slotLabel: String?,
    val occurrences: List<CourseOccurrence>,
)

/**
 * 整学期展开的结果。
 * [failureReason] 非空（文案资源 id）表示缺少必要配置，[courses] 必然为空。
 */
data class SchedulePlan(
    val courses: List<PlannedCourse>,
    val skipped: List<IcsSkippedCourse>,
    @StringRes val failureReason: Int?,
)

/**
 * 把课表按日历逐天展开成具体的上课时间。
 * 假期整天跳过，调课按调课后的日期落位并标记为 [CourseOccurrence.displaced]。
 */
fun planScheduleOccurrences(
    termStartDate: LocalDate?,
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    defaultWeekCount: Int = 20,
): SchedulePlan {
    val importedByDay: Map<Int, List<CourseItem>> = (1..7).associateWith { day ->
        schedule?.coursesOfDay(day).orEmpty().visibleScheduleCourses()
    }
    val visibleManual = manualCourses.visibleScheduleCourses()
    val planningCourses = importedByDay.values.flatten() + visibleManual

    if (termStartDate == null) {
        return SchedulePlan(emptyList(), emptyList(), R.string.ics_failure_no_term_start)
    }
    if (timingProfile == null) {
        return SchedulePlan(
            courses = emptyList(),
            skipped = planningCourses.map { it.toSkipped(R.string.ics_skip_no_timing) },
            failureReason = R.string.ics_failure_no_timing,
        )
    }

    val termStartMonday = termStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekCount = (planningCourses.mapNotNull { it.weeks.maxOrNull() }.maxOrNull() ?: defaultWeekCount)
        .coerceAtLeast(1)
    val termEnd = termStartMonday.plusDays(weekCount.toLong() * 7 - 1)

    val overrideTargets = overrides.flatMap { it.targetDates() }
    val iterationStart = (overrideTargets + termStartMonday).minOrNull() ?: termStartMonday
    val iterationEnd = (overrideTargets + termEnd).maxOrNull() ?: termEnd

    val groups = LinkedHashMap<String, MutableList<CourseOccurrence>>()
    val groupCourses = LinkedHashMap<String, Pair<CourseItem, String?>>()
    val skippedKeys = LinkedHashMap<String, IcsSkippedCourse>()

    var date = iterationStart
    while (!date.isAfter(iterationEnd)) {
        val dayResolution = resolveScheduleDay(date, overrides, holidayCalendar)
        if (dayResolution.isHoliday) {
            date = date.plusDays(1)
            continue
        }
        val sourceDate = dayResolution.sourceDate
        val weekIndex = resolveTermWeekNumber(termStartDate, sourceDate)
        val dayOfWeek = sourceDate.dayOfWeek.value
        val candidates = filterTemporaryCancelledCourses(
            date = date,
            courses = importedByDay[dayOfWeek].orEmpty() + visibleManual.filter { it.time.dayOfWeek == dayOfWeek },
            overrides = overrides,
        ).filter { isTermWeekNumberActive(weekIndex, it.weeks) }

        for (course in candidates) {
            val key = courseKey(course)
            val startSlot = timingProfile.coveringSlot(course.time.startNode)
            val endSlot = timingProfile.coveringSlot(course.time.endNode)
            val startTime = startSlot?.parseStart()
            val endTime = endSlot?.parseEnd()
            if (startTime == null || endTime == null) {
                skippedKeys.getOrPut(key) { course.toSkipped(R.string.ics_skip_missing_period) }
                continue
            }
            val startDateTime = date.atTime(startTime)
            // 结束时间不晚于开始时间说明这一节跨了午夜
            val endDateTime = if (endTime.isAfter(startTime)) {
                date.atTime(endTime)
            } else {
                date.plusDays(1).atTime(endTime)
            }
            groupCourses.getOrPut(key) { course to startSlot.label.takeIf { it.isNotBlank() } }
            groups.getOrPut(key) { mutableListOf() }.add(
                CourseOccurrence(
                    date = date,
                    start = startDateTime,
                    end = endDateTime,
                    displaced = sourceDate != date,
                ),
            )
        }
        date = date.plusDays(1)
    }

    val planned = groups.map { (key, occurrences) ->
        val (course, slotLabel) = groupCourses.getValue(key)
        PlannedCourse(course = course, slotLabel = slotLabel, occurrences = occurrences)
    }
    return SchedulePlan(planned, skippedKeys.values.toList(), failureReason = null)
}

internal fun courseKey(course: CourseItem): String =
    "${course.id}|${course.time.dayOfWeek}|${course.time.startNode}|${course.time.endNode}|${course.title}"

internal fun CourseItem.toSkipped(@StringRes reason: Int): IcsSkippedCourse =
    IcsSkippedCourse(
        title = title,
        dayOfWeek = time.dayOfWeek,
        startNode = time.startNode,
        endNode = time.endNode,
        reason = reason,
    )

private fun TermTimingProfile.coveringSlot(node: Int): ClassSlotTime? =
    slotTimes.firstOrNull { it.startNode <= node && node <= it.endNode }

private fun ClassSlotTime.parseStart(): LocalTime? = runCatching { LocalTime.parse(startTime) }.getOrNull()

private fun ClassSlotTime.parseEnd(): LocalTime? = runCatching { LocalTime.parse(endTime) }.getOrNull()
