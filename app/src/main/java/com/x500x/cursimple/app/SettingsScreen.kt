package com.x500x.cursimple.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.core.data.AutoSilenceMode
import com.x500x.cursimple.core.data.AutoSilencePreferences
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ScheduleBackgroundPreferences
import com.x500x.cursimple.core.data.ScheduleBackgroundType
import com.x500x.cursimple.core.data.ScheduleCardStylePreferences
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.ScheduleTextStylePreferences
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.DEFAULT_AI_IMPORT_TIMEOUT_SECONDS
import com.x500x.cursimple.core.data.DEFAULT_WEBDAV_URL
import com.x500x.cursimple.core.data.MAX_AI_IMPORT_TIMEOUT_SECONDS
import com.x500x.cursimple.core.data.MIN_AI_IMPORT_TIMEOUT_SECONDS
import com.x500x.cursimple.core.data.adaptScheduleBackgroundColorArgb
import com.x500x.cursimple.core.data.adaptScheduleForegroundColorArgb
import com.x500x.cursimple.core.data.coerceAiImportTimeoutSeconds
import com.x500x.cursimple.app.reminder.AlarmPermissionIntents
import com.x500x.cursimple.app.reminder.AutoSilenceController
import com.x500x.cursimple.app.util.LogExporter
import com.x500x.cursimple.app.webdav.WebDavConfig
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.MAX_SLOT_NODE
import com.x500x.cursimple.core.data.widget.MIN_SLOT_NODE
import com.x500x.cursimple.core.data.widget.SlotDraftInput
import com.x500x.cursimple.core.data.widget.TimingDraftError
import com.x500x.cursimple.core.data.widget.WidgetBackgroundMode
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.buildTimingSlots
import com.x500x.cursimple.core.data.widget.slotTimes
import com.x500x.cursimple.core.data.widget.timingDraftErrorText
import com.x500x.cursimple.core.data.widget.timingTemplates
import com.x500x.cursimple.core.data.widget.toDraftInput
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.widget.ScheduleWidgetUpdater
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.builtInHolidayYears
import com.x500x.cursimple.core.kernel.model.entryOn
import com.x500x.cursimple.core.kernel.model.localDate
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.sortedUserEntries
import com.x500x.cursimple.core.kernel.model.userEntryOn
import com.x500x.cursimple.core.kernel.model.weekIndexLabel
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.feature.schedule.ScheduleAppearancePreview
import com.x500x.cursimple.feature.schedule.ScheduleSettingsRoute
import com.x500x.cursimple.feature.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt
import com.x500x.cursimple.core.kernel.time.datePickerMillisToLocalDate
import com.x500x.cursimple.core.kernel.time.toDatePickerMillis

private enum class SettingsDestination {
    Root,
    Application,
    ScheduleData,
    TemporaryOverrides,
    Holidays,
    ScheduleSettings,
    ScheduleAppearance,
    ScheduleTextStyle,
    ScheduleHeaderStyle,
    ScheduleCardStyle,
    ScheduleBackground,
    ScheduleDisplay,
    TimingProfile,
    WidgetSettings,
    AutoSilence,
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
    SettingsDestination.Holidays -> "节假日与调休"
    SettingsDestination.ScheduleSettings -> "课表设置"
    SettingsDestination.ScheduleAppearance -> "外观"
    SettingsDestination.ScheduleTextStyle -> "文字样式"
    SettingsDestination.ScheduleHeaderStyle -> "表头"
    SettingsDestination.ScheduleCardStyle -> "卡片样式"
    SettingsDestination.ScheduleBackground -> "课表背景"
    SettingsDestination.ScheduleDisplay -> "显示"
    SettingsDestination.TimingProfile -> "节次上课时间"
    SettingsDestination.WidgetSettings -> "小组件设置"
    SettingsDestination.AutoSilence -> "上课自动静音"
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
    scheduleCustomColorsAdaptToTheme: Boolean,
    widgetThemePreferences: WidgetThemePreferences,
    currentWeekIndex: Int,
    alarmBackend: ReminderAlarmBackend,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings(),
    autoUpdateEnabled: Boolean,
    ignoredUpdateVersionCode: Int?,
    pluginRegistryRepo: String,
    componentMarketIndexUrl: String,
    privateFilesProviderEnabled: Boolean,
    webDavUrl: String,
    webDavUsername: String,
    webDavPassword: String,
    aiImportApiUrl: String,
    aiImportApiKey: String,
    aiImportModel: String,
    aiImportTimeoutSeconds: Int,
    developerModeEnabled: Boolean,
    debugForcedDateTime: LocalDateTime?,
    onPickThemeMode: () -> Unit,
    onPickThemeAccent: () -> Unit,
    appLanguage: AppLanguage = AppLanguage.System,
    onPickAppLanguage: () -> Unit = {},
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
    onScheduleCustomColorsAdaptToThemeChange: (Boolean) -> Unit,
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
    onUpsertHolidayCalendarEntry: (HolidayCalendarEntry) -> Unit = {},
    onRemoveHolidayCalendarEntry: (String) -> Unit = {},
    onClearHolidayCalendarEntries: () -> Unit = {},
    onHolidayCalendarBuiltInEnabledChange: (Boolean) -> Unit = {},
    onOpenWidgetPicker: () -> Unit,
    onPickWidgetThemeAccent: () -> Unit,
    onWidgetBackgroundImageUriChange: (String) -> Unit,
    onClearWidgetBackgroundImage: () -> Unit,
    onWidgetOpenAppOnDoubleClickChange: (Boolean) -> Unit,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
    onIgnoreUpdateVersion: (Int?) -> Unit,
    onPluginRegistryRepoChange: (String) -> Unit,
    onComponentMarketIndexUrlChange: (String) -> Unit,
    onPrivateFilesProviderEnabledChange: (Boolean) -> Unit,
    onWebDavSettingsChange: (String, String, String) -> Unit,
    onTestWebDavSettings: suspend (WebDavConfig) -> Result<Unit>,
    onAiImportSettingsChange: (String, String, String, Int) -> Unit,
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
    var showHolidayEditor by rememberSaveable { mutableStateOf(false) }
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
                TimingProfileEntryRow { navigate(SettingsDestination.TimingProfile) }
                SettingsActionRow(Icons.Rounded.EventRepeat, "临时调课", temporaryOverridesSubtitle(temporaryScheduleOverrides), {
                    navigate(SettingsDestination.TemporaryOverrides)
                })
                SettingsActionRow(Icons.Rounded.EventBusy, "节假日与调休", holidayCalendarSubtitle(holidayCalendar), {
                    navigate(SettingsDestination.Holidays)
                })
                SettingsActionRow(Icons.AutoMirrored.Rounded.MenuBook, "课表设置", "外观和显示", {
                    navigate(SettingsDestination.ScheduleSettings)
                })
                SettingsActionRow(Icons.Rounded.Widgets, "小组件设置", "主题、桌面小组件和背景", {
                    navigate(SettingsDestination.WidgetSettings)
                })
                SettingsActionRow(Icons.Rounded.Notifications, "上课自动静音", "上课时段自动切换手机状态，下课恢复", {
                    navigate(SettingsDestination.AutoSilence)
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
                    icon = Icons.Rounded.Language,
                    title = "语言",
                    subtitle = appLanguageLabel(appLanguage),
                    onClick = onPickAppLanguage,
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
            }

            SettingsDestination.ScheduleData -> {
                SettingsActionRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = "开学日期",
                    subtitle = termStartDate?.let {
                        val fmt = DateTimeFormatter.ofPattern("yyyy/M/d")
                        "${fmt.format(it)} · ${weekIndexLabel(currentWeekIndex)}"
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
                        "${weekIndexLabel(currentWeekIndex)} · 可按周数反推开学日期"
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

            SettingsDestination.Holidays -> {
                SettingsSwitchRow(
                    icon = Icons.Rounded.EventBusy,
                    title = "使用内置法定节假日",
                    subtitle = builtInHolidayCoverageSubtitle(),
                    checked = holidayCalendar.builtInEnabled,
                    onCheckedChange = onHolidayCalendarBuiltInEnabledChange,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.EventAvailable,
                    title = "调整某一天",
                    subtitle = "设为假日或设为调休上课日",
                    onClick = { showHolidayEditor = true },
                )
                Text(
                    text = "内置数据只收录来源确定的法定假日和补假。每年的调休连休和补班日期请在这里手动添加，" +
                        "手动条目优先于内置数据。调休日若要按其它星期的课表上课，请另外添加一条临时调课。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val userEntries = holidayCalendar.sortedUserEntries()
                if (userEntries.isEmpty()) {
                    Text(
                        text = "暂无手动调整",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    userEntries.forEach { entry ->
                        SettingsActionRow(
                            icon = if (entry.kind == HolidayEntryKind.Holiday) {
                                Icons.Rounded.EventBusy
                            } else {
                                Icons.Rounded.EventAvailable
                            },
                            title = holidayEntryTitle(entry),
                            subtitle = holidayEntrySubtitle(entry),
                            onClick = { showHolidayEditor = true },
                        )
                    }
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
                ScheduleAppearancePreview(
                    scheduleTextStyle = scheduleTextStyle,
                    scheduleCardStyle = scheduleCardStyle,
                    scheduleBackground = scheduleBackground,
                    scheduleDisplay = scheduleDisplay,
                    customColorsAdaptToTheme = scheduleCustomColorsAdaptToTheme,
                )
                SettingsSwitchRow(
                    Icons.Rounded.Brightness4,
                    "自定义颜色适应亮暗主题",
                    if (scheduleCustomColorsAdaptToTheme) {
                        "按当前亮暗主题调整课表自定义颜色"
                    } else {
                        "自定义颜色保持原样"
                    },
                    scheduleCustomColorsAdaptToTheme,
                    onScheduleCustomColorsAdaptToThemeChange,
                )
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
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        "当前主题显示为",
                        scheduleTextStyle.courseTextColorArgb.adaptForegroundForPreview(darkTheme),
                    )
                }
                NumberStepperRow("考试文字大小", scheduleTextStyle.examTextSizeSp, "sp", 8, 32, 1, onScheduleExamTextSizeSpChange)
                ColorAlphaRow("考试文字颜色", scheduleTextStyle.examTextColorArgb, onScheduleExamTextColorArgbChange)
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        "当前主题显示为",
                        scheduleTextStyle.examTextColorArgb.adaptForegroundForPreview(darkTheme),
                    )
                }
                SettingsSwitchRow(Icons.Rounded.Tune, "格子文字水平居中", "课程卡片文字水平居中", scheduleTextStyle.horizontalCenter, onScheduleTextHorizontalCenterChange)
                SettingsSwitchRow(Icons.Rounded.Tune, "格子文字竖直居中", "课程卡片内容竖直居中", scheduleTextStyle.verticalCenter, onScheduleTextVerticalCenterChange)
                SettingsSwitchRow(Icons.Rounded.Tune, "格子文字完全居中", "同时启用水平与竖直居中", scheduleTextStyle.fullCenter, onScheduleTextFullCenterChange)
            }

            SettingsDestination.ScheduleHeaderStyle -> {
                NumberStepperRow("表头文字大小", scheduleTextStyle.headerTextSizeSp, "sp", 8, 32, 1, onScheduleHeaderTextSizeSpChange)
                ColorAlphaRow(
                    "表头文字颜色",
                    scheduleTextStyle.resolvedHeaderTextColorArgb(darkTheme, false),
                    onScheduleHeaderTextColorArgbChange,
                )
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        "当前主题显示为",
                        scheduleTextStyle.resolvedHeaderTextColorArgb(darkTheme, true),
                    )
                }
                ColorAlphaRow(
                    "当前天表头背景颜色",
                    scheduleTextStyle.resolvedTodayHeaderBackgroundColorArgb(darkTheme, false),
                    onScheduleTodayHeaderBackgroundColorArgbChange,
                )
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        "当前主题显示为",
                        scheduleTextStyle.resolvedTodayHeaderBackgroundColorArgb(darkTheme, true),
                    )
                }
            }

            SettingsDestination.ScheduleCardStyle -> {
                NumberStepperRow("课程卡片圆角半径", scheduleCardStyle.courseCornerRadiusDp, "dp", 0, 32, 1, onScheduleCourseCornerRadiusDpChange)
                NumberStepperRow("课程卡片高度", scheduleCardStyle.courseCardHeightDp, "dp", 56, 160, 4, onScheduleCourseCardHeightDpChange)
                NumberStepperRow("课表透明度", scheduleCardStyle.scheduleOpacityPercent, "%", 0, 100, 5, onScheduleOpacityPercentChange)
                NumberStepperRow("非本周课程透明度", scheduleCardStyle.inactiveCourseOpacityPercent, "%", 0, 100, 5, onScheduleInactiveCourseOpacityPercentChange)
                ColorAlphaRow("格子边框颜色", scheduleCardStyle.gridBorderColorArgb, onScheduleGridBorderColorArgbChange)
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        "当前主题显示为",
                        scheduleCardStyle.gridBorderColorArgb.adaptForegroundForPreview(darkTheme),
                    )
                }
                NumberStepperRow("格子边框透明度", scheduleCardStyle.gridBorderOpacityPercent, "%", 0, 100, 5, onScheduleGridBorderOpacityPercentChange)
                FloatStepperRow("格子边框粗细", scheduleCardStyle.gridBorderWidthDp, "dp", 0f, 4f, 0.5f, onScheduleGridBorderWidthDpChange)
                SettingsSwitchRow(Icons.Rounded.Tune, "格子边框虚线", "关闭时使用实线", scheduleCardStyle.gridBorderDashed, onScheduleGridBorderDashedChange)
            }

            SettingsDestination.ScheduleBackground -> {
                ColorAlphaRow("背景颜色", scheduleBackground.colorArgb, onScheduleBackgroundColorArgbChange)
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        "当前主题显示为",
                        scheduleBackground.colorArgb.adaptBackgroundForPreview(darkTheme),
                    )
                }
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
                    title = "点击小组件打开 App",
                    subtitle = "开启后点击小组件主体进入应用",
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

            SettingsDestination.TimingProfile -> {
                TimingProfileSettingsSection()
            }

            SettingsDestination.AutoSilence -> {
                AutoSilenceSettingsSection()
            }

            SettingsDestination.Plugins -> {
                PluginSettingsSection(
                    pluginRegistryRepo = pluginRegistryRepo,
                    componentMarketIndexUrl = componentMarketIndexUrl,
                    onPluginRegistryRepoChange = onPluginRegistryRepoChange,
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
                    timeoutSeconds = aiImportTimeoutSeconds,
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
    if (showHolidayEditor) {
        HolidayCalendarDialog(
            settings = holidayCalendar,
            onUpsert = onUpsertHolidayCalendarEntry,
            onRemove = onRemoveHolidayCalendarEntry,
            onClear = onClearHolidayCalendarEntries,
            onDismiss = { showHolidayEditor = false },
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
    var alpha by rememberSaveable(normalized) { mutableIntStateOf(argbAlphaByte(normalized)) }
    var red by rememberSaveable(normalized) { mutableIntStateOf(argbRedByte(normalized)) }
    var green by rememberSaveable(normalized) { mutableIntStateOf(argbGreenByte(normalized)) }
    var blue by rememberSaveable(normalized) { mutableIntStateOf(argbBlueByte(normalized)) }
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
private fun TimingProfileEntryRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { DataStoreWidgetPreferencesRepository(context.applicationContext) }
    val profile by repository.timingProfileFlow.collectAsState(initial = null)
    val slotCount = profile?.slotTimes?.size ?: 0
    val subtitle = if (slotCount > 0) {
        "已设置 $slotCount 个时间段 · 卡片时间、提醒和导出都用它"
    } else {
        "未设置 · 手动录课需要它才能显示时间、建提醒、导出"
    }
    SettingsActionRow(
        icon = Icons.Rounded.Schedule,
        title = "节次上课时间",
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
private fun TimingProfileSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DataStoreWidgetPreferencesRepository(context.applicationContext) }
    val userPreferencesRepository = remember(context) { DataStoreUserPreferencesRepository(context.applicationContext) }
    val manuallyEdited by repository.timingProfileManuallyEditedFlow.collectAsState(initial = false)

    val drafts = remember { mutableStateListOf<SlotDraftInput>() }
    var errors by remember { mutableStateOf<List<TimingDraftError>>(emptyList()) }
    var showTemplatePicker by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val existing = repository.timingProfileFlow.first()
        if (existing != null && drafts.isEmpty()) {
            drafts.addAll(existing.slotTimes.map { it.toDraftInput() })
        }
    }

    fun updateRow(index: Int, transform: (SlotDraftInput) -> SlotDraftInput) {
        drafts[index] = transform(drafts[index])
    }

    if (drafts.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "还没有节次上课时间",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "课程卡片时间、创建提醒、日历与图片导出、上课自动静音都依赖它。" +
                        "可以套用下方模板再逐条改成本校作息，也可以手动逐条添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    Text(
        text = "每一节填写起始节、结束节和上下课时间。节次区间不能重叠，节次范围 " +
            "$MIN_SLOT_NODE-$MAX_SLOT_NODE。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (manuallyEdited) {
        Text(
            text = "当前为手动编辑的时间表，插件同步不会覆盖它。若要交回插件同步管理，请点下方“改回插件同步管理”。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    drafts.forEachIndexed { index, draft ->
        TimingSlotEditorRow(
            index = index,
            draft = draft,
            onChange = { updated -> updateRow(index) { updated } },
            onDelete = { drafts.removeAt(index) },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                drafts.add(SlotDraftInput("", "", "", "", ""))
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("添加一节")
        }
        OutlinedButton(
            onClick = { showTemplatePicker = true },
            modifier = Modifier.weight(1f),
        ) {
            Text("套用模板")
        }
    }

    if (errors.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "无法保存，请修正以下问题：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                errors.forEach { error ->
                    Text(
                        text = "· ${context.timingDraftErrorText(error)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

    Button(
        onClick = {
            val result = buildTimingSlots(drafts.toList())
            if (!result.isValid) {
                errors = result.errors
                return@Button
            }
            errors = emptyList()
            scope.launch {
                val existing = repository.timingProfileFlow.first()
                // 用户可能还没设开学日期，这里留空而不是发明一个，
                // 否则小组件、提醒与自动静音会据此算出周次，与界面显示的“未设置”矛盾
                val termStart = existing?.termStartLocalDate()?.toString()
                    ?: userPreferencesRepository.preferencesFlow.first().termStartDate?.toString()
                    ?: ""
                val profile = TermTimingProfile(
                    termStartDate = termStart,
                    slotTimes = result.slots,
                    timezone = existing?.timezone ?: "",
                )
                repository.saveManualTimingProfile(profile)
                withContext(Dispatchers.IO) {
                    ScheduleWidgetUpdater.refreshAll(context.applicationContext)
                    AutoSilenceController.evaluate(context.applicationContext, reason = "timing_profile_saved")
                }
                withContext(Dispatchers.Main) {
                    drafts.clear()
                    drafts.addAll(result.slots.map { it.toDraftInput() })
                    Toast.makeText(context, "已保存 ${result.slots.size} 个节次时间段", Toast.LENGTH_SHORT).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("保存")
    }

    if (manuallyEdited) {
        TextButton(
            onClick = {
                scope.launch {
                    repository.clearManualTimingProfileFlag()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已交回插件同步管理，下次同步会更新时间表", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("改回插件同步管理")
        }
    }

    if (showTemplatePicker) {
        TimingTemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onSelect = { template ->
                drafts.clear()
                drafts.addAll(template.slotTimes(context).map { it.toDraftInput() })
                errors = emptyList()
                showTemplatePicker = false
            },
        )
    }
}

@Composable
private fun TimingSlotEditorRow(
    index: Int,
    draft: SlotDraftInput,
    onChange: (SlotDraftInput) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 ${index + 1} 节",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "删除这一节",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.startNode,
                    onValueChange = { onChange(draft.copy(startNode = it.filter(Char::isDigit))) },
                    label = { Text("起始节") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.endNode,
                    onValueChange = { onChange(draft.copy(endNode = it.filter(Char::isDigit))) },
                    label = { Text("结束节") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.startTime,
                    onValueChange = { onChange(draft.copy(startTime = it)) },
                    label = { Text("开始") },
                    placeholder = { Text("08:00") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.endTime,
                    onValueChange = { onChange(draft.copy(endTime = it)) },
                    label = { Text("结束") },
                    placeholder = { Text("08:45") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.label,
                onValueChange = { onChange(draft.copy(label = it)) },
                label = { Text("名称（可选，如 第一大节）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimingTemplatePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (com.x500x.cursimple.core.data.widget.TimingTemplate) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("套用作息模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "模板时间为示例，套用后会替换现有内容，请按本校作息逐条调整。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                timingTemplates().forEach { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(template) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(template.nameRes),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(template.summaryRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun AutoSilenceSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DataStoreUserPreferencesRepository(context.applicationContext) }
    val preferences by repository.preferencesFlow.collectAsState(initial = null)
    val autoSilence = preferences?.autoSilence ?: AutoSilencePreferences()
    val sessionActive = preferences?.autoSilenceSession?.active == true
    val readiness = AutoSilenceController.readiness(context, autoSilence.mode)
    var showModePicker by remember { mutableStateOf(false) }

    fun refreshAutoSilence(reason: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AutoSilenceController.evaluate(context.applicationContext, reason = reason)
            }
        }
    }

    val blockingReason = readiness.blockingReason
    if (blockingReason != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "自动静音无法生效",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = blockingReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
    readiness.warning?.let { warning ->
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SettingsSwitchRow(
        icon = Icons.Rounded.Notifications,
        title = "上课自动静音",
        subtitle = if (autoSilence.enabled) {
            "有课的时段自动切换手机状态，下课恢复原状"
        } else {
            "关闭时不会改动手机的铃声和勿扰设置"
        },
        checked = autoSilence.enabled,
        onCheckedChange = { enabled ->
            val reason = AutoSilenceController.readiness(context, autoSilence.mode).blockingReason
            if (enabled && reason != null) {
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            } else {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repository.setAutoSilenceEnabled(enabled)
                        AutoSilenceController.evaluate(context.applicationContext, reason = "settings_toggle")
                    }
                }
            }
        },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Tune,
        title = "静音方式",
        subtitle = autoSilenceModeLabel(autoSilence.mode),
        onClick = { showModePicker = true },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Notifications,
        title = "勿扰权限",
        subtitle = when {
            readiness.notificationPolicyGranted -> "已授权，可以切换静音和勿扰"
            autoSilence.mode == AutoSilenceMode.Vibrate -> "仅震动不需要，改用静音或勿扰时才需要"
            else -> "未授权，点击后在列表里找到本应用打开"
        },
        onClick = {
            if (readiness.notificationPolicyGranted) {
                Toast.makeText(context, "勿扰权限已授权", Toast.LENGTH_SHORT).show()
            } else {
                launchSettingsIntent(context, AutoSilenceController.notificationPolicySettingsIntent())
            }
        },
    )
    if (autoSilence.mode == AutoSilenceMode.DoNotDisturb && !readiness.doNotDisturbAllowsAlarms) {
        SettingsActionRow(
            icon = Icons.Rounded.Warning,
            title = "勿扰未放行闹钟",
            subtitle = "请在系统勿扰设置里允许闹钟，否则课程提醒会被一起静音",
            onClick = { launchSettingsIntent(context, Intent(Settings.ACTION_SOUND_SETTINGS)) },
        )
    }
    SettingsActionRow(
        icon = Icons.Rounded.Restore,
        title = "当前状态",
        subtitle = if (sessionActive) "正在自动静音中，点击立即恢复" else "手机状态由用户自己控制",
        onClick = {
            if (sessionActive) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AutoSilenceController.restoreNow(
                            context = context.applicationContext,
                            reason = "settings_restore",
                            suppressUntilBlockEnd = true,
                        )
                    }
                    Toast.makeText(context, "已恢复原来的铃声状态", Toast.LENGTH_SHORT).show()
                }
            } else {
                refreshAutoSilence("settings_recheck")
                Toast.makeText(context, "已重新检查", Toast.LENGTH_SHORT).show()
            }
        },
    )
    Text(
        text = "静音只改动铃声模式或勿扰级别，课程提醒走闹钟音频通道，不受影响。" +
            "勿扰只会开到「仅优先级」，不会开成完全静音。" +
            "假日、临时调课取消的课不会触发静音；连堂课之间不会反复切换。" +
            "下课后恢复的是上课前的状态，如果上课途中你自己改过铃声，App 不会覆盖你的选择。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (showModePicker) {
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = { Text("静音方式") },
            text = {
                Column {
                    AutoSilenceMode.values().forEach { mode ->
                        SettingsActionRow(
                            icon = Icons.Rounded.Tune,
                            title = autoSilenceModeLabel(mode),
                            subtitle = autoSilenceModeDescription(mode),
                            onClick = {
                                showModePicker = false
                                val reason = AutoSilenceController.readiness(context, mode).blockingReason
                                if (reason != null && autoSilence.enabled) {
                                    Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.setAutoSilenceMode(mode)
                                        if (reason != null) {
                                            repository.setAutoSilenceEnabled(false)
                                        }
                                        AutoSilenceController.evaluate(
                                            context.applicationContext,
                                            reason = "settings_mode",
                                        )
                                    }
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModePicker = false }) { Text("关闭") }
            },
        )
    }
}

private fun autoSilenceModeLabel(mode: AutoSilenceMode): String = when (mode) {
    AutoSilenceMode.Vibrate -> "仅震动"
    AutoSilenceMode.Silent -> "静音"
    AutoSilenceMode.DoNotDisturb -> "勿扰（仅优先级）"
}

private fun autoSilenceModeDescription(mode: AutoSilenceMode): String = when (mode) {
    AutoSilenceMode.Vibrate -> "把铃声模式切到震动，不需要额外授权"
    AutoSilenceMode.Silent -> "把铃声模式切到静音，需要勿扰权限"
    AutoSilenceMode.DoNotDisturb -> "打开勿扰并放行闹钟，同时挡掉通知横幅，需要勿扰权限"
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

    // 计算权限健康状态
    val alarmPermissionsOk = exactAlarmEnabled && notificationEnabled && fullScreenIntentEnabled && batteryOptimizationIgnored
    val missingAlarmPermissions = buildList {
        if (!notificationEnabled) add("通知权限")
        if (!exactAlarmEnabled) add("精确闹钟权限")
        if (!fullScreenIntentEnabled) add("全屏响铃权限")
        if (!batteryOptimizationIgnored) add("后台运行权限")
    }

    // 权限健康状态警告卡片
    if (!alarmPermissionsOk) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚠️ 闹钟可能不响",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "以下权限未开启，可能导致课程提醒无法正常触发：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                missingAlarmPermissions.forEach { permission ->
                    Text(
                        text = "• $permission",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

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

@Composable
private fun ColorPreviewRow(title: String, argb: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Brightness7,
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
                    text = "${formatArgb(argb)} · 透明度 ${argbTransparencyPercent(argb)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                modifier = Modifier.size(28.dp),
                color = Color(argb),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {}
        }
    }
}

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

private fun Long.adaptForegroundForPreview(darkTheme: Boolean): Long =
    adaptScheduleForegroundColorArgb(this, darkTheme, true)

private fun Long.adaptBackgroundForPreview(darkTheme: Boolean): Long =
    adaptScheduleBackgroundColorArgb(this, darkTheme, true)

private fun ScheduleTextStylePreferences.resolvedHeaderTextColorArgb(
    darkTheme: Boolean,
    customColorsAdaptToTheme: Boolean,
): Long =
    if (headerTextColorCustomized) {
        adaptScheduleForegroundColorArgb(headerTextColorArgb, darkTheme, customColorsAdaptToTheme)
    } else if (darkTheme) {
        ScheduleTextStylePreferences.DEFAULT_DARK_HEADER_TEXT_COLOR_ARGB
    } else {
        ScheduleTextStylePreferences.DEFAULT_HEADER_TEXT_COLOR_ARGB
    }

private fun ScheduleTextStylePreferences.resolvedTodayHeaderBackgroundColorArgb(
    darkTheme: Boolean,
    customColorsAdaptToTheme: Boolean,
): Long =
    if (todayHeaderBackgroundColorCustomized) {
        adaptScheduleBackgroundColorArgb(
            todayHeaderBackgroundColorArgb,
            darkTheme,
            customColorsAdaptToTheme,
        )
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
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toDatePickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(datePickerMillisToLocalDate(millis))
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

@Composable
private fun HolidayCalendarDialog(
    settings: HolidayCalendarSettings,
    onUpsert: (HolidayCalendarEntry) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var targetDate by rememberSaveable { mutableStateOf(today) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val userEntry = settings.userEntryOn(targetDate)
    val effectiveEntry = settings.entryOn(targetDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节假日与调休") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateChoiceButton(
                    label = "日期",
                    date = targetDate,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pickDate = true },
                )
                Text(
                    text = holidayDateStatusText(targetDate, effectiveEntry, userEntry != null),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    label = { Text("备注（可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onUpsert(
                            HolidayCalendarEntry(
                                date = targetDate.toString(),
                                kind = HolidayEntryKind.Holiday,
                                name = name.trim(),
                            ),
                        )
                        name = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为假日")
                }
                OutlinedButton(
                    onClick = {
                        onUpsert(
                            HolidayCalendarEntry(
                                date = targetDate.toString(),
                                kind = HolidayEntryKind.Workday,
                                name = name.trim(),
                            ),
                        )
                        name = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为调休上课日")
                }
                if (userEntry != null) {
                    OutlinedButton(
                        onClick = { onRemove(targetDate.toString()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("取消这天的手动调整")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val userEntries = settings.sortedUserEntries()
                if (userEntries.isEmpty()) {
                    Text(
                        text = "暂无手动调整",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    userEntries.forEach { entry ->
                        HolidayEntryRow(
                            entry = entry,
                            onRemove = { onRemove(entry.date) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = if (settings.entries.isNotEmpty()) {
            {
                TextButton(onClick = onClear) { Text("清空") }
            }
        } else null,
    )

    if (pickDate) {
        SettingsDatePickerDialog(
            initial = targetDate,
            onConfirm = {
                targetDate = it
                pickDate = false
            },
            onDismiss = { pickDate = false },
        )
    }
}

@Composable
private fun HolidayEntryRow(
    entry: HolidayCalendarEntry,
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
                    text = holidayEntryTitle(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = holidayEntrySubtitle(entry),
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

private fun holidayCalendarSubtitle(settings: HolidayCalendarSettings): String {
    val manual = settings.entries.size
    val builtIn = if (settings.builtInEnabled) "内置已启用" else "内置已关闭"
    return if (manual == 0) builtIn else "$builtIn · $manual 条手动调整"
}

private fun builtInHolidayCoverageSubtitle(): String {
    val years = builtInHolidayYears
    return if (years.isEmpty()) {
        "暂无内置数据"
    } else {
        "已收录 ${years.joinToString("、")} 年法定假日与补假"
    }
}

private fun holidayEntryTitle(entry: HolidayCalendarEntry): String {
    val date = entry.localDate() ?: return "日期无效"
    return formatLongDate(date)
}

private fun holidayEntrySubtitle(entry: HolidayCalendarEntry): String {
    val kind = when (entry.kind) {
        HolidayEntryKind.Holiday -> "假日，不出课不提醒"
        HolidayEntryKind.Workday -> "调休上课日，照常出课"
    }
    val note = entry.name.trim()
    return if (note.isBlank()) kind else "$kind · $note"
}

private fun holidayDateStatusText(
    date: LocalDate,
    entry: HolidayCalendarEntry?,
    manual: Boolean,
): String {
    val source = if (manual) "手动" else "内置"
    val note = entry?.name?.trim().orEmpty()
    val suffix = if (note.isBlank()) "" else "（$note）"
    return when (entry?.kind) {
        HolidayEntryKind.Holiday -> "${formatLongDate(date)} 当前为${source}假日$suffix"
        HolidayEntryKind.Workday -> "${formatLongDate(date)} 当前为${source}调休上课日$suffix"
        null -> "${formatLongDate(date)} 当前按常规课表上课"
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
    pluginRegistryRepo: String,
    componentMarketIndexUrl: String,
    onPluginRegistryRepoChange: (String) -> Unit,
    onComponentMarketIndexUrlChange: (String) -> Unit,
) {
    val context = LocalContext.current
    var registryDraft by rememberSaveable(pluginRegistryRepo) {
        mutableStateOf(pluginRegistryRepo)
    }
    var componentUrlDraft by rememberSaveable(componentMarketIndexUrl) {
        mutableStateOf(componentMarketIndexUrl)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MarketIndexUrlEditor(
            title = "插件注册表仓库",
            placeholder = "owner/repo",
            value = registryDraft,
            onValueChange = { registryDraft = it },
            onSave = {
                onPluginRegistryRepoChange(registryDraft)
                Toast.makeText(context, "已保存插件注册表仓库", Toast.LENGTH_SHORT).show()
            },
        )
        MarketIndexUrlEditor(
            title = "组件市场索引",
            placeholder = "manifest.json",
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
    placeholder: String,
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
                label = { Text(placeholder) },
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
    timeoutSeconds: Int,
    onSave: (String, String, String, Int) -> Unit,
    onSaved: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var apiUrlDraft by rememberSaveable(apiUrl) { mutableStateOf(apiUrl) }
    var apiKeyDraft by rememberSaveable(apiKey) { mutableStateOf(apiKey) }
    var modelDraft by rememberSaveable(model) { mutableStateOf(model) }
    var timeoutDraft by rememberSaveable(timeoutSeconds) {
        mutableStateOf(coerceAiImportTimeoutSeconds(timeoutSeconds).toString())
    }

    SettingsEditorPanel(title = "AI 识图导入") {
        OutlinedTextField(
            value = apiUrlDraft,
            onValueChange = { apiUrlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API URL") },
            placeholder = { Text("https://api.openai.com/v1/chat/completions") },
            supportingText = { Text("只填到 /v1 或仅域名时，自动补 /chat/completions；如需 Responses API，请写完整 /v1/responses。") },
        )
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Key") },
            placeholder = { Text("sk-...") },
        )
        OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("模型（可选）") },
            placeholder = { Text("gpt-4o-mini") },
        )
        OutlinedTextField(
            value = timeoutDraft,
            onValueChange = { timeoutDraft = it.filter(Char::isDigit).take(3) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("超时（秒）") },
            placeholder = { Text(DEFAULT_AI_IMPORT_TIMEOUT_SECONDS.toString()) },
            supportingText = {
                Text("$MIN_AI_IMPORT_TIMEOUT_SECONDS-$MAX_AI_IMPORT_TIMEOUT_SECONDS 秒，留空使用 $DEFAULT_AI_IMPORT_TIMEOUT_SECONDS 秒")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(
            onClick = {
                val normalizedTimeout = coerceAiImportTimeoutSeconds(
                    timeoutDraft.toIntOrNull() ?: DEFAULT_AI_IMPORT_TIMEOUT_SECONDS,
                )
                timeoutDraft = normalizedTimeout.toString()
                onSave(apiUrlDraft, apiKeyDraft, modelDraft, normalizedTimeout)
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
        var showPluginLog by rememberSaveable { mutableStateOf(false) }
        DeveloperActionRow(
            icon = Icons.Rounded.Code,
            title = "插件日志",
            subtitle = "实时查看插件运行事件，支持按 trace_id/级别筛选",
            onClick = { showPluginLog = true },
        )
        if (showPluginLog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showPluginLog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                ),
            ) {
                com.x500x.cursimple.feature.plugin.PluginLogScreen(
                    onBack = { showPluginLog = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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

internal fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.System -> "跟随系统"
    AppLanguage.Chinese -> "简体中文"
    AppLanguage.English -> "English"
}
