package com.x500x.cursimple.feature.plugin.extension

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.plugin.R
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun FeedOverview(items: List<ExtensionFeedItem>, today: LocalDate, zone: ZoneId) {
    val counts = remember(items, today, zone) {
        val pending = items.filter { !it.done && !it.isNotice() }
        Triple(
            pending.size,
            pending.count { item -> item.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true },
            items.count { it.isNotice() },
        )
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SettingsCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeedStat(count = counts.first, label = stringResource(R.string.extension_feed_stat_pending), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                FeedStat(
                    count = counts.second,
                    label = stringResource(R.string.extension_group_today),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                FeedStat(count = counts.third, label = stringResource(R.string.extension_feed_stat_notices), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeedStat(count: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun FeedTypeFilters(
    palette: FeedPalette,
    selectedType: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all") {
            AppFilterChip(
                selected = selectedType == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.plugin_log_filter_all_levels)) },
            )
        }
        items(palette.types, key = { "type-${it.id}" }) { type ->
            AppFilterChip(
                selected = selectedType == type.id,
                onClick = { onSelect(type.id) },
                label = { Text(type.label.ifBlank { type.id }) },
                leadingIcon = { FeedTypeDot(palette.colorOfType(type.id)) },
            )
        }
    }
}

@Composable
internal fun FeedTypeBadge(item: ExtensionFeedItem, palette: FeedPalette) {
    val label = palette.labelOf(item) ?: return
    Surface(shape = RoundedCornerShape(50), color = palette.colorOf(item).copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FeedTypeDot(palette.colorOf(item))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun FeedTypeDot(color: Color) {
    Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
}

@Composable
internal fun FeedStatus(item: ExtensionFeedItem, now: Long) {
    val overdue = !item.done && !item.isNotice() && item.dueAt?.let { it < now } == true
    val color = when {
        overdue -> MaterialTheme.colorScheme.error
        item.done -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.done || overdue) {
            Icon(
                imageVector = if (overdue) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircleOutline,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = if (!item.done && !item.isNotice() && item.dueAt != null) whenText(item, now).orEmpty() else stringResource(feedStatusRes(item)),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

internal fun feedStatusRes(item: ExtensionFeedItem): Int = when {
    item.isNotice() && item.done -> R.string.extension_item_read
    item.isNotice() -> R.string.extension_item_unread
    item.done -> R.string.extension_item_done
    else -> R.string.extension_item_pending
}

@Composable
internal fun FeedListCard(item: ExtensionFeedItem, now: Long, palette: FeedPalette, onClick: () -> Unit) {
    val muted = item.historical
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (muted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = feedCardBorder(item, now),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                FeedTypeBadge(item, palette)
                FeedStatus(item, now)
                if (muted) {
                    Text(
                        text = stringResource(R.string.extension_item_historical),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.done || muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val source = listOfNotNull(
                item.course.ifBlank { null },
                item.category.ifBlank { null }?.takeIf { it != palette.labelOf(item) },
            ).joinToString(" · ")
            if (source.isNotBlank()) {
                Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (item.summary.isNotBlank()) {
                Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.anchorAt?.let {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Text(feedDateTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                if (item.anchorAt == null) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun feedCardBorder(item: ExtensionFeedItem, now: Long): BorderStroke = BorderStroke(
    width = if (!item.done && !item.isNotice() && item.dueAt?.let { it < now } == true) 1.dp else 0.5.dp,
    color = if (!item.done && !item.isNotice() && item.dueAt?.let { it < now } == true) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    },
)

@Composable
internal fun feedDateTime(timestamp: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = BeijingTime.zone
    return remember(timestamp, locale, zone) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMdHm")
        Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

@Composable
internal fun whenText(item: ExtensionFeedItem, now: Long): String? {
    val due = item.dueAt ?: return item.anchorAt?.let { feedDateTime(it) }
    if (item.done) return feedDateTime(due)
    val diff = due - now
    val hours = kotlin.math.abs(diff) / 3_600_000L
    return when {
        diff >= 0 && hours < 1 -> stringResource(R.string.extension_when_within_hour)
        diff >= 0 && hours < 48 -> pluralStringResource(R.plurals.extension_when_hours_left, hours.toInt(), hours.toInt())
        diff >= 0 -> pluralStringResource(R.plurals.extension_when_days_left, (hours / 24).toInt(), (hours / 24).toInt())
        hours < 48 -> pluralStringResource(R.plurals.extension_when_hours_overdue, hours.toInt().coerceAtLeast(1), hours.toInt().coerceAtLeast(1))
        else -> pluralStringResource(R.plurals.extension_when_days_overdue, (hours / 24).toInt(), (hours / 24).toInt())
    }
}
