package com.x500x.cursimple.core.kernel.model

import java.time.LocalDate
import java.time.LocalTime

/** 已应用调课、停课、周次和假日后的单次课程。缺少作息时仍保留课程，时间为空。 */
data class ScheduledCourseOccurrence(
    val course: CourseItem,
    val date: LocalDate,
    val sourceDate: LocalDate,
    val sourceWeek: Int?,
    val start: LocalTime?,
    val end: LocalTime?,
) {
    val location: String get() = course.locationForWeek(sourceWeek)
    val hasTime: Boolean get() = start != null && end != null
}

fun scheduledCourseOccurrencesOn(
    date: LocalDate,
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    termStartDate: LocalDate?,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
): List<ScheduledCourseOccurrence> {
    val visible = courses.filterNot { it.hidden || it.reminderOnly }
    val day = resolveScheduleDay(date, overrides, holidayCalendar)
    val movedSources = coursesMovedToWithOrigin(
        date, overrides, courseById = { id -> visible.firstOrNull { it.id == id } },
        isOriginallyActive = { course, from -> termStartDate == null || course.isActiveInTermWeekNumber(resolveTermWeekNumber(termStartDate, from)) },
    ).associate { it.first.id to it.second }
    return coursesScheduledOn(date, visible, overrides, holidayCalendar, termStartDate)
        .distinctBy { Triple(it.id, it.time.startNode, it.time.endNode) }
        .map { course ->
            val source = movedSources[course.id]
                ?: temporaryScheduleCourseSourceDate(date, course, day.sourceDate, overrides) ?: date
            val slots = timingProfile?.slotsCovering(course.time.startNode, course.time.endNode).orEmpty()
            val start = slots.firstOrNull { course.time.startNode in it.value.startNode..it.value.endNode }?.value?.startTime?.let(::occurrenceTime)
                ?: course.reminderStartTime?.let(::occurrenceTime)
            val end = slots.firstOrNull { course.time.endNode in it.value.startNode..it.value.endNode }?.value?.endTime?.let(::occurrenceTime)
                ?: course.reminderEndTime?.let(::occurrenceTime)
            val valid = start != null && end != null && start.isBefore(end)
            ScheduledCourseOccurrence(
                course, date, source, termStartDate?.let { resolveTermWeekNumber(it, source) },
                start.takeIf { valid }, end.takeIf { valid },
            )
        }.sortedWith(compareBy<ScheduledCourseOccurrence> { it.start == null }.thenBy { it.start }.thenBy { it.course.time.startNode }.thenBy { it.course.id })
}

private fun occurrenceTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw.trim()) }.getOrNull()
