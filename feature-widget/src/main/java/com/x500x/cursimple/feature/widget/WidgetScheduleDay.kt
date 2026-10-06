package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.filterTemporaryCancelledCourses
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.time.LocalDate

/** Resolved target/source dates, source teaching week, holiday state and displayed courses. */
internal data class WidgetScheduleDay(
    val targetDate: LocalDate,
    val sourceDate: LocalDate,
    val weekIndex: Int?,
    val holidayLabel: WidgetHolidayLabel?,
    val courses: List<CourseItem>,
    /** Holiday courses remain visible but do not receive in-progress or countdown status. */
    val onHoliday: Boolean = false,
)

/**
 * Resolve [targetDate] through overrides and holidays while retaining unavailable holiday
 * courses.
 */
internal fun resolveWidgetScheduleDay(
    targetDate: LocalDate,
    termStart: LocalDate?,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    coursesOfDayOfWeek: (Int) -> List<CourseItem>,
): WidgetScheduleDay {
    val resolution = resolveScheduleDay(targetDate, temporaryScheduleOverrides, holidayCalendar)
    val sourceDate = resolution.sourceDate
    val weekIndex = resolveWeekIndex(sourceDate, termStart)
    // Partial swaps need both days' courses before per-course source resolution.
    val candidates = (coursesOfDayOfWeek(sourceDate.dayOfWeek.value) + coursesOfDayOfWeek(targetDate.dayOfWeek.value))
        .distinct()
        .filterNot { isCourseMovedAwayFrom(targetDate, it, temporaryScheduleOverrides) }
    val movedIn = coursesMovedTo(
        date = targetDate,
        overrides = temporaryScheduleOverrides,
        courseById = { id -> (1..7).flatMap(coursesOfDayOfWeek).firstOrNull { it.id == id } },
        isOriginallyActive = { course, from -> course.activeOnWeek(resolveWeekIndex(from, termStart)) },
    ).visibleScheduleCourses()
    val courses = filterTemporaryCancelledCourses(
        date = targetDate,
        courses = candidates,
        overrides = temporaryScheduleOverrides,
    )
        .visibleScheduleCourses()
        .mapNotNull { course ->
            val courseSource = temporaryScheduleCourseSourceDate(
                date = targetDate,
                course = course,
                sourceDate = sourceDate,
                overrides = temporaryScheduleOverrides,
            ) ?: return@mapNotNull null
            course.takeIf { it.activeOnWeek(resolveWeekIndex(courseSource, termStart)) }
        }
        .plus(movedIn)
        .sortedBy { it.time.startNode }
    // Explicit moves can make a holiday active for moved-in courses; otherwise keep the holiday unavailable state.
    val holidayOverridden = resolution.isHoliday && movedIn.isNotEmpty()
    return WidgetScheduleDay(
        targetDate = targetDate,
        sourceDate = sourceDate,
        weekIndex = weekIndex,
        holidayLabel = if (resolution.isHoliday) widgetHolidayLabel(resolution.holidayName, resolution.holidayNameRes) else null,
        courses = if (holidayOverridden) movedIn.sortedBy { it.time.startNode } else courses,
        onHoliday = resolution.isHoliday && !holidayOverridden,
    )
}
