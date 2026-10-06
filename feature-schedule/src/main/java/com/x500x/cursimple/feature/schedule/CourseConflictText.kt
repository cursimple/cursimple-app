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

internal const val DRAFT_COURSE_ID = "draft-course"

private const val CONFLICT_PREVIEW_LIMIT = 3

/** Summarize excessive week segments by endpoints and count. */
private const val WEEK_SEGMENT_LIMIT = 4

/** Wait for complete period and week input before computing form conflicts. */
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

internal sealed interface NodeRangeLabel {
    /** One period only. */
    data class Single(val node: Int) : NodeRangeLabel

    data class Range(val startNode: Int, val endNode: Int) : NodeRangeLabel
}

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

internal sealed interface WeekRangesLabel {
    data object Empty : WeekRangesLabel

    data class Segments(val segments: List<IntRange>) : WeekRangesLabel

    /** Summarize endpoints and total when there are too many segments. */
    data class Summary(val firstWeek: Int, val lastWeek: Int, val weekCount: Int) : WeekRangesLabel
}

/** Compress consecutive weeks; summarize sparse ranges to limit message length. */
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

    is WeekRangesLabel.Summary -> resources.getQuantityString(
        R.plurals.schedule_conflict_week_summary,
        label.weekCount,
        label.firstWeek,
        label.lastWeek,
        label.weekCount,
    )
}

internal fun conflictKindNameRes(kind: CourseConflictKind): Int = when (kind) {
    CourseConflictKind.ExamVsExam -> R.string.schedule_conflict_kind_exam_vs_exam
    CourseConflictKind.ExamVsCourse -> R.string.schedule_conflict_kind_exam_vs_course
    CourseConflictKind.CourseVsCourse -> R.string.schedule_conflict_kind_course_vs_course
}

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

internal data class ConflictPair(
    val firstTitle: String,
    val secondTitle: String,
)

internal fun conflictPairTitle(conflict: CourseConflict): ConflictPair = ConflictPair(
    firstTitle = conflict.first.title,
    secondTitle = conflict.second.title,
)

internal data class ConflictPreviewItem(
    val title: String,
    val nodes: NodeRangeLabel,
    val weeks: WeekRangesLabel,
)

/** Preview up to three conflicts while retaining [totalCount]. */
internal data class AddCourseConflictWarning(
    val previewed: List<ConflictPreviewItem>,
    val totalCount: Int,
)

/** Return null without conflicts; warnings never prevent intentional overlapping schedules. */
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

internal sealed interface ExamCountdownLabel {
    data object Today : ExamCountdownLabel

    data object Tomorrow : ExamCountdownLabel

    data class DaysRemaining(val days: Long) : ExamCountdownLabel
}

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
        resources.getQuantityString(R.plurals.schedule_exam_countdown_days, label.days.toInt(), label.days)
}
