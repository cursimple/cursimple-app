package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule

/** Preview timetable differences before replacement or separate-term import. */
data class ScheduleImportDiff(
    val added: List<CourseItem>,
    val removed: List<CourseItem>,
    val keptCount: Int,
) {
    val hasChanges: Boolean get() = added.isNotEmpty() || removed.isNotEmpty()

    val changeCount: Int get() = added.size + removed.size
}

/**
 * Compare stable course characteristics rather than changing portal IDs; location changes count
 * as removal and addition.
 */
fun diffSchedules(current: TermSchedule?, incoming: TermSchedule?): ScheduleImportDiff {
    val before = current.visibleCourses().groupingBy { it.identity() }.eachCount()
    val after = incoming.visibleCourses().groupingBy { it.identity() }.eachCount()

    val added = mutableListOf<CourseItem>()
    val removed = mutableListOf<CourseItem>()
    var kept = 0

    incoming.visibleCourses().forEach { course ->
        val identity = course.identity()
        val surplus = after.getValue(identity) - (before[identity] ?: 0)
        if (surplus > 0 && added.count { it.identity() == identity } < surplus) {
            added += course
        } else {
            kept++
        }
    }
    current.visibleCourses().forEach { course ->
        val identity = course.identity()
        val shortfall = (before.getValue(identity)) - (after[identity] ?: 0)
        if (shortfall > 0 && removed.count { it.identity() == identity } < shortfall) {
            removed += course
        }
    }

    return ScheduleImportDiff(added = added, removed = removed, keptCount = kept)
}

private fun TermSchedule?.visibleCourses(): List<CourseItem> =
    this?.dailySchedules.orEmpty().flatMap { it.courses }.filterNot { it.hidden }

private fun CourseItem.identity(): String = listOf(
    title.trim(),
    time.dayOfWeek.toString(),
    time.startNode.toString(),
    time.endNode.toString(),
    weeks.sorted().joinToString(","),
    location.trim(),
).joinToString("|")
