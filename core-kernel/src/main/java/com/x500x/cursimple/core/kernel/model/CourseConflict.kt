package com.x500x.cursimple.core.kernel.model

/** 缺省学期周数，用来给"每周都上"的课程一个有限的周次区间。 */
const val DEFAULT_TERM_WEEK_COUNT: Int = 30

/** 冲突两侧的课程类别组合。 */
enum class CourseConflictKind {
    CourseVsCourse,
    ExamVsCourse,
    ExamVsExam,
}

/**
 * 两门课排在同一天、节次区间相交、并且教学周有交集。
 *
 * [overlappingNodes] 是两者节次的交集，[overlappingWeeks] 是两者共同上课的教学周，
 * 已去重并升序排列。
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

/** 节次区间，起止写反时按从小到大取。 */
fun CourseTimeSlot.nodeRange(): IntRange = minOf(startNode, endNode)..maxOf(startNode, endNode)

/**
 * 两门课共同上课的教学周。
 *
 * 周次列表为空表示每周都上，此时展开成 1 到上界的完整区间；
 * 上界取 [maxWeekCount] 与两侧显式周次最大值中的较大者，
 * 这样"每周都上"始终覆盖对方列出的每一周。
 * 小于 1 的周次不属于任何教学周，直接丢弃。
 */
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

/** 两个节次区间的交集，仅首尾相邻不算相交，无交集返回 null。 */
private fun intersectNodeRanges(first: IntRange, second: IntRange): IntRange? {
    val start = maxOf(first.first, second.first)
    val end = minOf(first.last, second.last)
    return if (start <= end) start..end else null
}

/**
 * 判断两门课是否冲突，不冲突返回 null。
 *
 * 下列情况都不算冲突：id 相同（同一门课）、任意一侧是只用于提醒的占位课程、
 * 不在同一天、节次区间不相交、教学周没有交集（单双周交替上课即属此类）。
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

/**
 * 课程集合内部两两比对得到的全部冲突，同一对课程只出现一次。
 * 按星期、起始节、课程名排序，方便逐条排查。
 */
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

/**
 * [candidate] 与 [others] 中每一门课的冲突，冲突里 first 恒为 [candidate]。
 * 用于加课表单实时提示：候选课还没入库，也能先算出它会撞上谁。
 */
fun conflictsWithCourse(
    candidate: CourseItem,
    others: List<CourseItem>,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
): List<CourseConflict> =
    others.distinctBy { it.id }
        .mapNotNull { courseConflictOrNull(candidate, it, maxWeekCount) }
        .sortedWith(compareBy({ it.overlappingNodes.first }, { it.second.title }))
