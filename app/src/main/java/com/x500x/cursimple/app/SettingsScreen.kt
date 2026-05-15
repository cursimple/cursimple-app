package com.x500x.cursimple.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.core.data.ScheduleBackgroundPreferences
import com.x500x.cursimple.core.data.ScheduleBackgroundType
import com.x500x.cursimple.core.data.ScheduleCardStylePreferences
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.ScheduleTextStylePreferences
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.DEFAULT_WEBDAV_URL
import com.x500x.cursimple.app.reminder.AlarmPermissionIntents
import com.x500x.cursimple.app.util.LogExporter
import com.x500x.cursimple.app.webdav.WebDavConfig
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.widget.WidgetBackgroundMode
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.feature.schedule.ScheduleSettingsRoute
import com.x500x.cursimple.feature.schedule.ScheduleViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

private enum class SettingsDestination {
    Root,
    Application,
    ScheduleData,
    TemporaryOverrides,
    ScheduleSettings,
    ScheduleAppearance,
    ScheduleTextStyle,
    ScheduleHeaderStyle,
    ScheduleCardStyle,
    ScheduleBackground,
    ScheduleDisplay,
    WidgetSettings,
    Plugins,
    WebDav,
    AiImport,
    Permissions,
}

enum class SettingsDestinationKey {
    WebDav,
    AiImport,
}

enum class SettingsReturnTargetKey {
    ImportExport,
}

private fun SettingsDestinationKey.toDestination(): SettingsDestination = when (this) {
    SettingsDestinationKey.WebDav -> SettingsDestination.WebDav
    SettingsDestinationKey.AiImport -> SettingsDestination.AiImport
}

private fun SettingsDestination.title(): String = when (this) {
    SettingsDestination.Root -> "设置"
    SettingsDestination.Application -> "应用"
    SettingsDestination.ScheduleData -> "课表数据"
    SettingsDestination.TemporaryOverrides -> "临时调课"
    SettingsDestination.ScheduleSettings -> "课表设置"
    SettingsDestination.ScheduleAppearance -> "外观"
    SettingsDestination.ScheduleTextStyle -> "文字样式"
    SettingsDestination.ScheduleHeaderStyle -> "表头"
    SettingsDestination.ScheduleCardStyle -> "卡片样式"
    SettingsDestination.ScheduleBackground -> "课表背景"
    SettingsDestination.ScheduleDisplay -> "显示"
    SettingsDestination.WidgetSettings -> "小组件设置"
    SettingsDestination.Plugins -> "插件"
    SettingsDestination.WebDav -> "WebDAV"
    SettingsDestination.AiImport -> "AI 识图导入"
    SettingsDestination.Permissions -> "权限"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsRoute(
    themeMode: ThemeMode,
    themeAccentLabel: String,
    termStartDate: LocalDate?,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    widgetThemePreferences: WidgetThemePreferences,
    currentWeekIndex: Int,
    alarmBackend: ReminderAlarmBackend,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    autoUpdateEnabled: Boolean,
    ignoredUpdateVersionCode: Int?,
    pluginMarketIndexUrl: String,
    componentMarketIndexUrl: String,
    privateFilesProviderEnabled: Boolean,
    webDavUrl: String,
    webDavUsername: String,
    webDavPassword: String,
    aiImportApiUrl: String,
    aiImportApiKey: String,
    aiImportModel: String,
    developerModeEnabled: Boolean,
    debugForcedDateTime: LocalDateTime?,
    onPickThemeMode: () -> Unit,
    onPickThemeAccent: () -> Unit,
    onPickTermStartDate: () -> Unit,
    onPickCurrentWeek: () -> Unit,
    onClearTermStartDate: () -> Unit,
    onScheduleCourseTextSizeSpChange: (Int) -> Unit,
    onScheduleCourseTextColorArgbChange: (Long) -> Unit,
    onScheduleExamTextSizeSpChange: (Int) -> Unit,
    onScheduleExamTextColorArgbChange: (Long) -> Unit,
    onScheduleHeaderTextSizeSpChange: (Int) -> Unit,
    onScheduleHeaderTextColorArgbChange: (Long) -> Unit,
    onScheduleTodayHeaderBackgroundColorArgbChange: (Long) -> Unit,
    onScheduleTextHorizontalCenterChange: (Boolean) -> Unit,
    onScheduleTextVerticalCenterChange: (Boolean) -> Unit,
    onScheduleTextFullCenterChange: (Boolean) -> Unit,
    onScheduleCourseCornerRadiusDpChange: (Int) -> Unit,
    onScheduleCourseCardHeightDpChange: (Int) -> Unit,
    onScheduleOpacityPercentChange: (Int) -> Unit,
    onScheduleInactiveCourseOpacityPercentChange: (Int) -> Unit,
    onScheduleGridBorderColorArgbChange: (Long) -> Unit,
    onScheduleGridBorderOpacityPercentChange: (Int) -> Unit,
    onScheduleGridBorderWidthDpChange: (Float) -> Unit,
    onScheduleGridBorderDashedChange: (Boolean) -> Unit,
    onScheduleBackgroundColorArgbChange: (Long) -> Unit,
    onScheduleBackgroundImageUriChange: (String) -> Unit,
    onClearScheduleBackgroundImage: () -> Unit,
    onScheduleBackgroundUseHeaderColor: () -> Unit,
    onScheduleNodeColumnTimeEnabledChange: (Boolean) -> Unit,
    onScheduleSaturdayVisibleChange: (Boolean) -> Unit,
    onScheduleWeekendVisibleChange: (Boolean) -> Unit,
    onScheduleLocationVisibleChange: (Boolean) -> Unit,
    onScheduleLocationPrefixAtEnabledChange: (Boolean) -> Unit,
    onScheduleTeacherVisibleChange: (Boolean) -> Unit,
    onTotalScheduleDisplayChange: (Boolean) -> Unit,
    onAlarmBackendChange: (ReminderAlarmBackend) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
    onUpsertTemporaryScheduleOverride: (TemporaryScheduleOverride) -> Unit,
    onRemoveTemporaryScheduleOverride: (String) -> Unit,
    onClearTemporaryScheduleOverrides: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onPickWidgetThemeAccent: () -> Unit,
    onWidgetBackgroundImageUriChange: (String) -> Unit,
    onClearWidgetBackgroundImage: () -> Unit,
    onWidgetOpenAppOnDoubleClickChange: (Boolean) -> Unit,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
    onIgnoreUpdateVersion: (Int?) -> Unit,
    onPluginMarketIndexUrlChange: (String) -> Unit,
    onComponentMarketIndexUrlChange: (String) -> Unit,
    onPrivateFilesProviderEnabledChange: (Boolean) -> Unit,
    onWebDavSettingsChange: (String, String, String) -> Unit,
    onTestWebDavSettings: suspend (WebDavConfig) -> Result<Unit>,
    onAiImportSettingsChange: (String, String, String) -> Unit,
    onSetDeveloperMode: (Boolean) -> Unit,
    onSetDebugForcedDateTime: (LocalDateTime?) -> Unit,
    onExportScheduleMetadata: () -> Unit,
    onResetScheduleAppearanceAndDisplay: () -> Unit,
    onResetAllSettings: () -> Unit,
    openDestination: SettingsDestinationKey? = null,
    onOpenDestinationConsumed: () -> Unit = {},
    returnTarget: SettingsReturnTargetKey? = null,
    onReturnTargetReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var backStack by rememberSaveable { mutableStateOf(listOf(SettingsDestination.Root.name)) }
    var settingsReturnReady by rememberSaveable { mutableStateOf(false) }
    val destination = SettingsDestination.valueOf(backStack.last())
    fun navigate(next: SettingsDestination) {
        backStack = backStack + next.name
    }
    fun savedDestinationConfigComplete(): Boolean = when (destination) {
        SettingsDestination.WebDav -> WebDavConfig(webDavUrl, webDavUsername, webDavPassword).isComplete
        SettingsDestination.AiImport -> aiImportApiUrl.isNotBlank() && aiImportApiKey.isNotBlank()
        else -> false
    }
    fun goBack() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }
    fun handleBack() {
        if (
            returnTarget == SettingsReturnTargetKey.ImportExport &&
            (settingsReturnReady || savedDestinationConfigComplete())
        ) {
            onReturnTargetReady()
        } else {
            goBack()
        }
    }
    androidx.compose.runtime.LaunchedEffect(openDestination) {
        val requested = openDestination?.toDestination() ?: return@LaunchedEffect
        backStack = listOf(SettingsDestination.Root.name, requested.name)
        settingsReturnReady = false
        onOpenDestinationConsumed()
    }
    BackHandler(enabled = destination != SettingsDestination.Root) {
        handleBack()
    }
    var showTemporaryOverrides by rememberSaveable { mutableStateOf(false) }
    var showAlarmBackendDialog by rememberSaveable { mutableStateOf(false) }
    var showResetScheduleAppearanceConfirm by rememberSaveable { mutableStateOf(false) }
    var showResetAllSettingsConfirm by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(context, if (granted) "通知权限已开启" else "通知权限未开启", Toast.LENGTH_SHORT).show()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(context, if (granted) "相机权限已开启" else "相机权限未开启", Toast.LENGTH_SHORT).show()
    }
    val scheduleBackgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (persisted) {
                onScheduleBackgroundImageUriChange(uri.toString())
            } else {
                Toast.makeText(context, "背景图片授权失败，请重新选择", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val widgetBackgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (persisted) {
                onWidgetBackgroundImageUriChange(uri.toString())
            } else {
                Toast.makeText(context, "小组件背景图片授权失败，请重新选择", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (destination == SettingsDestination.Root) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                IconButton(onClick = ::handleBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = destination.title(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when (destination) {
            SettingsDestination.Root -> {
                SettingsActionRow(Icons.Rounded.Palette, "应用", "主题和外观", { navigate(SettingsDestination.Application) })
                SettingsActionRow(Icons.Rounded.CalendarMonth, "课表数据", "开学日期和当前周", { navigate(SettingsDestination.ScheduleData) })
                SettingsActionRow(Icons.Rounded.EventRepeat, "临时调课", temporaryOverridesSubtitle(temporaryScheduleOverrides), {
                    navigate(SettingsDestination.TemporaryOverrides)
                })
                SettingsActionRow(Icons.AutoMirrored.Rounded.MenuBook, "课表设置", "外观和显示", {
                    navigate(SettingsDestination.ScheduleSettings)
                })
                SettingsActionRow(Icons.Rounded.Widgets, "小组件设置", "主题、桌面小组件和背景", {
                    navigate(SettingsDestination.WidgetSettings)
                })
                SettingsActionRow(Icons.Rounded.Code, "插件", "市场索引和组件索引", {
                    navigate(SettingsDestination.Plugins)
                })
                SettingsActionRow(Icons.Rounded.Storage, "WebDAV", webDavSettingsSubtitle(webDavUrl, webDavUsername), {
                    navigate(SettingsDestination.WebDav)
                })
                SettingsActionRow(Icons.Rounded.ImageSearch, "AI 识图导入", aiImportSettingsSubtitle(aiImportApiUrl, aiImportModel), {
                    navigate(SettingsDestination.AiImport)
                })
                SettingsActionRow(Icons.Rounded.Notifications, "权限", "通知、闹钟、相机和安装权限", {
                    navigate(SettingsDestination.Permissions)
                })
                SettingsActionRow(
                    icon = Icons.Rounded.Restore,
                    title = "恢复所有设置",
                    subtitle = "恢复应用、课表外观显示和小组件设置",
                    onClick = { showResetAllSettingsConfirm = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                UpdateCheckSection(
                    autoCheckEnabled = autoUpdateEnabled,
                    ignoredUpdateVersionCode = ignoredUpdateVersionCode,
                    onAutoCheckEnabledChange = onAutoUpdateEnabledChange,
                    onIgnoreUpdateVersion = onIgnoreUpdateVersion,
                )
            }

            SettingsDestination.Application -> {
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
            }

            SettingsDestination.ScheduleData -> {
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
            }

            SettingsDestination.TemporaryOverrides -> {
                SettingsActionRow(
                    icon = Icons.Rounded.EventRepeat,
                    title = "管理调课规则",
                    subtitle = temporaryOverridesSubtitle(temporaryScheduleOverrides),
                    onClick = { showTemporaryOverrides = true },
                )
                temporaryScheduleOverrides.forEach { rule ->
                    SettingsActionRow(
                        icon = Icons.Rounded.Schedule,
                        title = formatOverrideRange(rule),
                        subtitle = formatOverrideSource(rule),
                        onClick = { showTemporaryOverrides = true },
                    )
                }
            }

            SettingsDestination.ScheduleSettings -> {
                SettingsActionRow(Icons.Rounded.Palette, "外观", "文字、卡片和背景", {
                    navigate(SettingsDestination.ScheduleAppearance)
                })
                SettingsActionRow(Icons.AutoMirrored.Rounded.MenuBook, "显示", "列、地点、老师和总课表", {
                    navigate(SettingsDestination.ScheduleDisplay)
                })
                SettingsActionRow(
                    icon = Icons.Rounded.Restore,
                    title = "恢复课表外观/显示默认值",
                    subtitle = "恢复文字、表头、卡片、背景和显示选项",
                    onClick = { showResetScheduleAppearanceConfirm = true },
                )
            }

            SettingsDestination.ScheduleAppearance -> {
                SettingsActionRow(Icons.Rounded.Palette, "文字样式", "课程和考试文字", {
                    navigate(SettingsDestination.ScheduleTextStyle)
                })
                SettingsActionRow(Icons.Rounded.CalendarMonth, "表头", "表头文字和当前天背景", {
                    navigate(SettingsDestination.ScheduleHeaderStyle)
                })
                SettingsActionRow(Icons.Rounded.Tune, "卡片样式", "圆角、高度、透明度和边框", {
                    navigate(SettingsDestination.ScheduleCardStyle)
                })
                SettingsActionRow(Icons.Rounded.Download, "课表背景", backgroundSubtitle(scheduleBackground), {
                    navigate(SettingsDestination.ScheduleBackground)
                })
            }

            SettingsDestination.ScheduleTextStyle -> {
                NumberStepperRow("课程文字大小", scheduleTextStyle.courseTextSizeSp, "sp", 8, 32, 1, onScheduleCourseTextSizeSpChange)
                ColorAlphaRow("课程文字颜色", scheduleTextStyle.courseTextColorArgb, onScheduleCourseTextColorArgbChange)
                NumberStepperRow("考试文字大小", scheduleTextStyle.examTextSizeSp, "sp", 8, 32, 1, onScheduleExamTextSizeSpChange)
                ColorAlphaRow("考试文字颜色", scheduleTextStyle.examTextColorArgb, onScheduleExamTextColorArgbChange)
                SettingsSwitchRow(Icons.Rounded.Tune, "格子文字水平居中", "课程卡片文字水平居中", scheduleTextStyle.horizontalCenter, onScheduleTextHorizontalCenterChange)
                SettingsSwitchRow(Icons.Rounded.Tune, "格子文字竖直居中", "课程卡片内容竖直居中", scheduleTextStyle.verticalCenter, onScheduleTextVerticalCenterChange)
                SettingsSwitchRow(Icons.Rounded.Tune, "格子文字完全居中", "同时启用水平与竖直居中", scheduleTextStyle.fullCenter, onScheduleTextFullCenterChange)
            }

            SettingsDestination.ScheduleHeaderStyle -> {
                NumberStepperRow("表头文字大小", scheduleTextStyle.headerTextSizeSp, "sp", 8, 32, 1, onScheduleHeaderTextSizeSpChange)
                ColorAlphaRow(
                    "表头文字颜色",
                    scheduleTextStyle.resolvedHeaderTextColorArgb(darkTheme),
                    onScheduleHeaderTextColorArgbChange,
                )
                ColorAlphaRow(
                    "当前天表头背景颜色",
                    scheduleTextStyle.resolvedTodayHeaderBackgroundColorArgb(darkTheme),
                    onScheduleTodayHeaderBackgroundColorArgbChange,
                )
            }

            SettingsDestination.ScheduleCardStyle -> {
                NumberStepperRow("课程卡片圆角半径", scheduleCardStyle.courseCornerRadiusDp, "dp", 0, 32, 1, onScheduleCourseCornerRadiusDpChange)
                NumberStepperRow("课程卡片高度", scheduleCardStyle.courseCardHeightDp, "dp", 56, 160, 4, onScheduleCourseCardHeightDpChange)
                NumberStepperRow("课表透明度", scheduleCardStyle.scheduleOpacityPercent, "%", 0, 100, 5, onScheduleOpacityPercentChange)
                NumberStepperRow("非本周课程透明度", scheduleCardStyle.inactiveCourseOpacityPercent, "%", 0, 100, 5, onScheduleInactiveCourseOpacityPercentChange)
                ColorAlphaRow("格子边框颜色", scheduleCardStyle.gridBorderColorArgb, onScheduleGridBorderColorArgbChange)
                NumberStepperRow("格子边框透明度", scheduleCardStyle.gridBorderOpacityPercent, "%", 0, 100, 5, onScheduleGridBorderOpacityPercentChange)
                FloatStepperRow("格子边框粗细", scheduleCardStyle.gridBorderWidthDp, "dp", 0f, 4f, 0.5f, onScheduleGridBorderWidthDpChange)
                SettingsSwitchRow(Icons.Rounded.Tune, "格子边框虚线", "关闭时使用实线", scheduleCardStyle.gridBorderDashed, onScheduleGridBorderDashedChange)
            }

            SettingsDestination.ScheduleBackground -> {
                ColorAlphaRow("背景颜色", scheduleBackground.colorArgb, onScheduleBackgroundColorArgbChange)
                SettingsActionRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = "与表头背景一致",
                    subtitle = if (scheduleBackground.type == ScheduleBackgroundType.Header) {
                        "当前已使用表头区域的深浅模式底色"
                    } else {
                        "使用表头区域的深浅模式底色作为课表背景"
                    },
                    onClick = onScheduleBackgroundUseHeaderColor,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Download,
                    title = "背景图片",
                    subtitle = scheduleBackground.imageUri?.let { "已选择图片" } ?: "未选择",
                    onClick = { scheduleBackgroundLauncher.launch(arrayOf("image/*")) },
                )
                if (scheduleBackground.type == ScheduleBackgroundType.Image || scheduleBackground.imageUri != null) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Delete,
                        title = "清除背景图片",
                        subtitle = "恢复使用背景颜色",
                        onClick = onClearScheduleBackgroundImage,
                    )
                }
            }

            SettingsDestination.ScheduleDisplay -> {
                SettingsSwitchRow(Icons.Rounded.Schedule, "节数栏显示时间", "显示每节课的起止时间", scheduleDisplay.nodeColumnTimeEnabled, onScheduleNodeColumnTimeEnabledChange)
                SettingsSwitchRow(
                    Icons.Rounded.CalendarMonth,
                    "显示周六",
                    "周一到周六视图",
                    scheduleDisplay.saturdayVisible || scheduleDisplay.weekendVisible,
                    onScheduleSaturdayVisibleChange,
                )
                SettingsSwitchRow(
                    Icons.Rounded.CalendarMonth,
                    "显示周末",
                    "开启后同时显示周六和周日",
                    scheduleDisplay.weekendVisible,
                    {
                        onScheduleWeekendVisibleChange(it)
                        if (it) onScheduleSaturdayVisibleChange(true)
                    },
                )
                SettingsSwitchRow(Icons.Rounded.Schedule, "显示上课地点", "课程卡片和日视图显示地点", scheduleDisplay.locationVisible, onScheduleLocationVisibleChange)
                SettingsSwitchRow(Icons.Rounded.Schedule, "上课地点前加 @", "关闭后直接显示地点名称", scheduleDisplay.locationPrefixAtEnabled, onScheduleLocationPrefixAtEnabledChange)
                SettingsSwitchRow(Icons.Rounded.Schedule, "显示授课老师", "课程卡片显示教师名称", scheduleDisplay.teacherVisible, onScheduleTeacherVisibleChange)
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    title = "总课表显示",
                    subtitle = if (scheduleDisplay.totalScheduleDisplayEnabled) {
                        "周视图显示本学期全部课程，非本周课程置灰标注"
                    } else {
                        "周视图只显示本周课程"
                    },
                    checked = scheduleDisplay.totalScheduleDisplayEnabled,
                    onCheckedChange = onTotalScheduleDisplayChange,
                )
            }

            SettingsDestination.WidgetSettings -> {
                SettingsActionRow(
                    icon = Icons.Rounded.Palette,
                    title = "主题",
                    subtitle = widgetThemeLabel(widgetThemePreferences),
                    onClick = onPickWidgetThemeAccent,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Widgets,
                    title = "桌面小组件",
                    subtitle = "添加课表、下一节课或课程提醒",
                    onClick = onOpenWidgetPicker,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Schedule,
                    title = "双击小组件打开 App",
                    subtitle = "开启后双击小组件主体进入应用",
                    checked = widgetThemePreferences.openAppOnDoubleClickEnabled,
                    onCheckedChange = onWidgetOpenAppOnDoubleClickChange,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Download,
                    title = "背景选择",
                    subtitle = widgetThemePreferences.backgroundImageUri?.let { "已选择图片" } ?: "使用主题背景",
                    onClick = { widgetBackgroundLauncher.launch(arrayOf("image/*")) },
                )
                if (widgetThemePreferences.backgroundMode == WidgetBackgroundMode.Image ||
                    widgetThemePreferences.backgroundImageUri != null
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Delete,
                        title = "清除小组件背景",
                        subtitle = "恢复使用小组件主题",
                        onClick = onClearWidgetBackgroundImage,
                    )
                }
            }

            SettingsDestination.Plugins -> {
                PluginSettingsSection(
                    pluginMarketIndexUrl = pluginMarketIndexUrl,
                    componentMarketIndexUrl = componentMarketIndexUrl,
                    onPluginMarketIndexUrlChange = onPluginMarketIndexUrlChange,
                    onComponentMarketIndexUrlChange = onComponentMarketIndexUrlChange,
                )
            }

            SettingsDestination.WebDav -> {
                WebDavSettingsSection(
                    webDavUrl = webDavUrl,
                    webDavUsername = webDavUsername,
                    webDavPassword = webDavPassword,
                    onSave = onWebDavSettingsChange,
                    onTest = onTestWebDavSettings,
                    onSaved = { complete -> settingsReturnReady = complete },
                )
            }

            SettingsDestination.AiImport -> {
                AiImportSettingsSection(
                    apiUrl = aiImportApiUrl,
                    apiKey = aiImportApiKey,
                    model = aiImportModel,
                    onSave = onAiImportSettingsChange,
                    onSaved = { complete -> settingsReturnReady = complete },
                )
            }

            SettingsDestination.Permissions -> {
                PermissionsSection(
                    notificationLauncher = notificationLauncher::launch,
                    cameraLauncher = cameraLauncher::launch,
                )
            }
        }

        if (showResetScheduleAppearanceConfirm) {
            AlertDialog(
                onDismissRequest = { showResetScheduleAppearanceConfirm = false },
                title = { Text("恢复课表外观/显示") },
                text = { Text("将恢复文字、表头、卡片、背景和显示选项默认值，不会清除课程数据。确定继续？") },
                confirmButton = {
                    TextButton(onClick = {
                        onResetScheduleAppearanceAndDisplay()
                        showResetScheduleAppearanceConfirm = false
                        Toast.makeText(context, "课表外观/显示已恢复默认", Toast.LENGTH_SHORT).show()
                    }) { Text("恢复") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetScheduleAppearanceConfirm = false }) { Text("取消") }
                },
            )
        }

        if (showResetAllSettingsConfirm) {
            AlertDialog(
                onDismissRequest = { showResetAllSettingsConfirm = false },
                title = { Text("恢复所有设置") },
                text = { Text("将恢复应用、课表外观显示、小组件、提醒运行参数和更新设置，不会清除课表数据。确定继续？") },
                confirmButton = {
                    TextButton(onClick = {
                        onResetAllSettings()
                        showResetAllSettingsConfirm = false
                        Toast.makeText(context, "所有设置已恢复默认", Toast.LENGTH_SHORT).show()
                    }) { Text("恢复") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetAllSettingsConfirm = false }) { Text("取消") }
                },
            )
        }

        if (developerModeEnabled && destination == SettingsDestination.Root) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeveloperDebugSection(
                debugForcedDateTime = debugForcedDateTime,
                privateFilesProviderEnabled = privateFilesProviderEnabled,
                onSetDeveloperMode = onSetDeveloperMode,
                onPrivateFilesProviderEnabledChange = onPrivateFilesProviderEnabledChange,
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
private fun NumberStepperRow(
    title: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    AlarmNumberSettingRow(
        title = title,
        value = value,
        unit = unit,
        min = min,
        max = max,
        step = step,
        onValueChange = onValueChange,
    )
}

@Composable
private fun FloatStepperRow(
    title: String,
    value: Float,
    unit: String,
    min: Float,
    max: Float,
    step: Float,
    onValueChange: (Float) -> Unit,
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
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${formatFloat(value)} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { onValueChange((value - step).coerceIn(min, max)) },
                enabled = value > min,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("-") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onValueChange((value + step).coerceIn(min, max)) },
                enabled = value < max,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("+") }
        }
    }
}

@Composable
private fun ColorAlphaRow(
    title: String,
    argb: Long,
    onValueChange: (Long) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    SettingsActionRow(
        icon = Icons.Rounded.Palette,
        title = title,
        subtitle = "${formatArgb(argb)} · 透明度 ${argbTransparencyPercent(argb)}%",
        onClick = { showPicker = true },
        trailing = {
            Surface(
                modifier = Modifier.size(28.dp),
                color = Color(argb),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {}
        },
    )
    if (showPicker) {
        ColorPickerDialog(
            title = title,
            initialArgb = argb,
            onDismiss = { showPicker = false },
            onConfirm = { value ->
                onValueChange(value)
                showPicker = false
            },
        )
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initialArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val normalized = initialArgb and 0xFFFF_FFFFL
    var alpha by rememberSaveable(normalized) { mutableStateOf(argbAlphaByte(normalized)) }
    var red by rememberSaveable(normalized) { mutableStateOf(argbRedByte(normalized)) }
    var green by rememberSaveable(normalized) { mutableStateOf(argbGreenByte(normalized)) }
    var blue by rememberSaveable(normalized) { mutableStateOf(argbBlueByte(normalized)) }
    var hexText by rememberSaveable(normalized) { mutableStateOf(formatArgb(normalized)) }

    fun currentArgb(): Long = argbFromComponents(alpha, red, green, blue)
    fun syncHex() {
        hexText = formatArgb(currentArgb())
    }
    fun applyParsed(value: Long) {
        val color = value and 0xFFFF_FFFFL
        alpha = argbAlphaByte(color)
        red = argbRedByte(color)
        green = argbGreenByte(color)
        blue = argbBlueByte(color)
        hexText = formatArgb(color)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    color = Color(currentArgb()),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {}
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { value ->
                        hexText = value
                        parseArgbInput(value, alpha)?.let(::applyParsed)
                    },
                    label = { Text("ARGB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorComponentSlider("红色", red, 255) {
                    red = it
                    syncHex()
                }
                ColorComponentSlider("绿色", green, 255) {
                    green = it
                    syncHex()
                }
                ColorComponentSlider("蓝色", blue, 255) {
                    blue = it
                    syncHex()
                }
                ColorComponentSlider("透明度", alphaToTransparencyPercent(alpha), 100) {
                    alpha = transparencyPercentToAlpha(it)
                    syncHex()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentArgb()) }) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ColorComponentSlider(
    label: String,
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (max == 100) "$value%" else value.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, max)) },
            valueRange = 0f..max.toFloat(),
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PermissionsSection(
    notificationLauncher: (String) -> Unit,
    cameraLauncher: (String) -> Unit,
) {
    val context = LocalContext.current
    val alarmManager = remember(context) { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val notificationManager = remember(context) { context.getSystemService(NotificationManager::class.java) }
    val exactAlarmEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    val notificationEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    val fullScreenIntentEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        notificationManager.canUseFullScreenIntent()
    val batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    val cameraEnabled = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    val installPackagesEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

    SettingsSectionHeader("授权")
    SettingsActionRow(
        icon = Icons.Rounded.Notifications,
        title = "通知权限",
        subtitle = if (notificationEnabled) "已开启" else "未开启，响铃通知可能无法显示",
        onClick = {
            if (notificationEnabled) {
                Toast.makeText(context, "通知权限已开启", Toast.LENGTH_SHORT).show()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchSettingsIntent(context, AlarmPermissionIntents.appDetailsIntent(context))
            }
        },
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
    SettingsActionRow(
        icon = Icons.Rounded.Code,
        title = "相机权限",
        subtitle = if (cameraEnabled) "已开启" else "扫码导入课表时需要",
        onClick = {
            if (cameraEnabled) {
                Toast.makeText(context, "相机权限已开启", Toast.LENGTH_SHORT).show()
            } else {
                cameraLauncher(Manifest.permission.CAMERA)
            }
        },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Download,
        title = "安装未知应用权限",
        subtitle = if (installPackagesEnabled) "已允许安装更新包" else "APK 更新安装时需要",
        onClick = {
            if (installPackagesEnabled) {
                Toast.makeText(context, "安装未知应用权限已开启", Toast.LENGTH_SHORT).show()
            } else {
                launchSettingsIntent(context, unknownAppInstallSettingsIntent(context))
            }
        },
    )
    SettingsSectionHeader("声明")
    SettingsActionRow(
        icon = Icons.Rounded.Tune,
        title = "网络、唤醒与震动",
        subtitle = "这些为安装时声明权限，无需单独授权",
        onClick = {
            Toast.makeText(context, "无需单独授权", Toast.LENGTH_SHORT).show()
        },
    )
}

private fun formatArgb(argb: Long): String = "#%08X".format(argb and 0xFFFF_FFFFL)

private fun argbAlphaPercent(argb: Long): Int = (((argb ushr 24) and 0xFF) * 100 / 255).toInt()

private fun argbTransparencyPercent(argb: Long): Int = 100 - argbAlphaPercent(argb)

private fun argbAlphaByte(argb: Long): Int = ((argb ushr 24) and 0xFF).toInt()

private fun argbRedByte(argb: Long): Int = ((argb ushr 16) and 0xFF).toInt()

private fun argbGreenByte(argb: Long): Int = ((argb ushr 8) and 0xFF).toInt()

private fun argbBlueByte(argb: Long): Int = (argb and 0xFF).toInt()

private fun alphaToTransparencyPercent(alpha: Int): Int =
    100 - (alpha.coerceIn(0, 255) * 100 / 255)

private fun transparencyPercentToAlpha(transparencyPercent: Int): Int =
    ((100 - transparencyPercent.coerceIn(0, 100)) * 255 / 100).coerceIn(0, 255)

private fun argbFromComponents(alpha: Int, red: Int, green: Int, blue: Int): Long =
    ((alpha.coerceIn(0, 255).toLong() shl 24) or
        (red.coerceIn(0, 255).toLong() shl 16) or
        (green.coerceIn(0, 255).toLong() shl 8) or
        blue.coerceIn(0, 255).toLong()) and 0xFFFF_FFFFL

private fun parseArgbInput(input: String, fallbackAlpha: Int): Long? {
    val raw = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    if (raw.length != 6 && raw.length != 8) return null
    val value = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) {
        argbFromComponents(
            alpha = fallbackAlpha,
            red = ((value ushr 16) and 0xFF).toInt(),
            green = ((value ushr 8) and 0xFF).toInt(),
            blue = (value and 0xFF).toInt(),
        )
    } else {
        value and 0xFFFF_FFFFL
    }
}

private fun ScheduleTextStylePreferences.resolvedHeaderTextColorArgb(darkTheme: Boolean): Long =
    if (headerTextColorCustomized) {
        headerTextColorArgb
    } else if (darkTheme) {
        ScheduleTextStylePreferences.DEFAULT_DARK_HEADER_TEXT_COLOR_ARGB
    } else {
        ScheduleTextStylePreferences.DEFAULT_HEADER_TEXT_COLOR_ARGB
    }

private fun ScheduleTextStylePreferences.resolvedTodayHeaderBackgroundColorArgb(darkTheme: Boolean): Long =
    if (todayHeaderBackgroundColorCustomized) {
        todayHeaderBackgroundColorArgb
    } else if (darkTheme) {
        ScheduleTextStylePreferences.DEFAULT_DARK_TODAY_HEADER_BACKGROUND_COLOR_ARGB
    } else {
        ScheduleTextStylePreferences.DEFAULT_TODAY_HEADER_BACKGROUND_COLOR_ARGB
    }

private fun formatFloat(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

private fun backgroundSubtitle(background: ScheduleBackgroundPreferences): String = when (background.type) {
    ScheduleBackgroundType.Color -> "颜色 ${formatArgb(background.colorArgb)}"
    ScheduleBackgroundType.Image -> background.imageUri?.let { "图片背景" } ?: "图片未选择"
    ScheduleBackgroundType.Header -> "与表头区域背景一致"
}

private fun widgetThemeLabel(preferences: WidgetThemePreferences): String =
    if (preferences.backgroundMode == WidgetBackgroundMode.Image) {
        "图片背景"
    } else {
        themeAccentDisplayName(preferences.themeAccent)
    }

private fun themeAccentDisplayName(accent: ThemeAccent): String = when (accent) {
    ThemeAccent.Green -> "薄荷绿"
    ThemeAccent.Blue -> "海岸蓝"
    ThemeAccent.Purple -> "暮霭紫"
    ThemeAccent.Orange -> "暖陶橙"
    ThemeAccent.Pink -> "樱花粉"
}

private fun unknownAppInstallSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    } else {
        AlarmPermissionIntents.appDetailsIntent(context)
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

private fun webDavSettingsSubtitle(url: String, username: String): String {
    val hasAccount = username.isNotBlank()
    return if (hasAccount) {
        "${url.ifBlank { DEFAULT_WEBDAV_URL }} · 已配置账号"
    } else {
        "${url.ifBlank { DEFAULT_WEBDAV_URL }} · 未配置账号"
    }
}

private fun aiImportSettingsSubtitle(apiUrl: String, model: String): String {
    return when {
        apiUrl.isBlank() -> "未配置 API URL 和 Key"
        model.isNotBlank() -> "$model · 已配置 API"
        else -> "已配置 API"
    }
}

@Composable
private fun PluginSettingsSection(
    pluginMarketIndexUrl: String,
    componentMarketIndexUrl: String,
    onPluginMarketIndexUrlChange: (String) -> Unit,
    onComponentMarketIndexUrlChange: (String) -> Unit,
) {
    val context = LocalContext.current
    var pluginUrlDraft by rememberSaveable(pluginMarketIndexUrl) {
        mutableStateOf(pluginMarketIndexUrl)
    }
    var componentUrlDraft by rememberSaveable(componentMarketIndexUrl) {
        mutableStateOf(componentMarketIndexUrl)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MarketIndexUrlEditor(
            title = "插件市场索引",
            value = pluginUrlDraft,
            onValueChange = { pluginUrlDraft = it },
            onSave = {
                onPluginMarketIndexUrlChange(pluginUrlDraft)
                Toast.makeText(context, "插件市场索引已保存", Toast.LENGTH_SHORT).show()
            },
        )
        MarketIndexUrlEditor(
            title = "组件市场索引",
            value = componentUrlDraft,
            onValueChange = { componentUrlDraft = it },
            onSave = {
                onComponentMarketIndexUrlChange(componentUrlDraft)
                Toast.makeText(context, "组件市场索引已保存", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun MarketIndexUrlEditor(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("manifest.json") },
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun WebDavSettingsSection(
    webDavUrl: String,
    webDavUsername: String,
    webDavPassword: String,
    onSave: (String, String, String) -> Unit,
    onTest: suspend (WebDavConfig) -> Result<Unit>,
    onSaved: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlDraft by rememberSaveable(webDavUrl) { mutableStateOf(webDavUrl.ifBlank { DEFAULT_WEBDAV_URL }) }
    var usernameDraft by rememberSaveable(webDavUsername) { mutableStateOf(webDavUsername) }
    var passwordDraft by rememberSaveable(webDavPassword) { mutableStateOf(webDavPassword) }
    var testing by rememberSaveable { mutableStateOf(false) }

    SettingsEditorPanel(title = "WebDAV 连接") {
        OutlinedTextField(
            value = urlDraft,
            onValueChange = { urlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("URL") },
        )
        OutlinedTextField(
            value = usernameDraft,
            onValueChange = { usernameDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("账号") },
        )
        OutlinedTextField(
            value = passwordDraft,
            onValueChange = { passwordDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("密码") },
        )
        Button(
            onClick = {
                onSave(urlDraft, usernameDraft, passwordDraft)
                onSaved(WebDavConfig(urlDraft.trim().ifBlank { DEFAULT_WEBDAV_URL }, usernameDraft.trim(), passwordDraft).isComplete)
                Toast.makeText(context, "WebDAV 设置已保存", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }
        OutlinedButton(
            enabled = !testing,
            onClick = {
                testing = true
                scope.launch {
                    onTest(WebDavConfig(urlDraft, usernameDraft, passwordDraft))
                        .onSuccess { Toast.makeText(context, "WebDAV 连接正常", Toast.LENGTH_SHORT).show() }
                        .onFailure {
                            Toast.makeText(
                                context,
                                "WebDAV 连接失败：${it.message ?: "未知错误"}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    testing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (testing) "测试中..." else "测试连接")
        }
    }
}

@Composable
private fun AiImportSettingsSection(
    apiUrl: String,
    apiKey: String,
    model: String,
    onSave: (String, String, String) -> Unit,
    onSaved: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var apiUrlDraft by rememberSaveable(apiUrl) { mutableStateOf(apiUrl) }
    var apiKeyDraft by rememberSaveable(apiKey) { mutableStateOf(apiKey) }
    var modelDraft by rememberSaveable(model) { mutableStateOf(model) }

    SettingsEditorPanel(title = "AI 识图导入") {
        OutlinedTextField(
            value = apiUrlDraft,
            onValueChange = { apiUrlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API URL") },
        )
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Key") },
        )
        OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("模型（可选）") },
        )
        Button(
            onClick = {
                onSave(apiUrlDraft, apiKeyDraft, modelDraft)
                onSaved(apiUrlDraft.isNotBlank() && apiKeyDraft.isNotBlank())
                Toast.makeText(context, "AI 识图导入设置已保存", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun SettingsEditorPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun DeveloperDebugSection(
    debugForcedDateTime: LocalDateTime?,
    privateFilesProviderEnabled: Boolean,
    onSetDeveloperMode: (Boolean) -> Unit,
    onPrivateFilesProviderEnabledChange: (Boolean) -> Unit,
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
            text = "调试时间、导出日志、课表元数据与私有目录访问。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSwitchRow(
            icon = Icons.Rounded.FolderOpen,
            title = "允许文件管理器访问私有目录",
            subtitle = if (privateFilesProviderEnabled) {
                "已开启，系统文件管理器左侧栏会显示本应用入口"
            } else {
                "关闭后会从系统文件管理器左侧栏隐藏"
            },
            checked = privateFilesProviderEnabled,
            onCheckedChange = onPrivateFilesProviderEnabledChange,
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
    alarmBackend: ReminderAlarmBackend,
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onAlarmBackendChange: (ReminderAlarmBackend) -> Unit,
    onAlarmRingtoneUriChange: (String?) -> Unit,
    onAlarmAlertModeChange: (AlarmAlertMode) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScheduleSettingsRoute(
        viewModel = viewModel,
        alarmBackend = alarmBackend,
        alarmRingtoneUri = alarmRingtoneUri,
        alarmAlertMode = alarmAlertMode,
        alarmRingDurationSeconds = alarmRingDurationSeconds,
        alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
        alarmRepeatCount = alarmRepeatCount,
        onAlarmBackendChange = onAlarmBackendChange,
        onAlarmRingtoneUriChange = onAlarmRingtoneUriChange,
        onAlarmAlertModeChange = onAlarmAlertModeChange,
        onAlarmRingDurationSecondsChange = onAlarmRingDurationSecondsChange,
        onAlarmRepeatIntervalSecondsChange = onAlarmRepeatIntervalSecondsChange,
        onAlarmRepeatCountChange = onAlarmRepeatCountChange,
        onPickSystemRingtone = onPickSystemRingtone,
        onPickLocalAudio = onPickLocalAudio,
        modifier = modifier,
    )
}
