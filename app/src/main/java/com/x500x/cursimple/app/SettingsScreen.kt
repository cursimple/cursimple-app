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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LineStyle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Weekend
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.app.download.mirrorDownloaderLabels
import com.x500x.cursimple.app.download.SharedPrefsMirrorPreferenceStore
import com.x500x.cursimple.app.holiday.HolidayCalendarSyncer
import com.x500x.cursimple.app.holiday.HolidaySyncOutcome
import com.x500x.cursimple.app.holiday.holidaySyncYears
import com.x500x.cursimple.app.util.PREVIEW_MAX_EDGE_PX
import com.x500x.cursimple.app.util.decodeSampledImage
import com.x500x.cursimple.app.permission.PermissionRequestOutcome
import com.x500x.cursimple.app.term.isTermStartFromPlugin
import com.x500x.cursimple.app.permission.findActivity
import com.x500x.cursimple.app.permission.permissionRequestOutcome
import com.x500x.cursimple.app.update.UpdateNoticeState
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
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TimingProfileEntry
import com.x500x.cursimple.core.kernel.model.TimingProfileLibrary
import com.x500x.cursimple.core.kernel.model.active
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.kernel.time.ScheduleRowFitMode
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
    ScheduleData,
    TemporaryOverrides,
    Holidays,
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
    ScheduleBackground,
}

enum class SettingsReturnTargetKey {
    ImportExport,
}

private fun SettingsDestinationKey.toDestination(): SettingsDestination = when (this) {
    SettingsDestinationKey.WebDav -> SettingsDestination.WebDav
    SettingsDestinationKey.AiImport -> SettingsDestination.AiImport
    SettingsDestinationKey.ScheduleBackground -> SettingsDestination.ScheduleBackground
}

/** 深链跳转时补齐的上级页面，返回键沿这条链逐级回退。 */
private fun SettingsDestination.parentChain(): List<SettingsDestination> = when (this) {
    SettingsDestination.ScheduleBackground -> listOf(SettingsDestination.ScheduleAppearance)
    else -> emptyList()
}

@Composable
private fun SettingsDestination.title(): String = when (this) {
    SettingsDestination.Root -> stringResource(R.string.settings_dest_root)
    SettingsDestination.ScheduleData -> stringResource(R.string.settings_dest_schedule_data)
    SettingsDestination.TemporaryOverrides -> stringResource(R.string.settings_dest_temporary_overrides)
    SettingsDestination.Holidays -> stringResource(R.string.settings_dest_holidays)
    SettingsDestination.ScheduleAppearance -> stringResource(R.string.settings_dest_schedule_style)
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
    termStartUserDecided: Boolean,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    scheduleCustomColorsAdaptToTheme: Boolean,
    widgetThemePreferences: WidgetThemePreferences,
    currentWeekIndex: Int,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings(),
    autoUpdateEnabled: Boolean,
    betaUpdatesEnabled: Boolean,
    appTimeZoneId: String?,
    ignoredUpdateVersionCode: Int?,
    updateNotice: UpdateNoticeState,
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
    onScheduleBackgroundImageTransparencyPercentChange: (Int) -> Unit,
    onScheduleBackgroundUseHeaderColor: () -> Unit,
    onScheduleCustomColorsAdaptToThemeChange: (Boolean) -> Unit,
    onScheduleNodeColumnTimeEnabledChange: (Boolean) -> Unit,
    onScheduleSaturdayVisibleChange: (Boolean) -> Unit,
    onScheduleWeekendVisibleChange: (Boolean) -> Unit,
    onScheduleRowFitModeChange: (ScheduleRowFitMode) -> Unit,
    onScheduleWeekStartDayChange: (WeekStartDay) -> Unit,
    onCourseDragEnabledChange: (Boolean) -> Unit,
    onScheduleLocationVisibleChange: (Boolean) -> Unit,
    onScheduleTeacherVisibleChange: (Boolean) -> Unit,
    onTotalScheduleDisplayChange: (Boolean) -> Unit,
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
    onBetaUpdatesEnabledChange: (Boolean) -> Unit,
    onAppTimeZoneChange: (String?) -> Unit,
    onIgnoreUpdateVersion: (Int?) -> Unit,
    onMuteUpdateVersion: (Int?) -> Unit,
    onUpdateFound: (Int, String) -> Unit,
    onUpdateNoticeCleared: () -> Unit,
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
        backStack = buildList {
            add(SettingsDestination.Root.name)
            requested.parentChain().forEach { add(it.name) }
            add(requested.name)
        }
        settingsReturnReady = false
        onOpenDestinationConsumed()
    }
    BackHandler(enabled = destination != SettingsDestination.Root) {
        handleBack()
    }
    var showTemporaryOverrides by rememberSaveable { mutableStateOf(false) }
    var showHolidayEditor by rememberSaveable { mutableStateOf(false) }
    var showResetScheduleAppearanceConfirm by rememberSaveable { mutableStateOf(false) }
    var showResetAllSettingsConfirm by rememberSaveable { mutableStateOf(false) }
    // 被永久拒绝后系统不再弹窗，此时必须改把用户送到应用详情页，否则按钮点了没有任何反应
    fun handlePermissionResult(permission: String, granted: Boolean, grantedRes: Int, deniedRes: Int) {
        val activity = context.findActivity()
        val canAskAgain = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
        } ?: true
        when (permissionRequestOutcome(granted, canAskAgain)) {
            PermissionRequestOutcome.Granted ->
                Toast.makeText(context, context.getString(grantedRes), Toast.LENGTH_SHORT).show()

            PermissionRequestOutcome.Denied ->
                Toast.makeText(context, context.getString(deniedRes), Toast.LENGTH_SHORT).show()

            PermissionRequestOutcome.PermanentlyDenied -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_permission_open_settings),
                    Toast.LENGTH_LONG,
                ).show()
                launchSettingsIntent(context, AlarmPermissionIntents.appDetailsIntent(context))
            }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        handlePermissionResult(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            granted = granted,
            grantedRes = R.string.settings_toast_notification_granted,
            deniedRes = R.string.settings_toast_notification_denied,
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        handlePermissionResult(
            permission = Manifest.permission.CAMERA,
            granted = granted,
            grantedRes = R.string.settings_toast_camera_granted,
            deniedRes = R.string.settings_toast_camera_denied,
        )
    }
    var pendingBackgroundSource by remember { mutableStateOf<android.net.Uri?>(null) }
    pendingBackgroundSource?.let { source ->
        ScheduleBackgroundCropDialog(
            source = source,
            frameAspect = SCHEDULE_BACKGROUND_FRAME_ASPECT,
            onDismiss = { pendingBackgroundSource = null },
            onCropped = { cropped ->
                pendingBackgroundSource = null
                onScheduleBackgroundImageUriChange(cropped.toString())
            },
        )
    }
    val scheduleBackgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (persisted) {
                // 先让用户按课表比例裁切并确认，再落到设置里
                pendingBackgroundSource = uri
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
        if (destination != SettingsDestination.Root) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = ::handleBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = destination.title(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        when (destination) {
            SettingsDestination.Root -> {
                SettingsGroup(stringResource(R.string.settings_group_appearance)) {
                    SettingsActionRow(
                        icon = when (themeMode) {
                            ThemeMode.Dark -> Icons.Rounded.Brightness4
                            else -> Icons.Rounded.Brightness7
                        },
                        title = stringResource(R.string.settings_theme_mode_title),
                        subtitle = when (themeMode) {
                            ThemeMode.System -> stringResource(R.string.settings_theme_mode_system)
                            ThemeMode.Light -> stringResource(R.string.settings_theme_mode_light)
                            ThemeMode.Dark -> stringResource(R.string.settings_theme_mode_dark)
                        },
                        onClick = onPickThemeMode,
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_theme),
                        subtitle = themeAccentLabel,
                        onClick = onPickThemeAccent,
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.settings_dest_schedule_style),
                        subtitle = stringResource(R.string.settings_row_schedule_style_subtitle),
                        onClick = { navigate(SettingsDestination.ScheduleAppearance) },
                    )
                    SettingsActionRow(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = stringResource(R.string.settings_display),
                        subtitle = stringResource(R.string.settings_row_schedule_display_subtitle),
                        onClick = { navigate(SettingsDestination.ScheduleDisplay) },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Widgets,
                        title = stringResource(R.string.settings_dest_widget_settings),
                        subtitle = stringResource(R.string.settings_row_widget_settings_subtitle),
                        onClick = { navigate(SettingsDestination.WidgetSettings) },
                    )
                }

                SettingsGroup(stringResource(R.string.settings_group_schedule)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.CalendarMonth,
                        title = stringResource(R.string.settings_dest_schedule_data),
                        subtitle = stringResource(R.string.settings_row_schedule_data_subtitle),
                        onClick = { navigate(SettingsDestination.ScheduleData) },
                    )
                    TimingProfileEntryRow { navigate(SettingsDestination.TimingProfile) }
                    SettingsActionRow(
                        icon = Icons.Rounded.EventRepeat,
                        title = stringResource(R.string.settings_dest_temporary_overrides),
                        subtitle = temporaryOverridesSubtitle(temporaryScheduleOverrides),
                        onClick = { navigate(SettingsDestination.TemporaryOverrides) },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.EventBusy,
                        title = stringResource(R.string.settings_dest_holidays),
                        subtitle = holidayCalendarSubtitle(holidayCalendar),
                        onClick = { navigate(SettingsDestination.Holidays) },
                    )
                }

                SettingsGroup(stringResource(R.string.settings_group_reminder)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.VolumeOff,
                        title = stringResource(R.string.settings_dest_auto_silence),
                        subtitle = stringResource(R.string.settings_row_auto_silence_subtitle),
                        onClick = { navigate(SettingsDestination.AutoSilence) },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.settings_dest_permissions),
                        subtitle = stringResource(R.string.settings_row_permissions_subtitle),
                        onClick = { navigate(SettingsDestination.Permissions) },
                    )
                }

                SettingsGroup(stringResource(R.string.settings_group_data)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Extension,
                        title = stringResource(R.string.settings_dest_plugins),
                        subtitle = stringResource(R.string.settings_row_plugins_subtitle),
                        onClick = { navigate(SettingsDestination.Plugins) },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Storage,
                        title = "WebDAV",
                        subtitle = webDavSettingsSubtitle(webDavUrl, webDavUsername),
                        onClick = { navigate(SettingsDestination.WebDav) },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.ImageSearch,
                        title = stringResource(R.string.settings_dest_ai_import),
                        subtitle = aiImportSettingsSubtitle(aiImportApiUrl, aiImportModel),
                        onClick = { navigate(SettingsDestination.AiImport) },
                    )
                }

                SettingsGroup(stringResource(R.string.settings_group_general)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = appLanguageLabel(appLanguage),
                        onClick = onPickAppLanguage,
                    )
                    TimeZoneRow(zoneId = appTimeZoneId, onZoneChange = onAppTimeZoneChange)
                    SettingsActionRow(
                        icon = Icons.Rounded.Restore,
                        title = stringResource(R.string.settings_reset_all_title),
                        subtitle = stringResource(R.string.settings_reset_all_subtitle),
                        onClick = { showResetAllSettingsConfirm = true },
                    )
                }

                SettingsGroup(stringResource(R.string.update_section_title)) {
                    UpdateCheckSection(
                        autoCheckEnabled = autoUpdateEnabled,
                        betaUpdatesEnabled = betaUpdatesEnabled,
                        ignoredUpdateVersionCode = ignoredUpdateVersionCode,
                        updateNotice = updateNotice,
                        onAutoCheckEnabledChange = onAutoUpdateEnabledChange,
                        onIgnoreUpdateVersion = onIgnoreUpdateVersion,
                        onMuteUpdateVersion = onMuteUpdateVersion,
                        onUpdateFound = onUpdateFound,
                        onUpdateNoticeCleared = onUpdateNoticeCleared,
                    )
                    BetaUpdatesRow(
                        enabled = betaUpdatesEnabled,
                        onEnabledChange = onBetaUpdatesEnabledChange,
                    )
                }
            }

            SettingsDestination.ScheduleData -> {
                SettingsActionRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.settings_term_start_title),
                    subtitle = termStartDate?.let {
                        val fmt = DateTimeFormatter.ofPattern("yyyy/M/d")
                        val week = LocalContext.current.termWeekText(termWeekLabel(currentWeekIndex))
                        val source = if (isTermStartFromPlugin(termStartUserDecided, termStartDate)) {
                            " · " + stringResource(R.string.settings_term_start_from_plugin)
                        } else {
                            ""
                        }
                        "${fmt.format(it)} · $week$source"
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
                    icon = Icons.Rounded.TextFields,
                    title = stringResource(R.string.settings_text_style),
                    subtitle = stringResource(R.string.settings_text_style_subtitle),
                    onClick = { navigate(SettingsDestination.ScheduleTextStyle) },
                )
                SettingsActionRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.settings_header_style),
                    subtitle = stringResource(R.string.settings_header_style_subtitle),
                    onClick = { navigate(SettingsDestination.ScheduleHeaderStyle) },
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.settings_card_style),
                    subtitle = stringResource(R.string.settings_card_style_subtitle),
                    onClick = { navigate(SettingsDestination.ScheduleCardStyle) },
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Wallpaper,
                    title = stringResource(R.string.settings_schedule_background),
                    subtitle = backgroundSubtitle(scheduleBackground),
                    onClick = { navigate(SettingsDestination.ScheduleBackground) },
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.settings_reset_schedule_title),
                    subtitle = stringResource(R.string.settings_reset_schedule_subtitle),
                    onClick = { showResetScheduleAppearanceConfirm = true },
                )
            }

            SettingsDestination.ScheduleTextStyle -> {
                SettingsGroup(stringResource(R.string.settings_subgroup_course_text)) {
                    NumberStepperRow(stringResource(R.string.settings_course_text_size), scheduleTextStyle.courseTextSizeSp, "sp", 8, 32, 1, onScheduleCourseTextSizeSpChange)
                    ColorAlphaRow(stringResource(R.string.settings_course_text_color), scheduleTextStyle.courseTextColorArgb, onScheduleCourseTextColorArgbChange)
                    if (scheduleCustomColorsAdaptToTheme) {
                        ColorPreviewRow(
                            stringResource(R.string.settings_current_theme_preview),
                            scheduleTextStyle.courseTextColorArgb.adaptForegroundForPreview(darkTheme),
                        )
                    }
                }

                SettingsGroup(stringResource(R.string.settings_subgroup_exam_text)) {
                    NumberStepperRow(stringResource(R.string.settings_exam_text_size), scheduleTextStyle.examTextSizeSp, "sp", 8, 32, 1, onScheduleExamTextSizeSpChange)
                    ColorAlphaRow(stringResource(R.string.settings_exam_text_color), scheduleTextStyle.examTextColorArgb, onScheduleExamTextColorArgbChange)
                    if (scheduleCustomColorsAdaptToTheme) {
                        ColorPreviewRow(
                            stringResource(R.string.settings_current_theme_preview),
                            scheduleTextStyle.examTextColorArgb.adaptForegroundForPreview(darkTheme),
                        )
                    }
                }

                SettingsGroup(stringResource(R.string.settings_subgroup_alignment)) {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.FormatAlignCenter,
                        title = stringResource(R.string.settings_text_center_horizontal_title),
                        subtitle = stringResource(R.string.settings_text_center_horizontal_subtitle),
                        checked = scheduleTextStyle.horizontalCenter,
                        onCheckedChange = onScheduleTextHorizontalCenterChange,
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.VerticalAlignCenter,
                        title = stringResource(R.string.settings_text_center_vertical_title),
                        subtitle = stringResource(R.string.settings_text_center_vertical_subtitle),
                        checked = scheduleTextStyle.verticalCenter,
                        onCheckedChange = onScheduleTextVerticalCenterChange,
                    )
                }
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
                SettingsGroup(stringResource(R.string.settings_subgroup_card)) {
                    NumberStepperRow(stringResource(R.string.settings_card_corner_radius), scheduleCardStyle.courseCornerRadiusDp, "dp", 0, 32, 1, onScheduleCourseCornerRadiusDpChange)
                    NumberStepperRow(stringResource(R.string.settings_card_height), scheduleCardStyle.courseCardHeightDp, "dp", 56, 160, 4, onScheduleCourseCardHeightDpChange)
                    if (scheduleDisplay.rowFitMode == ScheduleRowFitMode.Fit) {
                        SettingsHintText(stringResource(R.string.settings_card_height_fit_hint))
                    }
                    NumberStepperRow(stringResource(R.string.settings_schedule_opacity), scheduleCardStyle.scheduleOpacityPercent, "%", 0, 100, 5, onScheduleOpacityPercentChange)
                    NumberStepperRow(stringResource(R.string.settings_inactive_course_opacity), scheduleCardStyle.inactiveCourseOpacityPercent, "%", 0, 100, 5, onScheduleInactiveCourseOpacityPercentChange)
                }

                SettingsGroup(stringResource(R.string.settings_subgroup_grid_border)) {
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
                        icon = Icons.Rounded.LineStyle,
                        title = stringResource(R.string.settings_grid_border_dashed_title),
                        subtitle = stringResource(R.string.settings_grid_border_dashed_subtitle),
                        checked = scheduleCardStyle.gridBorderDashed,
                        onCheckedChange = onScheduleGridBorderDashedChange,
                    )
                }
            }

            SettingsDestination.ScheduleBackground -> {
                ScheduleBackgroundPreview(
                    scheduleBackground = scheduleBackground,
                    scheduleCardStyle = scheduleCardStyle,
                    customColorsAdaptToTheme = scheduleCustomColorsAdaptToTheme,
                )
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
                    NumberStepperRow(
                        stringResource(R.string.settings_background_image_transparency),
                        scheduleBackground.imageTransparencyPercent,
                        "%",
                        0,
                        100,
                        5,
                        onScheduleBackgroundImageTransparencyPercentChange,
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.settings_background_image_clear_title),
                        subtitle = stringResource(R.string.settings_background_image_clear_subtitle),
                        onClick = onClearScheduleBackgroundImage,
                    )
                }
            }

            SettingsDestination.ScheduleDisplay -> {
                SettingsGroup(stringResource(R.string.settings_subgroup_visible_range)) {
                    WeekStartDayRow(
                        selected = scheduleDisplay.weekStartDay,
                        onSelect = onScheduleWeekStartDayChange,
                    )
                    VisibleDaysRow(
                        saturdayVisible = scheduleDisplay.saturdayVisible,
                        weekendVisible = scheduleDisplay.weekendVisible,
                        onSelect = { days ->
                            onScheduleSaturdayVisibleChange(days >= 6)
                            onScheduleWeekendVisibleChange(days == 7)
                        },
                    )
                    RowFitModeRow(
                        selected = scheduleDisplay.rowFitMode,
                        onSelect = onScheduleRowFitModeChange,
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

                SettingsGroup(stringResource(R.string.settings_subgroup_cell_info)) {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.settings_display_node_time_title),
                        subtitle = stringResource(R.string.settings_display_node_time_subtitle),
                        checked = scheduleDisplay.nodeColumnTimeEnabled,
                        onCheckedChange = onScheduleNodeColumnTimeEnabledChange,
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Place,
                        title = stringResource(R.string.settings_display_location_title),
                        subtitle = stringResource(R.string.settings_display_location_subtitle),
                        checked = scheduleDisplay.locationVisible,
                        onCheckedChange = onScheduleLocationVisibleChange,
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Person,
                        title = stringResource(R.string.settings_display_teacher_title),
                        subtitle = stringResource(R.string.settings_display_teacher_subtitle),
                        checked = scheduleDisplay.teacherVisible,
                        onCheckedChange = onScheduleTeacherVisibleChange,
                    )
                }

                SettingsGroup(stringResource(R.string.settings_subgroup_interaction)) {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.OpenWith,
                        title = stringResource(R.string.settings_display_course_drag_title),
                        subtitle = stringResource(R.string.settings_display_course_drag_subtitle),
                        checked = scheduleDisplay.courseDragEnabled,
                        onCheckedChange = onCourseDragEnabledChange,
                    )
                }
            }

            SettingsDestination.WidgetSettings -> {
                SettingsGroup(stringResource(R.string.settings_subgroup_widget_look)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_theme),
                        subtitle = widgetThemeLabel(widgetThemePreferences),
                        onClick = onPickWidgetThemeAccent,
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Wallpaper,
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

                SettingsGroup(stringResource(R.string.settings_subgroup_widget_behavior)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Widgets,
                        title = stringResource(R.string.settings_widget_home_title),
                        subtitle = stringResource(R.string.settings_widget_home_subtitle),
                        onClick = onOpenWidgetPicker,
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.TouchApp,
                        title = stringResource(R.string.settings_widget_open_app_title),
                        subtitle = stringResource(R.string.settings_widget_open_app_subtitle),
                        checked = widgetThemePreferences.openAppOnDoubleClickEnabled,
                        onCheckedChange = onWidgetOpenAppOnDoubleClickChange,
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

/** 设置列表的一个分组：标题加同类条目，条目间距比分组间距更紧。 */
@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingsSectionHeader(title)
        content()
    }
}

@Composable
internal fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
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
    // 用户去系统设置改完权限再回来，这里必须重读，否则界面一直停在进页面那一刻的状态
    val state = rememberPermissionState(context)

    val missingAlarmPermissions = buildList {
        if (!state.notification) add(stringResource(R.string.settings_permission_notification))
        if (!state.exactAlarm) add(stringResource(R.string.settings_permission_exact_alarm))
        if (!state.fullScreenIntent) add(stringResource(R.string.settings_permission_full_screen))
        if (!state.batteryOptimizationIgnored) add(stringResource(R.string.settings_permission_background))
    }

    PermissionSummaryCard(missing = missingAlarmPermissions)

    SettingsSectionHeader(stringResource(R.string.settings_section_grant))
    PermissionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_permission_notification),
        granted = state.notification,
        offText = stringResource(R.string.settings_permission_notification_off),
        onText = stringResource(R.string.settings_permission_runtime_hint),
        onClick = {
            // 已授予时也要能进系统设置关掉，不然用户在这一页只有单向操作
            if (!state.notification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchSettingsIntent(context, AlarmPermissionIntents.appDetailsIntent(context))
            }
        },
    )
    PermissionRow(
        icon = Icons.Rounded.Schedule,
        title = stringResource(R.string.settings_permission_exact_alarm),
        granted = state.exactAlarm,
        offText = stringResource(R.string.settings_permission_exact_alarm_off),
        onText = stringResource(R.string.settings_permission_manage_hint),
        onClick = { launchSettingsIntent(context, AlarmPermissionIntents.exactAlarmSettingsIntent(context)) },
    )
    PermissionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_permission_full_screen),
        granted = state.fullScreenIntent,
        offText = stringResource(R.string.settings_permission_full_screen_off),
        onText = stringResource(R.string.settings_permission_manage_hint),
        onClick = { launchSettingsIntent(context, AlarmPermissionIntents.fullScreenIntentSettingsIntent(context)) },
    )
    PermissionRow(
        icon = Icons.Rounded.Restore,
        title = stringResource(R.string.settings_permission_battery_title),
        granted = state.batteryOptimizationIgnored,
        offText = stringResource(R.string.settings_permission_battery_off),
        onText = stringResource(R.string.settings_permission_manage_hint),
        onClick = { launchSettingsIntent(context, AlarmPermissionIntents.batteryOptimizationIntent(context)) },
    )
    PermissionRow(
        icon = Icons.Rounded.Code,
        title = stringResource(R.string.settings_permission_camera),
        granted = state.camera,
        offText = stringResource(R.string.settings_permission_camera_off),
        onText = stringResource(R.string.settings_permission_runtime_hint),
        onClick = {
            if (state.camera) {
                launchSettingsIntent(context, AlarmPermissionIntents.appDetailsIntent(context))
            } else {
                cameraLauncher(Manifest.permission.CAMERA)
            }
        },
    )
    PermissionRow(
        icon = Icons.Rounded.Download,
        title = stringResource(R.string.settings_permission_install),
        granted = state.installPackages,
        offText = stringResource(R.string.settings_permission_install_off),
        onText = stringResource(R.string.settings_permission_manage_hint),
        onClick = { launchSettingsIntent(context, unknownAppInstallSettingsIntent(context)) },
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

/** 权限页关心的几项当前状态。 */
private data class AppPermissionState(
    val notification: Boolean,
    val exactAlarm: Boolean,
    val fullScreenIntent: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val camera: Boolean,
    val installPackages: Boolean,
)

private fun readPermissionState(context: Context): AppPermissionState {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    return AppPermissionState(
        notification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        exactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
        fullScreenIntent = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent(),
        batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName),
        camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        installPackages = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls(),
    )
}

/** 每次回到前台重读一次权限，跟随用户在系统设置里的改动。 */
@Composable
private fun rememberPermissionState(context: Context): AppPermissionState {
    var state by remember { mutableStateOf(readPermissionState(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                state = readPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
private fun PermissionSummaryCard(missing: List<String>) {
    val ok = missing.isEmpty()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        val onColor = if (ok) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = onColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (ok) R.string.settings_permissions_all_ok_title else R.string.settings_alarm_warning_title,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = onColor,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (ok) R.string.settings_permissions_all_ok_body else R.string.settings_alarm_warning_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = onColor,
            )
            missing.forEach { permission ->
                Text(
                    text = "\u2022 $permission",
                    style = MaterialTheme.typography.bodySmall,
                    color = onColor,
                )
            }
        }
    }
}

/** 名称、当前状态徽章与一句操作说明，点整行进对应的授予或系统设置入口。 */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    offText: String,
    onText: String,
    onClick: () -> Unit,
) {
    SettingsActionRow(
        icon = icon,
        title = title,
        subtitle = if (granted) onText else offText,
        onClick = onClick,
        trailing = {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (granted) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ) {
                Text(
                    text = stringResource(
                        if (granted) {
                            R.string.settings_permission_status_on
                        } else {
                            R.string.settings_permission_status_off
                        },
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (granted) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                )
            }
        },
    )
}

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


internal fun parseIsoDate(value: String): LocalDate? =
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
                pendingForcedDate = debugForcedDateTime?.toLocalDate() ?: BeijingTime.today()
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
            initial = pendingForcedDate ?: debugForcedDateTime?.toLocalDate() ?: BeijingTime.today(),
            onConfirm = { date ->
                pendingForcedDate = date
                showForcedDatePicker = false
                showForcedTimePicker = true
            },
            onDismiss = { showForcedDatePicker = false },
        )
    }

    if (showForcedTimePicker) {
        val baseDate = pendingForcedDate ?: debugForcedDateTime?.toLocalDate() ?: BeijingTime.today()
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
internal fun SettingsActionRow(
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
internal fun SettingsSwitchRow(
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
    ScheduleSettingsRoute(
        viewModel = viewModel,
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
        modifier = modifier,
    )
}

@Composable
internal fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.System -> stringResource(R.string.settings_language_system)
    AppLanguage.Chinese -> stringResource(R.string.settings_language_chinese)
    // 语言名用该语言自身书写，不随界面语言变化
    AppLanguage.TraditionalChinese -> "繁體中文"
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
        MirrorDownloader(
            labels = context.applicationContext.mirrorDownloaderLabels(),
            preferenceStore = SharedPrefsMirrorPreferenceStore(context.applicationContext),
        )
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
                    years = holidaySyncYears(BeijingTime.today()),
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

/**
 * 背景效果预览。
 * 按课表比例画出当前背景，图片与颜色都按实际透明度渲染，
 * 上面叠一格示意课程，用户不必回到课表就能看出可读性。
 */
@Composable
private fun ScheduleBackgroundPreview(
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    customColorsAdaptToTheme: Boolean,
) {
    val context = LocalContext.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val imageUri = scheduleBackground.imageUri?.takeIf(String::isNotBlank)
    val bitmap by androidx.compose.runtime.produceState<ImageBitmap?>(
        initialValue = null,
        key1 = imageUri,
    ) {
        value = imageUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    decodeSampledImage(context, android.net.Uri.parse(uri), PREVIEW_MAX_EDGE_PX)
                }.getOrNull()
            }
        }
    }
    val baseColor = when (scheduleBackground.type) {
        ScheduleBackgroundType.Header -> MaterialTheme.colorScheme.surface
        ScheduleBackgroundType.Color,
        ScheduleBackgroundType.Image,
        -> Color(
            adaptScheduleBackgroundColorArgb(
                scheduleBackground.colorArgb,
                darkTheme,
                customColorsAdaptToTheme,
            ).toULong() shl 32,
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_background_preview_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.55f)
                .aspectRatio(SCHEDULE_BACKGROUND_FRAME_ASPECT)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor.copy(alpha = baseColor.alpha * transparencyToAlpha(scheduleCardStyle.scheduleOpacityPercent))),
            )
            bitmap?.takeIf { scheduleBackground.type == ScheduleBackgroundType.Image }?.let { image ->
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(
                            transparencyToAlpha(scheduleCardStyle.scheduleOpacityPercent) *
                                transparencyToAlpha(scheduleBackground.imageTransparencyPercent),
                        ),
                )
            }
            if (scheduleBackground.type == ScheduleBackgroundType.Image && imageUri == null) {
                Text(
                    text = stringResource(R.string.settings_background_preview_empty),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 叠一格示意课程，用来判断背景之上文字还读不读得清
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp)
                    .width(96.dp)
                    .height(74.dp),
                shape = RoundedCornerShape(scheduleCardStyle.courseCornerRadiusDp.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_background_preview_course),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** 透明百分比换算成绘制用的 alpha，0 表示完全不透明。 */
private fun transparencyToAlpha(percent: Int): Float = 1f - (percent.coerceIn(0, 100) / 100f)

/** 平铺与滚动二选一，平铺把全部节次压进一屏，滚动保留设定行高并在右侧给出滑块。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowFitModeRow(selected: ScheduleRowFitMode, onSelect: (ScheduleRowFitMode) -> Unit) {
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
                text = stringResource(R.string.settings_display_row_fit_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_display_row_fit_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ScheduleRowFitMode.Fit to R.string.settings_display_row_fit_fit,
                    ScheduleRowFitMode.Scroll to R.string.settings_display_row_fit_scroll,
                ).forEach { (mode, label) ->
                    if (mode == selected) {
                        Button(onClick = { onSelect(mode) }) { Text(stringResource(label), maxLines = 1) }
                    } else {
                        OutlinedButton(onClick = { onSelect(mode) }) { Text(stringResource(label), maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** 五天、六天、整周三选一，替代原先相互牵连的两个开关。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisibleDaysRow(
    saturdayVisible: Boolean,
    weekendVisible: Boolean,
    onSelect: (Int) -> Unit,
) {
    val selected = when {
        weekendVisible -> 7
        saturdayVisible -> 6
        else -> 5
    }
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
                text = stringResource(R.string.settings_display_days_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_display_days_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    5 to R.string.settings_display_days_five,
                    6 to R.string.settings_display_days_six,
                    7 to R.string.settings_display_days_seven,
                ).forEach { (days, label) ->
                    if (days == selected) {
                        Button(onClick = { onSelect(days) }) { Text(stringResource(label), maxLines = 1) }
                    } else {
                        OutlinedButton(onClick = { onSelect(days) }) { Text(stringResource(label), maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** 课表大致的宽高比，裁切框按它预览。 */
private const val SCHEDULE_BACKGROUND_FRAME_ASPECT = 0.62f

/** 设置项下方的补充说明，用于解释某项在当前配置下不生效。 */
@Composable
private fun SettingsHintText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 14.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
