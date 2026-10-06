package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Serializable
data class TemporaryScheduleOverride(
    @SerialName("id") val id: String,
    @SerialName("type") val type: TemporaryScheduleOverrideType = TemporaryScheduleOverrideType.MakeUp,
    @SerialName("targetDate") val targetDate: String = "",
    @SerialName("sourceDate") val sourceDate: String = "",
    @SerialName("startDate") val startDate: String = "",
    @SerialName("endDate") val endDate: String = "",
    @SerialName("sourceDayOfWeek") val sourceDayOfWeek: Int? = null,
    @SerialName("cancelStartNode") val cancelStartNode: Int? = null,
    @SerialName("cancelEndNode") val cancelEndNode: Int? = null,
    @SerialName("cancelCourseId") val cancelCourseId: String? = null,
    /** Null interval bounds indicate a whole-day swap. */
    @SerialName("makeUpStartNode") val makeUpStartNode: Int? = null,
    @SerialName("makeUpEndNode") val makeUpEndNode: Int? = null,
    /** MoveCourse source and destination dates with target period bounds. */
    @SerialName("moveCourseId") val moveCourseId: String? = null,
    @SerialName("moveToStartNode") val moveToStartNode: Int? = null,
    @SerialName("moveToEndNode") val moveToEndNode: Int? = null,
)

@Serializable
enum class TemporaryScheduleOverrideType {
    @SerialName("make_up")
    MakeUp,

    @SerialName("cancel_course")
    CancelCourse,

    /** Move one course without replacing other courses on either day. */
    @SerialName("move_course")
    MoveCourse,
}

fun TemporaryScheduleOverride.containsDate(date: LocalDate): Boolean {
    return sourceDateFor(date) != null
}

fun TemporaryScheduleOverride.sourceDateFor(date: LocalDate): LocalDate? {
    if (type != TemporaryScheduleOverrideType.MakeUp) return null

    val explicitTarget = parseOverrideDate(targetDate)
    val explicitSource = parseOverrideDate(sourceDate)
    if (explicitTarget != null || explicitSource != null) {
        return if (explicitTarget == date) explicitSource else null
    }

    val legacySourceDayOfWeek = sourceDayOfWeek?.takeIf { it in 1..7 } ?: return null
    val start = parseOverrideDate(startDate) ?: return null
    val end = parseOverrideDate(endDate) ?: return null
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    if (date.isBefore(normalizedStart) || date.isAfter(normalizedEnd)) return null
    // Legacy weekday records use a persisted Monday anchor, independent of display week start.
    return date
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusDays((legacySourceDayOfWeek - 1).toLong())
}

fun TemporaryScheduleOverride.targetDates(): List<LocalDate> {
    val explicitTarget = parseOverrideDate(targetDate)
    if (explicitTarget != null) return listOf(explicitTarget)

    val start = parseOverrideDate(startDate) ?: return emptyList()
    val end = parseOverrideDate(endDate) ?: return emptyList()
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    val days = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd).toInt()
    return (0..days).map { offset -> normalizedStart.plusDays(offset.toLong()) }
}

fun TemporaryScheduleOverride.cancelsCourseOn(date: LocalDate, course: CourseItem): Boolean {
    if (type != TemporaryScheduleOverrideType.CancelCourse) return false
    if (date !in targetDates()) return false
    val requiredCourseId = cancelCourseId?.takeIf { it.isNotBlank() }
    if (requiredCourseId != null && requiredCourseId != course.id) return false
    val start = cancelStartNode ?: return false
    val end = cancelEndNode ?: start
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    return course.time.startNode <= normalizedEnd && course.time.endNode >= normalizedStart
}

/** Null for whole-day swaps, otherwise the affected period interval. */
fun TemporaryScheduleOverride.makeUpNodeRange(): IntRange? {
    if (type != TemporaryScheduleOverrideType.MakeUp) return null
    val start = makeUpStartNode ?: makeUpEndNode ?: return null
    val end = makeUpEndNode ?: makeUpStartNode ?: return null
    return minOf(start, end)..maxOf(start, end)
}

/**
 * Resolve the course's source day under whole-day or partial swaps; null means no occurrence.
 */
fun temporaryScheduleCourseSourceDate(
    date: LocalDate,
    course: CourseItem,
    sourceDate: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): LocalDate? {
    if (sourceDate == date) {
        return date.takeIf { course.time.dayOfWeek == date.dayOfWeek.value }
    }
    val range = matchingTemporaryScheduleOverride(date, overrides)?.makeUpNodeRange()
        ?: return sourceDate.takeIf { course.time.dayOfWeek == sourceDate.dayOfWeek.value }
    val inRange = course.time.startNode <= range.last && course.time.endNode >= range.first
    return when {
        inRange && course.time.dayOfWeek == sourceDate.dayOfWeek.value -> sourceDate
        !inRange && course.time.dayOfWeek == date.dayOfWeek.value -> date
        else -> null
    }
}

fun TemporaryScheduleOverride.moveToNodeRange(): IntRange? {
    if (type != TemporaryScheduleOverrideType.MoveCourse) return null
    val start = moveToStartNode ?: return null
    val end = moveToEndNode ?: start
    return minOf(start, end)..maxOf(start, end)
}

/** Suppress courses moved away from their original date to avoid duplicate appearances. */
fun isCourseMovedAwayFrom(
    date: LocalDate,
    course: CourseItem,
    overrides: List<TemporaryScheduleOverride>,
): Boolean = overrides.any { rule ->
    rule.type == TemporaryScheduleOverrideType.MoveCourse &&
        rule.moveCourseId == course.id &&
        parseOverrideDate(rule.sourceDate) == date
}

/** Whether [course] was individually moved into [date]. */
fun isCourseMovedTo(
    date: LocalDate,
    course: CourseItem,
    overrides: List<TemporaryScheduleOverride>,
): Boolean = overrides.any { rule ->
    rule.type == TemporaryScheduleOverrideType.MoveCourse &&
        rule.moveCourseId == course.id &&
        parseOverrideDate(rule.targetDate) == date
}

/**
 * Use [courseById] and original-day activity to materialize moved courses; skip missing or
 * inactive originals.
 */
fun coursesMovedTo(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    courseById: (String) -> CourseItem?,
    isOriginallyActive: (CourseItem, LocalDate) -> Boolean = { _, _ -> true },
): List<CourseItem> =
    coursesMovedToWithOrigin(date, overrides, courseById, isOriginallyActive).map { it.first }

/** [coursesMovedTo] with source-date metadata for moved-course presentation. */
fun coursesMovedToWithOrigin(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    courseById: (String) -> CourseItem?,
    isOriginallyActive: (CourseItem, LocalDate) -> Boolean = { _, _ -> true },
): List<Pair<CourseItem, LocalDate>> = overrides.mapNotNull { rule ->
    if (rule.type != TemporaryScheduleOverrideType.MoveCourse) return@mapNotNull null
    if (parseOverrideDate(rule.targetDate) != date) return@mapNotNull null
    val from = parseOverrideDate(rule.sourceDate) ?: return@mapNotNull null
    val courseId = rule.moveCourseId ?: return@mapNotNull null
    val course = courseById(courseId) ?: return@mapNotNull null
    if (!isOriginallyActive(course, from)) return@mapNotNull null
    val nodes = rule.moveToNodeRange() ?: return@mapNotNull null
    course.copy(
        time = course.time.copy(
            dayOfWeek = date.dayOfWeek.value,
            startNode = nodes.first,
            endNode = nodes.last,
        ),
    ) to from
}

data class CourseMovePlan(
    val removeIds: List<String> = emptyList(),
    val upsert: TemporaryScheduleOverride? = null,
)

/**
 * Rewrite an existing move rather than chaining copies. Returning to natural date and periods
 * removes the override; naturalStartNode and naturalEndNode identify that original position.
 */
fun planCourseMove(
    overrides: List<TemporaryScheduleOverride>,
    courseId: String,
    from: LocalDate,
    to: LocalDate,
    toStartNode: Int,
    toEndNode: Int,
    naturalStartNode: Int,
    naturalEndNode: Int,
    newId: () -> String,
): CourseMovePlan {
    val backToNaturalNodes = toStartNode == naturalStartNode && toEndNode == naturalEndNode
    val movedIn = overrides.lastOrNull { rule ->
        rule.type == TemporaryScheduleOverrideType.MoveCourse &&
            rule.moveCourseId == courseId &&
            parseOverrideDate(rule.targetDate) == from
    }
    if (movedIn != null) {
        val origin = parseOverrideDate(movedIn.sourceDate) ?: from
        if (to == origin && backToNaturalNodes) {
            return CourseMovePlan(removeIds = listOf(movedIn.id))
        }
        return CourseMovePlan(
            upsert = movedIn.copy(
                targetDate = to.toString(),
                moveToStartNode = toStartNode,
                moveToEndNode = toEndNode,
            ),
        )
    }
    if (to == from && backToNaturalNodes) return CourseMovePlan()
    return CourseMovePlan(
        upsert = TemporaryScheduleOverride(
            id = newId(),
            type = TemporaryScheduleOverrideType.MoveCourse,
            sourceDate = from.toString(),
            targetDate = to.toString(),
            moveCourseId = courseId,
            moveToStartNode = toStartNode,
            moveToEndNode = toEndNode,
        ),
    )
}

/** Apply [removeIds] before [upserts]. */
data class CancelCoursePlan(
    val removeIds: List<String> = emptyList(),
    val upserts: List<TemporaryScheduleOverride> = emptyList(),
)

/** Cancel only [course] on [date]; simultaneous courses are unaffected. */
fun planCancelCourse(date: LocalDate, course: CourseItem, newId: () -> String): CancelCoursePlan =
    CancelCoursePlan(upserts = listOf(courseCancelRule(date, course, newId())))

/**
 * Restore one course while preserving others cancelled by legacy period rules. Multi-day legacy
 * rules cannot be split and return null.
 */
fun planRestoreCourse(
    date: LocalDate,
    course: CourseItem,
    dayCourses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
    newId: () -> String,
): CancelCoursePlan? {
    val matching = overrides.filter { it.cancelsCourseOn(date, course) }
    if (matching.isEmpty()) return CancelCoursePlan()
    if (matching.any { it.targetDates().size > 1 }) return null
    val remaining = overrides - matching.toSet()
    val keepCancelled = matching
        .filter { it.cancelCourseId.isNullOrBlank() }
        .flatMap { rule -> dayCourses.filter { rule.cancelsCourseOn(date, it) } }
        .filter { it.id != course.id && !isCourseTemporarilyCancelled(date, it, remaining) }
        .distinctBy { it.id }
    return CancelCoursePlan(
        removeIds = matching.map { it.id },
        upserts = keepCancelled.map { courseCancelRule(date, it, newId()) },
    )
}

private fun courseCancelRule(date: LocalDate, course: CourseItem, id: String) = TemporaryScheduleOverride(
    id = id,
    type = TemporaryScheduleOverrideType.CancelCourse,
    targetDate = date.toString(),
    cancelCourseId = course.id,
    cancelStartNode = course.time.startNode,
    cancelEndNode = course.time.endNode,
)

fun matchingTemporaryScheduleOverride(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): TemporaryScheduleOverride? {
    return overrides.asReversed().firstOrNull { it.containsDate(date) }
}

fun resolveTemporaryScheduleSourceDate(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): LocalDate {
    return matchingTemporaryScheduleOverride(date, overrides)?.sourceDateFor(date) ?: date
}

fun isCourseTemporarilyCancelled(
    date: LocalDate,
    course: CourseItem,
    overrides: List<TemporaryScheduleOverride>,
): Boolean {
    return overrides.any { it.cancelsCourseOn(date, course) }
}

fun filterTemporaryCancelledCourses(
    date: LocalDate,
    courses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
): List<CourseItem> {
    if (overrides.none { it.type == TemporaryScheduleOverrideType.CancelCourse }) return courses
    return courses.filterNot { course -> isCourseTemporarilyCancelled(date, course, overrides) }
}

fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周$dayOfWeek"
}

private fun parseOverrideDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
