package com.x500x.cursimple.feature.schedule

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_COUNT
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_RING_DURATION_SECONDS
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.EditableAppAlarmSettings
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.reminderNotificationMessageText
import com.x500x.cursimple.core.reminder.model.reminderNotificationTitleText
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.model.isLegacy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleSettingsRoute(
    viewModel: ScheduleViewModel,
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onAlarmRingtoneUriChange: (String?) -> Unit,
    onAlarmAlertModeChange: (AlarmAlertMode) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleSettingsScreen(
        state = state,
        alarmRingtoneUri = alarmRingtoneUri,
        alarmAlertMode = alarmAlertMode,
        alarmRingDurationSeconds = alarmRingDurationSeconds,
        alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
        alarmRepeatCount = alarmRepeatCount,
        onAlarmRingtoneUriChange = onAlarmRingtoneUriChange,
        onAlarmAlertModeChange = onAlarmAlertModeChange,
        onAlarmRingDurationSecondsChange = onAlarmRingDurationSecondsChange,
        onAlarmRepeatIntervalSecondsChange = onAlarmRepeatIntervalSecondsChange,
        onAlarmRepeatCountChange = onAlarmRepeatCountChange,
        onPickSystemRingtone = onPickSystemRingtone,
        onPickLocalAudio = onPickLocalAudio,
        onSaveRule = viewModel::saveLabelReminderRule,
        onSetRuleEnabled = viewModel::setReminderRuleEnabled,
        onRemoveRule = viewModel::removeReminderRule,
        onSavePlaceholder = viewModel::savePlaceholderCourse,
        onDeletePlaceholder = viewModel::deletePlaceholderCourse,
        onSaveExamReminder = viewModel::saveExamReminder,
        onRefreshAlarms = viewModel::refreshReminderAlarmsNow,
        onDeleteAlarm = viewModel::removeAlarmRecord,
        onSetAppAlarmEnabled = viewModel::setAppAlarmEnabled,
        onUpdateAppAlarm = viewModel::updateAppAlarmSettings,
        onCreateManualAlarm = viewModel::createManualAppAlarm,
        modifier = modifier,
    )
}

@Composable
fun ScheduleSettingsScreen(
    state: ScheduleUiState,
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onAlarmRingtoneUriChange: (String?) -> Unit,
    onAlarmAlertModeChange: (AlarmAlertMode) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    onSaveRule: (String?, String, Boolean, Int, String?, List<ReminderLabelCondition>, List<ReminderLabelAction>) -> Unit,
    onSetRuleEnabled: (String, Boolean) -> Unit,
    onRemoveRule: (String) -> Unit,
    onSavePlaceholder: (String?, String, String, String, List<Int>, List<Int>, String?) -> Unit,
    onDeletePlaceholder: (String) -> Unit,
    onSaveExamReminder: (Boolean, Int, String?) -> Unit,
    onRefreshAlarms: () -> Unit,
    onDeleteAlarm: (String, ReminderAlarmBackend) -> Unit,
    onSetAppAlarmEnabled: (String, Boolean) -> Unit,
    onUpdateAppAlarm: (String, EditableAppAlarmSettings) -> Unit,
    onCreateManualAlarm: (Long, String, String, EditableAppAlarmSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingRule by remember { mutableStateOf<ReminderRule?>(null) }
    var showRuleEditor by rememberSaveable { mutableStateOf(false) }
    var showPlaceholderDialog by rememberSaveable { mutableStateOf(false) }
    var editingPlaceholder by remember { mutableStateOf<PlaceholderCourseGroup?>(null) }
    var editingAlarm by remember { mutableStateOf<SystemAlarmRecord?>(null) }
    var showManualAlarmDialog by rememberSaveable { mutableStateOf(false) }
    val slotLabels = remember(state.timingProfile, state.manualCourses) {
        (state.timingProfile?.slotTimes.orEmpty().map { it.label } +
            state.manualCourses.mapNotNull { it.slotLabelOverride })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val placeholderCourses = remember(state.manualCourses) {
        state.manualCourses
            .filter { it.reminderOnly }
            .groupBy { it.id.placeholderGroupId() }
            .values
            .map { PlaceholderCourseGroup(it.sortedBy { course -> course.time.dayOfWeek }) }
            .sortedWith(
                compareBy<PlaceholderCourseGroup>(
                    { it.representative.slotLabelOverride ?: it.representative.title },
                    { it.representative.reminderStartTime.orEmpty() },
                ),
            )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AlarmManagementCard(
                alarmRecords = state.systemAlarmRecords,
                onRefresh = onRefreshAlarms,
                onCreate = { showManualAlarmDialog = true },
                onEdit = { editingAlarm = it },
                onDelete = { onDeleteAlarm(it.alarmKey, it.backend) },
                onSetAppAlarmEnabled = onSetAppAlarmEnabled,
            )

            SectionHeader(stringResource(R.string.schedule_section_rules_title), stringResource(R.string.schedule_section_rules_subtitle))
            RuleManagementCard(
                rules = state.reminderRules.filter { it.scopeType == ReminderScopeType.LabelRule },
                slotLabels = slotLabels,
                placeholders = placeholderCourses,
                onAddRule = {
                    editingRule = null
                    showRuleEditor = true
                },
                onEditRule = {
                    editingRule = it
                    showRuleEditor = true
                },
                onSetRuleEnabled = onSetRuleEnabled,
                onRemoveRule = onRemoveRule,
                onAddPlaceholder = {
                    editingPlaceholder = null
                    showPlaceholderDialog = true
                },
                onEditPlaceholder = {
                    editingPlaceholder = it
                    showPlaceholderDialog = true
                },
                onDeletePlaceholder = onDeletePlaceholder,
            )

            CourseReminderCard(
                rules = state.reminderRules.filter { it.isCourseReminderRule() },
                onSetRuleEnabled = onSetRuleEnabled,
                onRemoveRule = onRemoveRule,
            )

            LegacyReminderRuleCard(
                rules = legacyReminderRules(state.reminderRules),
                onRemoveRule = onRemoveRule,
            )

            ReminderDefaultsCard(
                        alarmRingtoneUri = alarmRingtoneUri,
                alarmAlertMode = alarmAlertMode,
                alarmRingDurationSeconds = alarmRingDurationSeconds,
                alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
                alarmRepeatCount = alarmRepeatCount,
                onUseDefaultRingtone = { onAlarmRingtoneUriChange(null) },
                onPickSystemRingtone = {
                    onPickSystemRingtone { uri ->
                        onAlarmRingtoneUriChange(uri)
                    }
                },
                onPickLocalAudio = {
                    onPickLocalAudio { uri ->
                        onAlarmRingtoneUriChange(uri)
                    }
                },
                onAlertModeChange = onAlarmAlertModeChange,
                onRingDurationChange = onAlarmRingDurationSecondsChange,
                onRepeatIntervalChange = onAlarmRepeatIntervalSecondsChange,
                onRepeatCountChange = onAlarmRepeatCountChange,
            )

            ExamReminderCard(
                rules = state.reminderRules,
                alarmRingtoneUri = alarmRingtoneUri,
                alarmAlertMode = alarmAlertMode,
                alarmRingDurationSeconds = alarmRingDurationSeconds,
                alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
                alarmRepeatCount = alarmRepeatCount,
                onSave = onSaveExamReminder,
                onOpenRules = {
                    editingRule = null
                    showRuleEditor = true
                },
            )
        }
    }

    if (showRuleEditor) {
        ReminderRuleEditorDialog(
            rule = editingRule,
            slotLabels = slotLabels,
            onPickSystemRingtone = onPickSystemRingtone,
            onPickLocalAudio = onPickLocalAudio,
            onDismiss = { showRuleEditor = false },
            onSave = { ruleId, name, enabled, advance, ringtone, conditions, actions ->
                onSaveRule(ruleId, name, enabled, advance, ringtone, conditions, actions)
                showRuleEditor = false
            },
        )
    }

    if (showPlaceholderDialog) {
        PlaceholderCourseDialog(
            course = editingPlaceholder?.representative,
            daysOfWeek = editingPlaceholder?.daysOfWeek.orEmpty(),
            slotLabels = slotLabels,
            onDismiss = { showPlaceholderDialog = false },
            onSave = { id, label, start, end, weeks, days, title ->
                onSavePlaceholder(id, label, start, end, weeks, days, title)
                showPlaceholderDialog = false
            },
        )
    }

    editingAlarm?.let { alarm ->
        AppAlarmEditorDialog(
            record = alarm,
            onPickSystemRingtone = onPickSystemRingtone,
            onPickLocalAudio = onPickLocalAudio,
            onDismiss = { editingAlarm = null },
            onSave = { settings ->
                onUpdateAppAlarm(alarm.alarmKey, settings)
                editingAlarm = null
            },
        )
    }

    if (showManualAlarmDialog) {
        ManualAppAlarmDialog(
            onPickSystemRingtone = onPickSystemRingtone,
            onPickLocalAudio = onPickLocalAudio,
            onDismiss = { showManualAlarmDialog = false },
            onCreate = { triggerAtMillis, title, message, settings ->
                onCreateManualAlarm(triggerAtMillis, title, message, settings)
                showManualAlarmDialog = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlarmManagementCard(
    alarmRecords: List<SystemAlarmRecord>,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (SystemAlarmRecord) -> Unit,
    onDelete: (SystemAlarmRecord) -> Unit,
    onSetAppAlarmEnabled: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val nowMillis = System.currentTimeMillis()
    val appRecords = remember(alarmRecords, nowMillis) {
        alarmRecords
            .filter { it.backend == ReminderAlarmBackend.AppAlarmClock && it.triggerAtMillis >= nowMillis }
            .sortedBy { it.triggerAtMillis }
    }
    CardSurface {
        HeaderRow(
            icon = Icons.Rounded.Alarm,
            title = stringResource(R.string.schedule_alarm_card_title),
            subtitle = if (appRecords.isEmpty()) stringResource(R.string.schedule_alarm_card_empty_subtitle) else stringResource(R.string.schedule_alarm_card_count, appRecords.size),
            trailing = {
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.schedule_action_refresh))
                }
            },
        )
        if (!canScheduleExactAlarms(context)) {
            AlarmPermissionRow { launchExactAlarmSettings(context) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCreate) { Text(stringResource(R.string.schedule_new_alarm)) }
        }
        if (appRecords.isEmpty()) {
            EmptySurface(stringResource(R.string.schedule_alarm_none_pending))
        } else {
            appRecords.forEach { record ->
                AlarmRecordRow(
                    record = record,
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record) },
                    onSetEnabled = { onSetAppAlarmEnabled(record.alarmKey, it) },
                )
            }
        }
    }
}

@Composable
private fun AlarmRecordRow(
    record: SystemAlarmRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current
    // 有类型内容就按当前语言渲染，旧数据只有语言无关的展示文本时回退到它
    val title = record.titleContent?.let { context.reminderNotificationTitleText(it) }
        ?: record.displayTitle ?: record.alarmLabel ?: record.message
    val message = record.messageContent?.let { context.reminderNotificationMessageText(it) }
        ?: record.displayMessage ?: record.message
    val detail = listOf(
        message,
        stringResource(R.string.schedule_alarm_ring_seconds, record.ringDurationSeconds ?: DEFAULT_APP_ALARM_RING_DURATION_SECONDS),
        stringResource(R.string.schedule_alarm_interval_seconds, record.repeatIntervalSeconds ?: DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS),
        stringResource(R.string.schedule_alarm_count_times, record.repeatCount ?: DEFAULT_APP_ALARM_REPEAT_COUNT),
        stringResource(alarmRingtoneLabelRes(record.ringtoneUriOverride)),
        stringResource(alarmAlertModeLabelRes(record.alertModeOverride)),
    ).joinToString(" · ")
    // 时间、开关和按钮宽度固定，放大字号后会把描述挤成一列单字，因此描述独占整行
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatAlarmTime(record.triggerAtMillis, zone),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatAlarmDay(record.triggerAtMillis, zone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = record.enabled, onCheckedChange = onSetEnabled)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.schedule_cd_edit_alarm))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.schedule_cd_delete_alarm))
                }
            }
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuleManagementCard(
    rules: List<ReminderRule>,
    slotLabels: List<String>,
    placeholders: List<PlaceholderCourseGroup>,
    onAddRule: () -> Unit,
    onEditRule: (ReminderRule) -> Unit,
    onSetRuleEnabled: (String, Boolean) -> Unit,
    onRemoveRule: (String) -> Unit,
    onAddPlaceholder: () -> Unit,
    onEditPlaceholder: (PlaceholderCourseGroup) -> Unit,
    onDeletePlaceholder: (String) -> Unit,
) {
    CardSurface {
        HeaderRow(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.schedule_rule_card_title),
            subtitle = stringResource(R.string.schedule_rule_card_subtitle, slotLabels.size),
            trailing = { Button(onClick = onAddRule) { Text(stringResource(R.string.schedule_new_rule)) } },
        )
        if (rules.isEmpty()) {
            EmptySurface(stringResource(R.string.schedule_rule_none))
        } else {
            rules.forEach { rule ->
                RuleRow(
                    rule = rule,
                    onEdit = { onEditRule(rule) },
                    onSetEnabled = { onSetRuleEnabled(rule.ruleId, it) },
                    onDelete = { onRemoveRule(rule.ruleId) },
                )
            }
        }
        HeaderRow(
            icon = Icons.Rounded.Event,
            title = stringResource(R.string.schedule_placeholder_card_title),
            subtitle = if (placeholders.isEmpty()) stringResource(R.string.schedule_placeholder_empty) else stringResource(R.string.schedule_placeholder_count, placeholders.size),
            trailing = { OutlinedButton(onClick = onAddPlaceholder) { Text(stringResource(R.string.schedule_placeholder_add_title)) } },
        )
        placeholders.forEach { group ->
            val course = group.representative
            SurfaceRow {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(course.slotLabelOverride ?: course.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            R.string.schedule_placeholder_summary,
                            group.daysOfWeek.joinToString(","),
                            course.reminderStartTime ?: "--:--",
                            course.reminderEndTime ?: "--:--",
                            course.weeks.ifEmpty { listOf(0) }.joinToString(",").replace("0", stringResource(R.string.schedule_week_parity_all)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onEditPlaceholder(group) }) { Text(stringResource(R.string.schedule_action_edit)) }
                TextButton(onClick = { onDeletePlaceholder(course.id) }) { Text(stringResource(R.string.schedule_action_delete)) }
            }
        }
    }
}

private data class PlaceholderCourseGroup(
    val courses: List<CourseItem>,
) {
    val representative: CourseItem = courses.first()
    val daysOfWeek: List<Int> = courses.map { it.time.dayOfWeek }.distinct().sorted()
}

@Composable
private fun RuleRow(
    rule: ReminderRule,
    onEdit: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    SurfaceRow {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(rule.displayName ?: stringResource(R.string.schedule_rule_unnamed), fontWeight = FontWeight.SemiBold)
            Text(
                rule.conditionSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                rule.actionSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.schedule_rule_advance, rule.advanceMinutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(alarmRingtoneLabelRes(rule.ringtoneUri)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = onSetEnabled)
        IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.schedule_cd_edit_rule))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.schedule_cd_delete_rule))
        }
    }
}

@Composable
private fun ReminderDefaultsCard(
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onUseDefaultRingtone: () -> Unit,
    onPickSystemRingtone: () -> Unit,
    onPickLocalAudio: () -> Unit,
    onAlertModeChange: (AlarmAlertMode) -> Unit,
    onRingDurationChange: (Int) -> Unit,
    onRepeatIntervalChange: (Int) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
) {
    CardSurface {
        HeaderRow(Icons.Rounded.Settings, stringResource(R.string.schedule_defaults_title), stringResource(R.string.schedule_defaults_subtitle))
        AlarmRingtoneSelector(
            ringtoneUri = alarmRingtoneUri,
            onUseDefault = onUseDefaultRingtone,
            onPickSystem = onPickSystemRingtone,
            onPickLocal = onPickLocalAudio,
        )
        AlarmAlertModeSelector(
            selected = alarmAlertMode,
            includeDefault = false,
            onSelect = { mode -> mode?.let(onAlertModeChange) },
        )
        NumberSettingRow(stringResource(R.string.schedule_ring_duration), alarmRingDurationSeconds, stringResource(R.string.schedule_unit_seconds), 5, 600, 5, onRingDurationChange)
        NumberSettingRow(stringResource(R.string.schedule_ring_interval), alarmRepeatIntervalSeconds, stringResource(R.string.schedule_unit_seconds), 5, 3600, 5, onRepeatIntervalChange)
        NumberSettingRow(stringResource(R.string.schedule_ring_count), alarmRepeatCount, stringResource(R.string.schedule_unit_times), 1, 10, 1, onRepeatCountChange)
    }
}

@Composable
private fun ExamReminderCard(
    rules: List<ReminderRule>,
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onSave: (Boolean, Int, String?) -> Unit,
    onOpenRules: () -> Unit,
) {
    val enabled = examReminderEnabled(rules)
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    CardSurface {
        HeaderRow(Icons.Rounded.Event, stringResource(R.string.schedule_exam_card_title), if (enabled) stringResource(R.string.schedule_exam_card_on) else stringResource(R.string.schedule_exam_card_off))
        SurfaceRow {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.schedule_exam_all), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.schedule_exam_summary, stringResource(alarmRingtoneLabelRes(alarmRingtoneUri)), stringResource(alarmAlertModeLabelRes(alarmAlertMode)), alarmRingDurationSeconds, alarmRepeatIntervalSeconds, alarmRepeatCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked) showConfirm = true else onSave(false, 40, null)
                },
            )
        }
        TextButton(onClick = onOpenRules) { Text(stringResource(R.string.schedule_exam_open_rules)) }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.schedule_exam_confirm_title)) },
            text = {
                Text(
                    listOf(
                        stringResource(R.string.schedule_exam_confirm_intro),
                        stringResource(R.string.schedule_exam_confirm_ringtone, stringResource(alarmRingtoneLabelRes(alarmRingtoneUri))),
                        stringResource(R.string.schedule_exam_confirm_mode, stringResource(alarmAlertModeLabelRes(alarmAlertMode))),
                        stringResource(R.string.schedule_exam_confirm_duration, alarmRingDurationSeconds),
                        stringResource(R.string.schedule_exam_confirm_interval, alarmRepeatIntervalSeconds),
                        stringResource(R.string.schedule_exam_confirm_count, alarmRepeatCount),
                    ).joinToString("\n"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(true, 40, null)
                    showConfirm = false
                }) { Text(stringResource(R.string.schedule_exam_confirm_ok)) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.schedule_action_cancel)) } },
        )
    }
}

@Composable
private fun CourseReminderCard(
    rules: List<ReminderRule>,
    onSetRuleEnabled: (String, Boolean) -> Unit,
    onRemoveRule: (String) -> Unit,
) {
    if (rules.isEmpty()) return
    CardSurface {
        HeaderRow(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.schedule_course_reminder_card_title),
            subtitle = stringResource(R.string.schedule_course_reminder_card_subtitle, rules.size),
        )
        rules.forEach { rule ->
            SurfaceRow {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        rule.displayName?.removePrefix(COURSE_RULE_PREFIX) ?: stringResource(R.string.schedule_course_unnamed),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.schedule_course_rule_summary, rule.advanceMinutes, stringResource(alarmRingtoneLabelRes(rule.ringtoneUri))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = { onSetRuleEnabled(rule.ruleId, it) })
                IconButton(onClick = { onRemoveRule(rule.ruleId) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.schedule_cd_delete_reminder))
                }
            }
        }
    }
}

/** 旧版本写入、当前引擎不再展开的规则，列出来供用户确认和删除。 */
internal fun legacyReminderRules(rules: List<ReminderRule>): List<ReminderRule> =
    rules.filter { it.scopeType.isLegacy() }

/** 旧版规则在列表里显示的标题来源。 */
internal sealed interface LegacyReminderRuleLabel {
    /** 规则自带展示名。 */
    data class DisplayName(val name: String) : LegacyReminderRuleLabel

    /** 没有展示名，按作用域给通用标题。 */
    data class ScopeName(val nameRes: Int) : LegacyReminderRuleLabel
}

/** 作用域对应的通用标题资源 id。 */
internal fun legacyReminderRuleScopeNameRes(scopeType: ReminderScopeType): Int = when (scopeType) {
    ReminderScopeType.SingleCourse -> R.string.schedule_legacy_rule_scope_single_course
    ReminderScopeType.TimeSlot -> R.string.schedule_legacy_rule_scope_time_slot
    ReminderScopeType.Exam -> R.string.schedule_legacy_rule_scope_exam
    ReminderScopeType.FirstCourseOfPeriod, ReminderScopeType.LabelRule ->
        R.string.schedule_legacy_rule_scope_generic
}

internal fun legacyReminderRuleLabel(rule: ReminderRule): LegacyReminderRuleLabel =
    rule.displayName?.takeIf { it.isNotBlank() }?.let(LegacyReminderRuleLabel::DisplayName)
        ?: LegacyReminderRuleLabel.ScopeName(legacyReminderRuleScopeNameRes(rule.scopeType))

internal fun Context.legacyReminderRuleLabelText(label: LegacyReminderRuleLabel): String =
    when (label) {
        is LegacyReminderRuleLabel.DisplayName -> label.name
        is LegacyReminderRuleLabel.ScopeName -> getString(label.nameRes)
    }

@Composable
private fun LegacyReminderRuleCard(
    rules: List<ReminderRule>,
    onRemoveRule: (String) -> Unit,
) {
    if (rules.isEmpty()) return
    CardSurface {
        HeaderRow(
            icon = Icons.Rounded.Warning,
            title = stringResource(R.string.schedule_legacy_card_title),
            subtitle = stringResource(R.string.schedule_legacy_card_subtitle, rules.size),
        )
        rules.forEach { rule ->
            SurfaceRow {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        LocalContext.current.legacyReminderRuleLabelText(legacyReminderRuleLabel(rule)),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.schedule_legacy_rule_summary, rule.advanceMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = { onRemoveRule(rule.ruleId) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.schedule_cd_delete_legacy))
                }
            }
        }
    }
}

@Composable
private fun CardSurface(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SurfaceRow(content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun HeaderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun EmptySurface(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumberSettingRow(
    title: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    SurfaceRow {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("$value $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = { onValueChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) { Text("-") }
        OutlinedButton(
            onClick = { onValueChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) { Text("+") }
    }
}


@Composable
private fun AlarmPermissionRow(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.schedule_exact_alarm_off), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.schedule_go_enable)) }
        }
    }
}

@Composable
private fun ReminderRule.conditionSummary(): String {
    val exists = stringResource(R.string.schedule_reminder_presence_exists)
    val absent = stringResource(R.string.schedule_reminder_presence_absent)
    val empty = stringResource(R.string.schedule_no_condition)
    val separator = stringResource(R.string.schedule_summary_separator)
    return labelConditions.joinToString(separator) { condition ->
        val presence = when (condition.presence) {
            ReminderLabelPresence.Exists -> exists
            ReminderLabelPresence.Absent -> absent
        }
        "${condition.slotLabel} $presence"
    }.ifBlank { empty }
}

@Composable
private fun ReminderRule.actionSummary(): String {
    val remind = stringResource(R.string.schedule_reminder_action_remind)
    val skip = stringResource(R.string.schedule_reminder_action_skip)
    val empty = stringResource(R.string.schedule_no_action)
    val separator = stringResource(R.string.schedule_summary_separator)
    return labelActions.joinToString(separator) { action ->
        val type = when (action.action) {
            ReminderLabelActionType.Remind -> remind
            ReminderLabelActionType.Skip -> skip
        }
        "${action.slotLabel} $type"
    }.ifBlank { empty }
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching {
        alarmManager.canScheduleExactAlarms()
    }.getOrDefault(false)
}

private fun launchExactAlarmSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { error ->
        Toast.makeText(context, context.getString(R.string.schedule_open_settings_failed, error.message), Toast.LENGTH_SHORT).show()
    }
}

private fun formatAlarmTime(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone))

@Composable
private fun formatAlarmDay(millis: Long, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> stringResource(R.string.schedule_day_today)
        today.plusDays(1) -> stringResource(R.string.schedule_day_tomorrow)
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }
}
