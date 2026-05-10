package com.kebiao.viewer.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kebiao.viewer.app.reminder.AlarmPermissionIntents
import com.kebiao.viewer.app.util.LogExporter
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverrideType
import com.kebiao.viewer.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.kebiao.viewer.core.kernel.model.weekdayLabel
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
import com.kebiao.viewer.feature.schedule.ScheduleSettingsRoute
import com.kebiao.viewer.feature.schedule.ScheduleViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsRoute(
    themeMode: ThemeMode,
    themeAccentLabel: String,
    termStartDate: LocalDate?,
    timeZoneLabel: String,
    currentWeekIndex: Int,
    totalScheduleDisplayEnabled: Boolean,
    alarmBackend: ReminderAlarmBackend,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    autoUpdateEnabled: Boolean,
    ignoredUpdateVersionCode: Int?,
    developerModeEnabled: Boolean,
    debugForcedDateTime: LocalDateTime?,
    onPickThemeMode: () -> Unit,
    onPickThemeAccent: () -> Unit,
    onPickTermStartDate: () -> Unit,
    onPickCurrentWeek: () -> Unit,
    onClearTermStartDate: () -> Unit,
    onPickTimeZone: () -> Unit,
    onTotalScheduleDisplayChange: (Boolean) -> Unit,
    onAlarmBackendChange: (ReminderAlarmBackend) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
    onUpsertTemporaryScheduleOverride: (TemporaryScheduleOverride) -> Unit,
    onRemoveTemporaryScheduleOverride: (String) -> Unit,
    onClearTemporaryScheduleOverrides: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
    onIgnoreUpdateVersion: (Int?) -> Unit,
    onSetDeveloperMode: (Boolean) -> Unit,
    onSetDebugForcedDateTime: (LocalDateTime?) -> Unit,
    onExportScheduleMetadata: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showTemporaryOverrides by rememberSaveable { mutableStateOf(false) }
    var showAlarmBackendDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "偏好",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "管理外观、周次和课表显示方式。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsActionRow(
            icon = Icons.Rounded.Palette,
            title = "主题",
            subtitle = themeAccentLabel,
            onClick = onPickThemeAccent,
        )

        SettingsActionRow(
            icon = when (themeMode) {
                ThemeMode.Dark -> Icons.Rounded.Brightness4
                else -> Icons.Rounded.Brightness7
            },
            title = "外观",
            subtitle = when (themeMode) {
                ThemeMode.System -> "跟随系统"
                ThemeMode.Light -> "亮色"
                ThemeMode.Dark -> "暗色"
            },
            onClick = onPickThemeMode,
        )

        SettingsActionRow(
            icon = Icons.Rounded.CalendarMonth,
            title = "开学日期",
            subtitle = termStartDate?.let {
                val fmt = DateTimeFormatter.ofPattern("yyyy/M/d")
                "${fmt.format(it)} · 第 $currentWeekIndex 周"
            } ?: "点击设置（用于计算当前周次）",
            onClick = onPickTermStartDate,
            trailing = if (termStartDate != null) {
                {
                    TextButton(
                        onClick = onClearTermStartDate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("清除", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else null,
        )

        SettingsActionRow(
            icon = Icons.Rounded.CalendarMonth,
            title = "当前周",
            subtitle = if (termStartDate != null) {
                "第 $currentWeekIndex 周 · 可按周数反推开学日期"
            } else {
                "点击输入今天所在周数"
            },
            onClick = onPickCurrentWeek,
        )

        SettingsActionRow(
            icon = Icons.Rounded.Public,
            title = "时区",
            subtitle = timeZoneLabel,
            onClick = onPickTimeZone,
        )

        SettingsSwitchRow(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = "总课表显示",
            subtitle = if (totalScheduleDisplayEnabled) {
                "周视图显示本学期全部课程，非本周课程置灰标注"
            } else {
                "周视图只显示本周课程"
            },
            checked = totalScheduleDisplayEnabled,
            onCheckedChange = onTotalScheduleDisplayChange,
        )

        SettingsActionRow(
            icon = Icons.Rounded.EventRepeat,
            title = "临时调课",
            subtitle = temporaryOverridesSubtitle(temporaryScheduleOverrides),
            onClick = { showTemporaryOverrides = true },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsActionRow(
            icon = Icons.Rounded.Widgets,
            title = "桌面小组件",
            subtitle = "添加课表、下一节课或课程提醒",
            onClick = onOpenWidgetPicker,
        )

        UpdateCheckSection(
            autoCheckEnabled = autoUpdateEnabled,
            ignoredUpdateVersionCode = ignoredUpdateVersionCode,
            onAutoCheckEnabledChange = onAutoUpdateEnabledChange,
            onIgnoreUpdateVersion = onIgnoreUpdateVersion,
        )

        AlarmReliabilitySection(
            alarmBackend = alarmBackend,
            alarmRingDurationSeconds = alarmRingDurationSeconds,
            alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
            alarmRepeatCount = alarmRepeatCount,
            onPickBackend = { showAlarmBackendDialog = true },
            onRingDurationChange = onAlarmRingDurationSecondsChange,
            onRepeatIntervalChange = onAlarmRepeatIntervalSecondsChange,
            onRepeatCountChange = onAlarmRepeatCountChange,
        )

        if (developerModeEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeveloperDebugSection(
                debugForcedDateTime = debugForcedDateTime,
                onSetDeveloperMode = onSetDeveloperMode,
                onSetDebugForcedDateTime = onSetDebugForcedDateTime,
                onExportScheduleMetadata = onExportScheduleMetadata,
            )
        }
    }

    if (showTemporaryOverrides) {
        TemporaryScheduleOverridesDialog(
            overrides = temporaryScheduleOverrides,
            onAdd = onUpsertTemporaryScheduleOverride,
            onRemove = onRemoveTemporaryScheduleOverride,
            onClear = onClearTemporaryScheduleOverrides,
            onDismiss = { showTemporaryOverrides = false },
        )
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
private fun AlarmReliabilitySection(
    alarmBackend: ReminderAlarmBackend,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onPickBackend: () -> Unit,
    onRingDurationChange: (Int) -> Unit,
    onRepeatIntervalChange: (Int) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(context, if (granted) "通知权限已开启" else "通知权限未开启", Toast.LENGTH_SHORT).show()
    }
    val alarmManager = remember(context) { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val exactAlarmEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    val notificationEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    val notificationManager = remember(context) { context.getSystemService(NotificationManager::class.java) }
    val fullScreenIntentEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        notificationManager.canUseFullScreenIntent()
    val batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "提醒与闹钟",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "配置闹钟通道、相关权限与响铃节奏。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsActionRow(
            icon = Icons.Rounded.Schedule,
            title = "闹钟通道",
            subtitle = alarmBackendLabel(alarmBackend),
            onClick = onPickBackend,
        )
        SettingsActionRow(
            icon = Icons.Rounded.Schedule,
            title = "精确闹钟权限",
            subtitle = if (exactAlarmEnabled) "已开启" else "未开启，App 自管闹钟无法设置",
            onClick = {
                if (exactAlarmEnabled) {
                    Toast.makeText(context, "精确闹钟权限已开启", Toast.LENGTH_SHORT).show()
                } else {
                    launchSettingsIntent(context, AlarmPermissionIntents.exactAlarmSettingsIntent(context))
                }
            },
        )
        SettingsActionRow(
            icon = Icons.Rounded.Notifications,
            title = "通知权限",
            subtitle = if (notificationEnabled) "已开启" else "未开启，响铃通知可能无法显示",
            onClick = {
                if (notificationEnabled) {
                    Toast.makeText(context, "通知权限已开启", Toast.LENGTH_SHORT).show()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    launchSettingsIntent(context, AlarmPermissionIntents.appDetailsIntent(context))
                }
            },
        )
        SettingsActionRow(
            icon = Icons.Rounded.Notifications,
            title = "全屏响铃权限",
            subtitle = if (fullScreenIntentEnabled) "已开启" else "未开启，锁屏响铃页可能不会自动弹出",
            onClick = {
                if (fullScreenIntentEnabled) {
                    Toast.makeText(context, "全屏响铃权限已开启", Toast.LENGTH_SHORT).show()
                } else {
                    launchSettingsIntent(context, AlarmPermissionIntents.fullScreenIntentSettingsIntent(context))
                }
            },
        )
        SettingsActionRow(
            icon = Icons.Rounded.Restore,
            title = "省电优化",
            subtitle = if (batteryOptimizationIgnored) "已允许后台运行" else "建议允许后台运行，提升响铃服务可靠性",
            onClick = {
                if (batteryOptimizationIgnored) {
                    Toast.makeText(context, "已允许后台运行", Toast.LENGTH_SHORT).show()
                } else {
                    launchSettingsIntent(context, AlarmPermissionIntents.batteryOptimizationIntent(context))
                }
            },
        )
        AlarmNumberSettingRow(
            title = "响铃时长",
            value = alarmRingDurationSeconds,
            unit = "秒",
            min = 5,
            max = 600,
            step = 5,
            onValueChange = onRingDurationChange,
        )
        AlarmNumberSettingRow(
            title = "响铃间隔",
            value = alarmRepeatIntervalSeconds,
            unit = "秒",
            min = 5,
            max = 3600,
            step = 5,
            onValueChange = onRepeatIntervalChange,
        )
        AlarmNumberSettingRow(
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

@Composable
private fun AlarmNumberSettingRow(
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
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$value $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                enabled = value > min,
                onClick = { onValueChange((value - step).coerceAtLeast(min)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text("-")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = value < max,
                onClick = { onValueChange((value + step).coerceAtMost(max)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text("+")
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReminderAlarmBackend.entries.forEach { backend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(backend) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == backend,
                            onClick = { onSelect(backend) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = alarmBackendLabel(backend),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = alarmBackendDescription(backend),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

private fun alarmBackendLabel(backend: ReminderAlarmBackend): String = when (backend) {
    ReminderAlarmBackend.AppAlarmClock -> "App 自管闹钟"
    ReminderAlarmBackend.SystemClockApp -> "系统时钟 App 闹钟"
}

private fun alarmBackendDescription(backend: ReminderAlarmBackend): String = when (backend) {
    ReminderAlarmBackend.AppAlarmClock -> "使用 AlarmManager.setAlarmClock，由本 App 控制响铃。"
    ReminderAlarmBackend.SystemClockApp -> "使用系统时钟 App 创建和删除闹钟。"
}

private fun launchSettingsIntent(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching {
            context.startActivity(
                AlarmPermissionIntents.appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { error ->
            Toast.makeText(context, "无法打开设置：${error.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemporaryScheduleOverridesDialog(
    overrides: List<TemporaryScheduleOverride>,
    onAdd: (TemporaryScheduleOverride) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var mode by rememberSaveable { mutableStateOf(TemporaryOverrideDialogMode.MakeUp) }
    var targetDate by rememberSaveable { mutableStateOf(today) }
    var sourceDate by rememberSaveable { mutableStateOf(today) }
    var cancelStartNodeText by rememberSaveable { mutableStateOf("1") }
    var cancelEndNodeText by rememberSaveable { mutableStateOf("1") }
    var pickTargetDate by rememberSaveable { mutableStateOf(false) }
    var pickSourceDate by rememberSaveable { mutableStateOf(false) }
    val cancelStartNode = cancelStartNodeText.toIntOrNull()
    val cancelEndNode = cancelEndNodeText.toIntOrNull()
    val canAddCancellation = cancelStartNode != null &&
        cancelEndNode != null &&
        cancelStartNode in 1..32 &&
        cancelEndNode in cancelStartNode..32

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("临时调课") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverrideModeButton(
                        label = "补课/调课",
                        selected = mode == TemporaryOverrideDialogMode.MakeUp,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = TemporaryOverrideDialogMode.MakeUp },
                    )
                    OverrideModeButton(
                        label = "临时取消",
                        selected = mode == TemporaryOverrideDialogMode.CancelCourse,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = TemporaryOverrideDialogMode.CancelCourse },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateChoiceButton(
                        label = if (mode == TemporaryOverrideDialogMode.MakeUp) "调课日" else "取消日",
                        date = targetDate,
                        modifier = Modifier.weight(1f),
                        onClick = { pickTargetDate = true },
                    )
                    if (mode == TemporaryOverrideDialogMode.MakeUp) {
                        DateChoiceButton(
                            label = "按此日",
                            date = sourceDate,
                            modifier = Modifier.weight(1f),
                            onClick = { pickSourceDate = true },
                        )
                    }
                }
                if (mode == TemporaryOverrideDialogMode.MakeUp) {
                    Text(
                        text = "将在 ${formatLongDate(targetDate)} 显示并提醒 ${formatLongDate(sourceDate)} 的课程。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cancelStartNodeText,
                            onValueChange = { cancelStartNodeText = it.filter(Char::isDigit).take(2) },
                            label = { Text("取消起始节") },
                            singleLine = true,
                            isError = cancelStartNodeText.isNotBlank() && !canAddCancellation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = cancelEndNodeText,
                            onValueChange = { cancelEndNodeText = it.filter(Char::isDigit).take(2) },
                            label = { Text("取消结束节") },
                            singleLine = true,
                            isError = cancelEndNodeText.isNotBlank() && !canAddCancellation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (canAddCancellation) {
                            "将在 ${formatLongDate(targetDate)} 隐藏并取消第 $cancelStartNode-${cancelEndNode} 节对应课程提醒。"
                        } else {
                            "请输入 1 到 32 之间的有效节次范围。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (canAddCancellation) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        if (mode == TemporaryOverrideDialogMode.MakeUp) {
                            onAdd(
                                TemporaryScheduleOverride(
                                    id = UUID.randomUUID().toString(),
                                    type = TemporaryScheduleOverrideType.MakeUp,
                                    targetDate = targetDate.toString(),
                                    sourceDate = sourceDate.toString(),
                                ),
                            )
                        } else if (canAddCancellation) {
                            onAdd(
                                TemporaryScheduleOverride(
                                    id = UUID.randomUUID().toString(),
                                    type = TemporaryScheduleOverrideType.CancelCourse,
                                    targetDate = targetDate.toString(),
                                    cancelStartNode = cancelStartNode,
                                    cancelEndNode = cancelEndNode,
                                ),
                            )
                        }
                    },
                    enabled = mode == TemporaryOverrideDialogMode.MakeUp || canAddCancellation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (mode == TemporaryOverrideDialogMode.MakeUp) "添加规则" else "添加取消规则")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (overrides.isEmpty()) {
                    Text(
                        text = "暂无临时调课规则",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    overrides.forEach { rule ->
                        TemporaryOverrideRuleRow(
                            rule = rule,
                            onRemove = { onRemove(rule.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = if (overrides.isNotEmpty()) {
            {
                TextButton(onClick = onClear) { Text("清空") }
            }
        } else null,
    )

    if (pickTargetDate) {
        SettingsDatePickerDialog(
            initial = targetDate,
            onConfirm = {
                targetDate = it
                pickTargetDate = false
            },
            onDismiss = { pickTargetDate = false },
        )
    }
    if (pickSourceDate) {
        SettingsDatePickerDialog(
            initial = sourceDate,
            onConfirm = {
                sourceDate = it
                pickSourceDate = false
            },
            onDismiss = { pickSourceDate = false },
        )
    }
}

private enum class TemporaryOverrideDialogMode { MakeUp, CancelCourse }

@Composable
private fun OverrideModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun DateChoiceButton(
    label: String,
    date: LocalDate,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text("$label ${formatShortDate(date)}")
    }
}

@Composable
private fun TemporaryOverrideRuleRow(
    rule: TemporaryScheduleOverride,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatOverrideRange(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatOverrideSource(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove) {
                Text("删除")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())
                    }
                },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun temporaryOverridesSubtitle(overrides: List<TemporaryScheduleOverride>): String {
    return when {
        overrides.isEmpty() -> "未设置"
        overrides.size == 1 -> formatOverrideSummary(overrides.first())
        else -> "${overrides.size} 条规则 · ${formatOverrideSummary(overrides.last())}"
    }
}

private fun formatOverrideSummary(rule: TemporaryScheduleOverride): String {
    return "${formatOverrideRange(rule)} · ${formatOverrideSource(rule)}"
}

private fun formatOverrideRange(rule: TemporaryScheduleOverride): String {
    val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
    return target?.let(::formatShortDate) ?: "日期无效"
}

private fun formatOverrideSource(rule: TemporaryScheduleOverride): String {
    if (rule.type == TemporaryScheduleOverrideType.CancelCourse) {
        val start = rule.cancelStartNode
        val end = rule.cancelEndNode ?: start
        return if (start != null && end != null) {
            "取消第$start-${end}节"
        } else {
            "取消节次无效"
        }
    }
    val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
    val source = target?.let { resolveTemporaryScheduleSourceDate(it, listOf(rule)) }
    return if (source != null) {
        "按${formatLongDate(source)}课上"
    } else {
        "来源日期无效"
    }
}

private fun formatShortDate(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth}"

private fun formatLongDate(date: LocalDate): String =
    "${formatShortDate(date)} ${weekdayLabel(date.dayOfWeek.value)}"

private fun parseIsoDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

@Composable
private fun DeveloperDebugSection(
    debugForcedDateTime: LocalDateTime?,
    onSetDeveloperMode: (Boolean) -> Unit,
    onSetDebugForcedDateTime: (LocalDateTime?) -> Unit,
    onExportScheduleMetadata: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingForcedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var showForcedDatePicker by rememberSaveable { mutableStateOf(false) }
    var showForcedTimePicker by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "开发者调试",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "调试时间、导出日志与课表元数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DeveloperActionRow(
            icon = Icons.Rounded.CalendarMonth,
            title = "调试时间",
            subtitle = if (debugForcedDateTime != null) {
                "当前强制为 ${DateTimeFormatter.ofPattern("yyyy/M/d EEEE HH:mm").format(debugForcedDateTime)}"
            } else {
                "使用真实时间"
            },
            onClick = {
                pendingForcedDate = debugForcedDateTime?.toLocalDate() ?: LocalDate.now()
                showForcedDatePicker = true
            },
        )
        if (debugForcedDateTime != null) {
            DeveloperActionRow(
                icon = Icons.Rounded.Restore,
                title = "复原真实时间",
                subtitle = "课表、小组件与提醒恢复使用系统时间",
                onClick = {
                    onSetDebugForcedDateTime(null)
                    Toast.makeText(context, "已恢复真实时间", Toast.LENGTH_SHORT).show()
                },
            )
        }
        DeveloperActionRow(
            icon = Icons.Rounded.Download,
            title = "导出日志",
            subtitle = "导出完整 logcat、App 与插件诊断日志",
            onClick = {
                scope.launch {
                    val intent = LogExporter.exportRecentLogs(context)
                    if (intent != null) {
                        runCatching {
                            val chooser = Intent.createChooser(intent, "导出日志").apply {
                                clipData = intent.clipData
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }.onFailure {
                            Toast.makeText(context, "无法启动分享：${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "导出日志失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
        DeveloperActionRow(
            icon = Icons.Rounded.Delete,
            title = "清空日志",
            subtitle = "清理 App 自有诊断与已导出日志",
            onClick = {
                scope.launch {
                    if (LogExporter.clearLogs(context)) {
                        Toast.makeText(context, "已清空日志", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "清空日志失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
        DeveloperActionRow(
            icon = Icons.Rounded.Schedule,
            title = "导出课表元数据",
            subtitle = "导出当前课表、插件与调试状态",
            onClick = onExportScheduleMetadata,
        )
        DeveloperActionRow(
            icon = Icons.Rounded.BugReport,
            title = "关闭开发者模式",
            subtitle = "隐藏调试入口与工具",
            onClick = {
                onSetDeveloperMode(false)
                Toast.makeText(context, "已关闭开发者模式", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showForcedDatePicker) {
        SettingsDatePickerDialog(
            initial = pendingForcedDate ?: debugForcedDateTime?.toLocalDate() ?: LocalDate.now(),
            onConfirm = { date ->
                pendingForcedDate = date
                showForcedDatePicker = false
                showForcedTimePicker = true
            },
            onDismiss = { showForcedDatePicker = false },
        )
    }

    if (showForcedTimePicker) {
        val baseDate = pendingForcedDate ?: debugForcedDateTime?.toLocalDate() ?: LocalDate.now()
        ForcedTimePickerDialog(
            initial = debugForcedDateTime?.toLocalTime() ?: LocalTime.of(8, 0),
            onDismiss = { showForcedTimePicker = false },
            onConfirm = { time ->
                val combined = LocalDateTime.of(baseDate, time)
                onSetDebugForcedDateTime(combined)
                showForcedTimePicker = false
                Toast.makeText(
                    context,
                    "已强制时间：${DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").format(combined)}",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

}

@Composable
private fun DeveloperActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SettingsActionRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForcedTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择调试时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
fun SettingsRoute(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier,
) {
    ScheduleSettingsRoute(
        viewModel = viewModel,
        modifier = modifier,
    )
}
