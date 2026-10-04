package com.x500x.cursimple.feature.plugin.extension

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json

/** 开关下的第二级页面；先编辑并预览，保存后一次性更新课表事务。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionScheduleSettingsScreen(
    data: ExtensionData,
    types: List<PluginFeedTypeSpec>,
    onSave: (ExtensionScheduleSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(stateSaver = Saver<ExtensionScheduleSettings, String>(
        save = { Json.encodeToString(ExtensionScheduleSettings.serializer(), it) },
        restore = { runCatching { Json.decodeFromString(ExtensionScheduleSettings.serializer(), it) }.getOrNull() },
    )) { mutableStateOf(data.host.schedule) }
    var showTime by remember { mutableStateOf(false) }
    val zone = BeijingTime.zone
    val now = BeijingTime.nowMillis(zone)
    val shownTypes = remember(types, data.items) {
        (types + data.items.map { PluginFeedTypeSpec(it.type, it.category.ifBlank { it.type }) }).distinctBy { it.id }
    }
    val preview = remember(draft, data.items, now) { extensionScheduleItems(data.copy(host = data.host.copy(addToSchedule = true, schedule = draft)), now, zone) }
    BackHandler(onBack = onBack)
    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.plugin_action_back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.extension_schedule_details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.extension_schedule_independent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.extension_schedule_preview_count, preview.size), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.extension_schedule_default_rules), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.extension_schedule_types))
                SettingsCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val taskTypes = shownTypes.map { it.id }.toSet() intersect typesByKind(shownTypes, "task")
                        val noticeTypes = shownTypes.map { it.id }.toSet() intersect typesByKind(shownTypes, "notice")
                        AppFilterChip(selected = draft.includedTypes == null, onClick = { draft = draft.copy(includedTypes = null) }, label = { Text(stringResource(R.string.extension_schedule_all)) })
                        if (taskTypes.isNotEmpty()) AppFilterChip(selected = draft.includedTypes == taskTypes, onClick = { draft = draft.copy(includedTypes = taskTypes) }, label = { Text(stringResource(R.string.extension_schedule_tasks)) })
                        if (noticeTypes.isNotEmpty()) AppFilterChip(selected = draft.includedTypes == noticeTypes, onClick = { draft = draft.copy(includedTypes = noticeTypes) }, label = { Text(stringResource(R.string.extension_schedule_notices)) })
                    }
                    Text(stringResource(R.string.extension_schedule_type_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    if (draft.includedTypes?.isEmpty() == true) Text(stringResource(R.string.extension_schedule_none), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
            shownTypes.forEach { type ->
                item(key = "rule-${type.id}") {
                    ScheduleTypeCard(
                        type = type,
                        checked = draft.includes(type.id),
                        rule = draft.ruleFor(type.id),
                        onChecked = { checked ->
                            val chosen = draft.includedTypes ?: shownTypes.map { it.id }.toSet()
                            draft = draft.copy(includedTypes = if (checked) chosen + type.id else chosen - type.id)
                        },
                        onRule = { rule -> draft = draft.copy(typeRules = draft.typeRules + (type.id to rule)) },
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.extension_schedule_appearance))
                SettingsCard {
                    Row(Modifier.fillMaxWidth().clickable { showTime = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.extension_schedule_time), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.extension_schedule_time_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(draft.defaultStartTime, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    ChoiceRow(title = stringResource(R.string.extension_schedule_duration), value = draft.durationMinutes, choices = listOf(15, 30, 45, 60, 90, 120), label = { stringResource(R.string.extension_schedule_minutes, it) }, onSelect = { draft = draft.copy(durationMinutes = it) })
                    SwitchRow(stringResource(R.string.extension_schedule_title_type), stringResource(R.string.extension_schedule_title_desc), draft.showTypeInTitle) { draft = draft.copy(showTypeInTitle = it) }
                    SwitchRow(stringResource(R.string.extension_schedule_colors), stringResource(R.string.extension_schedule_colors_desc), draft.useTypeColors) { draft = draft.copy(useTypeColors = it) }
                }
            }
            item {
                SectionTitle(stringResource(R.string.extension_schedule_visibility))
                SettingsCard {
                    SwitchRow(stringResource(R.string.extension_schedule_completed), stringResource(R.string.extension_schedule_completed_desc), draft.includeCompleted) { draft = draft.copy(includeCompleted = it) }
                    SwitchRow(stringResource(R.string.extension_schedule_read), stringResource(R.string.extension_schedule_read_desc), draft.includeReadNotices) { draft = draft.copy(includeReadNotices = it) }
                    ChoiceRow(title = stringResource(R.string.extension_schedule_history), value = draft.historyDays, choices = listOf(7, 30, 90, 0), label = { if (it == 0) stringResource(R.string.extension_schedule_history_all) else stringResource(R.string.extension_schedule_days, it) }, onSelect = { draft = draft.copy(historyDays = it) })
                    Text(stringResource(R.string.extension_schedule_sync_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                SectionTitle(stringResource(R.string.extension_schedule_preview))
                SettingsCard {
                    val samples = preview.take(4)
                    if (samples.isEmpty()) Text(stringResource(R.string.extension_schedule_preview_empty), style = MaterialTheme.typography.bodyMedium)
                    samples.forEachIndexed { index, (item, placement) ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        val color = shownTypes.firstOrNull { it.id == item.type }?.color?.let(::parseHexColor) ?: MaterialTheme.colorScheme.primary
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(color, CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${placement.date} · ${placement.startTime}–${placement.endTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(stringResource(R.string.extension_schedule_preview_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
        Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.extension_schedule_save), modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
    if (showTime) {
        val initial = runCatching { LocalTime.parse(draft.defaultStartTime) }.getOrDefault(LocalTime.of(9, 0))
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.extension_schedule_time)) },
            text = { TimeInput(state) },
            confirmButton = { TextButton(onClick = { draft = draft.copy(defaultStartTime = String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute)); showTime = false }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun ScheduleTypeCard(type: PluginFeedTypeSpec, checked: Boolean, rule: ExtensionScheduleTypeRule, onChecked: (Boolean) -> Unit, onRule: (ExtensionScheduleTypeRule) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val color = type.color?.let(::parseHexColor) ?: MaterialTheme.colorScheme.primary
    SettingsCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(type.label.ifBlank { stringResource(R.string.extension_schedule_other) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(if (checked) dateSourceLabel(rule.dateSource) else stringResource(R.string.extension_schedule_hidden), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onCheckedChange = onChecked)
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, stringResource(R.string.extension_schedule_expand))
            }
        }
        AnimatedVisibility(visible = expanded && checked) {
            Column {
                ChoiceRow(title = stringResource(R.string.extension_schedule_date_source), value = rule.dateSource, choices = ExtensionScheduleDateSource.entries, label = { dateSourceLabel(it) }, onSelect = { onRule(rule.copy(dateSource = it)) })
                Text(stringResource(R.string.extension_schedule_missing_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.extension_schedule_offset), style = MaterialTheme.typography.bodyLarge)
                        Text(when { rule.dayOffset < 0 -> stringResource(R.string.extension_schedule_days_before, -rule.dayOffset); rule.dayOffset > 0 -> stringResource(R.string.extension_schedule_days_after, rule.dayOffset); else -> stringResource(R.string.extension_schedule_same_day) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onRule(rule.copy(dayOffset = (rule.dayOffset - 1).coerceAtLeast(-30))) }, enabled = rule.dayOffset > -30) { Icon(Icons.Rounded.Remove, stringResource(R.string.extension_schedule_earlier)) }
                    Text(rule.dayOffset.toString(), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { onRule(rule.copy(dayOffset = (rule.dayOffset + 1).coerceAtMost(30))) }, enabled = rule.dayOffset < 30) { Icon(Icons.Rounded.Add, stringResource(R.string.extension_schedule_later)) }
                }
                ChoiceRow(title = stringResource(R.string.extension_schedule_time_mode), value = rule.timeMode, choices = ExtensionScheduleTimeMode.entries, label = { stringResource(if (it == ExtensionScheduleTimeMode.Source) R.string.extension_schedule_source_time else R.string.extension_schedule_fixed_time) }, onSelect = { onRule(rule.copy(timeMode = it)) })
            }
        }
    }
}

@Composable
internal fun dateSourceLabel(source: ExtensionScheduleDateSource): String = stringResource(when (source) {
    ExtensionScheduleDateSource.Automatic -> R.string.extension_schedule_auto_date
    ExtensionScheduleDateSource.Publish -> R.string.extension_schedule_publish_date
    ExtensionScheduleDateSource.Start -> R.string.extension_schedule_start_date
    ExtensionScheduleDateSource.Due -> R.string.extension_schedule_due_date
})
