package com.x500x.cursimple.feature.schedule

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.reminder.model.FirstCourseCandidateScope
import com.x500x.cursimple.core.reminder.model.ReminderAction
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderCondition
import com.x500x.cursimple.core.reminder.model.ReminderConditionMode
import com.x500x.cursimple.core.reminder.model.ReminderNodeRange
import com.x500x.cursimple.core.reminder.model.ReminderTimeRange
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
        onSelectTimeSlot = viewModel::selectTimeSlot,
        onClearSelection = viewModel::clearSelection,
        onCreateReminder = viewModel::createReminderForSelection,
        onSaveFirstCourseReminder = viewModel::saveFlexibleFirstCourseReminder,
        onSaveCustomOccupancy = viewModel::saveCustomOccupancy,
        onRemoveCustomOccupancy = viewModel::removeCustomOccupancy,
        onSaveExamReminder = viewModel::saveExamReminder,
        onRemoveReminderRule = viewModel::removeReminderRule,
        onRemoveAlarmRecord = viewModel::removeAlarmRecord,
        onRefreshReminderAlarms = viewModel::refreshReminderAlarmsNow,
        alarmBackend = alarmBackend,
        alarmRingDurationSeconds = alarmRingDurationSeconds,
        alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
        alarmRepeatCount = alarmRepeatCount,
        onAlarmBackendChange = onAlarmBackendChange,
        onAlarmRingDurationSecondsChange = onAlarmRingDurationSecondsChange,
        onAlarmRepeatIntervalSecondsChange = onAlarmRepeatIntervalSecondsChange,
        onAlarmRepeatCountChange = onAlarmRepeatCountChange,
        modifier = modifier,
    )
}

@Composable
fun ScheduleSettingsScreen(
    state: ScheduleUiState,
    onSelectTimeSlot: (Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    onCreateReminder: (Int, String?) -> Unit,
    onSaveFirstCourseReminder: (
        String?,
        String,
        Boolean,
        Int,
        String?,
        FirstCourseCandidateScope,
        ReminderConditionMode,
        List<ReminderCondition>,
        List<ReminderAction>,
    ) -> Unit,
    onSaveCustomOccupancy: (
        String?,
        String,
        ReminderTimeRange,
        List<Int>,
        List<Int>,
        List<String>,
        List<String>,
        ReminderNodeRange?,
    ) -> Unit,
    onRemoveCustomOccupancy: (String) -> Unit,
    onSaveExamReminder: (Boolean, Int, String?) -> Unit,
    onRemoveReminderRule: (String) -> Unit,
    onRemoveAlarmRecord: (String, ReminderAlarmBackend) -> Unit,
    onRefreshReminderAlarms: () -> Unit,
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
    var advanceMinutesText by rememberSaveable { mutableStateOf("20") }
    var ringtoneUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showAlarmBackendDialog by rememberSaveable { mutableStateOf(false) }
    val selectedCourse = remember(state.selectionState, state.schedule) {
        selectedCourseFromState(state.selectionState, state.schedule)
    }
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
        }
        ringtoneUri = uri?.toString()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AlarmManagementCard(
                alarmRecords = state.systemAlarmRecords,
                onRefresh = onRefreshReminderAlarms,
                onRemoveAlarmRecord = onRemoveAlarmRecord,
            )

            ReminderDefaultsCard(
                alarmBackend = alarmBackend,
                alarmRingDurationSeconds = alarmRingDurationSeconds,
                alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
                alarmRepeatCount = alarmRepeatCount,
                onPickBackend = { showAlarmBackendDialog = true },
                onRingDurationChange = onAlarmRingDurationSecondsChange,
                onRepeatIntervalChange = onAlarmRepeatIntervalSecondsChange,
                onRepeatCountChange = onAlarmRepeatCountChange,
            )

            if (state.alarmRecommendations.isNotEmpty()) {
                MessageCard(
                    title = "提醒建议",
                    lines = state.alarmRecommendations.map { "建议提前 ${it.advanceMinutes} 分钟：${it.note}" },
                )
            }

            FirstCourseReminderSettingsCard(
                reminderRules = state.reminderRules,
                customOccupancies = state.customOccupancies,
                pluginId = state.pluginId,
                onSaveRule = onSaveFirstCourseReminder,
                onSaveOccupancy = onSaveCustomOccupancy,
                onRemoveOccupancy = onRemoveCustomOccupancy,
                onRemoveRule = onRemoveReminderRule,
            )

            ExamReminderSettingsCard(
                reminderRules = state.reminderRules,
                pluginId = state.pluginId,
                onSave = onSaveExamReminder,
            )

            if (state.selectionState != null) {
                ReminderComposerCard(
                    selectedCourse = selectedCourse,
                    selectionState = state.selectionState,
                    advanceMinutesText = advanceMinutesText,
                    ringtoneUri = ringtoneUri,
                    onAdvanceMinutesChange = { advanceMinutesText = it.filter(Char::isDigit) },
                    onPickRingtone = {
                        launchAlarmRingtonePicker(context) { intent ->
                            ringtoneLauncher.launch(intent)
                        }
                    },
                    onCreateReminder = {
                        onCreateReminder(advanceMinutesText.toIntOrNull() ?: 20, ringtoneUri)
                    },
                    onSelectSameSlot = {
                        selectedCourse?.let { course ->
                            onSelectTimeSlot(course.time.startNode, course.time.endNode)
                        }
                    },
                    onClearSelection = onClearSelection,
                )
            }

            if (state.reminderRules.isNotEmpty()) {
                ReminderRulesSection(
                    reminderRules = state.reminderRules,
                    schedule = state.schedule,
                    timingProfile = state.timingProfile,
                    manualCourses = state.manualCourses,
                    onRemoveReminderRule = onRemoveReminderRule,
                )
            }
        }

    }

    if (showAlarmBackendDialog) {
        AlarmBackendDialog(
            selected = alarmBackend,
            onSelect = {
                onAlarmBackendChange(it)
                showAlarmBackendDialog = false
            },
            onDismiss = { showAlarmBackendDialog = false },
        )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("提醒配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "默认响铃与闹钟通道",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ReminderActionRow(
                title = "闹钟通道",
                subtitle = alarmBackendFullLabel(alarmBackend),
                onClick = onPickBackend,
            )
            ReminderNumberSettingRow(
                title = "响铃时长",
                value = alarmRingDurationSeconds,
                unit = "秒",
                min = 5,
                max = 600,
                step = 5,
                onValueChange = onRingDurationChange,
            )
            ReminderNumberSettingRow(
                title = "响铃间隔",
                value = alarmRepeatIntervalSeconds,
                unit = "秒",
                min = 5,
                max = 3600,
                step = 5,
                onValueChange = onRepeatIntervalChange,
            )
            ReminderNumberSettingRow(
                title = "响铃次数",
                value = alarmRepeatCount,
                unit = "次",
                min = 1,
                max = 10,
                step = 1,
                onValueChange = onRepeatCountChange,
            )
        }
    }
}

@Composable
private fun ReminderActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderNumberSettingRow(
    title: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "$value $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { onValueChange((value - step).coerceAtLeast(min)) },
                enabled = value > min,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("-") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onValueChange((value + step).coerceAtMost(max)) },
                enabled = value < max,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("+") }
        }
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
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(backend) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = backend == selected,
                            onClick = { onSelect(backend) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(alarmBackendFullLabel(backend), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                alarmBackendDescription(backend),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun AlarmManagementCard(
    alarmRecords: List<SystemAlarmRecord>,
    onRefresh: () -> Unit,
    onRemoveAlarmRecord: (String, ReminderAlarmBackend) -> Unit,
) {
    val context = LocalContext.current
    val nowMillis = System.currentTimeMillis()
    val upcomingRecords = remember(alarmRecords, nowMillis) {
        alarmRecords
            .filter { it.triggerAtMillis >= nowMillis }
            .sortedBy { it.triggerAtMillis }
    }
    val exactAlarmEnabled = canScheduleExactAlarms(context)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("闹钟管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (upcomingRecords.isEmpty()) "暂无已设置闹钟" else "已设置 ${upcomingRecords.size} 个闹钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("刷新")
                }
            }

            if (!exactAlarmEnabled) {
                AlarmPermissionRow(
                    onOpenSettings = { launchExactAlarmSettings(context) },
                )
            }

            if (upcomingRecords.isEmpty()) {
                EmptyAlarmRow()
            } else {
                upcomingRecords.take(MAX_VISIBLE_ALARM_ROWS).forEach { record ->
                    AlarmRecordRow(
                        record = record,
                        onRemove = { onRemoveAlarmRecord(record.alarmKey, record.backend) },
                    )
                }
                if (upcomingRecords.size > MAX_VISIBLE_ALARM_ROWS) {
                    Text(
                        text = "还有 ${upcomingRecords.size - MAX_VISIBLE_ALARM_ROWS} 个稍后的闹钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmPermissionRow(
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "精确闹钟权限未开启",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "App 自管闹钟无法提交到系统",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Button(onClick = onOpenSettings) {
                Text("去开启")
            }
        }
    }
}

@Composable
private fun EmptyAlarmRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "没有等待触发的闹钟",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "创建课程提醒后会显示在这里",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlarmRecordRow(
    record: SystemAlarmRecord,
    onRemove: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatAlarmTime(record.triggerAtMillis, zone),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatAlarmDay(record.triggerAtMillis, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = record.alarmLabel ?: record.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${alarmBackendLabel(record.backend)} · ${formatAlarmDate(record.triggerAtMillis, zone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "取消本次闹钟",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { error ->
            Toast.makeText(context, "无法打开设置：${error.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun alarmBackendLabel(backend: ReminderAlarmBackend): String = when (backend) {
    ReminderAlarmBackend.AppAlarmClock -> "App 自管"
    ReminderAlarmBackend.SystemClockApp -> "系统时钟"
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

private fun formatAlarmDate(millis: Long, zone: ZoneId): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
    val date = dateTime.toLocalDate()
    val day = formatAlarmDay(millis, zone)
    return "$day ${weekdayShortLabel(date.dayOfWeek.value)}"
}

private fun weekdayShortLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周$dayOfWeek"
}

private const val MAX_VISIBLE_ALARM_ROWS = 6
