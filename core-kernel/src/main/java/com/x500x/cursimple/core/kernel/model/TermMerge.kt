package com.x500x.cursimple.core.kernel.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One term going into a merge, with everything the merge needs to shift it. */
data class MergedTermSource(
    val termId: String,
    val name: String,
    val termStartDate: String?,
    val schedule: TermSchedule? = null,
    val manualCourses: List<CourseItem> = emptyList(),
    val events: List<ScheduleEvent> = emptyList(),
)

/** The new term plus the per-source week offset the merge applied. */
data class MergedTermData(
    val name: String,
    val termStartDate: String?,
    val weekShift: Map<String, Int>,
    val schedule: TermSchedule?,
    val manualCourses: List<CourseItem>,
    val events: List<ScheduleEvent>,
)

private fun mondayOf(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

private fun parseIsoDate(iso: String?): LocalDate? =
    iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Whole weeks from [fromMonday] to [toMonday]; the caller guarantees both are Mondays. */
private fun weeksBetween(fromMonday: LocalDate, toMonday: LocalDate): Int =
    (ChronoUnit.DAYS.between(fromMonday, toMonday) / 7).toInt()

enum class TermMergeAlignment {
    ByRealDate,
    ByWeekNumber,
}

/**
 * Align merged terms by real calendar date (default) or by academic week number:
 * - [TermMergeAlignment.ByRealDate]: the earliest start date becomes the merged term's
 *   start, and other terms shift by the weeks between their start dates.
 * - [TermMergeAlignment.ByWeekNumber]: aligns syllabus weeks 1:1, ideal for reusing
 *   schedules from previous years/semesters without accumulating cross-year week gaps.
 */
fun mergeTerms(
    mergedTermId: String,
    sources: List<MergedTermSource>,
    alignment: TermMergeAlignment = TermMergeAlignment.ByRealDate,
    anchorTermId: String? = null,
): MergedTermData {
    val anchorSource = sources.firstOrNull { it.termId == anchorTermId }
    val starts = sources.mapNotNull { parseIsoDate(it.termStartDate) }
    val anchorStart = parseIsoDate(anchorSource?.termStartDate)
    val mergedStart = anchorStart ?: if (alignment == TermMergeAlignment.ByWeekNumber) {
        starts.maxOrNull() ?: starts.minOrNull()
    } else {
        starts.minOrNull()
    }
    val mergedStartIso = mergedStart?.toString()

    val baseMonday = mergedStart?.let(::mondayOf)
    val weekShift = sources.associate { source ->
        val sourceStart = parseIsoDate(source.termStartDate)
        val shift = if (alignment == TermMergeAlignment.ByWeekNumber || baseMonday == null || sourceStart == null) 0
        else weeksBetween(baseMonday, mondayOf(sourceStart))
        source.termId to shift
    }

    fun shiftCourses(courses: List<CourseItem>, shift: Int, sourceName: String?): List<CourseItem> =
        courses.mapNotNull { course ->
            val shiftedWeeks = if (shift == 0) course.weeks else course.weeks.map { it + shift }.filter { it > 0 }.distinct().sorted()
            if (shiftedWeeks.isEmpty()) null
            else {
                val originDetails = if (!sourceName.isNullOrBlank() && sources.size > 1 &&
                    course.details.none { it.label in setOf("来源学期", "Source Term", "來源學期") }
                ) {
                    listOf(CourseDetailField(label = "来源学期", value = sourceName.trim())) + course.details
                } else {
                    course.details
                }
                course.copy(
                    weeks = shiftedWeeks,
                    weekLocations = if (shift == 0) course.weekLocations else course.weekLocations.mapKeys { it.key + shift }.filterKeys { it > 0 },
                    weekTeachers = if (shift == 0) course.weekTeachers else course.weekTeachers.mapKeys { it.key + shift }.filterKeys { it > 0 },
                    weekTitles = if (shift == 0) course.weekTitles else course.weekTitles.mapKeys { it.key + shift }.filterKeys { it > 0 },
                    details = originDetails,
                )
            }
        }

    val scheduleCourses = mutableListOf<CourseItem>()
    val manualCourses = mutableListOf<CourseItem>()
    val events = mutableListOf<ScheduleEvent>()
    val seenCourseIds = mutableSetOf<String>()
    val seenEventIds = mutableSetOf<String>()

    sources.forEach { source ->
        val shift = weekShift[source.termId] ?: 0
        shiftCourses(source.schedule?.dailySchedules.orEmpty().flatMap { it.courses }, shift, source.name)
            .forEach { if (seenCourseIds.add(it.id)) scheduleCourses += it }
        shiftCourses(source.manualCourses, shift, source.name)
            .forEach { if (seenCourseIds.add(it.id)) manualCourses += it }
        source.events.forEach { event ->
            if (seenEventIds.add(event.id)) {
                events += event
            }
        }
    }

    val dailySchedules = scheduleCourses.groupBy { it.time.dayOfWeek }
        .toSortedMap()
        .map { (dayOfWeek, dayCourses) -> DailySchedule(dayOfWeek, dayCourses) }

    val schedule = if (sources.any { it.schedule != null }) {
        TermSchedule(
            termId = mergedTermId,
            updatedAt = sources.mapNotNull { it.schedule?.updatedAt }.maxOrNull().orEmpty(),
            dailySchedules = dailySchedules,
            sourcePluginId = null,
            legacyDisplayPluginId = null,
            externalImport = false,
        )
    } else null

    return MergedTermData(
        name = sources.map { it.name.trim() }.filter { it.isNotEmpty() }.joinToString(" + "),
        termStartDate = mergedStartIso,
        weekShift = weekShift,
        schedule = schedule,
        manualCourses = manualCourses,
        events = events,
    )
}
