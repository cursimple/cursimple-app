package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseConflict
import com.x500x.cursimple.core.kernel.model.CourseConflictKind
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.core.kernel.model.ExamCountdown
import com.x500x.cursimple.core.kernel.model.conflictsWithCourse
import com.x500x.cursimple.core.kernel.model.weekdayLabel

/** 表单里还没入库的候选课程用的临时 id，不会和任何已保存的课程相同。 */
internal const val DRAFT_COURSE_ID = "draft-course"

/** 加课表单里最多列出几门冲突课程，其余折成"等 N 门"。 */
private const val CONFLICT_PREVIEW_LIMIT = 3

/** 周次分段超过这个数量就不再逐段列出，只给首尾和总数。 */
private const val WEEK_SEGMENT_LIMIT = 4

/**
 * 表单当前填写的内容会和 [existingCourses] 里的哪些课冲突。
 * 节次或周次还没填完整（传 null）时不算冲突，等填全了再提示。
 */
internal fun draftCourseConflicts(
    existingCourses: List<CourseItem>,
    dayOfWeek: Int,
    startNode: Int?,
    endNode: Int?,
    weeks: List<Int>?,
    category: CourseCategory,
    maxNodeCount: Int = Int.MAX_VALUE,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
): List<CourseConflict> {
    if (startNode == null || endNode == null || weeks == null) return emptyList()
    if (startNode !in 1..maxNodeCount || endNode !in startNode..maxNodeCount) return emptyList()
    if (existingCourses.isEmpty()) return emptyList()
    val draft = CourseItem(
        id = DRAFT_COURSE_ID,
        title = "",
        weeks = weeks,
        category = category,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )
    return conflictsWithCourse(draft, existingCourses, maxWeekCount)
}

/** 节次区间文本。 */
internal fun describeNodeRange(range: IntRange): String =
    if (range.first == range.last) "第 ${range.first} 节" else "第 ${range.first}-${range.last} 节"

/**
 * 把周次列表压成连续区间文本，如 "3-6、9 周"。
 * 分段太多（单双周之类）时改成首尾加总数，避免提示被撑长。
 */
internal fun compactWeekRanges(weeks: List<Int>): String {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return ""
    val segments = mutableListOf<IntRange>()
    var start = sorted.first()
    var previous = start
    for (week in sorted.drop(1)) {
        if (week == previous + 1) {
            previous = week
            continue
        }
        segments += start..previous
        start = week
        previous = week
    }
    segments += start..previous
    if (segments.size > WEEK_SEGMENT_LIMIT) {
        return "${sorted.first()}-${sorted.last()} 周内共 ${sorted.size} 周"
    }
    return segments.joinToString("、") {
        if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}"
    } + " 周"
}

/** 冲突两侧的类别组合。 */
internal fun conflictKindLabel(kind: CourseConflictKind): String = when (kind) {
    CourseConflictKind.ExamVsExam -> "两场考试"
    CourseConflictKind.ExamVsCourse -> "考试与课程"
    CourseConflictKind.CourseVsCourse -> "两门课程"
}

/** 冲突发生在哪一天、哪几节、哪几周。 */
internal fun conflictScopeText(conflict: CourseConflict): String =
    "${weekdayLabel(conflict.dayOfWeek)} · ${describeNodeRange(conflict.overlappingNodes)} · " +
        compactWeekRanges(conflict.overlappingWeeks)

/** 课表管理页里一条冲突的标题，列出撞在一起的两门课。 */
internal fun conflictPairTitle(conflict: CourseConflict): String =
    "${conflict.first.title} × ${conflict.second.title}"

/**
 * 加课表单的冲突提示，没有冲突返回 null。
 * 只提示不拦截：学生可能有意排两个可选时段，最终由他自己决定要不要保存。
 */
internal fun addCourseConflictWarning(conflicts: List<CourseConflict>): String? {
    if (conflicts.isEmpty()) return null
    val preview = conflicts.take(CONFLICT_PREVIEW_LIMIT).joinToString("；") { conflict ->
        "${conflict.second.title}（${describeNodeRange(conflict.overlappingNodes)} · " +
            "${compactWeekRanges(conflict.overlappingWeeks)}）"
    }
    val rest = conflicts.size - CONFLICT_PREVIEW_LIMIT
    val tail = if (rest > 0) "等 ${conflicts.size} 门" else ""
    return "和已有课程时间重叠：$preview$tail。确认要这样排就直接保存。"
}

/** 考试倒计时文本。 */
internal fun examCountdownLabel(countdown: ExamCountdown): String = when (countdown.daysRemaining) {
    0L -> "就是今天"
    1L -> "还有 1 天"
    else -> "还有 ${countdown.daysRemaining} 天"
}
