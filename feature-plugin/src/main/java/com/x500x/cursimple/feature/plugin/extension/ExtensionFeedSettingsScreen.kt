package com.x500x.cursimple.feature.plugin.extension

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec
import com.x500x.cursimple.feature.plugin.R
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import kotlinx.serialization.json.Json

@Composable
internal fun ExtensionFeedSettingsScreen(
    data: ExtensionData,
    types: List<PluginFeedTypeSpec>,
    onSave: (ExtensionFeedSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(stateSaver = Saver<ExtensionFeedSettings, String>(
        save = { Json.encodeToString(ExtensionFeedSettings.serializer(), it) },
        restore = { runCatching { Json.decodeFromString(ExtensionFeedSettings.serializer(), it) }.getOrNull() },
    )) { mutableStateOf(data.host.feed) }
    val shownTypes = remember(types, data.items) {
        (types + data.items.map { PluginFeedTypeSpec(it.type, it.category.ifBlank { it.type }) }).distinctBy { it.id }
    }
    val count = extensionFeedItems(data.copy(host = data.host.copy(feed = draft)), BeijingTime.nowMillis(BeijingTime.zone), BeijingTime.zone).size
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.plugin_action_back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.extension_feed_settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.extension_feed_settings_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.extension_feed_settings_count, count), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.extension_feed_settings_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.extension_schedule_types))
                SettingsCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tasks = shownTypes.map { it.id }.toSet() intersect typesByKind(shownTypes, "task")
                        val announcements = shownTypes.map { it.id }.toSet() intersect typesByKind(shownTypes, "notice")
                        AppFilterChip(selected = draft.includedTypes == null, onClick = { draft = draft.copy(includedTypes = null) }, label = { Text(stringResource(R.string.extension_schedule_all)) })
                        if (tasks.isNotEmpty()) AppFilterChip(selected = draft.includedTypes == tasks, onClick = { draft = draft.copy(includedTypes = tasks) }, label = { Text(stringResource(R.string.extension_schedule_tasks)) })
                        if (announcements.isNotEmpty()) AppFilterChip(selected = draft.includedTypes == announcements, onClick = { draft = draft.copy(includedTypes = announcements) }, label = { Text(stringResource(R.string.extension_schedule_notices)) })
                    }
                    shownTypes.forEach { type ->
                        SwitchRow(type.label.ifBlank { stringResource(R.string.extension_schedule_other) }, "", draft.includes(type.id)) { checked ->
                            val chosen = draft.includedTypes ?: shownTypes.map { it.id }.toSet()
                            draft = draft.copy(includedTypes = if (checked) chosen + type.id else chosen - type.id)
                        }
                    }
                    if (draft.includedTypes?.isEmpty() == true) Text(stringResource(R.string.extension_feed_settings_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                SectionTitle(stringResource(R.string.extension_feed_settings_layout))
                SettingsCard {
                    Text(
                        stringResource(R.string.extension_feed_mode_month),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.extension_feed_settings_month_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceRow(stringResource(R.string.extension_feed_settings_date), draft.dateSource, ExtensionScheduleDateSource.entries, label = { dateSourceLabel(it) }, onSelect = { draft = draft.copy(dateSource = it) })
                    Text(stringResource(R.string.extension_feed_settings_date_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                SectionTitle(stringResource(R.string.extension_schedule_visibility))
                SettingsCard {
                    SwitchRow(stringResource(R.string.extension_schedule_completed), stringResource(R.string.extension_feed_settings_completed_desc), draft.includeCompleted) { draft = draft.copy(includeCompleted = it) }
                    SwitchRow(stringResource(R.string.extension_schedule_read), stringResource(R.string.extension_feed_settings_read_desc), draft.includeReadNotices) { draft = draft.copy(includeReadNotices = it) }
                    ChoiceRow(stringResource(R.string.extension_schedule_history), draft.historyDays, listOf(7, 30, 90, 0), label = { if (it == 0) stringResource(R.string.extension_schedule_history_all) else stringResource(R.string.extension_schedule_days, it) }, onSelect = { draft = draft.copy(historyDays = it) })
                    Text(stringResource(R.string.extension_feed_settings_history_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.extension_feed_settings_save), modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}
