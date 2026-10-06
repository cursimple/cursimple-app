package com.x500x.cursimple.core.kernel.model

const val DEFAULT_TERM_WEEK_COUNT: Int = 20

enum class CourseConflictKind {
    CourseVsCourse,
    ExamVsCourse,
    ExamVsExam,
}

/**
 * Course conflicts share day, periods and teaching weeks; intersections are sorted and
 * deduplicated.
 */
data class CourseConflict(
    val first: CourseItem,
    val second: CourseItem,
    val dayOfWeek: Int,
    val overlappingNodes: IntRange,
    val overlappingWeeks: List<Int>,
) {
    val kind: CourseConflictKind = when {
        first.category == CourseCategory.Exam && second.category == CourseCategory.Exam ->
            CourseConflictKind.ExamVsExam

        first.category == CourseCategory.Exam || second.category == CourseCategory.Exam ->
            CourseConflictKind.ExamVsCourse

        else -> CourseConflictKind.CourseVsCourse
    }
}

fun CourseTimeSlot.nodeRange(): IntRange = minOf(startNode, endNode)..maxOf(startNode, endNode)

/** Empty weeks means every positive week through the larger configured or explicit bound. */
fun termWeekIntersection(
    first: List<Int>,
    second: List<Int>,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
): List<Int> {
    val explicitMax = (first + second).filter(::isTermWeekNumberStarted).maxOrNull() ?: 0
    val bound = maxOf(maxWeekCount.coerceAtLeast(0), explicitMax)
    val left = expandTermWeeks(first, bound)
    val right = expandTermWeeks(second, bound).toHashSet()
    return left.filter { it in right }
}

private fun expandTermWeeks(weeks: List<Int>, bound: Int): List<Int> =
    if (weeks.isEmpty()) {
        (1..bound).toList()
    } else {
        weeks.filter(::isTermWeekNumberStarted).distinct().sorted()
    }

/** Return the period intersection or null; adjacent boundaries do not overlap. */
private fun intersectNodeRanges(first: IntRange, second: IntRange): IntRange? {
    val start = maxOf(first.first, second.first)
    val end = minOf(first.last, second.last)
    return if (start <= end) start..end else null
}

/**
 * Exclude identical IDs, reminder placeholders, different days and disjoint periods or weeks.
 */
fun courseConflictOrNull(
    first: CourseItem,
    second: CourseItem,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
): CourseConflict? {
    if (first.id == second.id) return null
    if (first.reminderOnly || second.reminderOnly) return null
    if (first.time.dayOfWeek != second.time.dayOfWeek) return null
    val nodes = intersectNodeRanges(first.time.nodeRange(), second.time.nodeRange()) ?: return null
    val weeks = termWeekIntersection(first.weeks, second.weeks, maxWeekCount)
    if (weeks.isEmpty()) return null
    return CourseConflict(
        first = first,
        second = second,
        dayOfWeek = first.time.dayOfWeek,
        overlappingNodes = nodes,
        overlappingWeeks = weeks,
    )
}

/** Report each course pair once, sorted by day, start period and title. */
fun findCourseConflicts(
    courses: List<CourseItem>,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
): List<CourseConflict> {
    val candidates = courses.distinctBy { it.id }.filterNot { it.reminderOnly }
    val conflicts = mutableListOf<CourseConflict>()
    for (i in candidates.indices) {
        for (j in i + 1 until candidates.size) {
            courseConflictOrNull(candidates[i], candidates[j], maxWeekCount)?.let(conflicts::add)
        }
    }
    return conflicts.sortedWith(
        compareBy(
            { it.dayOfWeek },
            { it.overlappingNodes.first },
            { it.first.title },
            { it.second.title },
        ),
    )
}

/** Compare an unsaved [candidate] with [others]; first always refers to [candidate]. */
fun conflictsWithCourse(
    candidate: CourseItem,
    others: List<CourseItem>,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
): List<CourseConflict> =
    others.distinctBy { it.id }
        .mapNotNull { courseConflictOrNull(candidate, it, maxWeekCount) }
        .sortedWith(compareBy({ it.overlappingNodes.first }, { it.second.title }))
