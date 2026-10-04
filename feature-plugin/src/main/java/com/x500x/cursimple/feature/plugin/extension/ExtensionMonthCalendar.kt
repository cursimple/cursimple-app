package com.x500x.cursimple.feature.plugin.extension

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 星期一为第一列，末行补齐；跨月格子留空，不将别的月份事务混进当月。 */
internal fun extensionMonthCells(month: YearMonth): List<LocalDate?> {
    val prefix = month.atDay(1).dayOfWeek.value - 1
    val total = ((prefix + month.lengthOfMonth() + 6) / 7) * 7
    return List(total) { index ->
        (index - prefix + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }
}

internal fun extensionMonthItems(
    items: List<ExtensionFeedItem>,
    month: YearMonth,
    source: ExtensionScheduleDateSource,
    zone: ZoneId,
): Map<LocalDate, List<ExtensionFeedItem>> = items.mapNotNull { item ->
    val at = extensionItemAnchor(item, source) ?: return@mapNotNull null
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    if (YearMonth.from(day) == month) day to item else null
}.groupBy({ it.first }, { it.second }).mapValues { (_, list) ->
    list.sortedWith(compareBy<ExtensionFeedItem> { extensionItemAnchor(it, source) }.thenBy { it.id })
}
