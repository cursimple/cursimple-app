package com.x500x.cursimple.app.extension

import com.x500x.cursimple.core.data.event.ScheduleEventRepository
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.ScheduleEventSource
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec
import com.x500x.cursimple.feature.plugin.extension.ExtensionData
import com.x500x.cursimple.feature.plugin.extension.ExtensionFeedItem
import com.x500x.cursimple.feature.plugin.extension.extensionScheduleItems
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Stable event IDs reconcile component category and date rules without duplicates. */
object ExtensionScheduleBridge {
    suspend fun apply(
        events: ScheduleEventRepository,
        pluginId: String,
        data: ExtensionData?,
        now: Long,
        feedTypes: List<PluginFeedTypeSpec> = emptyList(),
    ) {
        val prefix = eventPrefix(pluginId)
        val existing = events.eventsFlow.first().filter {
            it.source?.let { source -> source.componentId == pluginId } ?: it.id.startsWith(prefix)
        }.associateBy { it.id }
        val wanted = data?.let { desiredEvents(pluginId, it, now, BeijingTime.zone, feedTypes) }.orEmpty().associateBy { it.id }
        existing.keys.filterNot { it in wanted }.forEach { events.remove(it) }
        wanted.values.filter { existing[it.id] != it }.forEach { events.upsert(it) }
    }

    fun eventPrefix(pluginId: String): String = "ext-${sanitize(pluginId)}-"

    fun findSource(event: ScheduleEvent, data: ExtensionData): ExtensionFeedItem? {
        val source = event.source
        if (source != null) {
            if (source.componentId != data.pluginId) return null
            return data.items.firstOrNull { it.id == source.itemId }
        }
        return data.items.firstOrNull { event.id == eventPrefix(data.pluginId) + sanitize(it.id) }
    }

    internal fun desiredEvents(
        pluginId: String,
        data: ExtensionData,
        now: Long,
        zone: ZoneId,
        feedTypes: List<PluginFeedTypeSpec> = emptyList(),
    ): List<ScheduleEvent> {
        val settings = data.host.schedule
        val types = feedTypes.associateBy { it.id }
        return extensionScheduleItems(data, now, zone).mapNotNull { (item, placement) ->
            val type = types[item.type]
            val label = type?.label ?: item.category.ifBlank { item.type }
            val title = if (settings.showTypeInTitle) listOf(label, item.title).filter(String::isNotBlank).joinToString("：")
                else item.title
            ScheduleEvent(
                id = eventPrefix(pluginId) + sanitize(item.id),
                title = title.take(120),
                date = placement.date.toString(),
                startTime = placement.startTime.format(CLOCK),
                endTime = placement.endTime.format(CLOCK),
                location = item.course,
                note = listOf(item.author, item.content.ifBlank { item.summary }).filter(String::isNotBlank).joinToString("\n"),
                colorArgb = if (settings.useTypeColors) parseColor(type?.color) else null,
                source = ScheduleEventSource(pluginId, item.id),
            ).takeIf { it.isValid }
        }
    }

    internal fun parseColor(raw: String?): Long? = raw?.removePrefix("#")?.takeIf {
        it.length == 6 && it.all { char -> char.isDigit() || char.lowercaseChar() in 'a'..'f' }
    }?.toLongOrNull(16)?.let { 0xFF000000L or it }

    private fun sanitize(raw: String): String = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
}
