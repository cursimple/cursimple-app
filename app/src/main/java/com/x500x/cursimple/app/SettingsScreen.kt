@file:Suppress("LocalContextGetResourceValueCall")

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.CloudDownload
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
import androidx.compose.material.icons.rounded.NotificationsOff
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
import com.x500x.cursimple.R
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.app.download.mirrorDownloaderLabels
import com.x500x.cursimple.app.holiday.HolidayCalendarSyncer
import com.x500x.cursimple.app.holiday.HolidaySyncOutcome
import com.x500x.cursimple.app.holiday.holidaySyncYears
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
import com.x500x.cursimple.core.kernel.model.SyncedHolidayYear
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.kernel.time.WeekStartDay
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
import com.x500x.cursimple.core.kernel.model.termWeekLabel
import com.x500x.cursimple.core.kernel.model.termWeekText
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

@Composable
private fun SettingsDestination.title(): String = when (this) {
    SettingsDestination.Root -> stringResource(R.string.settings_dest_root)
    SettingsDestination.Application -> stringResource(R.string.settings_dest_application)
    SettingsDestination.ScheduleData -> stringResource(R.string.settings_dest_schedule_data)
    SettingsDestination.TemporaryOverrides -> stringResource(R.string.settings_dest_temporary_overrides)
    SettingsDestination.Holidays -> stringResource(R.string.settings_dest_holidays)
    SettingsDestination.ScheduleSettings -> stringResource(R.string.settings_dest_schedule_settings)
    SettingsDestination.ScheduleAppearance -> stringResource(R.string.settings_appearance)
    SettingsDestination.ScheduleTextStyle -> stringResource(R.string.settings_text_style)
    SettingsDestination.ScheduleHeaderStyle -> stringResource(R.string.settings_header_style)
    SettingsDestination.ScheduleCardStyle -> stringResource(R.string.settings_card_style)
    SettingsDestination.ScheduleBackground -> stringResource(R.string.settings_schedule_background)
    SettingsDestination.ScheduleDisplay -> stringResource(R.string.settings_display)
    SettingsDestination.TimingProfile -> stringResource(R.string.settings_dest_timing_profile)
    SettingsDestination.WidgetSettings -> stringResource(R.string.settings_dest_widget_settings)
    SettingsDestination.AutoSilence -> stringResource(R.string.settings_dest_auto_silence)
    SettingsDestination.Plugins -> stringResource(R.string.settings_dest_plugins)
    SettingsDestination.WebDav -> "WebDAV"
    SettingsDestination.AiImport -> stringResource(R.string.settings_dest_ai_import)
    SettingsDestination.Permissions -> stringResource(R.string.settings_dest_permissions)
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
    onScheduleWeekStartDayChange: (WeekStartDay) -> Unit,
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
    skipRemindersOnHoliday: Boolean = false,
    onSkipRemindersOnHolidayChange: (Boolean) -> Unit = {},
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
        val message = if (granted) {
            context.getString(R.string.settings_toast_notification_granted)
        } else {
            context.getString(R.string.settings_toast_notification_denied)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val message = if (granted) {
            context.getString(R.string.settings_toast_camera_granted)
        } else {
            context.getString(R.string.settings_toast_camera_denied)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val scheduleBackgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (persisted) {
                onScheduleBackgroundImageUriChange(uri.toString())
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_background_image_permission_failed),
                    Toast.LENGTH_SHORT,
                ).show()
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
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_widget_background_image_permission_failed),
                    Toast.LENGTH_SHORT,
                ).show()
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
                        contentDescription = stringResource(R.string.settings_back),
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
                SettingsActionRow(
                    Icons.Rounded.Palette,
                    stringResource(R.string.settings_dest_application),
                    stringResource(R.string.settings_row_application_subtitle),
                    { navigate(SettingsDestination.Application) },
                )
                SettingsActionRow(
                    Icons.Rounded.CalendarMonth,
                    stringResource(R.string.settings_dest_schedule_data),
                    stringResource(R.string.settings_row_schedule_data_subtitle),
                    { navigate(SettingsDestination.ScheduleData) },
                )
                TimingProfileEntryRow { navigate(SettingsDestination.TimingProfile) }
                SettingsActionRow(Icons.Rounded.EventRepeat, stringResource(R.string.settings_dest_temporary_overrides), temporaryOverridesSubtitle(temporaryScheduleOverrides), {
                    navigate(SettingsDestination.TemporaryOverrides)
                })
                SettingsActionRow(Icons.Rounded.EventBusy, stringResource(R.string.settings_dest_holidays), holidayCalendarSubtitle(holidayCalendar), {
                    navigate(SettingsDestination.Holidays)
                })
                SettingsActionRow(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    stringResource(R.string.settings_dest_schedule_settings),
                    stringResource(R.string.settings_row_schedule_settings_subtitle),
                    {
                        navigate(SettingsDestination.ScheduleSettings)
                    },
                )
                SettingsActionRow(
                    Icons.Rounded.Widgets,
                    stringResource(R.string.settings_dest_widget_settings),
                    stringResource(R.string.settings_row_widget_settings_subtitle),
                    { navigate(SettingsDestination.WidgetSettings) },
                )
                SettingsActionRow(
                    Icons.Rounded.Notifications,
                    stringResource(R.string.settings_dest_auto_silence),
                    stringResource(R.string.settings_row_auto_silence_subtitle),
                    { navigate(SettingsDestination.AutoSilence) },
                )
                SettingsActionRow(
                    Icons.Rounded.Code,
                    stringResource(R.string.settings_dest_plugins),
                    stringResource(R.string.settings_row_plugins_subtitle),
                    { navigate(SettingsDestination.Plugins) },
                )
                SettingsActionRow(Icons.Rounded.Storage, "WebDAV", webDavSettingsSubtitle(webDavUrl, webDavUsername), {
                    navigate(SettingsDestination.WebDav)
                })
                SettingsActionRow(Icons.Rounded.ImageSearch, stringResource(R.string.settings_dest_ai_import), aiImportSettingsSubtitle(aiImportApiUrl, aiImportModel), {
                    navigate(SettingsDestination.AiImport)
                })
                SettingsActionRow(
                    Icons.Rounded.Notifications,
                    stringResource(R.string.settings_dest_permissions),
                    stringResource(R.string.settings_row_permissions_subtitle),
                    { navigate(SettingsDestination.Permissions) },
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.settings_reset_all_title),
                    subtitle = stringResource(R.string.settings_reset_all_subtitle),
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
                    title = stringResource(R.string.settings_language),
                    subtitle = appLanguageLabel(appLanguage),
                    onClick = onPickAppLanguage,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_theme),
                    subtitle = themeAccentLabel,
                    onClick = onPickThemeAccent,
                )
                SettingsActionRow(
                    icon = when (themeMode) {
                        ThemeMode.Dark -> Icons.Rounded.Brightness4
                        else -> Icons.Rounded.Brightness7
                    },
                    title = stringResource(R.string.settings_appearance),
                    subtitle = when (themeMode) {
                        ThemeMode.System -> stringResource(R.string.settings_theme_mode_system)
                        ThemeMode.Light -> stringResource(R.string.settings_theme_mode_light)
                        ThemeMode.Dark -> stringResource(R.string.settings_theme_mode_dark)
                    },
                    onClick = onPickThemeMode,
                )
            }

            SettingsDestination.ScheduleData -> {
                SettingsActionRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.settings_term_start_title),
                    subtitle = termStartDate?.let {
                        val fmt = DateTimeFormatter.ofPattern("yyyy/M/d")
                        val week = LocalContext.current.termWeekText(termWeekLabel(currentWeekIndex))
                        "${fmt.format(it)} · $week"
                    } ?: stringResource(R.string.settings_term_start_unset),
                    onClick = onPickTermStartDate,
                    trailing = if (termStartDate != null) {
                        {
                            TextButton(
                                onClick = onClearTermStartDate,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(stringResource(R.string.settings_clear), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else null,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.settings_current_week_title),
                    subtitle = if (termStartDate != null) {
                        stringResource(
                            R.string.settings_current_week_subtitle_set,
                            LocalContext.current.termWeekText(termWeekLabel(currentWeekIndex)),
                        )
                    } else {
                        stringResource(R.string.settings_current_week_subtitle_unset)
                    },
                    onClick = onPickCurrentWeek,
                )
            }

            SettingsDestination.TemporaryOverrides -> {
                SettingsActionRow(
                    icon = Icons.Rounded.EventRepeat,
                    title = stringResource(R.string.settings_manage_override_rules),
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
                    title = stringResource(R.string.settings_holiday_builtin_title),
                    subtitle = builtInHolidayCoverageSubtitle(),
                    checked = holidayCalendar.builtInEnabled,
                    onCheckedChange = onHolidayCalendarBuiltInEnabledChange,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.NotificationsOff,
                    title = stringResource(R.string.settings_holiday_skip_reminders_title),
                    subtitle = stringResource(
                        if (skipRemindersOnHoliday) {
                            R.string.settings_holiday_skip_reminders_on
                        } else {
                            R.string.settings_holiday_skip_reminders_off
                        },
                    ),
                    checked = skipRemindersOnHoliday,
                    onCheckedChange = onSkipRemindersOnHolidayChange,
                )
                HolidayCalendarSyncRow(syncedYears = holidayCalendar.syncedYears)
                SettingsActionRow(
                    icon = Icons.Rounded.EventAvailable,
                    title = stringResource(R.string.settings_holiday_adjust_day_title),
                    subtitle = stringResource(R.string.settings_holiday_adjust_day_subtitle),
                    onClick = { showHolidayEditor = true },
                )
                Text(
                    text = stringResource(R.string.settings_holiday_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val userEntries = holidayCalendar.sortedUserEntries()
                if (userEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_holiday_no_manual_entries),
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
                SettingsActionRow(
                    Icons.Rounded.Palette,
                    stringResource(R.string.settings_appearance),
                    stringResource(R.string.settings_schedule_appearance_subtitle),
                    { navigate(SettingsDestination.ScheduleAppearance) },
                )
                SettingsActionRow(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    stringResource(R.string.settings_display),
                    stringResource(R.string.settings_schedule_display_subtitle),
                    { navigate(SettingsDestination.ScheduleDisplay) },
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.settings_reset_schedule_title),
                    subtitle = stringResource(R.string.settings_reset_schedule_subtitle),
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
                    stringResource(R.string.settings_adapt_colors_title),
                    if (scheduleCustomColorsAdaptToTheme) {
                        stringResource(R.string.settings_adapt_colors_on)
                    } else {
                        stringResource(R.string.settings_adapt_colors_off)
                    },
                    scheduleCustomColorsAdaptToTheme,
                    onScheduleCustomColorsAdaptToThemeChange,
                )
                SettingsActionRow(
                    Icons.Rounded.Palette,
                    stringResource(R.string.settings_text_style),
                    stringResource(R.string.settings_text_style_subtitle),
                    { navigate(SettingsDestination.ScheduleTextStyle) },
                )
                SettingsActionRow(
                    Icons.Rounded.CalendarMonth,
                    stringResource(R.string.settings_header_style),
                    stringResource(R.string.settings_header_style_subtitle),
                    { navigate(SettingsDestination.ScheduleHeaderStyle) },
                )
                SettingsActionRow(
                    Icons.Rounded.Tune,
                    stringResource(R.string.settings_card_style),
                    stringResource(R.string.settings_card_style_subtitle),
                    { navigate(SettingsDestination.ScheduleCardStyle) },
                )
                SettingsActionRow(
                    Icons.Rounded.Download,
                    stringResource(R.string.settings_schedule_background),
                    backgroundSubtitle(scheduleBackground),
                    { navigate(SettingsDestination.ScheduleBackground) },
                )
            }

            SettingsDestination.ScheduleTextStyle -> {
                NumberStepperRow(stringResource(R.string.settings_course_text_size), scheduleTextStyle.courseTextSizeSp, "sp", 8, 32, 1, onScheduleCourseTextSizeSpChange)
                ColorAlphaRow(stringResource(R.string.settings_course_text_color), scheduleTextStyle.courseTextColorArgb, onScheduleCourseTextColorArgbChange)
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        stringResource(R.string.settings_current_theme_preview),
                        scheduleTextStyle.courseTextColorArgb.adaptForegroundForPreview(darkTheme),
                    )
                }
                NumberStepperRow(stringResource(R.string.settings_exam_text_size), scheduleTextStyle.examTextSizeSp, "sp", 8, 32, 1, onScheduleExamTextSizeSpChange)
                ColorAlphaRow(stringResource(R.string.settings_exam_text_color), scheduleTextStyle.examTextColorArgb, onScheduleExamTextColorArgbChange)
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        stringResource(R.string.settings_current_theme_preview),
                        scheduleTextStyle.examTextColorArgb.adaptForegroundForPreview(darkTheme),
                    )
                }
                SettingsSwitchRow(
                    Icons.Rounded.Tune,
                    stringResource(R.string.settings_text_center_horizontal_title),
                    stringResource(R.string.settings_text_center_horizontal_subtitle),
                    scheduleTextStyle.horizontalCenter,
                    onScheduleTextHorizontalCenterChange,
                )
                SettingsSwitchRow(
                    Icons.Rounded.Tune,
                    stringResource(R.string.settings_text_center_vertical_title),
                    stringResource(R.string.settings_text_center_vertical_subtitle),
                    scheduleTextStyle.verticalCenter,
                    onScheduleTextVerticalCenterChange,
                )
                SettingsSwitchRow(
                    Icons.Rounded.Tune,
                    stringResource(R.string.settings_text_center_full_title),
                    stringResource(R.string.settings_text_center_full_subtitle),
                    scheduleTextStyle.fullCenter,
                    onScheduleTextFullCenterChange,
                )
            }

            SettingsDestination.ScheduleHeaderStyle -> {
                NumberStepperRow(stringResource(R.string.settings_header_text_size), scheduleTextStyle.headerTextSizeSp, "sp", 8, 32, 1, onScheduleHeaderTextSizeSpChange)
                ColorAlphaRow(
                    stringResource(R.string.settings_header_text_color),
                    scheduleTextStyle.resolvedHeaderTextColorArgb(darkTheme, false),
                    onScheduleHeaderTextColorArgbChange,
                )
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        stringResource(R.string.settings_current_theme_preview),
                        scheduleTextStyle.resolvedHeaderTextColorArgb(darkTheme, true),
                    )
                }
                ColorAlphaRow(
                    stringResource(R.string.settings_today_header_background_color),
                    scheduleTextStyle.resolvedTodayHeaderBackgroundColorArgb(darkTheme, false),
                    onScheduleTodayHeaderBackgroundColorArgbChange,
                )
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        stringResource(R.string.settings_current_theme_preview),
                        scheduleTextStyle.resolvedTodayHeaderBackgroundColorArgb(darkTheme, true),
                    )
                }
            }

            SettingsDestination.ScheduleCardStyle -> {
                NumberStepperRow(stringResource(R.string.settings_card_corner_radius), scheduleCardStyle.courseCornerRadiusDp, "dp", 0, 32, 1, onScheduleCourseCornerRadiusDpChange)
                NumberStepperRow(stringResource(R.string.settings_card_height), scheduleCardStyle.courseCardHeightDp, "dp", 56, 160, 4, onScheduleCourseCardHeightDpChange)
                NumberStepperRow(stringResource(R.string.settings_schedule_opacity), scheduleCardStyle.scheduleOpacityPercent, "%", 0, 100, 5, onScheduleOpacityPercentChange)
                NumberStepperRow(stringResource(R.string.settings_inactive_course_opacity), scheduleCardStyle.inactiveCourseOpacityPercent, "%", 0, 100, 5, onScheduleInactiveCourseOpacityPercentChange)
                ColorAlphaRow(stringResource(R.string.settings_grid_border_color), scheduleCardStyle.gridBorderColorArgb, onScheduleGridBorderColorArgbChange)
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        stringResource(R.string.settings_current_theme_preview),
                        scheduleCardStyle.gridBorderColorArgb.adaptForegroundForPreview(darkTheme),
                    )
                }
                NumberStepperRow(stringResource(R.string.settings_grid_border_opacity), scheduleCardStyle.gridBorderOpacityPercent, "%", 0, 100, 5, onScheduleGridBorderOpacityPercentChange)
                FloatStepperRow(stringResource(R.string.settings_grid_border_width), scheduleCardStyle.gridBorderWidthDp, "dp", 0f, 4f, 0.5f, onScheduleGridBorderWidthDpChange)
                SettingsSwitchRow(
                    Icons.Rounded.Tune,
                    stringResource(R.string.settings_grid_border_dashed_title),
                    stringResource(R.string.settings_grid_border_dashed_subtitle),
                    scheduleCardStyle.gridBorderDashed,
                    onScheduleGridBorderDashedChange,
                )
            }

            SettingsDestination.ScheduleBackground -> {
                ColorAlphaRow(stringResource(R.string.settings_background_color), scheduleBackground.colorArgb, onScheduleBackgroundColorArgbChange)
                if (scheduleCustomColorsAdaptToTheme) {
                    ColorPreviewRow(
                        stringResource(R.string.settings_current_theme_preview),
                        scheduleBackground.colorArgb.adaptBackgroundForPreview(darkTheme),
                    )
                }
                SettingsActionRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.settings_background_match_header_title),
                    subtitle = if (scheduleBackground.type == ScheduleBackgroundType.Header) {
                        stringResource(R.string.settings_background_match_header_on)
                    } else {
                        stringResource(R.string.settings_background_match_header_off)
                    },
                    onClick = onScheduleBackgroundUseHeaderColor,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.settings_background_image_title),
                    subtitle = if (scheduleBackground.imageUri != null) {
                        stringResource(R.string.settings_background_image_selected)
                    } else {
                        stringResource(R.string.settings_background_image_none)
                    },
                    onClick = { scheduleBackgroundLauncher.launch(arrayOf("image/*")) },
                )
                if (scheduleBackground.type == ScheduleBackgroundType.Image || scheduleBackground.imageUri != null) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.settings_background_image_clear_title),
                        subtitle = stringResource(R.string.settings_background_image_clear_subtitle),
                        onClick = onClearScheduleBackgroundImage,
                    )
                }
            }

            SettingsDestination.ScheduleDisplay -> {
                SettingsSwitchRow(
                    Icons.Rounded.Schedule,
                    stringResource(R.string.settings_display_node_time_title),
                    stringResource(R.string.settings_display_node_time_subtitle),
                    scheduleDisplay.nodeColumnTimeEnabled,
                    onScheduleNodeColumnTimeEnabledChange,
                )
                WeekStartDayRow(
                    selected = scheduleDisplay.weekStartDay,
                    onSelect = onScheduleWeekStartDayChange,
                )
                SettingsSwitchRow(
                    Icons.Rounded.CalendarMonth,
                    stringResource(R.string.settings_display_saturday_title),
                    stringResource(R.string.settings_display_saturday_subtitle),
                    scheduleDisplay.saturdayVisible || scheduleDisplay.weekendVisible,
                    onScheduleSaturdayVisibleChange,
                )
                SettingsSwitchRow(
                    Icons.Rounded.CalendarMonth,
                    stringResource(R.string.settings_display_weekend_title),
                    stringResource(R.string.settings_display_weekend_subtitle),
                    scheduleDisplay.weekendVisible,
                    {
                        onScheduleWeekendVisibleChange(it)
                        if (it) onScheduleSaturdayVisibleChange(true)
                    },
                )
                SettingsSwitchRow(
                    Icons.Rounded.Schedule,
                    stringResource(R.string.settings_display_location_title),
                    stringResource(R.string.settings_display_location_subtitle),
                    scheduleDisplay.locationVisible,
                    onScheduleLocationVisibleChange,
                )
                SettingsSwitchRow(
                    Icons.Rounded.Schedule,
                    stringResource(R.string.settings_display_location_at_title),
                    stringResource(R.string.settings_display_location_at_subtitle),
                    scheduleDisplay.locationPrefixAtEnabled,
                    onScheduleLocationPrefixAtEnabledChange,
                )
                SettingsSwitchRow(
                    Icons.Rounded.Schedule,
                    stringResource(R.string.settings_display_teacher_title),
                    stringResource(R.string.settings_display_teacher_subtitle),
                    scheduleDisplay.teacherVisible,
                    onScheduleTeacherVisibleChange,
                )
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    title = stringResource(R.string.settings_display_total_title),
                    subtitle = if (scheduleDisplay.totalScheduleDisplayEnabled) {
                        stringResource(R.string.settings_display_total_on)
                    } else {
                        stringResource(R.string.settings_display_total_off)
                    },
                    checked = scheduleDisplay.totalScheduleDisplayEnabled,
                    onCheckedChange = onTotalScheduleDisplayChange,
                )
            }

            SettingsDestination.WidgetSettings -> {
                SettingsActionRow(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_theme),
                    subtitle = widgetThemeLabel(widgetThemePreferences),
                    onClick = onPickWidgetThemeAccent,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Widgets,
                    title = stringResource(R.string.settings_widget_home_title),
                    subtitle = stringResource(R.string.settings_widget_home_subtitle),
                    onClick = onOpenWidgetPicker,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.settings_widget_open_app_title),
                    subtitle = stringResource(R.string.settings_widget_open_app_subtitle),
                    checked = widgetThemePreferences.openAppOnDoubleClickEnabled,
                    onCheckedChange = onWidgetOpenAppOnDoubleClickChange,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.settings_widget_background_title),
                    subtitle = if (widgetThemePreferences.backgroundImageUri != null) {
                        stringResource(R.string.settings_background_image_selected)
                    } else {
                        stringResource(R.string.settings_widget_background_theme)
                    },
                    onClick = { widgetBackgroundLauncher.launch(arrayOf("image/*")) },
                )
                if (widgetThemePreferences.backgroundMode == WidgetBackgroundMode.Image ||
                    widgetThemePreferences.backgroundImageUri != null
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.settings_widget_background_clear_title),
                        subtitle = stringResource(R.string.settings_widget_background_clear_subtitle),
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
                title = { Text(stringResource(R.string.settings_reset_schedule_dialog_title)) },
                text = { Text(stringResource(R.string.settings_reset_schedule_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        onResetScheduleAppearanceAndDisplay()
                        showResetScheduleAppearanceConfirm = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_toast_schedule_reset),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }) { Text(stringResource(R.string.settings_reset_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetScheduleAppearanceConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
                },
            )
        }

        if (showResetAllSettingsConfirm) {
            AlertDialog(
                onDismissRequest = { showResetAllSettingsConfirm = false },
                title = { Text(stringResource(R.string.settings_reset_all_title)) },
                text = { Text(stringResource(R.string.settings_reset_all_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        onResetAllSettings()
                        showResetAllSettingsConfirm = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_toast_all_reset),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }) { Text(stringResource(R.string.settings_reset_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetAllSettingsConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
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
        subtitle = stringResource(
            R.string.settings_color_transparency_summary,
            formatArgb(argb),
            argbTransparencyPercent(argb),
        ),
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
                ColorComponentSlider(stringResource(R.string.settings_color_red), red, 255) {
                    red = it
                    syncHex()
                }
                ColorComponentSlider(stringResource(R.string.settings_color_green), green, 255) {
                    green = it
                    syncHex()
                }
                ColorComponentSlider(stringResource(R.string.settings_color_blue), blue, 255) {
                    blue = it
                    syncHex()
                }
                ColorComponentSlider(stringResource(R.string.settings_color_transparency), alphaToTransparencyPercent(alpha), 100) {
                    alpha = transparencyPercentToAlpha(it)
                    syncHex()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentArgb()) }) { Text(stringResource(R.string.settings_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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
        stringResource(R.string.settings_timing_entry_subtitle_set, slotCount)
    } else {
        stringResource(R.string.settings_timing_entry_subtitle_unset)
    }
    SettingsActionRow(
        icon = Icons.Rounded.Schedule,
        title = stringResource(R.string.settings_dest_timing_profile),
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
            drafts.addAll(existing.slotTimes.mapIndexed { index, slot -> slot.toDraftInput(context, index + 1) })
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
                    text = stringResource(R.string.settings_timing_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_timing_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    Text(
        text = stringResource(R.string.settings_timing_hint, MIN_SLOT_NODE, MAX_SLOT_NODE),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (manuallyEdited) {
        Text(
            text = stringResource(R.string.settings_timing_manual_notice),
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
            Text(stringResource(R.string.settings_timing_add_row))
        }
        OutlinedButton(
            onClick = { showTemplatePicker = true },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.settings_timing_apply_template))
        }
    }

    if (errors.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.settings_timing_errors_title),
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
                    drafts.addAll(result.slots.mapIndexed { index, slot -> slot.toDraftInput(context, index + 1) })
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_toast_timing_saved, result.slots.size),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_save))
    }

    if (manuallyEdited) {
        TextButton(
            onClick = {
                scope.launch {
                    repository.clearManualTimingProfileFlag()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_toast_timing_handback),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_timing_handback))
        }
    }

    if (showTemplatePicker) {
        TimingTemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onSelect = { template ->
                drafts.clear()
                drafts.addAll(
                    template.slotTimes(context).mapIndexed { index, slot -> slot.toDraftInput(context, index + 1) },
                )
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
                    text = stringResource(R.string.settings_timing_row_index, index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.settings_timing_row_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.startNode,
                    onValueChange = { onChange(draft.copy(startNode = it.filter(Char::isDigit))) },
                    label = { Text(stringResource(R.string.settings_timing_start_node)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.endNode,
                    onValueChange = { onChange(draft.copy(endNode = it.filter(Char::isDigit))) },
                    label = { Text(stringResource(R.string.settings_timing_end_node)) },
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
                    label = { Text(stringResource(R.string.settings_timing_start_time)) },
                    placeholder = { Text("08:00") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.endTime,
                    onValueChange = { onChange(draft.copy(endTime = it)) },
                    label = { Text(stringResource(R.string.settings_timing_end_time)) },
                    placeholder = { Text("08:45") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.label,
                onValueChange = { onChange(draft.copy(label = it)) },
                label = { Text(stringResource(R.string.settings_timing_label_hint)) },
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
        title = { Text(stringResource(R.string.settings_timing_template_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.settings_timing_template_hint),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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

    val blockingReason = readiness.blockingReasonRes?.let { stringResource(it) }
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
                        text = stringResource(R.string.settings_auto_silence_blocked_title),
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
    readiness.warningRes?.let { warningRes ->
        val warning = stringResource(warningRes)
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SettingsSwitchRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_dest_auto_silence),
        subtitle = if (autoSilence.enabled) {
            stringResource(R.string.settings_auto_silence_on)
        } else {
            stringResource(R.string.settings_auto_silence_off)
        },
        checked = autoSilence.enabled,
        onCheckedChange = { enabled ->
            val reasonRes = AutoSilenceController.readiness(context, autoSilence.mode).blockingReasonRes
            if (enabled && reasonRes != null) {
                Toast.makeText(context, context.getString(reasonRes), Toast.LENGTH_LONG).show()
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
        title = stringResource(R.string.settings_auto_silence_mode_title),
        subtitle = autoSilenceModeLabel(autoSilence.mode),
        onClick = { showModePicker = true },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_dnd_permission_title),
        subtitle = when {
            readiness.notificationPolicyGranted -> stringResource(R.string.settings_dnd_permission_granted)
            autoSilence.mode == AutoSilenceMode.Vibrate -> stringResource(R.string.settings_dnd_permission_not_needed)
            else -> stringResource(R.string.settings_dnd_permission_missing)
        },
        onClick = {
            if (readiness.notificationPolicyGranted) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_dnd_granted),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                launchSettingsIntent(context, AutoSilenceController.notificationPolicySettingsIntent())
            }
        },
    )
    if (autoSilence.mode == AutoSilenceMode.DoNotDisturb && !readiness.doNotDisturbAllowsAlarms) {
        SettingsActionRow(
            icon = Icons.Rounded.Warning,
            title = stringResource(R.string.settings_dnd_alarms_blocked_title),
            subtitle = stringResource(R.string.settings_dnd_alarms_blocked_subtitle),
            onClick = { launchSettingsIntent(context, Intent(Settings.ACTION_SOUND_SETTINGS)) },
        )
    }
    SettingsActionRow(
        icon = Icons.Rounded.Restore,
        title = stringResource(R.string.settings_auto_silence_status_title),
        subtitle = if (sessionActive) {
            stringResource(R.string.settings_auto_silence_status_active)
        } else {
            stringResource(R.string.settings_auto_silence_status_idle)
        },
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
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_toast_ringer_restored),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                refreshAutoSilence("settings_recheck")
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_rechecked),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
    )
    Text(
        text = stringResource(R.string.settings_auto_silence_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (showModePicker) {
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = { Text(stringResource(R.string.settings_auto_silence_mode_title)) },
            text = {
                Column {
                    AutoSilenceMode.values().forEach { mode ->
                        SettingsActionRow(
                            icon = Icons.Rounded.Tune,
                            title = autoSilenceModeLabel(mode),
                            subtitle = autoSilenceModeDescription(mode),
                            onClick = {
                                showModePicker = false
                                val reasonRes = AutoSilenceController.readiness(context, mode).blockingReasonRes
                                if (reasonRes != null && autoSilence.enabled) {
                                    Toast.makeText(context, context.getString(reasonRes), Toast.LENGTH_LONG).show()
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.setAutoSilenceMode(mode)
                                        if (reasonRes != null) {
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
                TextButton(onClick = { showModePicker = false }) { Text(stringResource(R.string.settings_close)) }
            },
        )
    }
}

@Composable
private fun autoSilenceModeLabel(mode: AutoSilenceMode): String = when (mode) {
    AutoSilenceMode.Vibrate -> stringResource(R.string.settings_silence_mode_vibrate)
    AutoSilenceMode.Silent -> stringResource(R.string.settings_silence_mode_silent)
    AutoSilenceMode.DoNotDisturb -> stringResource(R.string.settings_silence_mode_dnd)
}

@Composable
private fun autoSilenceModeDescription(mode: AutoSilenceMode): String = when (mode) {
    AutoSilenceMode.Vibrate -> stringResource(R.string.settings_silence_mode_vibrate_desc)
    AutoSilenceMode.Silent -> stringResource(R.string.settings_silence_mode_silent_desc)
    AutoSilenceMode.DoNotDisturb -> stringResource(R.string.settings_silence_mode_dnd_desc)
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
        if (!notificationEnabled) add(context.getString(R.string.settings_permission_notification))
        if (!exactAlarmEnabled) add(context.getString(R.string.settings_permission_exact_alarm))
        if (!fullScreenIntentEnabled) add(context.getString(R.string.settings_permission_full_screen))
        if (!batteryOptimizationIgnored) add(context.getString(R.string.settings_permission_background))
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
                        text = stringResource(R.string.settings_alarm_warning_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_alarm_warning_body),
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

    SettingsSectionHeader(stringResource(R.string.settings_section_grant))
    SettingsActionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_permission_notification),
        subtitle = if (notificationEnabled) {
            stringResource(R.string.settings_permission_on)
        } else {
            stringResource(R.string.settings_permission_notification_off)
        },
        onClick = {
            if (notificationEnabled) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_notification_granted),
                    Toast.LENGTH_SHORT,
                ).show()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchSettingsIntent(context, AlarmPermissionIntents.appDetailsIntent(context))
            }
        },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Schedule,
        title = stringResource(R.string.settings_permission_exact_alarm),
        subtitle = if (exactAlarmEnabled) {
            stringResource(R.string.settings_permission_on)
        } else {
            stringResource(R.string.settings_permission_exact_alarm_off)
        },
        onClick = {
            if (exactAlarmEnabled) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_exact_alarm_granted),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                launchSettingsIntent(context, AlarmPermissionIntents.exactAlarmSettingsIntent(context))
            }
        },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_permission_full_screen),
        subtitle = if (fullScreenIntentEnabled) {
            stringResource(R.string.settings_permission_on)
        } else {
            stringResource(R.string.settings_permission_full_screen_off)
        },
        onClick = {
            if (fullScreenIntentEnabled) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_full_screen_granted),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                launchSettingsIntent(context, AlarmPermissionIntents.fullScreenIntentSettingsIntent(context))
            }
        },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Restore,
        title = stringResource(R.string.settings_permission_battery_title),
        subtitle = if (batteryOptimizationIgnored) {
            stringResource(R.string.settings_permission_battery_on)
        } else {
            stringResource(R.string.settings_permission_battery_off)
        },
        onClick = {
            if (batteryOptimizationIgnored) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_permission_battery_on),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                launchSettingsIntent(context, AlarmPermissionIntents.batteryOptimizationIntent(context))
            }
        },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Code,
        title = stringResource(R.string.settings_permission_camera),
        subtitle = if (cameraEnabled) {
            stringResource(R.string.settings_permission_on)
        } else {
            stringResource(R.string.settings_permission_camera_off)
        },
        onClick = {
            if (cameraEnabled) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_camera_granted),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                cameraLauncher(Manifest.permission.CAMERA)
            }
        },
    )
    SettingsActionRow(
        icon = Icons.Rounded.Download,
        title = stringResource(R.string.settings_permission_install),
        subtitle = if (installPackagesEnabled) {
            stringResource(R.string.settings_permission_install_on)
        } else {
            stringResource(R.string.settings_permission_install_off)
        },
        onClick = {
            if (installPackagesEnabled) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_install_granted),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                launchSettingsIntent(context, unknownAppInstallSettingsIntent(context))
            }
        },
    )
    SettingsSectionHeader(stringResource(R.string.settings_section_declared))
    SettingsActionRow(
        icon = Icons.Rounded.Tune,
        title = stringResource(R.string.settings_declared_permissions_title),
        subtitle = stringResource(R.string.settings_declared_permissions_subtitle),
        onClick = {
            Toast.makeText(
                context,
                context.getString(R.string.settings_toast_no_grant_needed),
                Toast.LENGTH_SHORT,
            ).show()
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
                    text = stringResource(
                        R.string.settings_color_transparency_summary,
                        formatArgb(argb),
                        argbTransparencyPercent(argb),
                    ),
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

@Composable
private fun backgroundSubtitle(background: ScheduleBackgroundPreferences): String = when (background.type) {
    ScheduleBackgroundType.Color -> stringResource(
        R.string.settings_background_summary_color,
        formatArgb(background.colorArgb),
    )
    ScheduleBackgroundType.Image -> if (background.imageUri != null) {
        stringResource(R.string.settings_background_summary_image)
    } else {
        stringResource(R.string.settings_background_summary_image_none)
    }
    ScheduleBackgroundType.Header -> stringResource(R.string.settings_background_summary_header)
}

@Composable
private fun widgetThemeLabel(preferences: WidgetThemePreferences): String =
    if (preferences.backgroundMode == WidgetBackgroundMode.Image) {
        stringResource(R.string.settings_background_summary_image)
    } else {
        themeAccentDisplayName(preferences.themeAccent)
    }

@Composable
private fun themeAccentDisplayName(accent: ThemeAccent): String = when (accent) {
    ThemeAccent.Green -> stringResource(R.string.settings_accent_green)
    ThemeAccent.Blue -> stringResource(R.string.settings_accent_blue)
    ThemeAccent.Purple -> stringResource(R.string.settings_accent_purple)
    ThemeAccent.Orange -> stringResource(R.string.settings_accent_orange)
    ThemeAccent.Pink -> stringResource(R.string.settings_accent_pink)
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
        title = { Text(stringResource(R.string.settings_alarm_backend_title)) },
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) }
        },
    )
}

@Composable
private fun alarmBackendLabel(backend: ReminderAlarmBackend): String = when (backend) {
    ReminderAlarmBackend.AppAlarmClock -> stringResource(R.string.settings_alarm_backend_app)
    ReminderAlarmBackend.SystemClockApp -> stringResource(R.string.settings_alarm_backend_system)
}

@Composable
private fun alarmBackendDescription(backend: ReminderAlarmBackend): String = when (backend) {
    ReminderAlarmBackend.AppAlarmClock -> stringResource(R.string.settings_alarm_backend_app_desc)
    ReminderAlarmBackend.SystemClockApp -> stringResource(R.string.settings_alarm_backend_system_desc)
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
            Toast.makeText(
                context,
                context.getString(R.string.settings_toast_open_settings_failed, error.message.toString()),
                Toast.LENGTH_SHORT,
            ).show()
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
        title = { Text(stringResource(R.string.settings_dest_temporary_overrides)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverrideModeButton(
                        label = stringResource(R.string.settings_override_mode_makeup),
                        selected = mode == TemporaryOverrideDialogMode.MakeUp,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = TemporaryOverrideDialogMode.MakeUp },
                    )
                    OverrideModeButton(
                        label = stringResource(R.string.settings_override_mode_cancel),
                        selected = mode == TemporaryOverrideDialogMode.CancelCourse,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = TemporaryOverrideDialogMode.CancelCourse },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateChoiceButton(
                        label = if (mode == TemporaryOverrideDialogMode.MakeUp) {
                            stringResource(R.string.settings_override_target_makeup)
                        } else {
                            stringResource(R.string.settings_override_target_cancel)
                        },
                        date = targetDate,
                        modifier = Modifier.weight(1f),
                        onClick = { pickTargetDate = true },
                    )
                    if (mode == TemporaryOverrideDialogMode.MakeUp) {
                        DateChoiceButton(
                            label = stringResource(R.string.settings_override_source_day),
                            date = sourceDate,
                            modifier = Modifier.weight(1f),
                            onClick = { pickSourceDate = true },
                        )
                    }
                }
                if (mode == TemporaryOverrideDialogMode.MakeUp) {
                    Text(
                        text = stringResource(
                            R.string.settings_override_makeup_hint,
                            formatLongDate(targetDate),
                            formatLongDate(sourceDate),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cancelStartNodeText,
                            onValueChange = { cancelStartNodeText = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.settings_override_cancel_start)) },
                            singleLine = true,
                            isError = cancelStartNodeText.isNotBlank() && !canAddCancellation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = cancelEndNodeText,
                            onValueChange = { cancelEndNodeText = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.settings_override_cancel_end)) },
                            singleLine = true,
                            isError = cancelEndNodeText.isNotBlank() && !canAddCancellation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (canAddCancellation) {
                            stringResource(
                                R.string.settings_override_cancel_hint,
                                formatLongDate(targetDate),
                                cancelStartNode.toString(),
                                cancelEndNode.toString(),
                            )
                        } else {
                            stringResource(R.string.settings_override_cancel_invalid)
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
                    Text(
                        if (mode == TemporaryOverrideDialogMode.MakeUp) {
                            stringResource(R.string.settings_override_add)
                        } else {
                            stringResource(R.string.settings_override_add_cancel)
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (overrides.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_override_empty),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) }
        },
        dismissButton = if (overrides.isNotEmpty()) {
            {
                TextButton(onClick = onClear) { Text(stringResource(R.string.settings_clear_all)) }
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
                Text(stringResource(R.string.settings_delete))
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
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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
        title = { Text(stringResource(R.string.settings_dest_holidays)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateChoiceButton(
                    label = stringResource(R.string.settings_date),
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
                    label = { Text(stringResource(R.string.settings_holiday_note_label)) },
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
                    Text(stringResource(R.string.settings_holiday_set_holiday))
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
                    Text(stringResource(R.string.settings_holiday_set_workday))
                }
                if (userEntry != null) {
                    OutlinedButton(
                        onClick = { onRemove(targetDate.toString()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_holiday_remove_manual))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val userEntries = settings.sortedUserEntries()
                if (userEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_holiday_no_manual_entries),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) }
        },
        dismissButton = if (settings.entries.isNotEmpty()) {
            {
                TextButton(onClick = onClear) { Text(stringResource(R.string.settings_clear_all)) }
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
                Text(stringResource(R.string.settings_delete))
            }
        }
    }
}

@Composable
private fun holidayCalendarSubtitle(settings: HolidayCalendarSettings): String {
    val manual = settings.entries.size
    val builtIn = if (settings.builtInEnabled) {
        stringResource(R.string.settings_holiday_builtin_on)
    } else {
        stringResource(R.string.settings_holiday_builtin_off)
    }
    return if (manual == 0) {
        builtIn
    } else {
        stringResource(R.string.settings_holiday_subtitle_with_manual, builtIn, manual)
    }
}

@Composable
private fun builtInHolidayCoverageSubtitle(): String {
    val years = builtInHolidayYears
    return if (years.isEmpty()) {
        stringResource(R.string.settings_holiday_builtin_none)
    } else {
        stringResource(
            R.string.settings_holiday_builtin_years,
            years.joinToString(stringResource(R.string.settings_holiday_year_separator)),
        )
    }
}

@Composable
private fun holidayEntryTitle(entry: HolidayCalendarEntry): String {
    val date = entry.localDate() ?: return stringResource(R.string.settings_invalid_date)
    return formatLongDate(date)
}

@Composable
private fun holidayEntrySubtitle(entry: HolidayCalendarEntry): String {
    val kind = when (entry.kind) {
        HolidayEntryKind.Holiday -> stringResource(R.string.settings_holiday_kind_holiday)
        HolidayEntryKind.Workday -> stringResource(R.string.settings_holiday_kind_workday)
    }
    val note = entry.name.trim()
    return if (note.isBlank()) kind else "$kind · $note"
}

@Composable
private fun holidayDateStatusText(
    date: LocalDate,
    entry: HolidayCalendarEntry?,
    manual: Boolean,
): String {
    val note = entry?.name?.trim().orEmpty()
    val suffix = if (note.isBlank()) {
        ""
    } else {
        stringResource(R.string.settings_holiday_status_note_suffix, note)
    }
    val dateText = formatLongDate(date)
    return when (entry?.kind) {
        HolidayEntryKind.Holiday -> if (manual) {
            stringResource(R.string.settings_holiday_status_holiday_manual, dateText, suffix)
        } else {
            stringResource(R.string.settings_holiday_status_holiday_builtin, dateText, suffix)
        }
        HolidayEntryKind.Workday -> if (manual) {
            stringResource(R.string.settings_holiday_status_workday_manual, dateText, suffix)
        } else {
            stringResource(R.string.settings_holiday_status_workday_builtin, dateText, suffix)
        }
        null -> stringResource(R.string.settings_holiday_status_normal, dateText)
    }
}

@Composable
private fun temporaryOverridesSubtitle(overrides: List<TemporaryScheduleOverride>): String {
    return when {
        overrides.isEmpty() -> stringResource(R.string.settings_not_set)
        overrides.size == 1 -> formatOverrideSummary(overrides.first())
        else -> stringResource(
            R.string.settings_override_subtitle_multi,
            overrides.size,
            formatOverrideSummary(overrides.last()),
        )
    }
}

@Composable
private fun formatOverrideSummary(rule: TemporaryScheduleOverride): String {
    return "${formatOverrideRange(rule)} · ${formatOverrideSource(rule)}"
}

@Composable
private fun formatOverrideRange(rule: TemporaryScheduleOverride): String {
    val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
    return target?.let(::formatShortDate) ?: stringResource(R.string.settings_invalid_date)
}

@Composable
private fun formatOverrideSource(rule: TemporaryScheduleOverride): String {
    if (rule.type == TemporaryScheduleOverrideType.CancelCourse) {
        val start = rule.cancelStartNode
        val end = rule.cancelEndNode ?: start
        return if (start != null && end != null) {
            stringResource(R.string.settings_override_source_cancel, start, end)
        } else {
            stringResource(R.string.settings_override_source_cancel_invalid)
        }
    }
    val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
    val source = target?.let { resolveTemporaryScheduleSourceDate(it, listOf(rule)) }
    return if (source != null) {
        stringResource(R.string.settings_override_source_makeup, formatLongDate(source))
    } else {
        stringResource(R.string.settings_override_source_invalid)
    }
}

private fun formatShortDate(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth}"

private fun formatLongDate(date: LocalDate): String =
    "${formatShortDate(date)} ${weekdayLabel(date.dayOfWeek.value)}"

private fun parseIsoDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

@Composable
private fun webDavSettingsSubtitle(url: String, username: String): String {
    val hasAccount = username.isNotBlank()
    val displayUrl = url.ifBlank { DEFAULT_WEBDAV_URL }
    return if (hasAccount) {
        stringResource(R.string.settings_webdav_subtitle_configured, displayUrl)
    } else {
        stringResource(R.string.settings_webdav_subtitle_unconfigured, displayUrl)
    }
}

@Composable
private fun aiImportSettingsSubtitle(apiUrl: String, model: String): String {
    return when {
        apiUrl.isBlank() -> stringResource(R.string.settings_ai_import_subtitle_none)
        model.isNotBlank() -> stringResource(R.string.settings_ai_import_subtitle_model, model)
        else -> stringResource(R.string.settings_ai_import_subtitle_configured)
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
            title = stringResource(R.string.settings_plugin_registry_title),
            placeholder = "owner/repo",
            value = registryDraft,
            onValueChange = { registryDraft = it },
            onSave = {
                onPluginRegistryRepoChange(registryDraft)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_plugin_registry_saved),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
        MarketIndexUrlEditor(
            title = stringResource(R.string.settings_component_market_title),
            placeholder = "manifest.json",
            value = componentUrlDraft,
            onValueChange = { componentUrlDraft = it },
            onSave = {
                onComponentMarketIndexUrlChange(componentUrlDraft)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_component_market_saved),
                    Toast.LENGTH_SHORT,
                ).show()
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
                Text(stringResource(R.string.settings_save))
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

    SettingsEditorPanel(title = stringResource(R.string.settings_webdav_panel_title)) {
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
            label = { Text(stringResource(R.string.settings_account)) },
        )
        OutlinedTextField(
            value = passwordDraft,
            onValueChange = { passwordDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_password)) },
        )
        Button(
            onClick = {
                onSave(urlDraft, usernameDraft, passwordDraft)
                onSaved(WebDavConfig(urlDraft.trim().ifBlank { DEFAULT_WEBDAV_URL }, usernameDraft.trim(), passwordDraft).isComplete)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_webdav_saved),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save))
        }
        OutlinedButton(
            enabled = !testing,
            onClick = {
                testing = true
                scope.launch {
                    onTest(WebDavConfig(urlDraft, usernameDraft, passwordDraft))
                        .onSuccess {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_toast_webdav_ok),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        .onFailure {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.settings_toast_webdav_failed,
                                    it.message ?: context.getString(R.string.settings_unknown_error),
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    testing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (testing) {
                    stringResource(R.string.settings_testing)
                } else {
                    stringResource(R.string.settings_test_connection)
                },
            )
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

    SettingsEditorPanel(title = stringResource(R.string.settings_dest_ai_import)) {
        OutlinedTextField(
            value = apiUrlDraft,
            onValueChange = { apiUrlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API URL") },
            placeholder = { Text("https://api.openai.com/v1/chat/completions") },
            supportingText = { Text(stringResource(R.string.settings_ai_import_url_hint)) },
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
            label = { Text(stringResource(R.string.settings_ai_import_model_label)) },
            placeholder = { Text("gpt-4o-mini") },
        )
        OutlinedTextField(
            value = timeoutDraft,
            onValueChange = { timeoutDraft = it.filter(Char::isDigit).take(3) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_ai_import_timeout_label)) },
            placeholder = { Text(DEFAULT_AI_IMPORT_TIMEOUT_SECONDS.toString()) },
            supportingText = {
                Text(
                stringResource(
                    R.string.settings_ai_import_timeout_hint,
                    MIN_AI_IMPORT_TIMEOUT_SECONDS,
                    MAX_AI_IMPORT_TIMEOUT_SECONDS,
                    DEFAULT_AI_IMPORT_TIMEOUT_SECONDS,
                ),
            )
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
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_ai_import_saved),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save))
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
                text = stringResource(R.string.settings_developer_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(R.string.settings_developer_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSwitchRow(
            icon = Icons.Rounded.FolderOpen,
            title = stringResource(R.string.settings_dev_files_title),
            subtitle = if (privateFilesProviderEnabled) {
                stringResource(R.string.settings_dev_files_on)
            } else {
                stringResource(R.string.settings_dev_files_off)
            },
            checked = privateFilesProviderEnabled,
            onCheckedChange = onPrivateFilesProviderEnabledChange,
        )
        DeveloperActionRow(
            icon = Icons.Rounded.CalendarMonth,
            title = stringResource(R.string.settings_dev_time_title),
            subtitle = if (debugForcedDateTime != null) {
                stringResource(
                    R.string.settings_dev_time_forced,
                    DateTimeFormatter.ofPattern("yyyy/M/d EEEE HH:mm").format(debugForcedDateTime),
                )
            } else {
                stringResource(R.string.settings_dev_time_real)
            },
            onClick = {
                pendingForcedDate = debugForcedDateTime?.toLocalDate() ?: LocalDate.now()
                showForcedDatePicker = true
            },
        )
        if (debugForcedDateTime != null) {
            DeveloperActionRow(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.settings_dev_time_restore_title),
                subtitle = stringResource(R.string.settings_dev_time_restore_subtitle),
                onClick = {
                    onSetDebugForcedDateTime(null)
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_toast_dev_time_restored),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
        DeveloperActionRow(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.settings_dev_export_logs_title),
            subtitle = stringResource(R.string.settings_dev_export_logs_subtitle),
            onClick = {
                scope.launch {
                    val intent = LogExporter.exportRecentLogs(context)
                    if (intent != null) {
                        runCatching {
                            val chooser = Intent.createChooser(
                                intent,
                                context.getString(R.string.settings_dev_export_logs_title),
                            ).apply {
                                clipData = intent.clipData
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }.onFailure {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_toast_share_failed, it.message.toString()),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_toast_export_logs_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
        DeveloperActionRow(
            icon = Icons.Rounded.Delete,
            title = stringResource(R.string.settings_dev_clear_logs_title),
            subtitle = stringResource(R.string.settings_dev_clear_logs_subtitle),
            onClick = {
                scope.launch {
                    if (LogExporter.clearLogs(context)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_toast_logs_cleared),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_toast_clear_logs_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
        DeveloperActionRow(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.settings_dev_export_metadata_title),
            subtitle = stringResource(R.string.settings_dev_export_metadata_subtitle),
            onClick = onExportScheduleMetadata,
        )
        var showPluginLog by rememberSaveable { mutableStateOf(false) }
        DeveloperActionRow(
            icon = Icons.Rounded.Code,
            title = stringResource(R.string.settings_dev_plugin_log_title),
            subtitle = stringResource(R.string.settings_dev_plugin_log_subtitle),
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
            title = stringResource(R.string.settings_dev_disable_title),
            subtitle = stringResource(R.string.settings_dev_disable_subtitle),
            onClick = {
                onSetDeveloperMode(false)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_dev_mode_off),
                    Toast.LENGTH_SHORT,
                ).show()
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
                    context.getString(
                        R.string.settings_toast_dev_time_forced,
                        DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").format(combined),
                    ),
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
        title = { Text(stringResource(R.string.settings_dev_time_picker_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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

@Composable
internal fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.System -> stringResource(R.string.settings_language_system)
    AppLanguage.Chinese -> stringResource(R.string.settings_language_chinese)
    AppLanguage.English -> "English"
}

/**
 * 节假日数据的同步入口。
 * 放假安排每年由通知决定，这里从公开维护的数据集取回并缓存，取不到时沿用已有数据。
 */
@Composable
private fun HolidayCalendarSyncRow(syncedYears: List<SyncedHolidayYear>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DataStoreUserPreferencesRepository(context.applicationContext) }
    val downloader = remember(context) {
        MirrorDownloader(labels = context.applicationContext.mirrorDownloaderLabels())
    }
    val syncer = remember(downloader) { HolidayCalendarSyncer(downloader) }
    var syncing by remember { mutableStateOf(false) }

    val subtitle = when {
        syncing -> stringResource(R.string.settings_holiday_sync_running)
        else -> holidaySyncSubtitle(syncedYears)
    }
    SettingsActionRow(
        icon = Icons.Rounded.CloudDownload,
        title = stringResource(R.string.settings_holiday_sync_title),
        subtitle = subtitle,
        onClick = {
            if (syncing) return@SettingsActionRow
            syncing = true
            scope.launch {
                val outcomes = syncer.sync(
                    years = holidaySyncYears(LocalDate.now()),
                    cached = syncedYears,
                    force = true,
                )
                repository.putSyncedHolidayYears(
                    outcomes.filterIsInstance<HolidaySyncOutcome.Updated>().map { it.year },
                )
                syncing = false
                Toast.makeText(context, context.holidaySyncMessage(outcomes), Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@Composable
private fun holidaySyncSubtitle(syncedYears: List<SyncedHolidayYear>): String {
    val usable = syncedYears.filter { it.entries.isNotEmpty() }
    if (usable.isEmpty()) return stringResource(R.string.settings_holiday_sync_never)
    return stringResource(
        R.string.settings_holiday_sync_years,
        usable.map { it.year }.sorted().joinToString(stringResource(R.string.settings_holiday_year_separator)),
        usable.last().source.ifBlank { stringResource(R.string.download_source_local_file) },
    )
}

/** 同步结果的提示文案，只报告最值得说的一条。 */
private fun Context.holidaySyncMessage(outcomes: List<HolidaySyncOutcome>): String {
    outcomes.filterIsInstance<HolidaySyncOutcome.Updated>().firstOrNull()
        ?.let { return getString(R.string.settings_holiday_sync_done) }
    outcomes.filterIsInstance<HolidaySyncOutcome.Unusable>().firstOrNull()
        ?.let { return getString(R.string.settings_holiday_sync_unusable, it.year) }
    outcomes.filterIsInstance<HolidaySyncOutcome.Unreachable>().firstOrNull()
        ?.let { return getString(R.string.settings_holiday_sync_unreachable, it.year) }
    return getString(R.string.settings_holiday_sync_fresh)
}

/** 一周起始日选择。两个取值对等，用并排按钮而不是开关。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekStartDayRow(selected: WeekStartDay, onSelect: (WeekStartDay) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_display_week_start_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_display_week_start_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WeekStartDay.entries.forEach { day ->
                    val label = when (day) {
                        WeekStartDay.Monday -> R.string.settings_display_week_start_monday
                        WeekStartDay.Sunday -> R.string.settings_display_week_start_sunday
                    }
                    if (day == selected) {
                        Button(onClick = { onSelect(day) }) {
                            Text(stringResource(label), maxLines = 2)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(day) }) {
                            Text(stringResource(label), maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
