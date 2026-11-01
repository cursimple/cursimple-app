package com.x500x.cursimple.core.data.term

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule

/**
 * What a new term starts with when copied from an old one: the course list (manual courses and the synced
 * timetable's structure), without anything tied to the old term's calendar.
 */
data class TermCopyPlan(
    val manualCourses: List<CourseItem>,
    /** The old term's synced timetable with its per-week details cleared; null when the old term had none. */
    val schedule: TermSchedule?,
)

/**
 * Week numbers, per-week overrides and (unless [keepReminders]) reminder times belong to the old term's calendar,
 * so they are dropped. Placeholder reminder-only rows and hidden or purged courses are not copied.
 */
fun planTermCopy(
    courses: List<CourseItem>,
    schedule: TermSchedule?,
    keepReminders: Boolean,
    newTermId: String,
): TermCopyPlan {
    val copied = courses
        .filter { !it.hidden && !it.purged && !it.reminderOnly }
        .map { course -> course.clearedFor(newTermId, keepReminders) }
    return TermCopyPlan(
        manualCourses = copied,
        schedule = schedule?.let { old ->
            old.copy(dailySchedules = old.dailySchedules.map { day ->
                day.copy(courses = day.courses.filter { !it.hidden && !it.purged }.map { it.clearedFor(newTermId, keepReminders) })
            })
        },
    )
}

private fun CourseItem.clearedFor(newTermId: String, keepReminders: Boolean): CourseItem = copy(
    id = "copy-${newTermId.take(8)}-$id",
    weeks = emptyList(),
    weekLocations = emptyMap(),
    weekTeachers = emptyMap(),
    weekTitles = emptyMap(),
    reminderStartTime = reminderStartTime.takeIf { keepReminders },
    reminderEndTime = reminderEndTime.takeIf { keepReminders },
)
