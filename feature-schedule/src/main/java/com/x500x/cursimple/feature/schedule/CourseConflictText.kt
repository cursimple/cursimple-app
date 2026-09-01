package com.x500x.cursimple.feature.schedule

import android.content.Context
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseConflict
import com.x500x.cursimple.core.kernel.model.CourseConflictKind
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.core.kernel.model.ExamCountdown
import com.x500x.cursimple.core.kernel.model.conflictsWithCourse
import com.x500x.cursimple.core.kernel.model.weekdayNameRes

/** 表单里还没入库的候选课程用的临时 id，不会和任何已保存的课程相同。 */
internal const val DRAFT_COURSE_ID = "draft-course"

/** 加课表单里最多列出几门冲突课程，其余折成总数。 */
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

/** 节次区间的两种写法。 */
internal sealed interface NodeRangeLabel {
    /** 只占一节。 */
    data class Single(val node: Int) : NodeRangeLabel

    /** 跨了连续的若干节。 */
    data class Range(val startNode: Int, val endNode: Int) : NodeRangeLabel
}

/** 节次区间。 */
internal fun describeNodeRange(range: IntRange): NodeRangeLabel =
    if (range.first == range.last) {
        NodeRangeLabel.Single(range.first)
    } else {
        NodeRangeLabel.Range(range.first, range.last)
    }

internal fun Context.nodeRangeText(label: NodeRangeLabel): String = when (label) {
    is NodeRangeLabel.Single -> getString(R.string.schedule_conflict_node_single, label.node)
    is NodeRangeLabel.Range ->
        getString(R.string.schedule_conflict_node_range, label.startNode, label.endNode)
}

/** 周次列表压缩后的形态。 */
internal sealed interface WeekRangesLabel {
    /** 没有周次可说。 */
    data object Empty : WeekRangesLabel

    /** 逐段列出的连续周次区间。 */
    data class Segments(val segments: List<IntRange>) : WeekRangesLabel

    /** 分段过多，只给首尾周次和总周数。 */
    data class Summary(val firstWeek: Int, val lastWeek: Int, val weekCount: Int) : WeekRangesLabel
}

/**
 * 把周次列表压成连续区间。
 * 分段太多（单双周之类）时改成首尾加总数，避免提示被撑长。
 */
internal fun compactWeekRanges(weeks: List<Int>): WeekRangesLabel {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return WeekRangesLabel.Empty
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
        return WeekRangesLabel.Summary(
            firstWeek = sorted.first(),
            lastWeek = sorted.last(),
            weekCount = sorted.size,
        )
    }
    return WeekRangesLabel.Segments(segments)
}

internal fun Context.weekRangesText(label: WeekRangesLabel): String = when (label) {
    WeekRangesLabel.Empty -> ""
    is WeekRangesLabel.Segments -> {
        val separator = getString(R.string.schedule_conflict_week_segment_separator)
        val joined = label.segments.joinToString(separator) { segment ->
            if (segment.first == segment.last) {
                getString(R.string.schedule_conflict_week_segment_single, segment.first)
            } else {
                getString(R.string.schedule_conflict_week_segment_range, segment.first, segment.last)
            }
        }
        getString(R.string.schedule_conflict_week_segments, joined)
    }

    is WeekRangesLabel.Summary -> getString(
        R.string.schedule_conflict_week_summary,
        label.firstWeek,
        label.lastWeek,
        label.weekCount,
    )
}

/** 冲突两侧的类别组合对应的文案资源 id。 */
internal fun conflictKindNameRes(kind: CourseConflictKind): Int = when (kind) {
    CourseConflictKind.ExamVsExam -> R.string.schedule_conflict_kind_exam_vs_exam
    CourseConflictKind.ExamVsCourse -> R.string.schedule_conflict_kind_exam_vs_course
    CourseConflictKind.CourseVsCourse -> R.string.schedule_conflict_kind_course_vs_course
}

/** 冲突发生在哪一天、哪几节、哪几周。 */
internal data class ConflictScope(
    val dayOfWeek: Int,
    val nodes: NodeRangeLabel,
    val weeks: WeekRangesLabel,
)

internal fun conflictScope(conflict: CourseConflict): ConflictScope = ConflictScope(
    dayOfWeek = conflict.dayOfWeek,
    nodes = describeNodeRange(conflict.overlappingNodes),
    weeks = compactWeekRanges(conflict.overlappingWeeks),
)

internal fun Context.conflictScopeText(scope: ConflictScope): String = getString(
    R.string.schedule_conflict_scope,
    getString(weekdayNameRes(scope.dayOfWeek)),
    nodeRangeText(scope.nodes),
    weekRangesText(scope.weeks),
)

/** 课表管理页里一条冲突涉及的两门课。 */
internal data class ConflictPair(
    val firstTitle: String,
    val secondTitle: String,
)

internal fun conflictPairTitle(conflict: CourseConflict): ConflictPair = ConflictPair(
    firstTitle = conflict.first.title,
    secondTitle = conflict.second.title,
)

internal fun Context.conflictPairTitleText(pair: ConflictPair): String =
    getString(R.string.schedule_conflict_pair_title, pair.firstTitle, pair.secondTitle)

/** 提示里列出的一门撞课课程。 */
internal data class ConflictPreviewItem(
    val title: String,
    val nodes: NodeRangeLabel,
    val weeks: WeekRangesLabel,
)

/** 加课表单的冲突提示内容，[previewed] 最多列出 3 门，[totalCount] 是冲突总数。 */
internal data class AddCourseConflictWarning(
    val previewed: List<ConflictPreviewItem>,
    val totalCount: Int,
)

/**
 * 加课表单的冲突提示，没有冲突返回 null。
 * 只提示不拦截：学生可能有意排两个可选时段，最终由他自己决定要不要保存。
 */
internal fun addCourseConflictWarning(conflicts: List<CourseConflict>): AddCourseConflictWarning? {
    if (conflicts.isEmpty()) return null
    return AddCourseConflictWarning(
        previewed = conflicts.take(CONFLICT_PREVIEW_LIMIT).map { conflict ->
            ConflictPreviewItem(
                title = conflict.second.title,
                nodes = describeNodeRange(conflict.overlappingNodes),
                weeks = compactWeekRanges(conflict.overlappingWeeks),
            )
        },
        totalCount = conflicts.size,
    )
}

internal fun Context.addCourseConflictWarningText(warning: AddCourseConflictWarning): String {
    val separator = getString(R.string.schedule_conflict_warning_separator)
    val preview = warning.previewed.joinToString(separator) { item ->
        getString(
            R.string.schedule_conflict_warning_item,
            item.title,
            nodeRangeText(item.nodes),
            weekRangesText(item.weeks),
        )
    }
    val tail = if (warning.totalCount > warning.previewed.size) {
        getString(R.string.schedule_conflict_warning_overflow, warning.totalCount)
    } else {
        ""
    }
    return getString(R.string.schedule_conflict_warning, preview + tail)
}

/** 距离考试还有多久。 */
internal sealed interface ExamCountdownLabel {
    /** 考试就在今天。 */
    data object Today : ExamCountdownLabel

    /** 考试在明天。 */
    data object Tomorrow : ExamCountdownLabel

    /** 离考试还有多于一天。 */
    data class DaysRemaining(val days: Long) : ExamCountdownLabel
}

/** 考试倒计时。 */
internal fun examCountdownLabel(countdown: ExamCountdown): ExamCountdownLabel =
    when (countdown.daysRemaining) {
        0L -> ExamCountdownLabel.Today
        1L -> ExamCountdownLabel.Tomorrow
        else -> ExamCountdownLabel.DaysRemaining(countdown.daysRemaining)
    }

internal fun Context.examCountdownText(label: ExamCountdownLabel): String = when (label) {
    ExamCountdownLabel.Today -> getString(R.string.schedule_exam_countdown_today)
    ExamCountdownLabel.Tomorrow -> getString(R.string.schedule_exam_countdown_tomorrow)
    is ExamCountdownLabel.DaysRemaining ->
        getString(R.string.schedule_exam_countdown_days, label.days)
}
