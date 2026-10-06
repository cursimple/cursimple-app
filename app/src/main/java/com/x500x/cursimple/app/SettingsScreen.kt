@file:Suppress("LocalContextGetResourceValueCall")

package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LineStyle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
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
import com.x500x.cursimple.app.reminder.AlarmDiagnostics
import com.x500x.cursimple.app.reminder.AlarmDiagnosticsReport
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.permission.AlarmSettingsIntents
import com.x500x.cursimple.core.reminder.permission.canScheduleExactAlarms
import com.x500x.cursimple.core.reminder.permission.canUseFullScreenIntent
import com.x500x.cursimple.core.reminder.permission.hasNotificationPermission
import com.x500x.cursimple.core.reminder.permission.isIgnoringBatteryOptimizations
import com.x500x.cursimple.core.reminder.permission.launchFirstAvailableSetting
import com.x500x.cursimple.app.reminder.AutoSilenceController
import com.x500x.cursimple.app.util.LogCategories
import com.x500x.cursimple.app.util.LogCategory
import com.x500x.cursimple.app.util.LogExporter
import com.x500x.cursimple.app.webdav.WebDavConfig
import com.x500x.cursimple.app.notice.ClassNoticeDiagnostics
import com.x500x.cursimple.app.notice.ClassNoticeNotifier
import com.x500x.cursimple.app.notice.ClassNoticeOverlay
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.widget.WidgetBackgroundMode
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.slotTimes
import com.x500x.cursimple.core.kernel.model.CancelCoursePlan
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.SyncedHolidayYear
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.active
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.kernel.time.ScheduleRowFitMode
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.sortedUserEntries
import com.x500x.cursimple.core.kernel.model.termWeekLabel
import com.x500x.cursimple.core.kernel.model.termWeekText
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.feature.schedule.ScheduleAppearancePreview
import com.x500x.cursimple.feature.schedule.ScheduleSettingsRoute
import com.x500x.cursimple.feature.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Checkbox
import com.x500x.cursimple.core.data.VendorPermissionKey

private enum class SettingsDestination {
    Root,
    ScheduleData,
    TemporaryOverrides,
    Holidays,
    ScheduleAppearance,
    BackgroundHub,
    ScheduleBackground,
    WidgetBackground,
    ScheduleDisplay,
    TimingProfile,
    WidgetSettings,
    AutoSilence,
    ClassNotice,
    Plugins,
    WebDav,
    AiImport,
    Permissions,
    DevTime,
    DevNotice,
    DevLogs,
    DevData,
}

enum class SettingsDestinationKey {
    WebDav,
    AiImport,
    ScheduleBackground,

    TemporaryOverrides,

    /** Open permission setup from the force-stop explanation. */
    Permissions,
}

enum class SettingsReturnTargetKey {
    ImportExport,
}

private fun SettingsDestinationKey.toDestination(): SettingsDestination = when (this) {
    SettingsDestinationKey.WebDav -> SettingsDestination.WebDav
    SettingsDestinationKey.AiImport -> SettingsDestination.AiImport
    SettingsDestinationKey.ScheduleBackground -> SettingsDestination.BackgroundHub
    SettingsDestinationKey.TemporaryOverrides -> SettingsDestination.TemporaryOverrides
    SettingsDestinationKey.Permissions -> SettingsDestination.Permissions
}

private fun SettingsDestination.parentChain(): List<SettingsDestination> = when (this) {
    SettingsDestination.ScheduleBackground,
    SettingsDestination.WidgetBackground,
    -> listOf(SettingsDestination.BackgroundHub)
    else -> emptyList()
}

/** Drawer destinations form their own navigation root; Back returns to the timetable. */
private fun SettingsDestination.isStandaloneEntry(): Boolean =
    this == SettingsDestination.BackgroundHub

/** Limit the pinned timetable preview to leave room for settings. */
private const val STYLE_PREVIEW_SLOTS = 2
private val STYLE_PREVIEW_MAX_HEIGHT = 200.dp

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> stringResource(R.string.settings_theme_mode_system)
    ThemeMode.Light -> stringResource(R.string.settings_theme_mode_light)
    ThemeMode.Dark -> stringResource(R.string.settings_theme_mode_dark)
}

/** Compact background label for narrow quick-setting tiles. */
@Composable
private fun quickBackgroundLabel(background: ScheduleBackgroundPreferences): String = when {
    background.type == ScheduleBackgroundType.Image && background.imageUri != null ->
        stringResource(R.string.settings_quick_bg_image)
    background.type == ScheduleBackgroundType.Color -> stringResource(R.string.settings_quick_bg_color)
    else -> stringResource(R.string.settings_quick_bg_theme)
}

/** Search includes pages and their individual controls, with related keywords. */
@Composable
private fun settingsSearchEntries(
    navigate: (SettingsDestination) -> Unit,
    onPickThemeMode: () -> Unit,
    onPickThemeAccent: () -> Unit,
    onPickTermStartDate: () -> Unit,
    onPickCurrentWeek: () -> Unit,
    onPickAppLanguage: () -> Unit,
    onReplayFirstRunGuide: () -> Unit,
    onResetAll: () -> Unit,
): List<SettingsSearchEntry> {
    val quick = stringResource(R.string.settings_group_quick)
    val schedule = stringResource(R.string.settings_group_schedule)
    val reminder = stringResource(R.string.settings_group_reminder)
    val data = stringResource(R.string.settings_group_data)
    val general = stringResource(R.string.settings_group_general)
    fun under(group: String, page: String) = "$group › $page"
    // Include common synonyms alongside visible setting labels.
    fun words(text: String): List<String> = text.split(' ').filter { it.isNotBlank() }
    val entries = mutableListOf<SettingsSearchEntry>()
    val themeWords = words(stringResource(R.string.settings_search_kw_theme))
    fun page(
        destination: SettingsDestination,
        icon: ImageVector,
        title: String,
        group: String,
        items: List<String>,
        synonyms: List<String> = emptyList(),
    ) {
        entries += SettingsSearchEntry(icon, title, group, items + synonyms) { navigate(destination) }
        items.forEach { item ->
            entries += SettingsSearchEntry(icon, item, under(group, title)) { navigate(destination) }
        }
    }

    entries += SettingsSearchEntry(
        Icons.Rounded.Brightness7,
        stringResource(R.string.settings_theme_mode_title),
        quick,
        listOf(
            stringResource(R.string.settings_theme_mode_system),
            stringResource(R.string.settings_theme_mode_light),
            stringResource(R.string.settings_theme_mode_dark),
        ) + themeWords,
        onPickThemeMode,
    )
    entries += SettingsSearchEntry(
        Icons.Rounded.Palette,
        stringResource(R.string.settings_theme),
        quick,
        themeWords,
        onPickThemeAccent,
    )

    val scheduleData = stringResource(R.string.settings_dest_schedule_data)
    entries += SettingsSearchEntry(
        Icons.Rounded.CalendarMonth,
        scheduleData,
        schedule,
        listOf(stringResource(R.string.settings_term_start_title), stringResource(R.string.settings_current_week_title)) +
            words(stringResource(R.string.settings_search_kw_data)),
    ) { navigate(SettingsDestination.ScheduleData) }
    entries += SettingsSearchEntry(
        Icons.Rounded.CalendarMonth,
        stringResource(R.string.settings_term_start_title),
        under(schedule, scheduleData),
        onClick = onPickTermStartDate,
    )
    entries += SettingsSearchEntry(
        Icons.Rounded.CalendarMonth,
        stringResource(R.string.settings_current_week_title),
        under(schedule, scheduleData),
        onClick = onPickCurrentWeek,
    )
    page(
        SettingsDestination.TimingProfile,
        Icons.Rounded.Schedule,
        stringResource(R.string.settings_dest_timing_profile),
        schedule,
        listOf(stringResource(R.string.settings_timing_template_title)),
        words(stringResource(R.string.settings_search_kw_timing)),
    )
    page(
        SettingsDestination.ScheduleDisplay,
        Icons.AutoMirrored.Rounded.MenuBook,
        stringResource(R.string.settings_display),
        schedule,
        listOf(
            stringResource(R.string.settings_today_overview_title),
            stringResource(R.string.settings_display_week_start_title),
            stringResource(R.string.settings_display_days_title),
            stringResource(R.string.settings_display_row_fit_title),
            stringResource(R.string.settings_display_total_title),
            stringResource(R.string.settings_display_node_time_title),
            stringResource(R.string.settings_display_location_title),
            stringResource(R.string.settings_display_teacher_title),
            stringResource(R.string.settings_display_course_drag_title),
            stringResource(R.string.settings_display_pinch_zoom_title),
        ),
        words(stringResource(R.string.settings_search_kw_display)),
    )
    entries += SettingsSearchEntry(
        Icons.AutoMirrored.Rounded.MenuBook,
        stringResource(R.string.settings_display_days_title),
        under(schedule, stringResource(R.string.settings_display)),
        listOf(
            stringResource(R.string.settings_display_days_five),
            stringResource(R.string.settings_display_days_six),
            stringResource(R.string.settings_display_days_seven),
            stringResource(R.string.settings_display_days_subtitle),
        ),
    ) { navigate(SettingsDestination.ScheduleDisplay) }
    page(
        SettingsDestination.ScheduleAppearance,
        Icons.Rounded.Style,
        stringResource(R.string.settings_dest_schedule_style),
        schedule,
        listOf(
            stringResource(R.string.settings_adapt_colors_title),
            stringResource(R.string.settings_course_text_size),
            stringResource(R.string.settings_course_text_color),
            stringResource(R.string.settings_exam_text_size),
            stringResource(R.string.settings_exam_text_color),
            stringResource(R.string.settings_text_center_horizontal_title),
            stringResource(R.string.settings_text_center_vertical_title),
            stringResource(R.string.settings_auto_shrink_title),
            stringResource(R.string.settings_truncation_ellipsis_title),
            stringResource(R.string.settings_header_text_size),
            stringResource(R.string.settings_header_text_color),
            stringResource(R.string.settings_today_header_background_color),
            stringResource(R.string.settings_card_corner_radius),
            stringResource(R.string.settings_card_height),
            stringResource(R.string.settings_schedule_opacity),
            stringResource(R.string.settings_inactive_course_opacity),
            stringResource(R.string.settings_grid_border_color),
            stringResource(R.string.settings_grid_border_opacity),
            stringResource(R.string.settings_grid_border_width),
            stringResource(R.string.settings_grid_border_dashed_title),
        ),
        words(stringResource(R.string.settings_search_kw_style)),
    )
    page(
        SettingsDestination.ScheduleBackground,
        Icons.Rounded.Wallpaper,
        stringResource(R.string.settings_schedule_background),
        schedule,
        listOf(
            stringResource(R.string.settings_background_image_title),
            stringResource(R.string.settings_background_image_transparency),
            stringResource(R.string.settings_background_color),
        ),
        words(stringResource(R.string.settings_search_kw_background)),
    )
    page(
        SettingsDestination.TemporaryOverrides,
        Icons.Rounded.EventRepeat,
        stringResource(R.string.settings_dest_temporary_overrides),
        schedule,
        emptyList(),
    )
    page(
        SettingsDestination.Holidays,
        Icons.Rounded.EventBusy,
        stringResource(R.string.settings_dest_holidays),
        schedule,
        listOf(
            stringResource(R.string.settings_holiday_builtin_title),
            stringResource(R.string.settings_holiday_skip_reminders_title),
            stringResource(R.string.settings_holiday_adjust_day_title),
        ),
        words(stringResource(R.string.settings_search_kw_holiday)),
    )
    page(
        SettingsDestination.WidgetSettings,
        Icons.Rounded.Widgets,
        stringResource(R.string.settings_dest_widget_settings),
        quick,
        listOf(
            stringResource(R.string.settings_widget_home_title),
            stringResource(R.string.settings_widget_open_app_title),
        ),
        words(stringResource(R.string.settings_search_kw_widget)),
    )
    page(
        SettingsDestination.WidgetBackground,
        Icons.Rounded.Widgets,
        stringResource(R.string.settings_dest_widget_background),
        quick,
        emptyList(),
    )
    page(
        SettingsDestination.ClassNotice,
        Icons.Rounded.NotificationsActive,
        stringResource(R.string.settings_dest_class_notice),
        reminder,
        listOf(
            stringResource(R.string.settings_class_notice_enable_title),
            stringResource(R.string.settings_class_notice_advance),
            stringResource(R.string.settings_class_notice_heads_up_title),
            stringResource(R.string.settings_class_notice_lock_title),
            stringResource(R.string.settings_class_notice_focus_title),
            stringResource(R.string.settings_class_notice_blur_title),
            stringResource(R.string.settings_class_notice_animation),
            stringResource(R.string.settings_alarm_pre_notice_title),
        ),
        words(stringResource(R.string.settings_search_kw_notice)),
    )
    page(
        SettingsDestination.AutoSilence,
        Icons.Rounded.VolumeOff,
        stringResource(R.string.settings_dest_auto_silence),
        reminder,
        listOf(stringResource(R.string.settings_auto_silence_mode_title)),
    )
    page(
        SettingsDestination.Permissions,
        Icons.Rounded.Security,
        stringResource(R.string.settings_dest_permissions),
        reminder,
        listOf(
            stringResource(R.string.settings_alarm_keep_alive_title),
            stringResource(R.string.settings_permission_battery_title),
            stringResource(R.string.settings_permission_vendor_battery_title),
            stringResource(R.string.settings_permission_autostart_title),
            stringResource(R.string.settings_permission_background_popup_title),
            stringResource(R.string.settings_dnd_permission_title),
            stringResource(R.string.settings_alarm_diagnostics_title),
        ),
        words(stringResource(R.string.settings_search_kw_permissions)),
    )
    page(
        SettingsDestination.Plugins,
        Icons.Rounded.Extension,
        stringResource(R.string.settings_dest_plugins),
        data,
        listOf(stringResource(R.string.market_sources_plugin_title), stringResource(R.string.market_sources_component_title), stringResource(R.string.github_account_title)),
        words("GitHub repo token 仓库 私有 來源 来源 登入 登录 令牌 插件 组件"),
    )
    page(SettingsDestination.WebDav, Icons.Rounded.Storage, "WebDAV", data, emptyList())
    page(SettingsDestination.AiImport, Icons.Rounded.ImageSearch, stringResource(R.string.settings_dest_ai_import), data, emptyList())
    entries += SettingsSearchEntry(Icons.Rounded.Language, stringResource(R.string.settings_language), general, onClick = onPickAppLanguage)
    entries += SettingsSearchEntry(
        Icons.Rounded.Explore,
        stringResource(R.string.settings_replay_guide_title),
        general,
        onClick = onReplayFirstRunGuide,
    )
    entries += SettingsSearchEntry(Icons.Rounded.Restore, stringResource(R.string.settings_reset_all_title), general, onClick = onResetAll)
    return entries
}

@Composable
private fun SettingsDestination.title(): String = when (this) {
    SettingsDestination.Root -> stringResource(R.string.settings_dest_root)
    SettingsDestination.ScheduleData -> stringResource(R.string.settings_dest_schedule_data)
    SettingsDestination.TemporaryOverrides -> stringResource(R.string.settings_dest_temporary_overrides)
    SettingsDestination.Holidays -> stringResource(R.string.settings_dest_holidays)
    SettingsDestination.ScheduleAppearance -> stringResource(R.string.settings_dest_schedule_style)
    SettingsDestination.BackgroundHub -> stringResource(R.string.settings_dest_background_hub)
    SettingsDestination.ScheduleBackground -> stringResource(R.string.settings_schedule_background)
    SettingsDestination.WidgetBackground -> stringResource(R.string.settings_dest_widget_background)
    SettingsDestination.ScheduleDisplay -> stringResource(R.string.settings_display)
    SettingsDestination.TimingProfile -> stringResource(R.string.settings_dest_timing_profile)
    SettingsDestination.WidgetSettings -> stringResource(R.string.settings_dest_widget_settings)
    SettingsDestination.AutoSilence -> stringResource(R.string.settings_dest_auto_silence)
    SettingsDestination.ClassNotice -> stringResource(R.string.settings_dest_class_notice)
    SettingsDestination.Plugins -> stringResource(R.string.settings_dest_plugins)
    SettingsDestination.WebDav -> "WebDAV"
    SettingsDestination.AiImport -> stringResource(R.string.settings_dest_ai_import)
    SettingsDestination.Permissions -> stringResource(R.string.settings_dest_permissions)
    SettingsDestination.DevTime -> stringResource(R.string.settings_dest_dev_time)
    SettingsDestination.DevNotice -> stringResource(R.string.settings_dest_dev_notice)
    SettingsDestination.DevLogs -> stringResource(R.string.settings_dest_dev_logs)
    SettingsDestination.DevData -> stringResource(R.string.settings_dest_dev_data)
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
    appTimeZoneId: String?,
    pluginSources: List<String>,
    componentSources: List<String>,
    marketSourceServices: MarketSourceServices,
    privateFilesProviderEnabled: Boolean,
    webDavUrl: String,
    webDavUsername: String,
    webDavPassword: String,
    aiImportApiUrl: String,
    aiImportApiKey: String,
    aiImportModel: String,
    aiImportTimeoutSeconds: Int,
    advancedToolsEnabled: Boolean,
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
    onScheduleAutoShrinkLongTitlesChange: (Boolean) -> Unit,
    onScheduleTruncationEllipsisChange: (Boolean) -> Unit,
    classNotice: ClassNoticePreferences = ClassNoticePreferences(),
    onClassNoticeEnabledChange: (Boolean) -> Unit = {},
    onClassNoticeAdvanceMinutesChange: (Int) -> Unit = {},
    onClassNoticeHeadsUpChange: (Boolean) -> Unit = {},
    onClassNoticeVibrationChange: (Boolean) -> Unit = {},
    onClassNoticeLockScreenChange: (Boolean) -> Unit = {},
    onClassNoticeFocusChange: (Boolean) -> Unit = {},
    onClassNoticeSkinChange: (ClassNoticeSkin) -> Unit = {},
    onClassNoticeAnimationChange: (ClassNoticeAnimation) -> Unit = {},
    onClassNoticeBlurChange: (Boolean) -> Unit = {},
    onClassNoticeBlurStrengthChange: (Int) -> Unit = {},
    onClassNoticeBannerDurationChange: (Int) -> Unit = {},
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
    onSchedulePinchZoomEnabledChange: (Boolean) -> Unit = {},
    onTodayOverviewEnabledChange: (Boolean) -> Unit = {},
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
    skipRemindersOnHoliday: Boolean = true,
    onSkipRemindersOnHolidayChange: (Boolean) -> Unit = {},
    alarmKeepAliveEnabled: Boolean = false,
    onAlarmKeepAliveEnabledChange: (Boolean) -> Unit = {},
    onOpenWidgetPicker: () -> Unit,
    onPickWidgetThemeAccent: () -> Unit,
    onWidgetBackgroundImageUriChange: (String) -> Unit,
    onClearWidgetBackgroundImage: () -> Unit,
    onWidgetBackgroundImageTransparencyPercentChange: (Int) -> Unit,
    vendorPermissionAcks: Set<String> = emptySet(),
    onVendorPermissionAckChange: (String, Boolean) -> Unit = { _, _ -> },
    onWidgetOpenAppOnDoubleClickChange: (Boolean) -> Unit,
    onAppTimeZoneChange: (String?) -> Unit,
    onPluginSourcesChange: (List<String>) -> Unit,
    onComponentSourcesChange: (List<String>) -> Unit,
    onPrivateFilesProviderEnabledChange: (Boolean) -> Unit,
    onWebDavSettingsChange: (String, String, String) -> Unit,
    onTestWebDavSettings: suspend (WebDavConfig) -> Result<Unit>,
    onAiImportSettingsChange: (String, String, String, Int) -> Unit,
    onSetAdvancedTools: (Boolean) -> Unit,
    onSetDebugForcedDateTime: (LocalDateTime?) -> Unit,
    onExportScheduleMetadata: () -> Unit,
    onResetScheduleAppearanceAndDisplay: () -> Unit,
    onResetAllSettings: () -> Unit,
    onReplayFirstRunGuide: () -> Unit,
    openDestination: SettingsDestinationKey? = null,
    onOpenDestinationConsumed: () -> Unit = {},
    returnTarget: SettingsReturnTargetKey? = null,
    onReturnTargetReady: () -> Unit = {},
    onOpenCourseSwap: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    courseTitleOf: (String) -> String? = { null },
    /** All timetable courses used by the selected-day cancellation picker. */
    scheduleCourses: List<CourseItem> = emptyList(),
    scheduleTimingProfile: TermTimingProfile? = null,
    onApplyCancelPlan: (CancelCoursePlan) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var backStack by rememberSaveable { mutableStateOf(listOf(SettingsDestination.Root.name)) }
    var settingsReturnReady by rememberSaveable { mutableStateOf(false) }
    val destination = SettingsDestination.valueOf(backStack.last())
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    var homeLayout by remember { mutableStateOf(SettingsHomeLayoutStore.load(context)) }
    val homeGrid = homeLayout == SettingsHomeLayout.Grid
    // Restore the root-page scroll offset after visiting subpages.
    var rootScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    fun navigate(next: SettingsDestination) {
        if (destination == SettingsDestination.Root) rootScrollOffset = scrollState.value
        backStack = backStack + next.name
    }
    LaunchedEffect(destination) {
        if (destination == SettingsDestination.Root) {
            val target = rootScrollOffset
            withTimeoutOrNull(ROOT_SCROLL_RESTORE_TIMEOUT_MS) {
                snapshotFlow { scrollState.maxValue }.first { it >= target }
            }
            scrollState.scrollTo(target)
        } else {
            scrollState.scrollTo(0)
        }
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
            if (!requested.isStandaloneEntry()) {
                add(SettingsDestination.Root.name)
                requested.parentChain().forEach { add(it.name) }
            }
            add(requested.name)
        }
        settingsReturnReady = false
        onOpenDestinationConsumed()
    }
    BackHandler(enabled = backStack.size > 1) {
        handleBack()
    }
    var showHolidayEditor by rememberSaveable { mutableStateOf(false) }
    var showResetScheduleAppearanceConfirm by rememberSaveable { mutableStateOf(false) }
    var showResetAllSettingsConfirm by rememberSaveable { mutableStateOf(false) }
    // Permanent permission denial requires the app settings page.
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
                launchSettingsIntent(context, AlarmSettingsIntents.appDetails(context))
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
    // Crop widget backgrounds before applying them to wide layouts.
    var pendingWidgetBackgroundSource by remember { mutableStateOf<android.net.Uri?>(null) }
    pendingWidgetBackgroundSource?.let { source ->
        ScheduleBackgroundCropDialog(
            source = source,
            frameAspect = WIDGET_BACKGROUND_FRAME_ASPECT,
            onDismiss = { pendingWidgetBackgroundSource = null },
            onCropped = { cropped ->
                pendingWidgetBackgroundSource = null
                onWidgetBackgroundImageUriChange(cropped.toString())
            },
        )
    }
    val widgetBackgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (persisted) {
                pendingWidgetBackgroundSource = uri
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
    val searchEntries = if (destination == SettingsDestination.Root && settingsQuery.isNotBlank()) {
        settingsSearchEntries(
            navigate = ::navigate,
            onPickThemeMode = onPickThemeMode,
            onPickThemeAccent = onPickThemeAccent,
            onPickTermStartDate = onPickTermStartDate,
            onPickCurrentWeek = onPickCurrentWeek,
            onPickAppLanguage = onPickAppLanguage,
            onReplayFirstRunGuide = onReplayFirstRunGuide,
            onResetAll = { showResetAllSettingsConfirm = true },
        )
    } else {
        emptyList()
    }
    val quickDrag = remember {
        SettingsQuickDragState(SettingsQuickStore.load(context)) { SettingsQuickStore.save(context, it) }
    }
    val quickItems = if (destination == SettingsDestination.Root) {
        val quickGroup = stringResource(R.string.settings_group_quick)
        val scheduleGroup = stringResource(R.string.settings_group_schedule)
        val reminderGroup = stringResource(R.string.settings_group_reminder)
        val dataGroup = stringResource(R.string.settings_group_data)
        val generalGroup = stringResource(R.string.settings_group_general)
        val slotCount = scheduleTimingProfile?.slotTimes?.size ?: 0
        listOf(
            SettingsQuickItem("theme_mode", quickGroup, SettingsQuickTileSpec(
                icon = if (themeMode == ThemeMode.Dark) Icons.Rounded.Brightness4 else Icons.Rounded.Brightness7,
                title = stringResource(R.string.settings_theme_mode_title),
                value = themeModeLabel(themeMode),
                onClick = onPickThemeMode,
            )),
            SettingsQuickItem("theme_accent", quickGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Palette,
                title = stringResource(R.string.settings_theme),
                value = themeAccentLabel,
                onClick = onPickThemeAccent,
            )),
            SettingsQuickItem("background", quickGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Wallpaper,
                title = stringResource(R.string.settings_schedule_background),
                value = quickBackgroundLabel(scheduleBackground),
                onClick = { navigate(SettingsDestination.ScheduleBackground) },
            )),
            SettingsQuickItem("current_week", quickGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.CalendarMonth,
                title = stringResource(R.string.settings_current_week_title),
                value = if (termStartDate != null) {
                    context.termWeekText(termWeekLabel(currentWeekIndex))
                } else {
                    stringResource(R.string.settings_quick_unset)
                },
                onClick = onPickCurrentWeek,
            )),
            SettingsQuickItem("widget", quickGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Widgets,
                title = stringResource(R.string.settings_quick_widget),
                value = stringResource(R.string.settings_quick_widget_value),
                onClick = { navigate(SettingsDestination.WidgetSettings) },
            )),
            SettingsQuickItem("schedule_data", scheduleGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.CalendarMonth,
                title = stringResource(R.string.settings_dest_schedule_data),
                value = stringResource(R.string.settings_row_schedule_data_subtitle),
                onClick = { navigate(SettingsDestination.ScheduleData) },
            )),
            SettingsQuickItem("timing", scheduleGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Schedule,
                title = stringResource(R.string.settings_dest_timing_profile),
                value = if (slotCount > 0) {
                    androidx.compose.ui.res.pluralStringResource(R.plurals.settings_timing_entry_subtitle_set, slotCount, slotCount)
                } else {
                    stringResource(R.string.settings_quick_unset)
                },
                onClick = { navigate(SettingsDestination.TimingProfile) },
            )),
            SettingsQuickItem("display", scheduleGroup, SettingsQuickTileSpec(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                title = stringResource(R.string.settings_display),
                value = stringResource(R.string.settings_row_schedule_display_subtitle),
                onClick = { navigate(SettingsDestination.ScheduleDisplay) },
            )),
            SettingsQuickItem("style", scheduleGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Style,
                title = stringResource(R.string.settings_dest_schedule_style),
                value = stringResource(R.string.settings_row_schedule_style_subtitle),
                onClick = { navigate(SettingsDestination.ScheduleAppearance) },
            )),
            SettingsQuickItem("overrides", scheduleGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.EventRepeat,
                title = stringResource(R.string.settings_dest_temporary_overrides),
                value = temporaryOverridesSubtitle(temporaryScheduleOverrides),
                onClick = { navigate(SettingsDestination.TemporaryOverrides) },
            )),
            SettingsQuickItem("holidays", scheduleGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.EventBusy,
                title = stringResource(R.string.settings_dest_holidays),
                value = holidayCalendarSubtitle(holidayCalendar),
                onClick = { navigate(SettingsDestination.Holidays) },
            )),
            SettingsQuickItem("class_notice", reminderGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.settings_dest_class_notice),
                value = if (classNotice.enabled) {
                    stringResource(R.string.settings_quick_notice_on, classNotice.advanceMinutes)
                } else {
                    stringResource(R.string.settings_class_notice_off)
                },
                onClick = { navigate(SettingsDestination.ClassNotice) },
                active = classNotice.enabled,
            )),
            SettingsQuickItem("auto_silence", reminderGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.VolumeOff,
                title = stringResource(R.string.settings_dest_auto_silence),
                value = stringResource(R.string.settings_row_auto_silence_subtitle),
                onClick = { navigate(SettingsDestination.AutoSilence) },
            )),
            SettingsQuickItem("permissions", reminderGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.settings_dest_permissions),
                value = stringResource(R.string.settings_row_permissions_subtitle),
                onClick = { navigate(SettingsDestination.Permissions) },
            )),
            SettingsQuickItem("plugins", dataGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Extension,
                title = stringResource(R.string.settings_dest_plugins),
                value = stringResource(R.string.settings_row_plugins_subtitle),
                onClick = { navigate(SettingsDestination.Plugins) },
            )),
            SettingsQuickItem("webdav", dataGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Storage,
                title = "WebDAV",
                value = webDavSettingsSubtitle(webDavUrl, webDavUsername),
                onClick = { navigate(SettingsDestination.WebDav) },
            )),
            SettingsQuickItem("ai_import", dataGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.ImageSearch,
                title = stringResource(R.string.settings_dest_ai_import),
                value = aiImportSettingsSubtitle(aiImportApiUrl, aiImportModel),
                onClick = { navigate(SettingsDestination.AiImport) },
            )),
            SettingsQuickItem("language", generalGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.settings_language),
                value = appLanguageLabel(appLanguage),
                onClick = onPickAppLanguage,
            )),
            SettingsQuickItem("guide", generalGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Explore,
                title = stringResource(R.string.settings_replay_guide_title),
                value = stringResource(R.string.settings_replay_guide_subtitle),
                onClick = onReplayFirstRunGuide,
            )),
        )
    } else {
        emptyList()
    }

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalSettingsQuickDrag provides quickDrag.takeIf { destination == SettingsDestination.Root },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // Pin navigation and preview while the settings content scrolls.
                if (destination != SettingsDestination.Root) {
                    Row(
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
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
                if (destination == SettingsDestination.ScheduleAppearance) {
                    ScheduleAppearancePreview(
                        scheduleTextStyle = scheduleTextStyle,
                        scheduleCardStyle = scheduleCardStyle,
                        scheduleBackground = scheduleBackground,
                        scheduleDisplay = scheduleDisplay,
                        customColorsAdaptToTheme = scheduleCustomColorsAdaptToTheme,
                        maxSlots = STYLE_PREVIEW_SLOTS,
                        maxHeight = STYLE_PREVIEW_MAX_HEIGHT,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { quickDrag.viewport = it.boundsInRoot() }
                        .verticalScroll(scrollState)
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            top = if (destination == SettingsDestination.Root) 14.dp else 4.dp,
                            bottom = 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (destination) {
                        SettingsDestination.Root -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                SettingsSearchField(
                                    query = settingsQuery,
                                    onQueryChange = { settingsQuery = it },
                                    modifier = Modifier.weight(1f),
                                )
                                SettingsHomeLayoutToggle(layout = homeLayout) {
                                    homeLayout = if (homeLayout == SettingsHomeLayout.List) {
                                        SettingsHomeLayout.Grid
                                    } else {
                                        SettingsHomeLayout.List
                                    }
                                    SettingsHomeLayoutStore.save(context, homeLayout)
                                }
                            }
                            if (settingsQuery.isNotBlank()) {
                                SettingsSearchResults(
                                    entries = searchEntries,
                                    query = settingsQuery,
                                    onPick = { entry ->
                                        settingsQuery = ""
                                        entry.onClick()
                                    },
                                )
                            } else {
                                SettingsQuickSection(state = quickDrag, items = quickItems)

                                SettingsCardGroup(stringResource(R.string.settings_group_schedule), tiles = homeGrid) {
                                    SettingsActionRow(
                                        icon = Icons.Rounded.CalendarMonth,
                                        title = stringResource(R.string.settings_dest_schedule_data),
                                        subtitle = stringResource(R.string.settings_row_schedule_data_subtitle),
                                        onClick = { navigate(SettingsDestination.ScheduleData) },
                                        quickId = "schedule_data",
                                    )
                                    TimingProfileEntryRow(quickId = "timing") { navigate(SettingsDestination.TimingProfile) }
                                    SettingsActionRow(
                                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                                        title = stringResource(R.string.settings_display),
                                        subtitle = stringResource(R.string.settings_row_schedule_display_subtitle),
                                        onClick = { navigate(SettingsDestination.ScheduleDisplay) },
                                        quickId = "display",
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Style,
                                        title = stringResource(R.string.settings_dest_schedule_style),
                                        subtitle = stringResource(R.string.settings_row_schedule_style_subtitle),
                                        onClick = { navigate(SettingsDestination.ScheduleAppearance) },
                                        quickId = "style",
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.EventRepeat,
                                        title = stringResource(R.string.settings_dest_temporary_overrides),
                                        subtitle = temporaryOverridesSubtitle(temporaryScheduleOverrides),
                                        onClick = { navigate(SettingsDestination.TemporaryOverrides) },
                                        quickId = "overrides",
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.EventBusy,
                                        title = stringResource(R.string.settings_dest_holidays),
                                        subtitle = holidayCalendarSubtitle(holidayCalendar),
                                        onClick = { navigate(SettingsDestination.Holidays) },
                                        quickId = "holidays",
                                    )
                                }

                                SettingsCardGroup(stringResource(R.string.settings_group_reminder), tiles = homeGrid) {
                                    // Expose the reminder switch without requiring another page.
                                    SettingsActionRow(
                                        icon = Icons.Rounded.NotificationsActive,
                                        title = stringResource(R.string.settings_dest_class_notice),
                                        subtitle = classNoticeSubtitle(classNotice),
                                        onClick = { navigate(SettingsDestination.ClassNotice) },
                                        trailing = {
                                            Switch(checked = classNotice.enabled, onCheckedChange = onClassNoticeEnabledChange)
                                        },
                                        tileActive = classNotice.enabled,
                                        quickId = "class_notice",
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.VolumeOff,
                                        title = stringResource(R.string.settings_dest_auto_silence),
                                        subtitle = stringResource(R.string.settings_row_auto_silence_subtitle),
                                        onClick = { navigate(SettingsDestination.AutoSilence) },
                                        quickId = "auto_silence",
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Security,
                                        title = stringResource(R.string.settings_dest_permissions),
                                        subtitle = stringResource(R.string.settings_row_permissions_subtitle),
                                        onClick = { navigate(SettingsDestination.Permissions) },
                                        quickId = "permissions",
                                    )
                                }

                                SettingsCardGroup(stringResource(R.string.settings_group_data), tiles = homeGrid) {
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Extension,
                                        title = stringResource(R.string.settings_dest_plugins),
                                        subtitle = stringResource(R.string.settings_row_plugins_subtitle),
                                        onClick = { navigate(SettingsDestination.Plugins) },
                                        quickId = "plugins",
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Storage,
                                        title = "WebDAV",
                                        subtitle = webDavSettingsSubtitle(webDavUrl, webDavUsername),
                                        onClick = { navigate(SettingsDestination.WebDav) },
                                        quickId = "webdav",
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.ImageSearch,
                                        title = stringResource(R.string.settings_dest_ai_import),
                                        subtitle = aiImportSettingsSubtitle(aiImportApiUrl, aiImportModel),
                                        onClick = { navigate(SettingsDestination.AiImport) },
                                        quickId = "ai_import",
                                    )
                                }

                                SettingsCardGroup(stringResource(R.string.settings_group_general), tiles = homeGrid) {
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Language,
                                        title = stringResource(R.string.settings_language),
                                        subtitle = appLanguageLabel(appLanguage),
                                        onClick = onPickAppLanguage,
                                        quickId = "language",
                                    )
                                    TimeZoneRow(zoneId = appTimeZoneId, onZoneChange = onAppTimeZoneChange)
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Explore,
                                        title = stringResource(R.string.settings_replay_guide_title),
                                        subtitle = stringResource(R.string.settings_replay_guide_subtitle),
                                        onClick = onReplayFirstRunGuide,
                                        quickId = "guide",
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Restore,
                                        title = stringResource(R.string.settings_reset_all_title),
                                        subtitle = stringResource(R.string.settings_reset_all_subtitle),
                                        onClick = { showResetAllSettingsConfirm = true },
                                    )
                                }
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
                                        AppOutlinedButton(
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
                            TemporaryOverrideSettingsSection(
                                overrides = temporaryScheduleOverrides,
                                onUpsert = onUpsertTemporaryScheduleOverride,
                                onRemove = onRemoveTemporaryScheduleOverride,
                                onClear = onClearTemporaryScheduleOverrides,
                                onOpenCourseSwap = onOpenCourseSwap,
                                courseTitleOf = courseTitleOf,
                                courses = scheduleCourses,
                                timingProfile = scheduleTimingProfile,
                                holidayCalendar = holidayCalendar,
                                termStartDate = termStartDate,
                                onApplyCancelPlan = onApplyCancelPlan,
                            )
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
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.FormatSize,
                                    title = stringResource(R.string.settings_auto_shrink_title),
                                    subtitle = stringResource(R.string.settings_auto_shrink_subtitle),
                                    checked = scheduleTextStyle.autoShrinkLongTitles,
                                    onCheckedChange = onScheduleAutoShrinkLongTitlesChange,
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.MoreHoriz,
                                    title = stringResource(R.string.settings_truncation_ellipsis_title),
                                    subtitle = stringResource(R.string.settings_truncation_ellipsis_subtitle),
                                    checked = scheduleTextStyle.truncationEllipsis,
                                    onCheckedChange = onScheduleTruncationEllipsisChange,
                                )
                            }

                            SettingsGroup(stringResource(R.string.settings_header_style)) {
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

                            SettingsGroup(stringResource(R.string.settings_dest_background_hub)) {
                                SettingsActionRow(
                                    icon = Icons.Rounded.Wallpaper,
                                    title = stringResource(R.string.settings_schedule_background),
                                    subtitle = backgroundSubtitle(scheduleBackground),
                                    onClick = { navigate(SettingsDestination.ScheduleBackground) },
                                )
                            }

                            SettingsActionRow(
                                icon = Icons.Rounded.Restore,
                                title = stringResource(R.string.settings_reset_schedule_title),
                                subtitle = stringResource(R.string.settings_reset_schedule_subtitle),
                                onClick = { showResetScheduleAppearanceConfirm = true },
                            )
                        }

                        SettingsDestination.DevTime -> {
                            AdvancedTimeSection(
                                debugForcedDateTime = debugForcedDateTime,
                                onSetDebugForcedDateTime = onSetDebugForcedDateTime,
                            )
                        }

                        SettingsDestination.DevNotice -> {
                            ClassNoticeTestRows(classNotice)
                        }

                        SettingsDestination.DevLogs -> {
                            AdvancedLogsSection()
                        }

                        SettingsDestination.DevData -> {
                            AdvancedDataSection(
                                privateFilesProviderEnabled = privateFilesProviderEnabled,
                                onPrivateFilesProviderEnabledChange = onPrivateFilesProviderEnabledChange,
                                onExportScheduleMetadata = onExportScheduleMetadata,
                            )
                        }

                        SettingsDestination.BackgroundHub -> {
                            SettingsActionRow(
                                icon = Icons.Rounded.Wallpaper,
                                title = stringResource(R.string.settings_schedule_background),
                                subtitle = backgroundSubtitle(scheduleBackground),
                                onClick = { navigate(SettingsDestination.ScheduleBackground) },
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.Widgets,
                                title = stringResource(R.string.settings_dest_widget_background),
                                subtitle = if (widgetThemePreferences.backgroundImageUri != null) {
                                    stringResource(R.string.settings_background_image_selected)
                                } else {
                                    stringResource(R.string.settings_widget_background_theme)
                                },
                                onClick = { navigate(SettingsDestination.WidgetBackground) },
                            )
                        }

                        SettingsDestination.WidgetBackground -> {
                            WidgetBackgroundPreview(widgetThemePreferences = widgetThemePreferences)
                            SettingsActionRow(
                                icon = Icons.Rounded.Wallpaper,
                                title = stringResource(R.string.settings_background_image_title),
                                subtitle = if (widgetThemePreferences.backgroundImageUri != null) {
                                    stringResource(R.string.settings_background_image_selected)
                                } else {
                                    stringResource(R.string.settings_background_image_none)
                                },
                                onClick = { widgetBackgroundLauncher.launch(arrayOf("image/*")) },
                            )
                            if (widgetThemePreferences.backgroundMode == WidgetBackgroundMode.Image ||
                                widgetThemePreferences.backgroundImageUri != null
                            ) {
                                SliderPercentRow(
                                    title = stringResource(R.string.settings_background_image_transparency),
                                    value = widgetThemePreferences.backgroundImageTransparencyPercent,
                                    onValueChange = onWidgetBackgroundImageTransparencyPercentChange,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.Delete,
                                    title = stringResource(R.string.settings_widget_background_clear_title),
                                    subtitle = stringResource(R.string.settings_widget_background_clear_subtitle),
                                    onClick = onClearWidgetBackgroundImage,
                                )
                            }
                            SettingsActionRow(
                                icon = Icons.Rounded.Palette,
                                title = stringResource(R.string.settings_theme),
                                subtitle = widgetThemeLabel(widgetThemePreferences),
                                onClick = onPickWidgetThemeAccent,
                            )
                        }

                        SettingsDestination.ScheduleBackground -> {
                            ScheduleBackgroundPreview(
                                scheduleBackground = scheduleBackground,
                                scheduleCardStyle = scheduleCardStyle,
                                scheduleTextStyle = scheduleTextStyle,
                                customColorsAdaptToTheme = scheduleCustomColorsAdaptToTheme,
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.Wallpaper,
                                title = stringResource(R.string.settings_background_image_title),
                                subtitle = if (scheduleBackground.imageUri != null) {
                                    stringResource(R.string.settings_background_image_selected)
                                } else {
                                    stringResource(R.string.settings_background_image_none)
                                },
                                onClick = { scheduleBackgroundLauncher.launch(arrayOf("image/*")) },
                            )
                            if (scheduleBackground.type == ScheduleBackgroundType.Image || scheduleBackground.imageUri != null) {
                                SliderPercentRow(
                                    title = stringResource(R.string.settings_background_image_transparency),
                                    value = scheduleBackground.imageTransparencyPercent,
                                    onValueChange = onScheduleBackgroundImageTransparencyPercentChange,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.Delete,
                                    title = stringResource(R.string.settings_background_image_clear_title),
                                    subtitle = stringResource(R.string.settings_background_image_clear_subtitle),
                                    onClick = onClearScheduleBackgroundImage,
                                )
                            }
                            ColorAlphaRow(stringResource(R.string.settings_background_color), scheduleBackground.colorArgb, onScheduleBackgroundColorArgbChange)
                            if (scheduleCustomColorsAdaptToTheme) {
                                ColorPreviewRow(
                                    stringResource(R.string.settings_current_theme_preview),
                                    scheduleBackground.colorArgb.adaptBackgroundForPreview(darkTheme),
                                )
                            }
                            SettingsActionRow(
                                icon = Icons.Rounded.Restore,
                                title = stringResource(R.string.settings_background_reset_title),
                                subtitle = stringResource(R.string.settings_background_reset_subtitle),
                                onClick = onScheduleBackgroundUseHeaderColor,
                            )
                        }

                        SettingsDestination.ScheduleDisplay -> {
                            SettingsSwitchRow(
                                icon = Icons.Rounded.Schedule,
                                title = stringResource(R.string.settings_today_overview_title),
                                subtitle = stringResource(R.string.settings_today_overview_desc),
                                checked = scheduleDisplay.todayOverviewEnabled,
                                onCheckedChange = onTodayOverviewEnabledChange,
                            )
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
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.ZoomIn,
                                    title = stringResource(R.string.settings_display_pinch_zoom_title),
                                    subtitle = stringResource(R.string.settings_display_pinch_zoom_subtitle),
                                    checked = scheduleDisplay.pinchZoomEnabled,
                                    onCheckedChange = onSchedulePinchZoomEnabledChange,
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
                                // Keep background selection, cropping and opacity controls on one page.
                                SettingsActionRow(
                                    icon = Icons.Rounded.Wallpaper,
                                    title = stringResource(R.string.settings_widget_background_title),
                                    subtitle = if (widgetThemePreferences.backgroundImageUri != null) {
                                        stringResource(R.string.settings_background_image_selected)
                                    } else {
                                        stringResource(R.string.settings_widget_background_theme)
                                    },
                                    onClick = { navigate(SettingsDestination.WidgetBackground) },
                                )
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

                        SettingsDestination.ClassNotice -> {
                            ClassNoticeSettingsSection(
                                preferences = classNotice,
                                onEnabledChange = onClassNoticeEnabledChange,
                                onAdvanceMinutesChange = onClassNoticeAdvanceMinutesChange,
                                onHeadsUpChange = onClassNoticeHeadsUpChange,
                                onVibrationChange = onClassNoticeVibrationChange,
                                onLockScreenChange = onClassNoticeLockScreenChange,
                                onFocusChange = onClassNoticeFocusChange,
                                onSkinChange = onClassNoticeSkinChange,
                                onAnimationChange = onClassNoticeAnimationChange,
                                onBlurChange = onClassNoticeBlurChange,
                                onBlurStrengthChange = onClassNoticeBlurStrengthChange,
                                onBannerDurationChange = onClassNoticeBannerDurationChange,
                            )
                        }

                        SettingsDestination.Plugins -> {
                            MarketSourceSettings(
                                pluginSources = pluginSources,
                                componentSources = componentSources,
                                onPluginSourcesChange = onPluginSourcesChange,
                                onComponentSourcesChange = onComponentSourcesChange,
                                services = marketSourceServices,
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
                                alarmKeepAliveEnabled = alarmKeepAliveEnabled,
                                onAlarmKeepAliveEnabledChange = onAlarmKeepAliveEnabledChange,
                                vendorPermissionAcks = vendorPermissionAcks,
                                onVendorPermissionAckChange = onVendorPermissionAckChange,
                            )
                        }
                    }

                    if (showResetScheduleAppearanceConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetScheduleAppearanceConfirm = false },
                            title = { Text(stringResource(R.string.settings_reset_schedule_dialog_title)) },
                            text = { Text(stringResource(R.string.settings_reset_schedule_dialog_message)) },
                            confirmButton = {
                                AppOutlinedButton(onClick = {
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
                                AppOutlinedButton(onClick = { showResetScheduleAppearanceConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
                            },
                        )
                    }

                    if (showResetAllSettingsConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetAllSettingsConfirm = false },
                            title = { Text(stringResource(R.string.settings_reset_all_title)) },
                            text = { Text(stringResource(R.string.settings_reset_all_dialog_message)) },
                            confirmButton = {
                                AppOutlinedButton(onClick = {
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
                                AppOutlinedButton(onClick = { showResetAllSettingsConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
                            },
                        )
                    }

                    if (advancedToolsEnabled && destination == SettingsDestination.Root && settingsQuery.isBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AdvancedToolsSection(
                            debugForcedDateTime = debugForcedDateTime,
                            onSetAdvancedTools = onSetAdvancedTools,
                            onNavigate = ::navigate,
                        )
                    }
                }
            }
        }
        if (destination == SettingsDestination.Root) {
            SettingsQuickAutoScroll(state = quickDrag, scrollState = scrollState)
            SettingsQuickDragGhost(state = quickDrag, items = quickItems)
        }
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

/** Report slider values during dragging for a live percentage preview. */
@Composable
private fun SliderPercentRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$value%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value.toFloat(),
                // Avoid duplicate writes for unchanged integer steps.
                onValueChange = { next ->
                    val rounded = next.toInt().coerceIn(0, 100)
                    if (rounded != value) onValueChange(rounded)
                },
                valueRange = 0f..100f,
                steps = 19,
            )
        }
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
            AppOutlinedButton(
                onClick = { onValueChange((value - step).coerceIn(min, max)) },
                enabled = value > min,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("-") }
            Spacer(Modifier.width(8.dp))
            AppOutlinedButton(
                onClick = { onValueChange((value + step).coerceIn(min, max)) },
                enabled = value < max,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("+") }
        }
    }
}

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

internal val LocalSettingsRowInCard = staticCompositionLocalOf { false }

/** Group related settings in one card with dividers. */
@Composable
internal fun SettingsCardGroup(
    title: String,
    tiles: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (tiles) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsSectionHeader(title)
            SettingsTileGrid(content)
        }
        return
    }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val dividerStart = with(density) { 46.dp.toPx() }
    val dividerEnd = with(density) { 14.dp.toPx() }
    val dividerWidth = with(density) { 1.dp.toPx() }
    val dividerTops = remember { mutableListOf<Int>() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingsSectionHeader(title)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompositionLocalProvider(LocalSettingsRowInCard provides true) {
                Layout(
                    content = content,
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        dividerTops.forEach { y ->
                            drawLine(
                                color = dividerColor,
                                start = Offset(dividerStart, y.toFloat()),
                                end = Offset(size.width - dividerEnd, y.toFloat()),
                                strokeWidth = dividerWidth,
                            )
                        }
                    },
                ) { measurables, constraints ->
                    val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
                    layout(constraints.maxWidth, placeables.sumOf { it.height }) {
                        dividerTops.clear()
                        var y = 0
                        placeables.forEach { placeable ->
                            if (y > 0 && placeable.height > 0) dividerTops += y
                            placeable.placeRelative(0, y)
                            y += placeable.height
                        }
                    }
                }
            }
        }
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
                AppOutlinedButton(onClick = { showModePicker = false }) { Text(stringResource(R.string.settings_close)) }
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
    alarmKeepAliveEnabled: Boolean = false,
    onAlarmKeepAliveEnabledChange: (Boolean) -> Unit = {},
    vendorPermissionAcks: Set<String> = emptySet(),
    onVendorPermissionAckChange: (String, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    // Refresh permission state after returning from system settings.
    val state = rememberPermissionState(context)

    val missingAlarmPermissions = buildList {
        if (!state.notification) add(stringResource(R.string.settings_permission_notification))
        if (!state.exactAlarm) add(stringResource(R.string.settings_permission_exact_alarm))
        if (!state.fullScreenIntent) add(stringResource(R.string.settings_permission_full_screen))
        if (!state.batteryOptimizationIgnored) add(stringResource(R.string.settings_permission_background))
        // Disabled guard scheduling can leave alarms vulnerable to vendor cleanup.
        if (!alarmKeepAliveEnabled) add(stringResource(R.string.settings_alarm_keep_alive_title))
        if (state.miuiAutoStart == false) add(stringResource(R.string.settings_permission_autostart_title))
    }

    PermissionSummaryCard(missing = missingAlarmPermissions)

    // Silent guard status affects background alarm delivery alongside permissions.
    SettingsSwitchRow(
        icon = Icons.Rounded.Restore,
        title = stringResource(R.string.settings_alarm_keep_alive_title),
        subtitle = stringResource(
            if (alarmKeepAliveEnabled) {
                R.string.settings_alarm_keep_alive_on
            } else {
                R.string.settings_alarm_keep_alive_off
            },
        ),
        checked = alarmKeepAliveEnabled,
        onCheckedChange = onAlarmKeepAliveEnabledChange,
    )

    // Diagnostics include channels, power policy and registrations, beyond permission checks.
    var showAlarmDiagnostics by remember { mutableStateOf(false) }
    SettingsActionRow(
        icon = Icons.Rounded.BugReport,
        title = stringResource(R.string.settings_alarm_diagnostics_title),
        subtitle = stringResource(R.string.settings_alarm_diagnostics_subtitle),
        onClick = { showAlarmDiagnostics = true },
    )
    if (showAlarmDiagnostics) {
        AlarmDiagnosticsDialog(onDismiss = { showAlarmDiagnostics = false })
    }

    SettingsSectionHeader(stringResource(R.string.settings_section_grant))
    PermissionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_permission_notification),
        granted = state.notification,
        offText = stringResource(R.string.settings_permission_notification_off),
        onText = stringResource(R.string.settings_permission_runtime_hint),
        onClick = {
            // Granted permissions still link to system settings for revocation.
            if (!state.notification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchSettingsIntents(context, AlarmSettingsIntents.notifications(context))
            }
        },
    )
    PermissionRow(
        icon = Icons.Rounded.Schedule,
        title = stringResource(R.string.settings_permission_exact_alarm),
        granted = state.exactAlarm,
        offText = stringResource(R.string.settings_permission_exact_alarm_off),
        onText = stringResource(R.string.settings_permission_manage_hint),
        onClick = { launchSettingsIntents(context, AlarmSettingsIntents.exactAlarm(context)) },
    )
    PermissionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_permission_full_screen),
        granted = state.fullScreenIntent,
        offText = stringResource(R.string.settings_permission_full_screen_off),
        onText = stringResource(R.string.settings_permission_manage_hint),
        onClick = { launchSettingsIntents(context, AlarmSettingsIntents.fullScreenIntent(context)) },
    )
    PermissionRow(
        icon = Icons.Rounded.Restore,
        title = stringResource(R.string.settings_permission_battery_title),
        granted = state.batteryOptimizationIgnored,
        offText = stringResource(R.string.settings_permission_battery_off),
        // Already-exempt apps open the exemption list rather than an ignored request Intent.
        onText = stringResource(R.string.settings_permission_battery_on),
        onClick = {
            launchSettingsIntents(context, AlarmSettingsIntents.batteryOptimization(context))
        },
    )
    if (AlarmSettingsIntents.hasVendorAutoStartPage(context)) {
        // Use queried vendor status where available; otherwise rely on explicit user confirmation.
        val miuiAutoStart = state.miuiAutoStart
        if (miuiAutoStart != null) {
            PermissionRow(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.settings_permission_autostart_title),
                granted = miuiAutoStart,
                offText = stringResource(R.string.settings_permission_autostart_off_xiaomi),
                onText = stringResource(R.string.settings_permission_manage_hint),
                onClick = { launchSettingsIntents(context, AlarmSettingsIntents.vendorAutoStart(context)) },
            )
        } else {
            VendorPermissionRow(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.settings_permission_autostart_title),
                subtitle = stringResource(R.string.settings_permission_autostart_off),
                acked = VendorPermissionKey.AUTO_START in vendorPermissionAcks,
                onOpen = { launchSettingsIntents(context, AlarmSettingsIntents.vendorAutoStart(context)) },
                onAckChange = { onVendorPermissionAckChange(VendorPermissionKey.AUTO_START, it) },
            )
        }
        val miuiPopup = state.miuiBackgroundPopup
        if (miuiPopup != null) {
            PermissionRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.settings_permission_background_popup_title),
                granted = miuiPopup,
                offText = stringResource(R.string.settings_permission_background_popup_off),
                onText = stringResource(R.string.settings_permission_manage_hint),
                onClick = { launchSettingsIntents(context, AlarmSettingsIntents.backgroundPopup(context)) },
            )
        } else {
            VendorPermissionRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.settings_permission_background_popup_title),
                subtitle = stringResource(R.string.settings_permission_background_popup_off),
                acked = VendorPermissionKey.BACKGROUND_POPUP in vendorPermissionAcks,
                onOpen = { launchSettingsIntents(context, AlarmSettingsIntents.backgroundPopup(context)) },
                onAckChange = { onVendorPermissionAckChange(VendorPermissionKey.BACKGROUND_POPUP, it) },
            )
        }
        if (AlarmSettingsIntents.hasVendorBatterySaver()) {
            VendorPermissionRow(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.settings_permission_vendor_battery_title),
                subtitle = stringResource(R.string.settings_permission_vendor_battery_off),
                acked = VendorPermissionKey.BATTERY_SAVER in vendorPermissionAcks,
                onOpen = { launchSettingsIntents(context, AlarmSettingsIntents.vendorBatterySaver(context)) },
                onAckChange = { onVendorPermissionAckChange(VendorPermissionKey.BATTERY_SAVER, it) },
            )
        }
        // Explain Recents locking; the system exposes no API for it.
        VendorKeepAliveGuide()
    }
    PermissionRow(
        icon = Icons.Rounded.Code,
        title = stringResource(R.string.settings_permission_camera),
        granted = state.camera,
        offText = stringResource(R.string.settings_permission_camera_off),
        onText = stringResource(R.string.settings_permission_runtime_hint),
        onClick = {
            if (state.camera) {
                launchSettingsIntent(context, AlarmSettingsIntents.appDetails(context))
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

/** Load DataStore diagnostics asynchronously and show a loading state. */
@Composable
private fun AlarmDiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val report by produceState<AlarmDiagnosticsReport?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { AlarmDiagnostics.collect(context) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_alarm_diagnostics_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_alarm_diagnostics_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val lines = report?.lines
                if (lines == null) {
                    Text(
                        stringResource(R.string.settings_alarm_diagnostics_loading),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    lines.forEach { (label, value) ->
                        Text(
                            text = "$label: $value",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
        dismissButton = {
            val current = report
            AppOutlinedButton(
                enabled = current != null,
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "cursimple-alarm-diagnostics",
                            current?.asText().orEmpty(),
                        ),
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_alarm_diagnostics_copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) { Text(stringResource(R.string.settings_alarm_diagnostics_copy)) }
        },
    )
}

/** Current permission-page status. */
private data class AppPermissionState(
    val notification: Boolean,
    val exactAlarm: Boolean,
    val fullScreenIntent: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val camera: Boolean,
    val installPackages: Boolean,
    val miuiAutoStart: Boolean? = null,
    val miuiBackgroundPopup: Boolean? = null,
)

private fun readPermissionState(context: Context): AppPermissionState {
    return AppPermissionState(
        notification = hasNotificationPermission(context),
        exactAlarm = canScheduleExactAlarms(context),
        fullScreenIntent = canUseFullScreenIntent(context),
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
        camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        installPackages = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls(),
        miuiAutoStart = com.x500x.cursimple.core.reminder.permission.MiuiPermissions.autoStart(context),
        miuiBackgroundPopup = com.x500x.cursimple.core.reminder.permission.MiuiPermissions.backgroundStartActivity(context),
    )
}

/** Explain vendor Recents locking and show the latest force-stop timestamp. */
@Composable
private fun VendorKeepAliveGuide() {
    val context = LocalContext.current
    val guideRes = when (com.x500x.cursimple.core.reminder.permission.VendorRom.current()) {
        com.x500x.cursimple.core.reminder.permission.VendorRom.Xiaomi -> R.string.settings_keepalive_guide_xiaomi
        com.x500x.cursimple.core.reminder.permission.VendorRom.Huawei,
        com.x500x.cursimple.core.reminder.permission.VendorRom.Honor -> R.string.settings_keepalive_guide_huawei
        com.x500x.cursimple.core.reminder.permission.VendorRom.Oppo,
        com.x500x.cursimple.core.reminder.permission.VendorRom.OnePlus -> R.string.settings_keepalive_guide_oppo
        com.x500x.cursimple.core.reminder.permission.VendorRom.Vivo -> R.string.settings_keepalive_guide_vivo
        else -> R.string.settings_keepalive_guide_generic
    }
    val lastForceStop = remember { com.x500x.cursimple.app.reminder.ForceStopMonitor.lastForceStopAtMillis(context) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.settings_keepalive_guide_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(guideRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lastForceStop != null) {
                val whenText = java.time.Instant.ofEpochMilli(lastForceStop)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm"))
                Text(
                    text = stringResource(R.string.settings_keepalive_force_stopped, whenText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Recheck permissions on every foreground entry. */
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

/** A null [granted] means status is unavailable; require manual confirmation. */

/** Unqueryable vendor permissions need user confirmation after visiting settings. */
@Composable
private fun VendorPermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    acked: Boolean,
    onOpen: () -> Unit,
    onAckChange: (Boolean) -> Unit,
) {
    SettingsActionRow(
        icon = icon,
        title = title,
        subtitle = if (acked) stringResource(R.string.settings_permission_vendor_acked) else subtitle,
        onClick = onOpen,
        trailing = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_permission_vendor_ack_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Checkbox(checked = acked, onCheckedChange = onAckChange)
            }
        },
    )
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean?,
    offText: String,
    onText: String,
    onClick: () -> Unit,
) {
    val container = when (granted) {
        true -> MaterialTheme.colorScheme.secondaryContainer
        false -> MaterialTheme.colorScheme.errorContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (granted) {
        true -> MaterialTheme.colorScheme.onSecondaryContainer
        false -> MaterialTheme.colorScheme.onErrorContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusRes = when (granted) {
        true -> R.string.settings_permission_status_on
        false -> R.string.settings_permission_status_off
        null -> R.string.settings_permission_status_unknown
    }
    SettingsActionRow(
        icon = icon,
        title = title,
        subtitle = if (granted == true) onText else offText,
        onClick = onClick,
        trailing = {
            Surface(shape = RoundedCornerShape(50), color = container) {
                Text(
                    text = stringResource(statusRes),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
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
private fun widgetThemeLabel(preferences: WidgetThemePreferences): String = when {
    preferences.backgroundMode == WidgetBackgroundMode.Image ->
        stringResource(R.string.settings_background_summary_image)
    preferences.followsAppThemeAccent -> stringResource(R.string.settings_accent_follow_app)
    preferences.themeAccent == ThemeAccent.Custom ->
        stringResource(R.string.main_accent_custom_value, formatRgbHex(preferences.customColorArgb))
    else -> themeAccentDisplayName(preferences.themeAccent)
}

@Composable
private fun themeAccentDisplayName(accent: ThemeAccent): String = when (accent) {
    ThemeAccent.Green -> stringResource(R.string.settings_accent_green)
    ThemeAccent.Blue -> stringResource(R.string.settings_accent_blue)
    ThemeAccent.Purple -> stringResource(R.string.settings_accent_purple)
    ThemeAccent.Orange -> stringResource(R.string.settings_accent_orange)
    ThemeAccent.Pink -> stringResource(R.string.settings_accent_pink)
    ThemeAccent.Custom -> stringResource(R.string.main_accent_custom)
}

private fun unknownAppInstallSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    } else {
        AlarmSettingsIntents.appDetails(context)
    }

@Composable
internal fun AlarmNumberSettingRow(
    title: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
    editable: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        NumberInputDialog(
            title = title,
            unit = unit,
            initial = value,
            min = min,
            max = max,
            onConfirm = {
                onValueChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (editable) {
                            Modifier.clickable { editing = true }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (editable) {
                        stringResource(R.string.settings_number_tap_to_edit, value, unit)
                    } else {
                        "$value $unit"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppOutlinedButton(
                enabled = value > min,
                onClick = { onValueChange((value - step).coerceAtLeast(min)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text("-")
            }
            Spacer(modifier = Modifier.width(8.dp))
            AppOutlinedButton(
                enabled = value < max,
                onClick = { onValueChange((value + step).coerceAtMost(max)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text("+")
            }
        }
    }
}

/**
 * Accept decimal integers within [min]..[max]; disable confirmation rather than silently clamp.
 */
@Composable
private fun NumberInputDialog(
    title: String,
    unit: String,
    initial: Int,
    min: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in min..max

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.map { ch ->
                            if (ch in '\uFF10'..'\uFF19') ch - 0xFEE0 else ch
                        }.filter { it.isDigit() }.take(4).joinToString("")
                    },
                    singleLine = true,
                    suffix = { Text(unit) },
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_number_range_hint, min, max, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (text.isNotEmpty() && !valid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            AppOutlinedButton(enabled = valid, onClick = { onConfirm(parsed!!) }) {
                Text(stringResource(R.string.settings_number_confirm))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

private fun launchSettingsIntent(context: Context, intent: Intent) {
    launchSettingsIntents(context, listOf(intent))
}

/** Try candidate settings activities in order and report failure only after all fail. */
internal fun launchSettingsIntents(context: Context, intents: List<Intent>) {
    val opened = launchFirstAvailableSetting(context, intents + AlarmSettingsIntents.appDetails(context))
    if (!opened) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_toast_open_settings_manually),
            Toast.LENGTH_LONG,
        ).show()
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
        AppOutlinedButton(
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

/** Developer tools are grouped into subpages. */
@Composable
private fun AdvancedToolsSection(
    debugForcedDateTime: LocalDateTime?,
    onSetAdvancedTools: (Boolean) -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
) {
    val context = LocalContext.current
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
                text = stringResource(R.string.settings_advanced_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(R.string.settings_advanced_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsActionRow(
            icon = Icons.Rounded.CalendarMonth,
            title = stringResource(R.string.settings_dest_dev_time),
            subtitle = if (debugForcedDateTime != null) {
                stringResource(
                    R.string.settings_dev_time_forced,
                    DateTimeFormatter.ofPattern("yyyy/M/d EEEE HH:mm").format(debugForcedDateTime),
                )
            } else {
                stringResource(R.string.settings_dev_time_real)
            },
            onClick = { onNavigate(SettingsDestination.DevTime) },
        )
        SettingsActionRow(
            icon = Icons.Rounded.NotificationsActive,
            title = stringResource(R.string.settings_dest_dev_notice),
            subtitle = stringResource(R.string.settings_dev_hub_notice_subtitle),
            onClick = { onNavigate(SettingsDestination.DevNotice) },
        )
        SettingsActionRow(
            icon = Icons.Rounded.Code,
            title = stringResource(R.string.settings_dest_dev_logs),
            subtitle = stringResource(R.string.settings_dev_hub_logs_subtitle),
            onClick = { onNavigate(SettingsDestination.DevLogs) },
        )
        SettingsActionRow(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.settings_dest_dev_data),
            subtitle = stringResource(R.string.settings_dev_hub_data_subtitle),
            onClick = { onNavigate(SettingsDestination.DevData) },
        )
        AdvancedActionRow(
            icon = Icons.Rounded.BugReport,
            title = stringResource(R.string.settings_advanced_disable_title),
            subtitle = stringResource(R.string.settings_dev_disable_subtitle),
            onClick = {
                onSetAdvancedTools(false)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_advanced_off),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
}

@Composable
private fun AdvancedTimeSection(
    debugForcedDateTime: LocalDateTime?,
    onSetDebugForcedDateTime: (LocalDateTime?) -> Unit,
) {
    val context = LocalContext.current
    var pendingForcedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var showForcedDatePicker by rememberSaveable { mutableStateOf(false) }
    var showForcedTimePicker by rememberSaveable { mutableStateOf(false) }

    AdvancedActionRow(
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
        AdvancedActionRow(
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
private fun AdvancedLogsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabledCategories by remember(context) {
        mutableStateOf(LogCategory.entries.filter { LogCategories.isEnabled(context, it) }.toSet())
    }
    var showLogCategories by rememberSaveable { mutableStateOf(false) }
    AdvancedActionRow(
        icon = Icons.Rounded.Tune,
        title = stringResource(R.string.settings_dev_log_categories_title),
        subtitle = stringResource(
            R.string.settings_dev_log_categories_subtitle,
            enabledCategories.size,
            LogCategory.entries.size,
        ),
        onClick = { showLogCategories = true },
    )
    if (showLogCategories) {
        LogCategoriesDialog(
            enabled = enabledCategories,
            onToggle = { category, enabled ->
                LogCategories.setEnabled(context, category, enabled)
                enabledCategories = if (enabled) enabledCategories + category else enabledCategories - category
            },
            onDismiss = { showLogCategories = false },
        )
    }
    AdvancedActionRow(
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
    AdvancedActionRow(
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
    var showPluginLog by rememberSaveable { mutableStateOf(false) }
    AdvancedActionRow(
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
}

@Composable
private fun LogCategoriesDialog(
    enabled: Set<LogCategory>,
    onToggle: (LogCategory, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dev_log_categories_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.settings_dev_log_categories_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LogCategory.entries.forEach { category ->
                    val checked = category in enabled
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(category, it) },
                            ),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            text = stringResource(category.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
    )
}

@Composable
private fun AdvancedDataSection(
    privateFilesProviderEnabled: Boolean,
    onPrivateFilesProviderEnabledChange: (Boolean) -> Unit,
    onExportScheduleMetadata: () -> Unit,
) {
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
    AdvancedActionRow(
        icon = Icons.Rounded.Schedule,
        title = stringResource(R.string.settings_dev_export_metadata_title),
        subtitle = stringResource(R.string.settings_dev_export_metadata_subtitle),
        onClick = onExportScheduleMetadata,
    )
    ReleaseAnnouncementPreviewRow()
}

/** Load private preview drafts first, then debug assets; resolve images locally. */
@Composable
private fun ReleaseAnnouncementPreviewRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<com.x500x.cursimple.app.update.LocalReleasePreview?>(null) }
    var loading by remember { mutableStateOf(false) }
    val previewDirName = com.x500x.cursimple.app.update.RELEASE_PREVIEW_DIR
    val missingMessage = stringResource(R.string.settings_dev_release_preview_missing, previewDirName)
    AdvancedActionRow(
        icon = Icons.Rounded.NewReleases,
        title = stringResource(R.string.settings_dev_release_preview_title),
        subtitle = if (loading) stringResource(R.string.update_announcement_loading)
            else stringResource(R.string.settings_dev_release_preview_subtitle, previewDirName),
        onClick = {
            if (!loading) {
                loading = true
                scope.launch {
                    val notes = com.x500x.cursimple.app.update.loadLocalReleasePreview(context)
                    loading = false
                    if (notes == null) Toast.makeText(context, missingMessage, Toast.LENGTH_LONG).show()
                    else preview = notes
                }
            }
        },
    )
    preview?.let { notes ->
        com.x500x.cursimple.app.ReleaseAnnouncementDialog(
            versionName = notes.versionName,
            markdown = notes.markdown,
            imageLoader = com.x500x.cursimple.app.update.rememberReleaseImageLoader(notes.localDir, notes.localAssetDir),
            onDismiss = { preview = null },
        )
    }
}

@Composable
private fun AdvancedActionRow(
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
            AppOutlinedButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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
    tileActive: Boolean = false,
    /** Stable IDs support dragging entries into quick settings; see [SettingsQuickItem]. */
    quickId: String? = null,
) {
    val quickDrag = LocalSettingsQuickDrag.current
    val click = if (quickId != null && quickDrag != null) {
        { if (!quickDrag.consumeClickAfterDrag()) onClick() }
    } else {
        onClick
    }
    if (LocalSettingsRowAsTile.current) {
        SettingsQuickTile(
            tile = SettingsQuickTileSpec(icon, title, subtitle, click, active = tileActive),
            valueMaxLines = 2,
            modifier = Modifier.settingsQuickDragSource(quickId),
        )
        return
    }
    val inCard = LocalSettingsRowInCard.current
    val shape = if (inCard) RectangleShape else RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .settingsQuickDragSource(quickId)
            .clip(shape)
            .clickable(onClick = click),
        color = if (inCard) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
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
    val inCard = LocalSettingsRowInCard.current
    val shape = if (inCard) RectangleShape else RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        color = if (inCard) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
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
    AppLanguage.TraditionalChinese -> "繁體中文"
    AppLanguage.English -> "English"
}

/** Refresh published holiday data; retain the cache on failure. */
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

/** Show the most relevant synchronization result. */
private fun Context.holidaySyncMessage(outcomes: List<HolidaySyncOutcome>): String {
    outcomes.filterIsInstance<HolidaySyncOutcome.Updated>().firstOrNull()
        ?.let { return getString(R.string.settings_holiday_sync_done) }
    outcomes.filterIsInstance<HolidaySyncOutcome.Unusable>().firstOrNull()
        ?.let { return getString(R.string.settings_holiday_sync_unusable, it.year) }
    outcomes.filterIsInstance<HolidaySyncOutcome.Unreachable>().firstOrNull()
        ?.let { return getString(R.string.settings_holiday_sync_unreachable, it.year) }
    return getString(R.string.settings_holiday_sync_fresh)
}

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
                        AppOutlinedButton(onClick = { onSelect(day) }) {
                            Text(stringResource(label), maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

/** Preview background opacity and course readability at timetable proportions. */
@Composable
private fun ScheduleBackgroundPreview(
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleTextStyle: ScheduleTextStylePreferences,
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
            // Use a miniature timetable to assess background readability.
            SchedulePreviewMiniature(
                scheduleCardStyle = scheduleCardStyle,
                scheduleTextStyle = scheduleTextStyle,
                customColorsAdaptToTheme = customColorsAdaptToTheme,
                darkTheme = darkTheme,
            )
        }
    }
}

/** Preview headers, periods and courses using current colors and corner shapes. */
@Composable
private fun SchedulePreviewMiniature(
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleTextStyle: ScheduleTextStylePreferences,
    customColorsAdaptToTheme: Boolean,
    darkTheme: Boolean,
) {
    val headerColor = Color(
        scheduleTextStyle.resolvedHeaderTextColorArgb(darkTheme, customColorsAdaptToTheme)
            .toULong() shl 32,
    )
    val todayContainer = Color(
        scheduleTextStyle.resolvedTodayHeaderBackgroundColorArgb(darkTheme, customColorsAdaptToTheme)
            .toULong() shl 32,
    )
    val cardShape = RoundedCornerShape(scheduleCardStyle.courseCornerRadiusDp.dp)
    val cardAlpha = 1f - (scheduleCardStyle.scheduleOpacityPercent.coerceIn(0, 100) / 100f)
    val filled = listOf(
        listOf(true, false, true, false, true),
        listOf(true, true, false, false, false),
        listOf(false, false, true, true, false),
        listOf(false, true, false, false, true),
    )

    Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(12.dp))
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (index == 2) todayContainer else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(headerColor.copy(alpha = 0.75f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        filled.forEach { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.width(12.dp).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(headerColor.copy(alpha = 0.55f)),
                    )
                }
                row.forEach { hasCourse ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                        if (hasCourse) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = cardShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = cardAlpha),
                            ) {
                                Column(modifier = Modifier.padding(3.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)),
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.55f)
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Preview widget backgrounds at their actual proportions with sample content. */
@Composable
private fun WidgetBackgroundPreview(widgetThemePreferences: WidgetThemePreferences) {
    val context = LocalContext.current
    val imageUri = widgetThemePreferences.backgroundImageUri?.takeIf(String::isNotBlank)
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
    val imageAlpha = 1f - (widgetThemePreferences.backgroundImageTransparencyPercent.coerceIn(0, 100) / 100f)

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
                .fillMaxWidth()
                .aspectRatio(WIDGET_BACKGROUND_FRAME_ASPECT)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            bitmap?.let { image ->
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(imageAlpha),
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_background_preview_course),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

private fun transparencyToAlpha(percent: Int): Float = 1f - (percent.coerceIn(0, 100) / 100f)

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
                        AppOutlinedButton(onClick = { onSelect(mode) }) { Text(stringResource(label), maxLines = 1) }
                    }
                }
            }
        }
    }
}

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
                        AppOutlinedButton(onClick = { onSelect(days) }) { Text(stringResource(label), maxLines = 1) }
                    }
                }
            }
        }
    }
}

private const val SCHEDULE_BACKGROUND_FRAME_ASPECT = 0.62f

private const val WIDGET_BACKGROUND_FRAME_ASPECT = 2.0f

@Composable
private fun SettingsHintText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 14.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Developer previews can force overlay or system delivery independently of the selected skin.
 */
@Composable
private fun ClassNoticeTestRows(classNotice: ClassNoticePreferences) {
    val noticeTheme = com.x500x.cursimple.app.notice.NoticeTheme.current()
    val context = LocalContext.current
    fun warnIfBlocked(preferences: ClassNoticePreferences) {
        if (ClassNoticeNotifier.systemBlocked(context, preferences)) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_toast_dev_notice_blocked),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    // Diagnostics distinguish vendor notification layers and link to relevant system controls.
    var showDiagnostics by remember { mutableStateOf(false) }
    AdvancedActionRow(
        icon = Icons.Rounded.BugReport,
        title = stringResource(R.string.settings_dev_notice_diagnostics_title),
        subtitle = stringResource(R.string.settings_dev_notice_diagnostics_subtitle),
        onClick = { showDiagnostics = true },
    )
    if (showDiagnostics) {
        ClassNoticeDiagnosticsDialog(classNotice, onDismiss = { showDiagnostics = false })
    }
    val channelId = ClassNoticeNotifier.channelIdFor(classNotice)
    AdvancedActionRow(
        icon = Icons.Rounded.Tune,
        title = stringResource(R.string.settings_dev_notice_channel_settings_title),
        subtitle = stringResource(R.string.settings_dev_notice_channel_settings_subtitle, channelId),
        onClick = {
            ClassNoticeNotifier.ensureChannel(context)
            context.openClassNoticeSettings(channelId)
        },
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        AdvancedActionRow(
            icon = Icons.Rounded.CenterFocusStrong,
            title = stringResource(R.string.settings_dev_notice_promotion_settings_title),
            subtitle = stringResource(R.string.settings_dev_notice_promotion_settings_subtitle),
            onClick = {
                ClassNoticeNotifier.ensureChannel(context)
                context.openIslandSettings(channelId)
            },
        )
    }
    AdvancedActionRow(
        icon = Icons.Rounded.NotificationsActive,
        title = stringResource(R.string.settings_dev_notice_test_title),
        subtitle = stringResource(R.string.settings_dev_notice_test_subtitle),
        onClick = {
            warnIfBlocked(classNotice)
            ClassNoticeNotifier.notifyPreview(context, classNotice, noticeTheme)
        },
    )
    AdvancedActionRow(
        icon = Icons.Rounded.Layers,
        title = stringResource(R.string.settings_dev_notice_overlay_title),
        subtitle = stringResource(R.string.settings_dev_notice_overlay_subtitle),
        onClick = {
            if (!ClassNoticeOverlay.canDraw(context)) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_toast_dev_notice_no_overlay),
                    Toast.LENGTH_SHORT,
                ).show()
                context.openOverlayPermissionSettings()
            } else {
                val forced = classNotice.copy(skin = ClassNoticeSkin.Overlay)
                ClassNoticeOverlay.show(context, ClassNoticeNotifier.previewContent(context, forced), forced, noticeTheme)
            }
        },
    )
    AdvancedActionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_dev_notice_system_title),
        subtitle = stringResource(R.string.settings_dev_notice_system_subtitle),
        onClick = {
            val forced = classNotice.copy(skin = ClassNoticeSkin.System)
            warnIfBlocked(forced)
            ClassNoticeNotifier.notifyPreview(context, forced, noticeTheme)
        },
    )
    AdvancedActionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_dev_notice_plain_title),
        subtitle = stringResource(R.string.settings_dev_notice_plain_subtitle),
        onClick = {
            val forced = classNotice.copy(skin = ClassNoticeSkin.System)
            warnIfBlocked(forced)
            ClassNoticeNotifier.notifyPlainTest(context, forced, noticeTheme)
        },
    )
    AdvancedActionRow(
        icon = Icons.Rounded.NotificationsActive,
        title = stringResource(R.string.settings_dev_notice_sound_title),
        subtitle = stringResource(R.string.settings_dev_notice_sound_subtitle),
        onClick = {
            val forced = classNotice.copy(skin = ClassNoticeSkin.System)
            warnIfBlocked(forced)
            ClassNoticeNotifier.notifySoundTest(context, forced, noticeTheme)
        },
    )
    AdvancedActionRow(
        icon = Icons.Rounded.Schedule,
        title = stringResource(R.string.settings_dev_notice_delayed_title),
        subtitle = stringResource(R.string.settings_dev_notice_delayed_subtitle),
        onClick = {
            warnIfBlocked(classNotice)
            val app = context.applicationContext
            // Use a main-thread Handler because screen coroutines can stop on background entry.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { ClassNoticeNotifier.notifyPreview(app, classNotice, noticeTheme) },
                DEV_NOTICE_DELAY_MS,
            )
            Toast.makeText(
                context,
                context.getString(R.string.settings_toast_dev_notice_delayed),
                Toast.LENGTH_SHORT,
            ).show()
        },
    )
}

/** Read cross-process notification diagnostics on IO and include the report in logs. */
@Composable
internal fun ClassNoticeDiagnosticsDialog(preferences: ClassNoticePreferences, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lines by produceState<List<Pair<String, String>>?>(initialValue = null, context, preferences) {
        value = withContext(Dispatchers.IO) {
            ClassNoticeDiagnostics.report(context, preferences).also { report ->
                ReminderLogger.info("class_notice.diagnostics.report", report.toMap())
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dev_notice_diagnostics_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_dev_notice_diagnostics_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val current = lines
                if (current == null) {
                    Text(
                        stringResource(R.string.settings_alarm_diagnostics_loading),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    current.forEach { (key, value) ->
                        Text(text = "$key: $value", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
        dismissButton = {
            val current = lines
            AppOutlinedButton(
                enabled = current != null,
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "cursimple-class-notice-diagnostics",
                            current?.let(ClassNoticeDiagnostics::asText).orEmpty(),
                        ),
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_dev_notice_diagnostics_copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) { Text(stringResource(R.string.settings_dev_notice_diagnostics_copy)) }
        },
    )
}

private const val DEV_NOTICE_DELAY_MS = 5_000L

private const val ROOT_SCROLL_RESTORE_TIMEOUT_MS = 500L
