package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.teacherForWeek
import com.x500x.cursimple.core.kernel.model.titleForWeek
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
    // Per-week names, places and teachers count too, so a search can reach a single variant.
    return (listOf(course.title, course.teacher, course.location) +
        course.weekTitles.values + course.weekLocations.values + course.weekTeachers.values)
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

internal enum class VariantField { Title, Time, Location, Teacher }

/** One arrangement of a course: the weeks sharing the same title, time, location and teacher. */
internal data class CourseVariant(
    val entry: CourseLibraryEntry,
    /** Empty when the course lists no weeks, i.e. every week. */
    val weeks: List<Int>,
    val title: String,
    val location: String,
    val teacher: String,
    /** Fields that differ from the group's main arrangement. */
    val changed: Set<VariantField>,
) {
    val time get() = entry.course.time
}

/** Courses sharing a name and category, shown as one card that expands into its variants. */
internal data class CourseLibraryGroup(
    val key: String,
    val entries: List<CourseLibraryEntry>,
    val variants: List<CourseVariant>,
) {
    val main: CourseLibraryEntry get() = entries.first()
    val hasVariants: Boolean get() = variants.size > 1
}

/**
 * Group [entries] (already sorted) by name and category, keeping their order. Per-week
 * titles, locations and teachers split a course into variants, as do differing periods.
 */
internal fun groupCourseLibrary(entries: List<CourseLibraryEntry>, keyPrefix: String = ""): List<CourseLibraryGroup> =
    entries.groupBy { it.course.title.trim() to it.course.category }
        .map { (key, members) ->
            val base = members.first().course
            val variants = members.flatMap { entry -> courseVariants(entry, base) }
            CourseLibraryGroup("$keyPrefix${key.second.name}|${key.first}|${base.id}", members, variants)
        }

private fun courseVariants(entry: CourseLibraryEntry, base: CourseItem): List<CourseVariant> {
    val course = entry.course
    fun variant(weeks: List<Int>, week: Int?): CourseVariant {
        val title = course.titleForWeek(week)
        val location = course.locationForWeek(week)
        val teacher = course.teacherForWeek(week)
        val changed = buildSet {
            if (title.trim() != base.title.trim()) add(VariantField.Title)
            if (course.time != base.time) add(VariantField.Time)
            if (location.trim() != base.location.trim()) add(VariantField.Location)
            if (teacher.trim() != base.teacher.trim()) add(VariantField.Teacher)
        }
        return CourseVariant(entry, weeks, title, location, teacher, changed)
    }
    val overridden = (course.weekTitles.keys + course.weekLocations.keys + course.weekTeachers.keys)
    // Without listed weeks the course runs every week; overrides then carve out their own weeks.
    val weeks = course.weeks.ifEmpty { overridden.sorted() }
    val byArrangement = weeks.distinct().sorted().groupBy { week ->
        Triple(course.titleForWeek(week), course.locationForWeek(week), course.teacherForWeek(week))
    }
    val variants = byArrangement.values.map { group -> variant(group, group.first()) }.toMutableList()
    if (course.weeks.isEmpty()) {
        // The remaining weeks keep the defaults; merge with an override that matches them.
        val defaults = variants.indexOfFirst { it.title == course.title && it.location == course.location && it.teacher == course.teacher }
        if (defaults >= 0) variants[defaults] = variant(emptyList(), null) else variants.add(0, variant(emptyList(), null))
    }
    return variants.ifEmpty { listOf(variant(course.weeks, null)) }
}
