package com.x500x.cursimple.core.kernel.model

import java.time.LocalDate

/** Resolved [sourceDate] for one day; holidays suppress classes and reminders. */
data class ScheduleDayResolution(
    val date: LocalDate,
    val sourceDate: LocalDate,
    val isHoliday: Boolean,
    val holidayName: String?,
    val holidayNameRes: Int? = null,
    /** Mark make-up workdays that replace otherwise nonworking days. */
    val isMakeUpWorkday: Boolean = false,
    /**
     * Optional partial-swap interval; resolve actual courses through
     * [temporaryScheduleCourseSourceDate].
     */
    val makeUpNodeRange: IntRange? = null,
)

/**
 * Apply manual day declarations, then explicit make-up overrides, then downloaded or bundled
 * holidays. Course cancellations do not redefine the day's holiday status.
 */
fun resolveScheduleDay(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
): ScheduleDayResolution {
    val userEntry = holidayCalendar.userEntryOn(date)
    val effectiveEntry = when {
        userEntry != null -> userEntry
        matchingTemporaryScheduleOverride(date, overrides) != null -> null
        else -> holidayCalendar.syncedEntryOn(date) ?: holidayCalendar.builtInEntryOn(date)
    }
    val holiday = effectiveEntry?.kind == HolidayEntryKind.Holiday
    // Only mark workday overrides when the original day was nonworking.
    val makeUpWorkday = effectiveEntry?.kind == HolidayEntryKind.Workday &&
        date.dayOfWeek.value >= 6
    return ScheduleDayResolution(
        date = date,
        sourceDate = if (holiday) date else resolveTemporaryScheduleSourceDate(date, overrides),
        isHoliday = holiday,
        holidayName = if (holiday) effectiveEntry?.name?.takeIf { it.isNotBlank() } else null,
        holidayNameRes = when {
            !holiday || userEntry != null -> null
            else -> builtInHolidayNameResOn(date) ?: effectiveEntry?.name?.let(::holidayNameResOfName)
        },
        isMakeUpWorkday = makeUpWorkday,
        makeUpNodeRange = if (holiday) null else matchingTemporaryScheduleOverride(date, overrides)?.makeUpNodeRange(),
    )
}

/**
 * Resolve source-week course coverage, moves and cancellations. [includeCancelled] supports
 * restoration UI; null [termStartDate] disables week filtering.
 */
fun coursesScheduledOn(
    date: LocalDate,
    courses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    termStartDate: LocalDate?,
    includeCancelled: Boolean = false,
): List<CourseItem> {
    val resolution = resolveScheduleDay(date, overrides, holidayCalendar)
    val activeOn: (CourseItem, LocalDate) -> Boolean = { course, day ->
        termStartDate == null || course.isActiveInTermWeekNumber(resolveTermWeekNumber(termStartDate, day))
    }
    val movedIn = coursesMovedTo(
        date = date,
        overrides = overrides,
        courseById = { id -> courses.firstOrNull { it.id == id } },
        isOriginallyActive = activeOn,
    )
    val staying = if (resolution.isHoliday) {
        emptyList()
    } else {
        courses
            .filterNot { isCourseMovedAwayFrom(date, it, overrides) }
            .filter { course ->
                val source = temporaryScheduleCourseSourceDate(date, course, resolution.sourceDate, overrides)
                source != null && activeOn(course, source)
            }
    }
    return (staying + movedIn)
        .let { all -> if (includeCancelled) all else all.filterNot { isCourseTemporarilyCancelled(date, it, overrides) } }
        .sortedWith(compareBy({ it.time.startNode }, { it.time.endNode }))
}
