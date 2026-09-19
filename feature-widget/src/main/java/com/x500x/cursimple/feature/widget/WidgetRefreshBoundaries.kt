package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 小组件需要重画的时刻。
 *
 * 固定周期的守护链最坏会让上课状态滞后一整个周期，因此额外按节次边界排点：
 * 每节课转入即将开始、课前提前量、开始与结束各刷一次，状态切换的那一刻就能对上。
 *
 * 零点这一刻永远排：跨过它「今天」就换了一天，翻页偏移也要在这时作废。
 * 少了它，一早起来看到的还是昨天那一页，得等周期刷新或第一节课边界才回到当天。
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
    // 跨过午夜的节次要看昨天那一份，次日的边界则供当天最后一节之后使用
    for (dayOffset in -1L..1L) {
        val date = today.plusDays(dayOffset)
        for (slot in slots) {
            val start = slot.parseTime(slot.startTime) ?: continue
            val end = slot.parseTime(slot.endTime) ?: continue
            val startAt = date.atTime(start)
            // 结束不晚于开始说明这一节跨了午夜
            val endAt = if (end.isAfter(start)) date.atTime(end) else date.plusDays(1).atTime(end)
            boundaries.add(startAt.minusMinutes(soonMinutes))
            boundaries.add(startAt.minusMinutes(leadMinutes))
            boundaries.add(startAt)
            boundaries.add(endAt)
        }
    }
    // 零点单独占一个槽位，不跟节次边界抢：节次再密也不能把「换天」这一刷挤掉
    val upcoming = boundaries.filter { it.isAfter(now) && it != midnight }.take(limit - 1)
    return (upcoming + midnight).sorted()
}

private fun ClassSlotTime.parseTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value) }.getOrNull()
