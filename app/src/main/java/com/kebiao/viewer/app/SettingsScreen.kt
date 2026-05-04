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
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.feature.schedule.ScheduleSettingsRoute
import com.kebiao.viewer.feature.schedule.ScheduleViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AppSettingsRoute(
    themeMode: ThemeMode,
    themeAccentLabel: String,
    termStartDate: LocalDate?,
    timeZoneLabel: String,
    currentWeekIndex: Int,
    totalScheduleDisplayEnabled: Boolean,
    onPickThemeMode: () -> Unit,
    onPickThemeAccent: () -> Unit,
    onPickTermStartDate: () -> Unit,
    onPickCurrentWeek: () -> Unit,
    onClearTermStartDate: () -> Unit,
    onPickTimeZone: () -> Unit,
    onTotalScheduleDisplayChange: (Boolean) -> Unit,
    onOpenWidgetPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsActionRow(
            icon = Icons.Rounded.Widgets,
            title = "桌面小组件",
            subtitle = "添加课表、下一节课或课程提醒",
            onClick = onOpenWidgetPicker,
        )
    }
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
