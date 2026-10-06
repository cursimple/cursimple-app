package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.util.Locale

internal enum class CourseSource { Plugin, Manual }

internal data class CourseLibraryEntry(
    val course: CourseItem,
    val source: CourseSource,
    val overridesPlugin: Boolean = false,
) {
    val editable: Boolean get() = true

    /**
     * Restore plugin data by removing its manual override; plugin originals persist across
     * sync.
     */
    val removable: Boolean get() = source == CourseSource.Manual && !overridesPlugin

    val restorable: Boolean get() = overridesPlugin
}

internal enum class CourseSortMode { ByWeekday, ByTitle, BySource }

/**
 * Merge sources for display, excluding reminder placeholders and tracking overridden originals
 * for restoration.
 */
internal fun buildCourseLibrary(
    pluginCourses: List<CourseItem>,
    manualCourses: List<CourseItem>,
): List<CourseLibraryEntry> {
    val manual = manualCourses.visibleScheduleCourses()
    val pluginIds = pluginCourses.mapTo(mutableSetOf()) { it.id }
    val manualIds = manual.mapTo(mutableSetOf()) { it.id }
    val plugin = pluginCourses.visibleScheduleCourses().filterNot { it.id in manualIds }
    return plugin.map { CourseLibraryEntry(it, CourseSource.Plugin) } +
        manual.map { CourseLibraryEntry(it, CourseSource.Manual, overridesPlugin = it.id in pluginIds) }
}

internal fun matchesCourseQuery(course: CourseItem, query: String): Boolean {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return true
    return listOf(course.title, course.teacher, course.location)
        .any { it.lowercase(Locale.ROOT).contains(needle) }
}

/** Apply day, period and title tie-breakers for stable sorting. */
internal fun sortCourseLibrary(
    entries: List<CourseLibraryEntry>,
    mode: CourseSortMode,
): List<CourseLibraryEntry> {
    val fallback = compareBy<CourseLibraryEntry>(
        { it.course.time.dayOfWeek },
        { it.course.time.startNode },
        { it.course.title },
        { it.course.id },
    )
    return when (mode) {
        CourseSortMode.ByWeekday -> entries.sortedWith(fallback)
        CourseSortMode.ByTitle -> entries.sortedWith(compareBy<CourseLibraryEntry> { it.course.title }.then(fallback))
        CourseSortMode.BySource -> entries.sortedWith(compareBy<CourseLibraryEntry> { it.source }.then(fallback))
    }
}

/** Group by the timetable's [columnDayOfWeeks] display order. */
internal fun groupCourseLibraryByWeekday(
    entries: List<CourseLibraryEntry>,
    columnDayOfWeeks: List<Int>,
): List<Pair<Int, List<CourseLibraryEntry>>> {
    val byDay = entries.groupBy { it.course.time.dayOfWeek }
    val ordered = columnDayOfWeeks + (1..7).filterNot { it in columnDayOfWeeks }
    return ordered.mapNotNull { day ->
        byDay[day]?.takeIf { it.isNotEmpty() }?.let { day to it }
    }
}
