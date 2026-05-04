package com.kebiao.viewer.app

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.kernel.model.weekdayLabel
import com.kebiao.viewer.feature.schedule.ScheduleSettingsRoute
import com.kebiao.viewer.feature.schedule.ScheduleViewModel
import java.time.Instant
import java.time.LocalDate
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
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    onPickThemeMode: () -> Unit,
    onPickThemeAccent: () -> Unit,
    onPickTermStartDate: () -> Unit,
    onPickCurrentWeek: () -> Unit,
    onClearTermStartDate: () -> Unit,
    onPickTimeZone: () -> Unit,
    onTotalScheduleDisplayChange: (Boolean) -> Unit,
    onUpsertTemporaryScheduleOverride: (TemporaryScheduleOverride) -> Unit,
    onRemoveTemporaryScheduleOverride: (String) -> Unit,
    onClearTemporaryScheduleOverrides: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showTemporaryOverrides by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "偏好",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "管理外观、周次和课表显示方式。",
            style = MaterialTheme.typography.bodyMedium,
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
    var startDate by rememberSaveable { mutableStateOf(today) }
    var endDate by rememberSaveable { mutableStateOf(today) }
    var sourceDayOfWeek by rememberSaveable { mutableIntStateOf(today.dayOfWeek.value) }
    var pickStartDate by rememberSaveable { mutableStateOf(false) }
    var pickEndDate by rememberSaveable { mutableStateOf(false) }

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
                    DateChoiceButton(
                        label = "开始",
                        date = startDate,
                        modifier = Modifier.weight(1f),
                        onClick = { pickStartDate = true },
                    )
                    DateChoiceButton(
                        label = "结束",
                        date = endDate,
                        modifier = Modifier.weight(1f),
                        onClick = { pickEndDate = true },
                    )
                }
                WeekdaySelector(
                    selected = sourceDayOfWeek,
                    onSelect = { sourceDayOfWeek = it },
                )
                Button(
                    onClick = {
                        val normalizedStart = minOf(startDate, endDate)
                        val normalizedEnd = maxOf(startDate, endDate)
                        onAdd(
                            TemporaryScheduleOverride(
                                id = UUID.randomUUID().toString(),
                                startDate = normalizedStart.toString(),
                                endDate = normalizedEnd.toString(),
                                sourceDayOfWeek = sourceDayOfWeek,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("添加规则")
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

    if (pickStartDate) {
        SettingsDatePickerDialog(
            initial = startDate,
            onConfirm = {
                startDate = it
                pickStartDate = false
            },
            onDismiss = { pickStartDate = false },
        )
    }
    if (pickEndDate) {
        SettingsDatePickerDialog(
            initial = endDate,
            onConfirm = {
                endDate = it
                pickEndDate = false
            },
            onDismiss = { pickEndDate = false },
        )
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
private fun WeekdaySelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1, 2, 3, 4).let { days ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { day ->
                    FilterChip(
                        selected = selected == day,
                        onClick = { onSelect(day) },
                        label = { Text(weekdayLabel(day)) },
                    )
                }
            }
        }
        listOf(5, 6, 7).let { days ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { day ->
                    FilterChip(
                        selected = selected == day,
                        onClick = { onSelect(day) },
                        label = { Text(weekdayLabel(day)) },
                    )
                }
            }
        }
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
                    text = "按${weekdayLabel(rule.sourceDayOfWeek)}课上",
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
    return "${formatOverrideRange(rule)} · 按${weekdayLabel(rule.sourceDayOfWeek)}课上"
}

private fun formatOverrideRange(rule: TemporaryScheduleOverride): String {
    val start = parseIsoDate(rule.startDate)
    val end = parseIsoDate(rule.endDate)
    if (start == null || end == null) return "日期无效"
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    return if (normalizedStart == normalizedEnd) {
        formatShortDate(normalizedStart)
    } else {
        "${formatShortDate(normalizedStart)} - ${formatShortDate(normalizedEnd)}"
    }
}

private fun formatShortDate(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth}"

private fun parseIsoDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

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
