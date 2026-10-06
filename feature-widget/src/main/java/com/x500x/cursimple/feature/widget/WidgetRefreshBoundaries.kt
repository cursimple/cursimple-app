package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Schedule class-state boundaries and a dedicated midnight refresh alongside the periodic
 * guard.
 */
internal fun widgetRefreshBoundaries(
    slots: List<ClassSlotTime>,
    now: LocalDateTime,
    leadMinutes: Long = 5,
    soonMinutes: Long = SOON_THRESHOLD_MINUTES,
    limit: Int = 8,
): List<LocalDateTime> {
    if (limit <= 0) return emptyList()
    val today = now.toLocalDate()
    val midnight = today.plusDays(1).atStartOfDay()
    if (slots.isEmpty()) return listOf(midnight)
    val boundaries = sortedSetOf<LocalDateTime>()
    for (dayOffset in -1L..1L) {
        val date = today.plusDays(dayOffset)
        for (slot in slots) {
            val start = slot.parseTime(slot.startTime) ?: continue
            val end = slot.parseTime(slot.endTime) ?: continue
            val startAt = date.atTime(start)
            val endAt = if (end.isAfter(start)) date.atTime(end) else date.plusDays(1).atTime(end)
            boundaries.add(startAt.minusMinutes(soonMinutes))
            boundaries.add(startAt.minusMinutes(leadMinutes))
            boundaries.add(startAt)
            boundaries.add(endAt)
        }
    }
    // Reserve midnight's own slot so dense class boundaries cannot displace it.
    val upcoming = boundaries.filter { it.isAfter(now) && it != midnight }.take(limit - 1)
    return (upcoming + midnight).sorted()
}

private fun ClassSlotTime.parseTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value) }.getOrNull()
