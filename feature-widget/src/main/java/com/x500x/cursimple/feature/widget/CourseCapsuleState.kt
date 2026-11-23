package com.x500x.cursimple.feature.widget

import java.time.Duration
import java.time.LocalDateTime

internal const val CAPSULE_WARNING_MINUTES = 10L

internal data class CourseCapsuleState(
    val course: NextCourseRow,
    val inProgress: Boolean,
    val urgent: Boolean,
    val elapsedMinutes: Long = 0,
    val remainingMinutes: Long = 0,
)

internal fun resolveCourseCapsuleState(rows: List<NextCourseRow>, now: LocalDateTime): CourseCapsuleState? {
    val available = rows.filterNot { it.isPast || it.endAt?.let { end -> !end.isAfter(now) } == true }
    fun NextCourseRow.isLive(): Boolean =
        startAt?.let { !now.isBefore(it) } == true && endAt?.let { now.isBefore(it) } == true

    val current = available.filter { it.isLive() }.minByOrNull { it.startAt!! }
        ?: available.filter { it.startAt?.isAfter(now) == true }.minByOrNull { it.startAt!! }
        ?: available.firstOrNull { it.startAt == null && it.isFocus }
        ?: available.firstOrNull { it.startAt == null }
        ?: return null
    val remaining = current.startAt?.let { Duration.between(now, it) }
    val elapsed = current.startAt?.let { if (current.isLive()) Duration.between(it, now).toMinutes() else 0 } ?: 0
    val remainingMinutes = remaining?.takeIf { !it.isNegative && !it.isZero }?.let {
        it.toMinutes() + if (it == Duration.ofMinutes(it.toMinutes())) 0 else 1
    } ?: 0L
    return CourseCapsuleState(
        course = current,
        inProgress = current.isLive(),
        urgent = remainingMinutes in 1..CAPSULE_WARNING_MINUTES,
        elapsedMinutes = elapsed,
        remainingMinutes = remainingMinutes,
    )
}
