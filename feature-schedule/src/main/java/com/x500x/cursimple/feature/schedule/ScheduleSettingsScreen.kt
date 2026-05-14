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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_COUNT
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_RING_DURATION_SECONDS
import com.x500x.cursimple.core.reminder.model.EditableAppAlarmSettings
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleSettingsRoute(
    viewModel: ScheduleViewModel,
    alarmBackend: ReminderAlarmBackend,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onAlarmBackendChange: (ReminderAlarmBackend) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleSettingsScreen(
        state = state,
        alarmBackend = alarmBackend,
        alarmRingDurationSeconds = alarmRingDurationSeconds,
        alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
        alarmRepeatCount = alarmRepeatCount,
        onAlarmBackendChange = onAlarmBackendChange,
        onAlarmRingDurationSecondsChange = onAlarmRingDurationSecondsChange,
        onAlarmRepeatIntervalSecondsChange = onAlarmRepeatIntervalSecondsChange,
        onAlarmRepeatCountChange = onAlarmRepeatCountChange,
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
    alarmBackend: ReminderAlarmBackend,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onAlarmBackendChange: (ReminderAlarmBackend) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
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
    var showBackendDialog by rememberSaveable { mutableStateOf(false) }
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

            SectionHeader("规则设置", "按 slotTimes.label 的条件和动作生成提醒")
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

            ReminderDefaultsCard(
                alarmBackend = alarmBackend,
                alarmRingDurationSeconds = alarmRingDurationSeconds,
                alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
                alarmRepeatCount = alarmRepeatCount,
                onPickBackend = { showBackendDialog = true },
                onRingDurationChange = onAlarmRingDurationSecondsChange,
                onRepeatIntervalChange = onAlarmRepeatIntervalSecondsChange,
                onRepeatCountChange = onAlarmRepeatCountChange,
            )

            ExamReminderCard(
                rules = state.reminderRules,
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

    if (showBackendDialog) {
        AlarmBackendDialog(
            selected = alarmBackend,
            onSelect = {
                onAlarmBackendChange(it)
                showBackendDialog = false
            },
            onDismiss = { showBackendDialog = false },
        )
    }

    if (showRuleEditor) {
        ReminderRuleEditorDialog(
            rule = editingRule,
            slotLabels = slotLabels,
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
            onDismiss = { editingAlarm = null },
            onSave = { settings ->
                onUpdateAppAlarm(alarm.alarmKey, settings)
                editingAlarm = null
            },
        )
    }

    if (showManualAlarmDialog) {
        ManualAppAlarmDialog(
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
            title = "闹钟管理",
            subtitle = if (appRecords.isEmpty()) "暂无 APP 自管闹钟" else "APP 自管闹钟 ${appRecords.size} 个",
            trailing = {
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新")
                }
            },
        )
        if (!canScheduleExactAlarms(context)) {
            AlarmPermissionRow { launchExactAlarmSettings(context) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCreate) { Text("新建闹钟") }
        }
        if (appRecords.isEmpty()) {
            EmptySurface("没有等待触发的 APP 自管闹钟")
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
    SurfaceRow {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatAlarmTime(record.triggerAtMillis, zone), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(formatAlarmDay(record.triggerAtMillis, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.displayTitle ?: record.alarmLabel ?: record.message, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(
                    record.displayMessage ?: record.message,
                    "响铃 ${record.ringDurationSeconds ?: DEFAULT_APP_ALARM_RING_DURATION_SECONDS} 秒",
                    "间隔 ${record.repeatIntervalSeconds ?: DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS} 秒",
                    "${record.repeatCount ?: DEFAULT_APP_ALARM_REPEAT_COUNT} 次",
                    if (record.ringtoneUriOverride.isNullOrBlank()) "默认铃声" else "自定义铃声",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = record.enabled, onCheckedChange = onSetEnabled)
        IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, contentDescription = "编辑闹钟")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "删除闹钟")
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
            title = "规则管理",
            subtitle = "可选 label ${slotLabels.size} 个",
            trailing = { Button(onClick = onAddRule) { Text("新建规则") } },
        )
        if (rules.isEmpty()) {
            EmptySurface("还没有 label 规则")
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
            title = "占位课",
            subtitle = if (placeholders.isEmpty()) "暂无隐藏占位课" else "隐藏占位课 ${placeholders.size} 个",
            trailing = { OutlinedButton(onClick = onAddPlaceholder) { Text("新增占位课") } },
        )
        placeholders.forEach { group ->
            val course = group.representative
            SurfaceRow {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(course.slotLabelOverride ?: course.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        "周${group.daysOfWeek.joinToString(",")} · ${course.reminderStartTime ?: "--:--"}-${course.reminderEndTime ?: "--:--"} · ${course.weeks.ifEmpty { listOf(0) }.joinToString(",").replace("0", "全部周")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onEditPlaceholder(group) }) { Text("编辑") }
                TextButton(onClick = { onDeletePlaceholder(course.id) }) { Text("删除") }
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
            Text(rule.displayName ?: "未命名规则", fontWeight = FontWeight.SemiBold)
            Text(
                "如果 ${rule.conditionSummary()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "就 ${rule.actionSummary()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("提前 ${rule.advanceMinutes} 分钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Switch(checked = rule.enabled, onCheckedChange = onSetEnabled)
        IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, contentDescription = "编辑规则")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "删除规则")
        }
    }
}

@Composable
private fun ReminderDefaultsCard(
    alarmBackend: ReminderAlarmBackend,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onPickBackend: () -> Unit,
    onRingDurationChange: (Int) -> Unit,
    onRepeatIntervalChange: (Int) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
) {
    CardSurface {
        HeaderRow(Icons.Rounded.Settings, "默认闹钟设置", "规则未单独设置时使用这里的参数")
        SettingRow("闹钟通道", alarmBackendFullLabel(alarmBackend), onClick = onPickBackend)
        NumberSettingRow("响铃时长", alarmRingDurationSeconds, "秒", 5, 600, 5, onRingDurationChange)
        NumberSettingRow("响铃间隔", alarmRepeatIntervalSeconds, "秒", 5, 3600, 5, onRepeatIntervalChange)
        NumberSettingRow("响铃次数", alarmRepeatCount, "次", 1, 10, 1, onRepeatCountChange)
    }
}

@Composable
private fun ExamReminderCard(
    rules: List<ReminderRule>,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onSave: (Boolean, Int, String?) -> Unit,
    onOpenRules: () -> Unit,
) {
    val enabled = rules.any {
        it.scopeType == ReminderScopeType.LabelRule &&
            it.displayName?.startsWith("考试提醒：") == true &&
            it.enabled
    }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    CardSurface {
        HeaderRow(Icons.Rounded.Event, "考试提醒", if (enabled) "所有考试前提醒已开启" else "默认关闭")
        SurfaceRow {
            Column(modifier = Modifier.weight(1f)) {
                Text("所有考试前提醒", fontWeight = FontWeight.SemiBold)
                Text(
                    "响铃 $alarmRingDurationSeconds 秒 · 间隔 $alarmRepeatIntervalSeconds 秒 · $alarmRepeatCount 次",
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
        TextButton(onClick = onOpenRules) { Text("进入规则管理编辑考试规则") }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认考试提醒参数") },
            text = { Text("将按默认闹钟设置为所有考试 label 创建提醒规则。") },
            confirmButton = {
                TextButton(onClick = {
                    onSave(true, 40, null)
                    showConfirm = false
                }) { Text("确认开启") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("取消") } },
        )
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
private fun AlarmBackendDialog(
    selected: ReminderAlarmBackend,
    onSelect: (ReminderAlarmBackend) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("闹钟通道") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderAlarmBackend.entries.forEach { backend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(backend) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = backend == selected, onClick = { onSelect(backend) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(alarmBackendFullLabel(backend))
                            Text(alarmBackendDescription(backend), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
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
            Text("精确闹钟权限未开启", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onOpenSettings) { Text("去开启") }
        }
    }
}

private fun ReminderRule.conditionSummary(): String =
    labelConditions.joinToString("，") { condition ->
        val presence = when (condition.presence) {
            ReminderLabelPresence.Exists -> "存在"
            ReminderLabelPresence.Absent -> "不存在"
        }
        "${condition.slotLabel} $presence"
    }.ifBlank { "无条件" }

private fun ReminderRule.actionSummary(): String =
    labelActions.joinToString("，") { action ->
        val type = when (action.action) {
            ReminderLabelActionType.Remind -> "提醒"
            ReminderLabelActionType.Skip -> "跳过"
        }
        "${action.slotLabel} $type"
    }.ifBlank { "无动作" }

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
        Toast.makeText(context, "无法打开设置：${error.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun alarmBackendFullLabel(backend: ReminderAlarmBackend): String = when (backend) {
    ReminderAlarmBackend.AppAlarmClock -> "App 自管闹钟"
    ReminderAlarmBackend.SystemClockApp -> "系统时钟 App 闹钟"
}

private fun alarmBackendDescription(backend: ReminderAlarmBackend): String = when (backend) {
    ReminderAlarmBackend.AppAlarmClock -> "使用系统精确闹钟，由课简控制响铃。"
    ReminderAlarmBackend.SystemClockApp -> "调用系统时钟 App 创建和删除闹钟。"
}

private fun formatAlarmTime(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone))

private fun formatAlarmDay(millis: Long, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }
}
