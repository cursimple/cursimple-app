package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 设置页预览与课表桥共用同一个计算，设置显示的日期就是最终课表上的日期。 */
data class ExtensionSchedulePlacement(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

/** 用户所选的日期来源不存在时依次回退；原始发布日期、开考时间和截止时间不变。 */
fun extensionItemAnchor(item: ExtensionFeedItem, source: ExtensionScheduleDateSource): Long? {
    // 通知落在发布那天；任务有开始时间（如开考）就落在开始那天，否则落在截止那天。
    // 宿主只看组件给了哪些时间，不区分具体是哪种任务
    val automatic = if (item.isNotice()) item.publishAt ?: item.startAt ?: item.dueAt
    else item.startAt ?: item.dueAt ?: item.publishAt
    return when (source) {
        ExtensionScheduleDateSource.Automatic -> automatic
        ExtensionScheduleDateSource.Publish -> item.publishAt
        ExtensionScheduleDateSource.Start -> item.startAt
        ExtensionScheduleDateSource.Due -> item.dueAt
    } ?: automatic ?: item.firstSeenAt.takeIf { it > 0 }
}

fun extensionFeedItems(data: ExtensionData, now: Long, zone: ZoneId): List<ExtensionFeedItem> {
    val settings = data.host.feed
    val oldest = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(settings.historyDays.coerceAtLeast(0).toLong())
    return data.items.filter { item ->
        settings.includes(item.type) &&
            (!item.done || if (item.isNotice()) settings.includeReadNotices else settings.includeCompleted) &&
            (settings.historyDays <= 0 || extensionItemAnchor(item, settings.dateSource)?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate() >= oldest
            } == true)
    }
}

/**
 * 这条内容算「通知」还是「任务」。
 *
 * 只认组件声明的 [ExtensionFeedItem.kind]（条目上或清单里），宿主不认任何具体类型名。
 * 没声明时按通用规则：有截止或开始时间的是任务，只有发布时间的是通知。
 */
fun ExtensionFeedItem.isNotice(): Boolean = when (kind?.lowercase()) {
    "notice" -> true
    "task" -> false
    else -> dueAt == null && startAt == null
}

/** 清单里声明了类型语义时把它盖到条目上；组件没声明就保持原样，由 [isNotice] 兜底推断。 */
internal fun applyDeclaredKinds(items: List<ExtensionFeedItem>, manifest: PluginManifest): List<ExtensionFeedItem> {
    val declared = declaredKinds(manifest.extension?.feedTypes.orEmpty())
    if (declared.isEmpty()) return items
    return items.map { item -> item.kind?.let { item } ?: declared[item.type]?.let { item.copy(kind = it) } ?: item }
}

/** 类型 → 语义，只取清单里声明的 `kind`；没声明的类型交给 [isNotice] 按有无时间判断 */
internal fun declaredKinds(types: List<PluginFeedTypeSpec>): Map<String, String> = types.mapNotNull { type ->
    type.kind?.lowercase()?.takeIf { it == "task" || it == "notice" }?.let { type.id to it }
}.toMap()

/** 这套清单里，哪些类型算「任务」、哪些算「通知」，供设置页分组用 */
internal fun typesByKind(types: List<PluginFeedTypeSpec>, kind: String): Set<String> =
    declaredKinds(types).filterValues { it == kind }.keys

fun placeExtensionItem(
    item: ExtensionFeedItem,
    settings: ExtensionScheduleSettings,
    zone: ZoneId,
): ExtensionSchedulePlacement? {
    val rule = settings.ruleFor(item.type)
    val source = extensionItemAnchor(item, rule.dateSource) ?: return null
    val anchor = Instant.ofEpochMilli(source).atZone(zone)
    val date = anchor.toLocalDate().plusDays(rule.dayOffset.coerceIn(-30, 30).toLong())
    val duration = settings.durationMinutes.coerceIn(5, 180)
    val fixed = runCatching { LocalTime.parse(settings.defaultStartTime) }.getOrDefault(LocalTime.of(9, 0))
    val lastMinute = 23 * 60 + 59
    fun minute(time: LocalTime) = time.hour * 60 + time.minute
    val start = item.startAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val due = item.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val useSource = rule.timeMode == ExtensionScheduleTimeMode.Source && !item.isNotice()
    val selectedDate = anchor.toLocalDate()
    val (from, until) = when {
        useSource && start != null && due != null && start.toLocalDate() == selectedDate &&
            due.toLocalDate() == selectedDate && minute(due.toLocalTime()) > minute(start.toLocalTime()) ->
            minute(start.toLocalTime()) to minute(due.toLocalTime())
        useSource && source == item.dueAt -> {
            val end = minute(anchor.toLocalTime())
            if (end > 0) (end - duration).coerceAtLeast(0) to end
            else 0 to duration
        }
        useSource && source == item.startAt -> {
            val begin = minute(anchor.toLocalTime()).coerceAtMost(lastMinute - 1)
            begin to (begin + duration).coerceAtMost(lastMinute)
        }
        else -> {
            val begin = minute(fixed).coerceAtMost(lastMinute - 1)
            begin to (begin + duration).coerceAtMost(lastMinute)
        }
    }
    return ExtensionSchedulePlacement(date, LocalTime.of(from / 60, from % 60), LocalTime.of(until / 60, until % 60))
}

fun extensionScheduleItems(data: ExtensionData, now: Long, zone: ZoneId): List<Pair<ExtensionFeedItem, ExtensionSchedulePlacement>> {
    if (!data.host.addToSchedule) return emptyList()
    val settings = data.host.schedule
    val oldest = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(settings.historyDays.coerceAtLeast(0).toLong())
    return data.items.mapNotNull { item ->
        if (!settings.includes(item.type)) return@mapNotNull null
        if (item.done && !(if (item.isNotice()) settings.includeReadNotices else settings.includeCompleted)) return@mapNotNull null
        val placement = placeExtensionItem(item, settings, zone) ?: return@mapNotNull null
        if (settings.historyDays > 0 && placement.date < oldest) return@mapNotNull null
        item to placement
    }.sortedWith(compareBy({ it.second.date }, { it.second.startTime }, { it.first.id }))
}
