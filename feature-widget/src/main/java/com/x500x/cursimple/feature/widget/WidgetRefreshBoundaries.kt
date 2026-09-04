package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 小组件需要重画的时刻。
 *
 * 固定周期的守护链最坏会让上课状态滞后一整个周期，因此额外按节次边界排点：
 * 每节课的课前提前量、开始与结束各刷一次，状态切换的那一刻就能对上。
 */
internal fun widgetRefreshBoundaries(
    slots: List<ClassSlotTime>,
    now: LocalDateTime,
    leadMinutes: Long = 5,
    limit: Int = 6,
): List<LocalDateTime> {
    if (slots.isEmpty() || limit <= 0) return emptyList()
    val today = now.toLocalDate()
    val boundaries = sortedSetOf<LocalDateTime>()
    // 跨过午夜的节次要看昨天那一份，次日的边界则供当天最后一节之后使用
    for (dayOffset in -1L..1L) {
        val date = today.plusDays(dayOffset)
        for (slot in slots) {
            val start = slot.parseTime(slot.startTime) ?: continue
            val end = slot.parseTime(slot.endTime) ?: continue
            val startAt = date.atTime(start)
            // 结束不晚于开始说明这一节跨了午夜
            val endAt = if (end.isAfter(start)) date.atTime(end) else date.plusDays(1).atTime(end)
            boundaries.add(startAt.minusMinutes(leadMinutes))
            boundaries.add(startAt)
            boundaries.add(endAt)
        }
    }
    return boundaries.filter { it.isAfter(now) }.take(limit)
}

private fun ClassSlotTime.parseTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value) }.getOrNull()
