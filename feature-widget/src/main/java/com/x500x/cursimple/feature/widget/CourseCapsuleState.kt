package com.x500x.cursimple.feature.widget

import java.time.Duration
import java.time.LocalDateTime

internal data class CourseCapsuleState(
    val course: NextCourseRow,
    val inProgress: Boolean,
    val urgent: Boolean,
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
    return CourseCapsuleState(
        course = current,
        inProgress = current.isLive(),
        urgent = remaining != null && !remaining.isNegative && !remaining.isZero && remaining <= Duration.ofMinutes(10),
    )
}
