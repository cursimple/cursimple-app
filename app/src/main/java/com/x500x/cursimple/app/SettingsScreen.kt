@file:Suppress("LocalContextGetResourceValueCall")

package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppMotion
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedTextField
import com.x500x.cursimple.core.data.MAX_TIME_COLUMN_WIDTH_DP
import com.x500x.cursimple.core.data.MIN_TIME_COLUMN_WIDTH_DP
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics
import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import com.x500x.cursimple.core.data.CoursePaletteStyle
import com.x500x.cursimple.core.data.widget.WidgetThemeMode
import com.x500x.cursimple.feature.schedule.theme.coursePaletteForStyle
import androidx.compose.material3.RadioButton
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.QrCodeScanner
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
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LineStyle
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.x500x.cursimple.feature.plugin.ui.AppSlider
import kotlin.math.roundToInt
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.DEFAULT_AI_IMPORT_TIMEOUT_SECONDS
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
import com.x500x.cursimple.feature.widget.WidgetCatalog
import com.x500x.cursimple.core.reminder.permission.MiuiPermissions
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Info

private enum class SettingsDestination {
    Root,
    ScheduleData,
    TemporaryOverrides,
    Holidays,
    ScheduleAppearance,
    StyleCards,
    StyleCourseText,
    StyleHeader,
    StyleAlignment,
    StyleExamText,
    StyleGrid,
    StyleColorAdapt,
    ScheduleBackground,
    WidgetBackground,
    ScheduleDisplay,
    DisplayLayout,
    DisplayCellInfo,
    DisplayGridTime,
    DisplayInteraction,
    DisplayStats,
    DisplayCourseEndWarning,
    TimingProfile,
    WidgetSettings,
    AutoSilence,
    ClassNotice,
    EventReminder,
    Plugins,
    Sidebar,
    LocalBackup,
    WebDav,
    AiImport,
    Permissions,
    DevTime,
    DevNotice,
    DevNoticeAod,
    DevNoticeIsland,
    DevNoticeCenter,
    DevAlarm,
    DevLogs,
    DevData,
}

enum class SettingsDestinationKey {
    LocalBackup,
    WebDav,
    AiImport,
    ScheduleBackground,
    ScheduleStyle,
    WidgetBackground,

    TemporaryOverrides,

    /** Open permission setup from the force-stop explanation. */
    Permissions,
}

enum class SettingsReturnTargetKey {
    ImportExport,
}

private fun SettingsDestinationKey.toDestination(): SettingsDestination = when (this) {
    SettingsDestinationKey.LocalBackup -> SettingsDestination.LocalBackup
    SettingsDestinationKey.WebDav -> SettingsDestination.WebDav
    SettingsDestinationKey.AiImport -> SettingsDestination.AiImport
    SettingsDestinationKey.ScheduleBackground -> SettingsDestination.ScheduleBackground
    SettingsDestinationKey.ScheduleStyle -> SettingsDestination.ScheduleAppearance
    SettingsDestinationKey.WidgetBackground -> SettingsDestination.WidgetBackground
    SettingsDestinationKey.TemporaryOverrides -> SettingsDestination.TemporaryOverrides
    SettingsDestinationKey.Permissions -> SettingsDestination.Permissions
}

private fun SettingsDestination.parentChain(): List<SettingsDestination> = when (this) {
    SettingsDestination.StyleCards,
    SettingsDestination.StyleCourseText,
    SettingsDestination.StyleHeader,
    SettingsDestination.StyleAlignment,
    SettingsDestination.StyleExamText,
    SettingsDestination.StyleGrid,
    SettingsDestination.StyleColorAdapt,
    SettingsDestination.ScheduleBackground,
    -> listOf(SettingsDestination.ScheduleAppearance)

    SettingsDestination.WidgetBackground -> listOf(SettingsDestination.WidgetSettings)

    SettingsDestination.DisplayLayout,
    SettingsDestination.DisplayCellInfo,
    SettingsDestination.DisplayGridTime,
    SettingsDestination.DisplayInteraction,
    SettingsDestination.DisplayStats,
    SettingsDestination.DisplayCourseEndWarning,
    -> listOf(SettingsDestination.ScheduleDisplay)

    else -> emptyList()
}

/** Shortcut destinations form their own navigation root; Back returns to where they were opened. */
private fun SettingsDestination.isStandaloneEntry(): Boolean =
    this == SettingsDestination.ScheduleBackground || this == SettingsDestination.TemporaryOverrides

/** Limit the pinned timetable preview to leave room for settings. */
private const val STYLE_PREVIEW_SLOTS = 6
private val STYLE_PREVIEW_MAX_HEIGHT = 260.dp

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> stringResource(R.string.settings_theme_mode_system)
    ThemeMode.Light -> stringResource(R.string.settings_theme_mode_light)
    ThemeMode.Dark -> stringResource(R.string.settings_theme_mode_dark)
}

/** Palette name shown in the style page summary. */
@Composable
private fun coursePaletteStyleLabel(style: CoursePaletteStyle): String = stringResource(
    when (style) {
        CoursePaletteStyle.Default -> R.string.settings_course_palette_default
        CoursePaletteStyle.Morandi -> R.string.settings_course_palette_morandi
        CoursePaletteStyle.Macaron -> R.string.settings_course_palette_macaron
        CoursePaletteStyle.Vibrant -> R.string.settings_course_palette_vibrant
        CoursePaletteStyle.Vintage -> R.string.settings_course_palette_vintage
    },
)

/** One-line alignment summary for the style page. */
@Composable
private fun styleAlignmentSummary(horizontalCenter: Boolean, verticalCenter: Boolean): String =
    stringResource(
        when {
            horizontalCenter && verticalCenter -> R.string.settings_style_alignment_both
            horizontalCenter -> R.string.settings_style_alignment_horizontal
            verticalCenter -> R.string.settings_style_alignment_vertical
            else -> R.string.settings_style_alignment_none
        },
    )

/**
 * Joins enabled labels for a display index row, or says nothing is on. Rows that list independent
 * switches read as a phrase (middle dot); the card-content row reads as a plain enumeration.
 */
@Composable
private fun displaySummaryJoined(
    labels: List<String>,
    separatorRes: Int = R.string.settings_summary_separator,
): String =
    if (labels.isEmpty()) {
        stringResource(R.string.settings_display_summary_empty)
    } else {
        labels.joinToString(stringResource(separatorRes))
    }

/** One-line layout summary for the display index page. */
@Composable
private fun displayLayoutSummary(display: ScheduleDisplayPreferences): String = displaySummaryJoined(
    listOf(
        stringResource(
            if (display.rowFitMode == ScheduleRowFitMode.Fit) {
                R.string.settings_display_row_fit_fit
            } else {
                R.string.settings_display_row_fit_scroll
            },
        ),
        stringResource(
            when {
                display.weekendVisible -> R.string.settings_display_days_seven
                display.saturdayVisible -> R.string.settings_display_days_six
                else -> R.string.settings_display_days_five
            },
        ),
        stringResource(
            if (display.weekStartDay == WeekStartDay.Monday) {
                R.string.settings_display_week_start_monday
            } else {
                R.string.settings_display_week_start_sunday
            },
        ),
    ),
)

/** Which course details the cards currently show. */
@Composable
private fun displayCellInfoSummary(display: ScheduleDisplayPreferences): String = displaySummaryJoined(
    buildList {
        if (display.totalScheduleDisplayEnabled) add(stringResource(R.string.settings_display_summary_total))
        if (display.locationVisible) add(stringResource(R.string.settings_display_summary_location))
        if (display.teacherVisible) add(stringResource(R.string.settings_display_summary_teacher))
        if (display.nodeColumnTimeEnabled) add(stringResource(R.string.settings_display_summary_node_time))
    },
    separatorRes = R.string.settings_list_separator,
)

/** Period column width plus the live time indicator state. */
@Composable
private fun displayGridTimeSummary(display: ScheduleDisplayPreferences): String {
    val column = if (display.timeColumnWidthDp > 0) {
        stringResource(R.string.settings_display_summary_time_column_fixed, display.timeColumnWidthDp)
    } else {
        stringResource(R.string.settings_display_summary_time_column_auto)
    }
    val timeline = stringResource(
        if (display.timelineIndicatorEnabled) {
            R.string.settings_display_summary_timeline
        } else {
            R.string.settings_display_summary_no_timeline
        },
    )
    return column + stringResource(R.string.settings_summary_separator) + timeline
}

/** Enabled timetable gestures. */
@Composable
private fun displayInteractionSummary(display: ScheduleDisplayPreferences): String = displaySummaryJoined(
    buildList {
        if (display.courseDragEnabled) add(stringResource(R.string.settings_display_course_drag_title))
        if (display.pinchZoomEnabled) add(stringResource(R.string.settings_display_pinch_zoom_title))
    },
)

/** Enabled statistic surfaces. */
@Composable
private fun displayStatsSummary(display: ScheduleDisplayPreferences): String = displaySummaryJoined(
    buildList {
        if (display.todayOverviewEnabled) add(stringResource(R.string.settings_display_summary_today_overview))
        if (display.courseSessionCountIncludeHolidays) {
            add(stringResource(R.string.settings_course_session_holidays_title))
        }
    },
)

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
    val display = stringResource(R.string.settings_display)
    page(
        SettingsDestination.ScheduleDisplay,
        Icons.AutoMirrored.Rounded.MenuBook,
        display,
        schedule,
        listOf(
            stringResource(R.string.settings_subgroup_layout),
            stringResource(R.string.settings_subgroup_cell_info),
            stringResource(R.string.settings_subgroup_grid_time),
            stringResource(R.string.settings_subgroup_interaction),
            stringResource(R.string.settings_subgroup_stats),
            stringResource(R.string.settings_course_end_warning_menu_title),
        ),
        words(stringResource(R.string.settings_search_kw_display)),
    )
    page(
        SettingsDestination.DisplayLayout,
        Icons.Rounded.ViewWeek,
        stringResource(R.string.settings_subgroup_layout),
        under(schedule, display),
        listOf(
            stringResource(R.string.settings_display_row_fit_title),
            stringResource(R.string.settings_display_auto_scroll_title),
            stringResource(R.string.settings_display_days_title),
            stringResource(R.string.settings_display_week_start_title),
        ),
    )
    entries += SettingsSearchEntry(
        Icons.AutoMirrored.Rounded.MenuBook,
        stringResource(R.string.settings_display_days_title),
        under(schedule, display),
        listOf(
            stringResource(R.string.settings_display_days_five),
            stringResource(R.string.settings_display_days_six),
            stringResource(R.string.settings_display_days_seven),
            stringResource(R.string.settings_display_days_subtitle),
        ),
    ) { navigate(SettingsDestination.DisplayLayout) }
    page(
        SettingsDestination.DisplayCellInfo,
        Icons.AutoMirrored.Rounded.MenuBook,
        stringResource(R.string.settings_subgroup_cell_info),
        under(schedule, display),
        listOf(
            stringResource(R.string.settings_display_total_title),
            stringResource(R.string.settings_display_location_title),
            stringResource(R.string.settings_display_location_at_title),
            stringResource(R.string.settings_display_teacher_title),
            stringResource(R.string.settings_display_node_time_title),
        ),
    )
    page(
        SettingsDestination.DisplayGridTime,
        Icons.Rounded.GridOn,
        stringResource(R.string.settings_subgroup_grid_time),
        under(schedule, display),
        listOf(
            stringResource(R.string.settings_time_column_width_title),
            stringResource(R.string.settings_grid_dashed_lines_title),
            stringResource(R.string.settings_timeline_indicator_title),
            stringResource(R.string.settings_period_break_dividers_title),
        ),
    )
    page(
        SettingsDestination.DisplayInteraction,
        Icons.Rounded.OpenWith,
        stringResource(R.string.settings_subgroup_interaction),
        under(schedule, display),
        listOf(
            stringResource(R.string.settings_display_course_drag_title),
            stringResource(R.string.settings_display_pinch_zoom_title),
        ),
    )
    page(
        SettingsDestination.DisplayStats,
        Icons.Rounded.EventAvailable,
        stringResource(R.string.settings_subgroup_stats),
        under(schedule, display),
        listOf(
            stringResource(R.string.settings_today_overview_title),
            stringResource(R.string.settings_course_session_holidays_title),
        ),
    )
    page(
        SettingsDestination.DisplayCourseEndWarning,
        Icons.Rounded.EventAvailable,
        stringResource(R.string.settings_course_end_warning_menu_title),
        under(schedule, display),
        listOf(
            stringResource(R.string.settings_course_end_warning_title),
            stringResource(R.string.settings_course_end_warning_final_week),
            stringResource(R.string.settings_course_end_warning_two_weeks),
        ),
    )
    val scheduleStyle = stringResource(R.string.settings_dest_schedule_style)
    page(
        SettingsDestination.ScheduleAppearance,
        Icons.Rounded.Style,
        scheduleStyle,
        schedule,
        listOf(
            stringResource(R.string.settings_subgroup_card),
            stringResource(R.string.settings_subgroup_course_text),
            stringResource(R.string.settings_header_style),
            stringResource(R.string.settings_subgroup_alignment),
            stringResource(R.string.settings_subgroup_exam_text),
            stringResource(R.string.settings_subgroup_grid_border),
            stringResource(R.string.settings_subgroup_color_adapt),
            stringResource(R.string.settings_schedule_background),
        ),
        words(stringResource(R.string.settings_search_kw_style)),
    )
    page(
        SettingsDestination.StyleCards,
        Icons.Rounded.Dashboard,
        stringResource(R.string.settings_subgroup_card),
        under(schedule, scheduleStyle),
        listOf(
            stringResource(R.string.settings_card_corner_radius),
            stringResource(R.string.settings_card_height),
            stringResource(R.string.settings_schedule_opacity),
            stringResource(R.string.settings_inactive_course_opacity),
        ),
    )
    page(
        SettingsDestination.StyleCourseText,
        Icons.Rounded.TextFields,
        stringResource(R.string.settings_subgroup_course_text),
        under(schedule, scheduleStyle),
        listOf(
            stringResource(R.string.settings_course_text_size),
            stringResource(R.string.settings_course_text_color),
        ),
    )
    page(
        SettingsDestination.StyleHeader,
        Icons.Rounded.ViewWeek,
        stringResource(R.string.settings_header_style),
        under(schedule, scheduleStyle),
        listOf(
            stringResource(R.string.settings_header_text_size),
            stringResource(R.string.settings_header_text_color),
            stringResource(R.string.settings_today_header_background_color),
        ),
    )
    page(
        SettingsDestination.StyleAlignment,
        Icons.Rounded.FormatAlignCenter,
        stringResource(R.string.settings_subgroup_alignment),
        under(schedule, scheduleStyle),
        listOf(
            stringResource(R.string.settings_text_center_horizontal_title),
            stringResource(R.string.settings_text_center_vertical_title),
            stringResource(R.string.settings_auto_shrink_title),
            stringResource(R.string.settings_truncation_ellipsis_title),
        ),
    )
    page(
        SettingsDestination.StyleExamText,
        Icons.Rounded.Quiz,
        stringResource(R.string.settings_subgroup_exam_text),
        under(schedule, scheduleStyle),
        listOf(
            stringResource(R.string.settings_exam_text_size),
            stringResource(R.string.settings_exam_text_color),
        ),
    )
    page(
        SettingsDestination.StyleGrid,
        Icons.Rounded.GridOn,
        stringResource(R.string.settings_subgroup_grid_border),
        under(schedule, scheduleStyle),
        listOf(
            stringResource(R.string.settings_grid_border_color),
            stringResource(R.string.settings_grid_border_opacity),
            stringResource(R.string.settings_grid_border_width),
            stringResource(R.string.settings_grid_border_dashed_title),
        ),
    )
    page(
        SettingsDestination.StyleColorAdapt,
        Icons.Rounded.Brightness4,
        stringResource(R.string.settings_subgroup_color_adapt),
        under(schedule, scheduleStyle),
        listOf(stringResource(R.string.settings_adapt_colors_title)),
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
        schedule,
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
        SettingsDestination.EventReminder,
        Icons.AutoMirrored.Rounded.EventNote,
        stringResource(R.string.settings_dest_event_reminder),
        reminder,
        listOf(
            stringResource(R.string.settings_event_reminder_enabled_title),
            stringResource(R.string.settings_event_reminder_advance_title),
        ),
    )
    page(
        SettingsDestination.AutoSilence,
        Icons.AutoMirrored.Rounded.VolumeOff,
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
    page(
        SettingsDestination.LocalBackup,
        Icons.Rounded.Archive,
        stringResource(R.string.ie_local_backup_title),
        data,
        listOf(stringResource(R.string.ie_local_backup_body)),
        words("备份 恢复 ZIP 导出 导入 本地"),
    )
    page(SettingsDestination.WebDav, Icons.Rounded.Storage, "WebDAV", data, emptyList())
    page(
        SettingsDestination.Sidebar,
        Icons.AutoMirrored.Rounded.ViewSidebar,
        stringResource(R.string.settings_sidebar_title),
        general,
        listOf(stringResource(R.string.settings_sidebar_shortcuts), stringResource(R.string.settings_sidebar_restore_drawer)),
        words("侧边栏 側邊欄 菜单 選單 导航 導覽 底栏 底边栏 布局 版面 sidebar drawer menu navigation layout"),
    )
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

data class SettingsTopBar(val title: String, val onBack: (() -> Unit)?)

@Composable
private fun SettingsDestination.title(): String = when (this) {
    SettingsDestination.Root -> stringResource(R.string.settings_dest_root)
    SettingsDestination.ScheduleData -> stringResource(R.string.settings_dest_schedule_data)
    SettingsDestination.TemporaryOverrides -> stringResource(R.string.settings_dest_temporary_overrides)
    SettingsDestination.Holidays -> stringResource(R.string.settings_dest_holidays)
    SettingsDestination.ScheduleAppearance -> stringResource(R.string.settings_dest_schedule_style)
    SettingsDestination.StyleCards -> stringResource(R.string.settings_subgroup_card)
    SettingsDestination.StyleCourseText -> stringResource(R.string.settings_subgroup_course_text)
    SettingsDestination.StyleHeader -> stringResource(R.string.settings_header_style)
    SettingsDestination.StyleAlignment -> stringResource(R.string.settings_subgroup_alignment)
    SettingsDestination.StyleExamText -> stringResource(R.string.settings_subgroup_exam_text)
    SettingsDestination.StyleGrid -> stringResource(R.string.settings_subgroup_grid_border)
    SettingsDestination.StyleColorAdapt -> stringResource(R.string.settings_subgroup_color_adapt)
    SettingsDestination.ScheduleBackground -> stringResource(R.string.settings_schedule_background)
    SettingsDestination.WidgetBackground -> stringResource(R.string.settings_dest_widget_background)
    SettingsDestination.ScheduleDisplay -> stringResource(R.string.settings_display)
    SettingsDestination.DisplayLayout -> stringResource(R.string.settings_subgroup_layout)
    SettingsDestination.DisplayCellInfo -> stringResource(R.string.settings_subgroup_cell_info)
    SettingsDestination.DisplayGridTime -> stringResource(R.string.settings_subgroup_grid_time)
    SettingsDestination.DisplayInteraction -> stringResource(R.string.settings_subgroup_interaction)
    SettingsDestination.DisplayStats -> stringResource(R.string.settings_subgroup_stats)
    SettingsDestination.DisplayCourseEndWarning -> stringResource(R.string.settings_course_end_warning_menu_title)
    SettingsDestination.TimingProfile -> stringResource(R.string.settings_dest_timing_profile)
    SettingsDestination.WidgetSettings -> stringResource(R.string.settings_dest_widget_settings)
    SettingsDestination.AutoSilence -> stringResource(R.string.settings_dest_auto_silence)
    SettingsDestination.ClassNotice -> stringResource(R.string.settings_dest_class_notice)
    SettingsDestination.EventReminder -> stringResource(R.string.settings_dest_event_reminder)
    SettingsDestination.Plugins -> stringResource(R.string.settings_dest_plugins)
    SettingsDestination.Sidebar -> stringResource(R.string.settings_sidebar_title)
    SettingsDestination.LocalBackup -> stringResource(R.string.ie_local_backup_title)
    SettingsDestination.WebDav -> "WebDAV"
    SettingsDestination.AiImport -> stringResource(R.string.settings_dest_ai_import)
    SettingsDestination.Permissions -> stringResource(R.string.settings_dest_permissions)
    SettingsDestination.DevTime -> stringResource(R.string.settings_dest_dev_time)
    SettingsDestination.DevNotice -> stringResource(R.string.settings_dest_dev_notice)
    SettingsDestination.DevNoticeAod -> stringResource(R.string.notice_test_aod)
    SettingsDestination.DevNoticeIsland -> stringResource(R.string.notice_test_island)
    SettingsDestination.DevNoticeCenter -> stringResource(R.string.notice_test_center)
    SettingsDestination.DevAlarm -> stringResource(R.string.settings_dest_dev_alarm)
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
    scheduleEventReminder: com.x500x.cursimple.core.data.EventReminderPreferences = com.x500x.cursimple.core.data.EventReminderPreferences(),
    onClassNoticeEnabledChange: (Boolean) -> Unit = {},
    onScheduleEventReminderEnabledChange: (Boolean) -> Unit = {},
    onScheduleEventReminderAdvanceMinutesChange: (Int) -> Unit = {},
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
    onClassNoticeEveningBriefingChange: (Boolean) -> Unit = {},
    onClassNoticeTransitReminderChange: (Boolean) -> Unit = {},
    onClassNoticeExamAlertChange: (Boolean) -> Unit = {},
    onCoursePaletteStyleChange: (CoursePaletteStyle) -> Unit = {},
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
    onScheduleAutoScrollWhenCrowdedChange: (Boolean) -> Unit = {},
    onScheduleWeekStartDayChange: (WeekStartDay) -> Unit,
    onCourseDragEnabledChange: (Boolean) -> Unit,
    onSchedulePinchZoomEnabledChange: (Boolean) -> Unit = {},
    onTodayOverviewEnabledChange: (Boolean) -> Unit = {},
    onCourseEndWarningModeChange: (com.x500x.cursimple.core.kernel.model.CourseEndWarningMode) -> Unit = {},
    onCourseSessionCountIncludeHolidaysChange: (Boolean) -> Unit = {},
    onScheduleLocationVisibleChange: (Boolean) -> Unit,
    onScheduleTeacherVisibleChange: (Boolean) -> Unit,
    onScheduleLocationAtPrefixChange: (Boolean) -> Unit = {},
    onTotalScheduleDisplayChange: (Boolean) -> Unit,
    onScheduleTimelineIndicatorEnabledChange: (Boolean) -> Unit = {},
    onScheduleGridDashedLinesEnabledChange: (Boolean) -> Unit = {},
    onScheduleTimeColumnWidthDpChange: (Int) -> Unit = {},
    onInactiveCourseDisplayModeChange: (com.x500x.cursimple.core.data.InactiveCourseDisplayMode) -> Unit = {},
    onDayViewEventsEnabledChange: (Boolean) -> Unit = {},
    onPeriodBreakDividersEnabledChange: (Boolean) -> Unit = {},
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
    onWidgetBackgroundOpacityPercentChange: (Int) -> Unit = {},
    onWidgetFrostedGlassEnabledChange: (Boolean) -> Unit = {},
    onWidgetThemeModeChange: (com.x500x.cursimple.core.data.widget.WidgetThemeMode) -> Unit = {},
    onWidgetOpenAppOnDoubleClickChange: (Boolean) -> Unit,
    onWidgetShowEventsChange: (Boolean) -> Unit = {},
    onAppTimeZoneChange: (String?) -> Unit,
    onPluginSourcesChange: (List<String>) -> Unit,
    onComponentSourcesChange: (List<String>) -> Unit,
    drawerLayout: com.x500x.cursimple.core.data.navigation.DrawerLayout = com.x500x.cursimple.core.data.navigation.DrawerLayout.DEFAULT,
    componentLayoutActive: Boolean = false,
    onDrawerLayoutChange: (com.x500x.cursimple.core.data.navigation.DrawerLayout) -> Unit = {},
    onRestoreDrawer: () -> Unit = {},
    bottomBarSize: com.x500x.cursimple.core.data.navigation.BottomBarSize? = null,
    onBottomBarSizeChange: (com.x500x.cursimple.core.data.navigation.BottomBarSize) -> Unit = {},
    onPrivateFilesProviderEnabledChange: (Boolean) -> Unit,
    onCreateAppBackup: suspend () -> AppBackupPayload,
    onRestoreAppBackup: suspend (AppBackupPayload) -> Unit,
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
    /** Reports the subpage title and back action so the app bar can replace the menu button. */
    onTopBarChange: (SettingsTopBar?) -> Unit = {},
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
    var navDirection by remember { mutableIntStateOf(1) }
    fun navigate(next: SettingsDestination) {
        if (destination == SettingsDestination.Root) rootScrollOffset = scrollState.value
        navDirection = 1
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
        if (backStack.size > 1) {
            navDirection = -1
            backStack = backStack.dropLast(1)
        }
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
        navDirection = 1
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
    val topBarTitle = if (destination == SettingsDestination.Root) null else destination.title()
    val canGoBack = backStack.size > 1
    val latestHandleBack by rememberUpdatedState(::handleBack)
    LaunchedEffect(topBarTitle, canGoBack) {
        onTopBarChange(topBarTitle?.let { SettingsTopBar(it, if (canGoBack) ({ latestHandleBack() }) else null) })
    }
    DisposableEffect(Unit) {
        onDispose { onTopBarChange(null) }
    }
    var showHolidayEditor by rememberSaveable { mutableStateOf(false) }
    var showResetScheduleAppearanceConfirm by rememberSaveable { mutableStateOf(false) }
    var showResetAllSettingsConfirm by rememberSaveable { mutableStateOf(false) }
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
                icon = Icons.AutoMirrored.Rounded.VolumeOff,
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
            SettingsQuickItem("local_backup", dataGroup, SettingsQuickTileSpec(
                icon = Icons.Rounded.Archive,
                title = stringResource(R.string.ie_local_backup_title),
                value = stringResource(R.string.ie_local_backup_body),
                onClick = { navigate(SettingsDestination.LocalBackup) },
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
            Column(modifier = Modifier.fillMaxSize()) {
            // The style preview stays put while pages slide below it, so every change can be watched live.
            if (destination.showsSchedulePreview()) {
                ScheduleAppearancePreview(
                    scheduleTextStyle = scheduleTextStyle,
                    scheduleCardStyle = scheduleCardStyle,
                    scheduleBackground = scheduleBackground,
                    scheduleDisplay = scheduleDisplay,
                    customColorsAdaptToTheme = scheduleCustomColorsAdaptToTheme,
                    maxSlots = STYLE_PREVIEW_SLOTS,
                    maxHeight = STYLE_PREVIEW_MAX_HEIGHT,
                    modifier = Modifier
                        .padding(start = 18.dp, end = 18.dp, bottom = 8.dp)
                        .testTag("settings-style-preview"),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val forward = navDirection >= 0
                    AppMotion.sharedAxisX(forward)
                },
                modifier = Modifier.fillMaxSize(),
                label = "SettingsDestinationTransition",
            ) { currentDest ->
                val pageScrollState = if (currentDest == SettingsDestination.Root) scrollState else rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (currentDest == SettingsDestination.Root) {
                                    Modifier.onGloballyPositioned { quickDrag.viewport = it.boundsInRoot() }
                                } else Modifier
                            )
                            .verticalScroll(pageScrollState)
                            .padding(
                                start = 18.dp,
                                end = 18.dp,
                                top = if (currentDest == SettingsDestination.Root) 14.dp else 4.dp,
                                bottom = 24.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (currentDest) {
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
                                        icon = Icons.Rounded.CalendarMonth,
                                        title = stringResource(R.string.settings_dest_schedule_data),
                                        subtitle = stringResource(R.string.settings_row_schedule_data_subtitle),
                                        onClick = { navigate(SettingsDestination.ScheduleData) },
                                        quickId = "schedule_data",
                                    )
                                    TimingProfileEntryRow(quickId = "timing") { navigate(SettingsDestination.TimingProfile) }
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
                                    // Kept in the list as well: removing the quick tile must not hide the widgets page.
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Widgets,
                                        title = stringResource(R.string.settings_dest_widget_settings),
                                        subtitle = stringResource(R.string.settings_row_widget_subtitle),
                                        onClick = { navigate(SettingsDestination.WidgetSettings) },
                                        quickId = "widgets",
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
                                        icon = Icons.AutoMirrored.Rounded.EventNote,
                                        title = stringResource(R.string.settings_dest_event_reminder),
                                        subtitle = if (scheduleEventReminder.enabled) {
                                            stringResource(R.string.schedule_event_reminder_summary_minutes, scheduleEventReminder.advanceMinutes)
                                        } else {
                                            stringResource(R.string.schedule_event_reminder_summary_off)
                                        },
                                        onClick = { navigate(SettingsDestination.EventReminder) },
                                        trailing = {
                                            Switch(checked = scheduleEventReminder.enabled, onCheckedChange = onScheduleEventReminderEnabledChange)
                                        },
                                        tileActive = scheduleEventReminder.enabled,
                                    )
                                    SettingsActionRow(
                                        icon = Icons.AutoMirrored.Rounded.VolumeOff,
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
                                        icon = Icons.Rounded.Archive,
                                        title = stringResource(R.string.ie_local_backup_title),
                                        subtitle = stringResource(R.string.ie_local_backup_body),
                                        onClick = { navigate(SettingsDestination.LocalBackup) },
                                        quickId = "local_backup",
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
                                        icon = Icons.AutoMirrored.Rounded.ViewSidebar,
                                        title = stringResource(R.string.settings_sidebar_title),
                                        subtitle = stringResource(R.string.settings_sidebar_subtitle),
                                        onClick = { navigate(SettingsDestination.Sidebar) },
                                    )
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
                            SettingsCard {
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
                            SettingsSectionHeader(stringResource(R.string.settings_holiday_group_builtin))
                            SettingsCard {
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
                            }
                            SettingsSectionHeader(stringResource(R.string.settings_holiday_group_manual))
                            val userEntries = holidayCalendar.sortedUserEntries()
                            SettingsCard {
                                SettingsActionRow(
                                    icon = Icons.Rounded.EventAvailable,
                                    title = stringResource(R.string.settings_holiday_adjust_day_title),
                                    subtitle = stringResource(R.string.settings_holiday_adjust_day_subtitle),
                                    onClick = { showHolidayEditor = true },
                                )
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
                            if (userEntries.isEmpty()) {
                                SettingsHintText(stringResource(R.string.settings_holiday_no_manual_entries))
                            }
                            SettingsHintText(stringResource(R.string.settings_holiday_note))
                        }

                        SettingsDestination.ScheduleAppearance -> {
                            // Each group is its own page; this page is the index with live summaries.
                            SettingsCard {
                                SettingsActionRow(
                                    icon = Icons.Rounded.Dashboard,
                                    title = stringResource(R.string.settings_subgroup_card),
                                    subtitle = stringResource(
                                        R.string.settings_style_cards_summary,
                                        coursePaletteStyleLabel(scheduleCardStyle.coursePaletteStyle),
                                        scheduleCardStyle.courseCornerRadiusDp,
                                        scheduleCardStyle.courseCardHeightDp,
                                    ),
                                    onClick = { navigate(SettingsDestination.StyleCards) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.TextFields,
                                    title = stringResource(R.string.settings_subgroup_course_text),
                                    subtitle = "${scheduleTextStyle.courseTextSizeSp}sp · ${formatRgbHex(scheduleTextStyle.courseTextColorArgb.toInt())}",
                                    onClick = { navigate(SettingsDestination.StyleCourseText) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.ViewWeek,
                                    title = stringResource(R.string.settings_header_style),
                                    subtitle = "${scheduleTextStyle.headerTextSizeSp}sp · ${formatRgbHex(scheduleTextStyle.headerTextColorArgb.toInt())}",
                                    onClick = { navigate(SettingsDestination.StyleHeader) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.FormatAlignCenter,
                                    title = stringResource(R.string.settings_subgroup_alignment),
                                    subtitle = styleAlignmentSummary(
                                        horizontalCenter = scheduleTextStyle.horizontalCenter,
                                        verticalCenter = scheduleTextStyle.verticalCenter,
                                    ),
                                    onClick = { navigate(SettingsDestination.StyleAlignment) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.Quiz,
                                    title = stringResource(R.string.settings_subgroup_exam_text),
                                    subtitle = "${scheduleTextStyle.examTextSizeSp}sp · ${formatRgbHex(scheduleTextStyle.examTextColorArgb.toInt())}",
                                    onClick = { navigate(SettingsDestination.StyleExamText) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.GridOn,
                                    title = stringResource(R.string.settings_subgroup_grid_border),
                                    subtitle = stringResource(
                                        R.string.settings_style_grid_summary,
                                        stringResource(
                                            if (scheduleCardStyle.gridBorderDashed) {
                                                R.string.settings_style_grid_dashed
                                            } else {
                                                R.string.settings_style_grid_solid
                                            },
                                        ),
                                        scheduleCardStyle.gridBorderOpacityPercent,
                                    ),
                                    onClick = { navigate(SettingsDestination.StyleGrid) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.Brightness4,
                                    title = stringResource(R.string.settings_subgroup_color_adapt),
                                    subtitle = stringResource(
                                        if (scheduleCustomColorsAdaptToTheme) {
                                            R.string.settings_adapt_colors_on
                                        } else {
                                            R.string.settings_adapt_colors_off
                                        },
                                    ),
                                    onClick = { navigate(SettingsDestination.StyleColorAdapt) },
                                )
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

                        SettingsDestination.StyleCards -> {
                            CoursePaletteStyleSelector(
                                currentStyle = scheduleCardStyle.coursePaletteStyle,
                                onSelect = onCoursePaletteStyleChange,
                                isDark = darkTheme,
                            )
                            SettingsCard {
                                NumberStepperRow(stringResource(R.string.settings_card_corner_radius), scheduleCardStyle.courseCornerRadiusDp, "dp", 0, 32, 1, onScheduleCourseCornerRadiusDpChange)
                                NumberStepperRow(stringResource(R.string.settings_card_height), scheduleCardStyle.courseCardHeightDp, "dp", 56, 160, 4, onScheduleCourseCardHeightDpChange)
                                NumberStepperRow(stringResource(R.string.settings_schedule_opacity), scheduleCardStyle.scheduleOpacityPercent, "%", 0, 100, 5, onScheduleOpacityPercentChange)
                                NumberStepperRow(stringResource(R.string.settings_inactive_course_opacity), scheduleCardStyle.inactiveCourseOpacityPercent, "%", 0, 100, 5, onScheduleInactiveCourseOpacityPercentChange)
                            }
                            if (scheduleDisplay.rowFitMode == ScheduleRowFitMode.Fit) {
                                SettingsHintText(stringResource(R.string.settings_card_height_fit_hint))
                            }
                        }

                        SettingsDestination.StyleCourseText -> {
                            SettingsCard {
                                NumberStepperRow(
                                    title = stringResource(R.string.settings_course_text_size),
                                    value = scheduleTextStyle.courseTextSizeSp,
                                    unit = "sp",
                                    min = 8,
                                    max = 32,
                                    step = 1,
                                    onValueChange = onScheduleCourseTextSizeSpChange,
                                    icon = Icons.Rounded.FormatSize,
                                )
                                ColorAlphaRow(stringResource(R.string.settings_course_text_color), scheduleTextStyle.courseTextColorArgb, onScheduleCourseTextColorArgbChange)
                            }
                            if (scheduleCustomColorsAdaptToTheme) {
                                SettingsCard {
                                    ColorPreviewRow(
                                        stringResource(R.string.settings_current_theme_preview),
                                        scheduleTextStyle.courseTextColorArgb.adaptForegroundForPreview(darkTheme),
                                    )
                                }
                            }
                        }

                        SettingsDestination.StyleHeader -> {
                            SettingsCard {
                                NumberStepperRow(
                                    title = stringResource(R.string.settings_header_text_size),
                                    value = scheduleTextStyle.headerTextSizeSp,
                                    unit = "sp",
                                    min = 8,
                                    max = 32,
                                    step = 1,
                                    onValueChange = onScheduleHeaderTextSizeSpChange,
                                    icon = Icons.Rounded.FormatSize,
                                )
                                ColorAlphaRow(
                                    stringResource(R.string.settings_header_text_color),
                                    scheduleTextStyle.resolvedHeaderTextColorArgb(darkTheme, false),
                                    onScheduleHeaderTextColorArgbChange,
                                )
                                ColorAlphaRow(
                                    stringResource(R.string.settings_today_header_background_color),
                                    scheduleTextStyle.resolvedTodayHeaderBackgroundColorArgb(darkTheme, false),
                                    onScheduleTodayHeaderBackgroundColorArgbChange,
                                )
                            }
                            if (scheduleCustomColorsAdaptToTheme) {
                                SettingsCard {
                                    ColorPreviewRow(
                                        stringResource(R.string.settings_current_theme_preview_header_text),
                                        scheduleTextStyle.resolvedHeaderTextColorArgb(darkTheme, true),
                                    )
                                    ColorPreviewRow(
                                        stringResource(R.string.settings_current_theme_preview_today_header),
                                        scheduleTextStyle.resolvedTodayHeaderBackgroundColorArgb(darkTheme, true),
                                    )
                                }
                            }
                        }

                        SettingsDestination.StyleAlignment -> {
                            SettingsCard {
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
                        }

                        SettingsDestination.StyleExamText -> {
                            SettingsCard {
                                NumberStepperRow(
                                    title = stringResource(R.string.settings_exam_text_size),
                                    value = scheduleTextStyle.examTextSizeSp,
                                    unit = "sp",
                                    min = 8,
                                    max = 32,
                                    step = 1,
                                    onValueChange = onScheduleExamTextSizeSpChange,
                                    icon = Icons.Rounded.FormatSize,
                                )
                                ColorAlphaRow(stringResource(R.string.settings_exam_text_color), scheduleTextStyle.examTextColorArgb, onScheduleExamTextColorArgbChange)
                            }
                            if (scheduleCustomColorsAdaptToTheme) {
                                SettingsCard {
                                    ColorPreviewRow(
                                        stringResource(R.string.settings_current_theme_preview),
                                        scheduleTextStyle.examTextColorArgb.adaptForegroundForPreview(darkTheme),
                                    )
                                }
                            }
                        }

                        SettingsDestination.StyleGrid -> {
                            SettingsCard {
                                ColorAlphaRow(stringResource(R.string.settings_grid_border_color), scheduleCardStyle.gridBorderColorArgb, onScheduleGridBorderColorArgbChange)
                                NumberStepperRow(
                                    title = stringResource(R.string.settings_grid_border_opacity),
                                    value = scheduleCardStyle.gridBorderOpacityPercent,
                                    unit = "%",
                                    min = 0,
                                    max = 100,
                                    step = 5,
                                    onValueChange = onScheduleGridBorderOpacityPercentChange,
                                    icon = Icons.Rounded.Opacity,
                                )
                                FloatStepperRow(
                                    title = stringResource(R.string.settings_grid_border_width),
                                    value = scheduleCardStyle.gridBorderWidthDp,
                                    unit = "dp",
                                    min = 0f,
                                    max = 4f,
                                    step = 0.5f,
                                    onValueChange = onScheduleGridBorderWidthDpChange,
                                    icon = Icons.Rounded.LineWeight,
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.LineStyle,
                                    title = stringResource(R.string.settings_grid_border_dashed_title),
                                    subtitle = stringResource(R.string.settings_grid_border_dashed_subtitle),
                                    checked = scheduleCardStyle.gridBorderDashed,
                                    onCheckedChange = onScheduleGridBorderDashedChange,
                                )
                            }
                            if (scheduleCustomColorsAdaptToTheme) {
                                SettingsCard {
                                    ColorPreviewRow(
                                        stringResource(R.string.settings_current_theme_preview),
                                        scheduleCardStyle.gridBorderColorArgb.adaptForegroundForPreview(darkTheme),
                                    )
                                }
                            }
                        }

                        SettingsDestination.StyleColorAdapt -> {
                            SettingsCard {
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
                            }
                        }

                        SettingsDestination.DevTime -> {
                            AdvancedTimeSection(
                                debugForcedDateTime = debugForcedDateTime,
                                onSetDebugForcedDateTime = onSetDebugForcedDateTime,
                            )
                        }

                        SettingsDestination.DevNotice -> {
                            if (advancedToolsEnabled) NoticeDisplayTestEntries { target ->
                                navigate(when (target) {
                                    com.x500x.cursimple.app.notice.NoticeDisplayTarget.Aod -> SettingsDestination.DevNoticeAod
                                    com.x500x.cursimple.app.notice.NoticeDisplayTarget.Island -> SettingsDestination.DevNoticeIsland
                                    com.x500x.cursimple.app.notice.NoticeDisplayTarget.Center -> SettingsDestination.DevNoticeCenter
                                })
                            }
                            if (advancedToolsEnabled) ClassNoticeTestRows(classNotice)
                        }

                        SettingsDestination.DevNoticeAod, SettingsDestination.DevNoticeIsland, SettingsDestination.DevNoticeCenter -> {
                            if (advancedToolsEnabled) NoticeDisplayTestSection(when (destination) {
                                SettingsDestination.DevNoticeAod -> com.x500x.cursimple.app.notice.NoticeDisplayTarget.Aod
                                SettingsDestination.DevNoticeIsland -> com.x500x.cursimple.app.notice.NoticeDisplayTarget.Island
                                else -> com.x500x.cursimple.app.notice.NoticeDisplayTarget.Center
                            }, classNotice)
                        }

                        SettingsDestination.DevAlarm -> {
                            if (advancedToolsEnabled) AlarmTestSection()
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

                        SettingsDestination.WidgetBackground -> {
                            WidgetBackgroundPreview(widgetThemePreferences = widgetThemePreferences)
                            SettingsCard {
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
                                        icon = Icons.Rounded.Opacity,
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Delete,
                                        title = stringResource(R.string.settings_widget_background_clear_title),
                                        subtitle = stringResource(R.string.settings_widget_background_clear_subtitle),
                                        onClick = onClearWidgetBackgroundImage,
                                    )
                                }
                            }
                            SettingsCard {
                                SliderPercentRow(
                                    title = stringResource(R.string.widget_background_opacity_title),
                                    value = widgetThemePreferences.backgroundOpacityPercent,
                                    onValueChange = onWidgetBackgroundOpacityPercentChange,
                                    icon = Icons.Rounded.Opacity,
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.Layers,
                                    title = stringResource(R.string.widget_frosted_glass_title),
                                    subtitle = stringResource(R.string.widget_frosted_glass_subtitle),
                                    checked = widgetThemePreferences.frostedGlassEnabled,
                                    onCheckedChange = onWidgetFrostedGlassEnabledChange,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.Palette,
                                    title = stringResource(R.string.settings_theme),
                                    subtitle = widgetThemeLabel(widgetThemePreferences),
                                    onClick = onPickWidgetThemeAccent,
                                )
                            }
                        }

                        SettingsDestination.ScheduleBackground -> {
                            SettingsCard {
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
                                ColorAlphaRow(stringResource(R.string.settings_background_color), scheduleBackground.colorArgb, onScheduleBackgroundColorArgbChange)
                                SettingsActionRow(
                                    icon = Icons.Rounded.Restore,
                                    title = stringResource(R.string.settings_background_reset_title),
                                    subtitle = stringResource(R.string.settings_background_reset_subtitle),
                                    onClick = onScheduleBackgroundUseHeaderColor,
                                )
                                if (scheduleCustomColorsAdaptToTheme) {
                                    ColorPreviewRow(
                                        stringResource(R.string.settings_current_theme_preview),
                                        scheduleBackground.colorArgb.adaptBackgroundForPreview(darkTheme),
                                    )
                                }
                            }
                            if (scheduleBackground.type == ScheduleBackgroundType.Image || scheduleBackground.imageUri != null) {
                                SettingsHintText(stringResource(R.string.settings_background_image_note))
                                SettingsCard {
                                    SliderPercentRow(
                                        title = stringResource(R.string.settings_background_image_transparency),
                                        value = scheduleBackground.imageTransparencyPercent,
                                        onValueChange = onScheduleBackgroundImageTransparencyPercentChange,
                                        icon = Icons.Rounded.Opacity,
                                    )
                                    SettingsActionRow(
                                        icon = Icons.Rounded.Delete,
                                        title = stringResource(R.string.settings_background_image_clear_title),
                                        subtitle = stringResource(R.string.settings_background_image_clear_subtitle),
                                        onClick = onClearScheduleBackgroundImage,
                                    )
                                }
                            }
                        }

                        SettingsDestination.ScheduleDisplay -> {
                            // Each group is its own page; this page is the index with live summaries.
                            SettingsCard {
                                SettingsActionRow(
                                    icon = Icons.Rounded.ViewWeek,
                                    title = stringResource(R.string.settings_subgroup_layout),
                                    subtitle = displayLayoutSummary(scheduleDisplay),
                                    onClick = { navigate(SettingsDestination.DisplayLayout) },
                                )
                                SettingsActionRow(
                                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                                    title = stringResource(R.string.settings_subgroup_cell_info),
                                    subtitle = displayCellInfoSummary(scheduleDisplay),
                                    onClick = { navigate(SettingsDestination.DisplayCellInfo) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.GridOn,
                                    title = stringResource(R.string.settings_subgroup_grid_time),
                                    subtitle = displayGridTimeSummary(scheduleDisplay),
                                    onClick = { navigate(SettingsDestination.DisplayGridTime) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.OpenWith,
                                    title = stringResource(R.string.settings_subgroup_interaction),
                                    subtitle = displayInteractionSummary(scheduleDisplay),
                                    onClick = { navigate(SettingsDestination.DisplayInteraction) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.EventAvailable,
                                    title = stringResource(R.string.settings_subgroup_stats),
                                    subtitle = displayStatsSummary(scheduleDisplay),
                                    onClick = { navigate(SettingsDestination.DisplayStats) },
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.EventAvailable,
                                    title = stringResource(R.string.settings_course_end_warning_menu_title),
                                    subtitle = if (scheduleDisplay.courseEndWarningMode == com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.Off) {
                                        stringResource(R.string.settings_course_end_warning_off)
                                    } else if (scheduleDisplay.courseEndWarningMode == com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.FinalWeek) {
                                        stringResource(R.string.settings_course_end_warning_final_week)
                                    } else {
                                        stringResource(R.string.settings_course_end_warning_two_weeks)
                                    },
                                    onClick = { navigate(SettingsDestination.DisplayCourseEndWarning) },
                                )
                            }
                        }

                        SettingsDestination.DisplayLayout -> {
                            SettingsCard {
                                RowFitModeRow(
                                    selected = scheduleDisplay.rowFitMode,
                                    onSelect = onScheduleRowFitModeChange,
                                    icon = Icons.Rounded.Height,
                                )
                                if (scheduleDisplay.rowFitMode == ScheduleRowFitMode.Fit) {
                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.SwapVert,
                                        title = stringResource(R.string.settings_display_auto_scroll_title),
                                        subtitle = stringResource(R.string.settings_display_auto_scroll_subtitle),
                                        checked = scheduleDisplay.autoScrollWhenCrowded,
                                        onCheckedChange = onScheduleAutoScrollWhenCrowdedChange,
                                    )
                                }
                                VisibleDaysRow(
                                    saturdayVisible = scheduleDisplay.saturdayVisible,
                                    weekendVisible = scheduleDisplay.weekendVisible,
                                    onSelect = { days ->
                                        onScheduleSaturdayVisibleChange(days >= 6)
                                        onScheduleWeekendVisibleChange(days == 7)
                                    },
                                    icon = Icons.Rounded.ViewWeek,
                                )
                                WeekStartDayRow(
                                    selected = scheduleDisplay.weekStartDay,
                                    onSelect = onScheduleWeekStartDayChange,
                                    icon = Icons.Rounded.Today,
                                )
                            }
                        }

                        SettingsDestination.DisplayCellInfo -> {
                            SettingsCard {
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
                                if (scheduleDisplay.totalScheduleDisplayEnabled) {
                                    InactiveCourseModeSelectorRow(
                                        mode = scheduleDisplay.inactiveCourseDisplayMode,
                                        onSelect = onInactiveCourseDisplayModeChange,
                                        icon = Icons.Rounded.VisibilityOff,
                                    )
                                }
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.Place,
                                    title = stringResource(R.string.settings_display_location_title),
                                    subtitle = stringResource(R.string.settings_display_location_subtitle),
                                    checked = scheduleDisplay.locationVisible,
                                    onCheckedChange = onScheduleLocationVisibleChange,
                                )
                                if (scheduleDisplay.locationVisible) {
                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.AlternateEmail,
                                        title = stringResource(R.string.settings_display_location_at_title),
                                        subtitle = stringResource(R.string.settings_display_location_at_subtitle),
                                        checked = scheduleDisplay.locationAtPrefix,
                                        onCheckedChange = onScheduleLocationAtPrefixChange,
                                    )
                                }
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.Event,
                                    title = stringResource(R.string.settings_display_day_events_title),
                                    subtitle = stringResource(R.string.settings_display_day_events_subtitle),
                                    checked = scheduleDisplay.dayViewEventsEnabled,
                                    onCheckedChange = onDayViewEventsEnabledChange,
                                )
SettingsSwitchRow(
                                    icon = Icons.Rounded.Person,
                                    title = stringResource(R.string.settings_display_teacher_title),
                                    subtitle = stringResource(R.string.settings_display_teacher_subtitle),
                                    checked = scheduleDisplay.teacherVisible,
                                    onCheckedChange = onScheduleTeacherVisibleChange,
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.Schedule,
                                    title = stringResource(R.string.settings_display_node_time_title),
                                    subtitle = stringResource(R.string.settings_display_node_time_subtitle),
                                    checked = scheduleDisplay.nodeColumnTimeEnabled,
                                    onCheckedChange = onScheduleNodeColumnTimeEnabledChange,
                                )
                            }
                        }

                        SettingsDestination.DisplayGridTime -> {
                            SettingsCard {
                                TimeColumnWidthRow(
                                    widthDp = scheduleDisplay.timeColumnWidthDp,
                                    onChange = onScheduleTimeColumnWidthDpChange,
                                    icon = Icons.Rounded.ViewColumn,
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.LineStyle,
                                    title = stringResource(R.string.settings_grid_dashed_lines_title),
                                    subtitle = stringResource(R.string.settings_grid_dashed_lines_subtitle),
                                    checked = scheduleDisplay.gridDashedLinesEnabled,
                                    onCheckedChange = onScheduleGridDashedLinesEnabledChange,
                                )
                                if (scheduleDisplay.gridDashedLinesEnabled) {
                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.LineStyle,
                                        title = stringResource(R.string.settings_grid_border_dashed_title),
                                        subtitle = stringResource(R.string.settings_grid_border_dashed_subtitle),
                                        checked = scheduleCardStyle.gridBorderDashed,
                                        onCheckedChange = onScheduleGridBorderDashedChange,
                                    )
                                }
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.Schedule,
                                    title = stringResource(R.string.settings_timeline_indicator_title),
                                    subtitle = stringResource(R.string.settings_timeline_indicator_subtitle),
                                    checked = scheduleDisplay.timelineIndicatorEnabled,
                                    onCheckedChange = onScheduleTimelineIndicatorEnabledChange,
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.Schedule,
                                    title = stringResource(R.string.settings_period_break_dividers_title),
                                    subtitle = stringResource(R.string.settings_period_break_dividers_subtitle),
                                    checked = scheduleDisplay.periodBreakDividersEnabled,
                                    onCheckedChange = onPeriodBreakDividersEnabledChange,
                                )
                            }
                        }

                        SettingsDestination.DisplayInteraction -> {
                            SettingsCard {
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

                        SettingsDestination.DisplayStats -> {
                            SettingsCard {
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.Schedule,
                                    title = stringResource(R.string.settings_today_overview_title),
                                    subtitle = stringResource(R.string.settings_today_overview_desc),
                                    checked = scheduleDisplay.todayOverviewEnabled,
                                    onCheckedChange = onTodayOverviewEnabledChange,
                                )
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.EventAvailable,
                                    title = stringResource(R.string.settings_course_session_holidays_title),
                                    subtitle = stringResource(R.string.settings_course_session_holidays_subtitle),
                                    checked = scheduleDisplay.courseSessionCountIncludeHolidays,
                                    onCheckedChange = onCourseSessionCountIncludeHolidaysChange,
                                )
                            }
                        }

                        SettingsDestination.DisplayCourseEndWarning -> {
                            SettingsCard {
                                SettingsSwitchRow(
                                    icon = Icons.Rounded.EventAvailable,
                                    title = stringResource(R.string.settings_course_end_warning_title),
                                    subtitle = stringResource(R.string.settings_course_end_warning_subtitle),
                                    checked = scheduleDisplay.courseEndWarningMode != com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.Off,
                                    onCheckedChange = { enabled ->
                                        onCourseEndWarningModeChange(
                                            if (enabled) com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.FinalWeek
                                            else com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.Off,
                                        )
                                    },
                                )
                                if (scheduleDisplay.courseEndWarningMode != com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.Off) {
                                    SettingsRadioRow(
                                        title = stringResource(R.string.settings_course_end_warning_final_week),
                                        subtitle = stringResource(R.string.settings_course_end_warning_range_subtitle),
                                        selected = scheduleDisplay.courseEndWarningMode == com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.FinalWeek,
                                        onSelect = { onCourseEndWarningModeChange(com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.FinalWeek) },
                                    )
                                    SettingsRadioRow(
                                        title = stringResource(R.string.settings_course_end_warning_two_weeks),
                                        subtitle = stringResource(R.string.settings_course_end_warning_two_weeks_subtitle),
                                        selected = scheduleDisplay.courseEndWarningMode == com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.TwoWeeks,
                                        onSelect = { onCourseEndWarningModeChange(com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.TwoWeeks) },
                                    )
                                }
                            }
                        }

                        SettingsDestination.WidgetSettings -> {
                            SettingsSectionHeader(stringResource(R.string.settings_subgroup_widget_look))
                            SettingsCard {
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
                                WidgetThemeModeRow(
                                    selected = widgetThemePreferences.widgetThemeMode,
                                    onSelect = onWidgetThemeModeChange,
                                    icon = Icons.Rounded.DarkMode,
                                )
                            }

                            SettingsSectionHeader(stringResource(R.string.settings_subgroup_widget_behavior))
                            SettingsCard {
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
                                SettingsSwitchRow(
                                    icon = Icons.AutoMirrored.Rounded.EventNote,
                                    title = stringResource(R.string.settings_widget_show_events_title),
                                    subtitle = stringResource(R.string.settings_widget_show_events_subtitle),
                                    checked = widgetThemePreferences.showEvents,
                                    onCheckedChange = onWidgetShowEventsChange,
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
                                onEveningBriefingChange = onClassNoticeEveningBriefingChange,
                                onTransitReminderChange = onClassNoticeTransitReminderChange,
                                onExamAlertChange = onClassNoticeExamAlertChange,
                            )
                        }

                        SettingsDestination.EventReminder -> {
                            SettingsCard {
                                SettingsSwitchRow(
                                    icon = Icons.AutoMirrored.Rounded.EventNote,
                                    title = stringResource(R.string.settings_event_reminder_enabled_title),
                                    subtitle = stringResource(R.string.settings_event_reminder_enabled_subtitle),
                                    checked = scheduleEventReminder.enabled,
                                    onCheckedChange = onScheduleEventReminderEnabledChange,
                                )
                                if (scheduleEventReminder.enabled) {
                                    AlarmNumberSettingRow(
                                        title = stringResource(R.string.settings_event_reminder_advance_title),
                                        value = scheduleEventReminder.advanceMinutes,
                                        unit = stringResource(R.string.settings_minutes_unit),
                                        min = 0,
                                        max = 60,
                                        step = 5,
                                        onValueChange = onScheduleEventReminderAdvanceMinutesChange,
                                        icon = Icons.Rounded.AccessTime,
                                    )
                                }
                            }
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

                        SettingsDestination.Sidebar -> {
                            com.x500x.cursimple.app.navigation.SidebarSettingsPage(
                                layout = drawerLayout,
                                componentLayoutActive = componentLayoutActive,
                                onChange = onDrawerLayoutChange,
                                onRestoreDrawer = onRestoreDrawer,
                                bottomBarSize = bottomBarSize,
                                onBottomBarSizeChange = onBottomBarSizeChange,
                            )
                        }

                        SettingsDestination.LocalBackup -> {
                            LocalBackupSettingsSection(
                                onCreateAppBackup = onCreateAppBackup,
                                onRestoreAppBackup = onRestoreAppBackup,
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
                            WebDavAutoBackupPanel(configComplete = WebDavConfig(webDavUrl, webDavUsername, webDavPassword).isComplete)
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
                                classNotice = classNotice,
                                alarmKeepAliveEnabled = alarmKeepAliveEnabled,
                                onAlarmKeepAliveEnabledChange = onAlarmKeepAliveEnabledChange,
                            )
                        }
                    }

                    if (showResetScheduleAppearanceConfirm) {
                        AppAlertDialog(
                            onDismissRequest = { showResetScheduleAppearanceConfirm = false },
                            icon = { Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            title = { Text(stringResource(R.string.settings_reset_schedule_dialog_title)) },
                            text = { Text(stringResource(R.string.settings_reset_schedule_dialog_message)) },
                            confirmButton = {
                                AppDialogPrimaryButton(onClick = {
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
                                AppDialogSecondaryButton(onClick = { showResetScheduleAppearanceConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
                            },
                        )
                    }

                    if (showResetAllSettingsConfirm) {
                        AppAlertDialog(
                            onDismissRequest = { showResetAllSettingsConfirm = false },
                            icon = { Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            title = { Text(stringResource(R.string.settings_reset_all_title)) },
                            text = { Text(stringResource(R.string.settings_reset_all_dialog_message)) },
                            confirmButton = {
                                AppDialogPrimaryButton(onClick = {
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
                                AppDialogSecondaryButton(onClick = { showResetAllSettingsConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
                            },
                        )
                    }

                    if (advancedToolsEnabled && currentDest == SettingsDestination.Root && settingsQuery.isBlank()) {
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
    icon: ImageVector? = null,
) {
    SettingsControlSurface(verticalArrangement = Arrangement.spacedBy(2.dp), icon = icon) {
        SettingsControlHeading(title = title)
        AppSlider(
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

/** Period column width: automatic by default; the slider shows the column's share of the screen width. */
@Composable
private fun TimeColumnWidthRow(widthDp: Int, onChange: (Int) -> Unit, icon: ImageVector? = null) {
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.coerceAtLeast(1)
    val autoLabel = stringResource(R.string.settings_time_column_width_auto)
    SettingsControlSurface(
        modifier = Modifier.testTag("settings-time-column-width"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        icon = icon,
    ) {
        SettingsControlHeading(
            title = stringResource(R.string.settings_time_column_width_title),
            subtitle = stringResource(R.string.settings_time_column_width_subtitle),
        )
        AppSlider(
            value = (if (widthDp <= 0) TIME_COLUMN_SLIDER_START_DP else widthDp).toFloat(),
            onValueChange = { next ->
                val rounded = next.toInt().coerceIn(MIN_TIME_COLUMN_WIDTH_DP, MAX_TIME_COLUMN_WIDTH_DP)
                if (rounded != widthDp) onChange(rounded)
            },
            valueRange = MIN_TIME_COLUMN_WIDTH_DP.toFloat()..MAX_TIME_COLUMN_WIDTH_DP.toFloat(),
            valueLabel = { dp ->
                if (widthDp <= 0) autoLabel else "${(dp * 100f / screenWidthDp).roundToInt()}%"
            },
            modifier = Modifier.testTag("settings-time-column-slider"),
        )
        if (widthDp > 0) {
            AppOutlinedButton(
                onClick = { onChange(0) },
                modifier = Modifier.testTag("settings-time-column-reset"),
            ) { Text(stringResource(R.string.settings_time_column_width_reset)) }
        }
    }
}

/** Where the slider rests while the width is automatic. */
private const val TIME_COLUMN_SLIDER_START_DP = 32

@Composable
private fun NumberStepperRow(
    title: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
    /** Keeps the icon column aligned with switch/action rows sharing the card. */
    icon: ImageVector? = null,
) {
    AlarmNumberSettingRow(
        title = title,
        value = value,
        unit = unit,
        min = min,
        max = max,
        step = step,
        onValueChange = onValueChange,
        icon = icon,
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
    /** Keeps the icon column aligned with switch/action rows sharing the card. */
    icon: ImageVector? = null,
) {
    val inCard = LocalSettingsRowInCard.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (inCard) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(if (inCard) 0.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SettingsRowIcon(icon)
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (inCard) {
                        MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp, letterSpacing = 0.sp)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (inCard) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(
                    text = "${formatFloat(value)} $unit",
                    modifier = if (inCard) Modifier.padding(top = 2.dp) else Modifier,
                    style = if (inCard) {
                        MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.5.sp)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StepChip(
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.settings_value_decrease),
                enabled = value > min,
                onClick = { onValueChange((value - step).coerceIn(min, max)) },
            )
            Spacer(modifier = Modifier.width(8.dp))
            StepChip(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.settings_value_increase),
                enabled = value < max,
                onClick = { onValueChange((value + step).coerceIn(min, max)) },
            )
        }
    }
}

/**
 * Surface for a row that holds several controls. Inside a card it stays plain so the card keeps a
 * single surface and draws its own dividers; on its own it remains a soft rounded box.
 */
@Composable
private fun SettingsControlSurface(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    /** Keeps the icon column aligned with switch/action rows sharing the card. */
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inCard = LocalSettingsRowInCard.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (inCard) 0.dp else 16.dp),
        color = if (inCard) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SettingsRowIcon(icon)
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

/** Title plus optional explanation for a multi-control row, spaced like the other card rows. */
@Composable
private fun SettingsControlHeading(
    title: String,
    subtitle: String? = null,
    /** Keeps a status line under the explanation, e.g. the live auto-scroll notice. */
    trailing: @Composable (() -> Unit)? = null,
) {
    val inCard = LocalSettingsRowInCard.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = if (inCard) {
                MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp, letterSpacing = 0.sp)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            fontWeight = if (inCard) FontWeight.Bold else FontWeight.SemiBold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = if (inCard) {
                    MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.5.sp)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

/** One single-choice option: a tinted pill with a radio, title and explanation, all tappable. */
@Composable
internal fun SettingsRadioRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    /** Optional visual at the row's end, e.g. a colour palette preview. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    accent.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = { if (it) onSelect() })
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A 20dp ring keeps the option compact; the Material button would add its 48dp touch box.
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(modifier = Modifier.size(10.dp).background(accent, CircleShape))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    letterSpacing = 0.sp,
                ),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Card of self-contained option rows (no dividers), e.g. radio groups and palette pickers. */
@Composable
internal fun SettingsOptionCard(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) SettingsSectionHeader(title)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                content()
            }
        }
    }
}

/** Compact 30dp pill for a small inline choice group; filled with the accent when chosen. */
@Composable
internal fun SettingsOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(if (selected) accent else Color.Transparent)
            .border(1.5.dp, if (selected) accent else accent.copy(alpha = 0.55f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else accent,
            maxLines = 1,
        )
    }
}

internal val LocalSettingsRowInCard = staticCompositionLocalOf { false }

/** Group related settings in one card with dividers. */
@Composable
internal fun SettingsCardGroup(
    title: String,
    tiles: Boolean = false,
    /** Lets a dragged row and its shadow leave the card; rows must stay transparent at rest. */
    liftable: Boolean = false,
    dividerStart: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    if (tiles) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsSectionHeader(title)
            SettingsTileGrid(content)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingsSectionHeader(title)
        SettingsCard(liftable = liftable, dividerStart = dividerStart, content = content)
    }
}

/** Container card with standard 22dp corners, border, and divided rows. */
@Composable
internal fun SettingsCard(
    liftable: Boolean = false,
    /** Where dividers start: past the row icon normally, at the row padding for icon-less rows. */
    dividerStart: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(22.dp)
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val rows: @Composable () -> Unit = {
        CompositionLocalProvider(LocalSettingsRowInCard provides true) {
            SettingsDividedRows(liftable, dividerStart, content)
        }
    }
    if (liftable) {
        // No clip: Surface would cut the dragged row's shadow at the card edge.
        Box(Modifier.fillMaxWidth().background(cardColor, cardShape).border(cardBorder, cardShape)) { rows() }
    } else {
        Surface(shape = cardShape, color = cardColor, border = cardBorder, modifier = Modifier.fillMaxWidth()) { rows() }
    }
}

/** Stacks card rows and draws an inset divider between every pair that has height. */
@Composable
private fun SettingsDividedRows(
    liftable: Boolean,
    dividerStartDp: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val density = LocalDensity.current
    val dividerStart = with(density) { dividerStartDp.toPx() }
    val dividerEnd = with(density) { 14.dp.toPx() }
    val dividerWidth = with(density) { 1.dp.toPx() }
    val dividerTops = remember { mutableListOf<Int>() }
    Layout(
        content = content,
        modifier = Modifier.drawWithContent {
            fun drawDividers() = dividerTops.forEach { y ->
                drawLine(
                    color = dividerColor,
                    start = Offset(dividerStart, y.toFloat()),
                    end = Offset(size.width - dividerEnd, y.toFloat()),
                    strokeWidth = dividerWidth,
                )
            }
            // Lifted rows are opaque, so dividers go underneath instead of across them.
            if (liftable) {
                drawDividers()
                drawContent()
            } else {
                drawContent()
                drawDividers()
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

/**
 * Rows a switch reveals. Inside a card they keep the card's dividers; elsewhere they stack with the
 * page's usual gap. Opening grows them down from the switch, closing folds them back.
 */
@Composable
internal fun SettingsReveal(visible: Boolean, content: @Composable () -> Unit) {
    val inCard = LocalSettingsRowInCard.current
    AnimatedVisibility(visible = visible, enter = AppMotion.revealEnter, exit = AppMotion.revealExit) {
        if (inCard) {
            SettingsDividedRows(liftable = false, content = content)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, letterSpacing = 0.5.sp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp),
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

    fun refreshAutoSilence(reason: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AutoSilenceController.evaluate(context.applicationContext, reason = reason)
            }
        }
    }

    val blockingReason = readiness.blockingReasonRes?.let { stringResource(it) }
    if (blockingReason != null) {
        AutoSilenceWarningBanner(
            title = stringResource(R.string.settings_auto_silence_blocked_title),
            message = blockingReason,
        )
    }
    readiness.warningRes?.let { warningRes ->
        AutoSilenceWarningBanner(message = stringResource(warningRes))
    }

    SettingsCard {
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
    }
    // The three modes are a radio list: the chosen one tints its own pill instead of opening a picker.
    SettingsOptionCard(stringResource(R.string.settings_auto_silence_mode_title)) {
        AutoSilenceMode.values().forEach { mode ->
            SettingsRadioRow(
                title = autoSilenceModeLabel(mode),
                subtitle = autoSilenceModeDescription(mode),
                selected = autoSilence.mode == mode,
                onSelect = {
                    if (autoSilence.mode != mode) {
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
                    }
                },
            )
        }
    }
    SettingsSectionHeader(stringResource(R.string.settings_auto_silence_status_section))
    SettingsCard {
        SettingsActionRow(
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.settings_dnd_permission_title),
            subtitle = when {
                readiness.notificationPolicyGranted -> stringResource(R.string.settings_dnd_permission_granted_hint)
                autoSilence.mode == AutoSilenceMode.Vibrate -> stringResource(R.string.settings_dnd_permission_not_needed)
                else -> stringResource(R.string.settings_dnd_permission_missing_hint)
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
            trailing = {
                SettingsRowValue(
                    text = when {
                        readiness.notificationPolicyGranted -> stringResource(R.string.settings_dnd_permission_value_granted)
                        autoSilence.mode == AutoSilenceMode.Vibrate -> stringResource(R.string.settings_dnd_permission_value_not_needed)
                        else -> stringResource(R.string.settings_dnd_permission_value_missing)
                    },
                    highlighted = sessionActive,
                )
            },
        )
        SettingsActionRow(
            icon = Icons.Rounded.Schedule,
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
            trailing = {
                SettingsRowValue(
                    text = if (sessionActive) {
                        stringResource(R.string.settings_auto_silence_status_value_active)
                    } else {
                        stringResource(R.string.settings_auto_silence_status_value_idle)
                    },
                    highlighted = sessionActive,
                )
            },
        )
    }
    if (autoSilence.mode == AutoSilenceMode.DoNotDisturb && !readiness.doNotDisturbAllowsAlarms) {
        AutoSilenceWarningBanner(
            title = stringResource(R.string.settings_dnd_alarms_blocked_title),
            message = stringResource(R.string.settings_dnd_alarms_blocked_subtitle),
            onClick = { launchSettingsIntent(context, Intent(Settings.ACTION_SOUND_SETTINGS)) },
        )
    }
    SettingsHintText(stringResource(R.string.settings_auto_silence_note))
}

/** Amber notice under a settings card, e.g. Do Not Disturb swallowing the class alarms. */
@Composable
private fun AutoSilenceWarningBanner(
    message: String,
    title: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 1.dp).size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp, lineHeight = 17.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
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

private val calendarPermissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

@Composable
internal fun PermissionsSection(
    classNotice: ClassNoticePreferences,
    alarmKeepAliveEnabled: Boolean,
    onAlarmKeepAliveEnabledChange: (Boolean) -> Unit,
    showDiagnostics: Boolean = true,
) {
    val context = LocalContext.current
    var permissionResultVersion by remember { mutableIntStateOf(0) }
    var blockedRuntimePermissions by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    fun handlePermissionResult(permission: String, granted: Boolean, grantedRes: Int, deniedRes: Int) {
        permissionResultVersion++
        val canAskAgain = context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
        } ?: true
        val outcome = permissionRequestOutcome(granted, canAskAgain)
        blockedRuntimePermissions = ArrayList(blockedRuntimePermissions - permission)
        if (outcome == PermissionRequestOutcome.PermanentlyDenied) {
            blockedRuntimePermissions = ArrayList(blockedRuntimePermissions + permission)
        }
        val message = when (outcome) {
            PermissionRequestOutcome.Granted -> grantedRes
            PermissionRequestOutcome.Denied -> deniedRes
            PermissionRequestOutcome.PermanentlyDenied -> R.string.permissions_request_use_settings
        }
        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        handlePermissionResult(
            Manifest.permission.POST_NOTIFICATIONS, granted,
            R.string.settings_toast_notification_granted, R.string.settings_toast_notification_denied,
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        handlePermissionResult(
            Manifest.permission.CAMERA, granted,
            R.string.settings_toast_camera_granted, R.string.settings_toast_camera_denied,
        )
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val missing = calendarPermissions.firstOrNull { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        handlePermissionResult(
            missing ?: Manifest.permission.WRITE_CALENDAR, missing == null,
            R.string.permissions_calendar_granted, R.string.calendar_toast_permission_denied,
        )
        if (missing == null) {
            blockedRuntimePermissions = ArrayList(blockedRuntimePermissions - calendarPermissions.toSet())
        }
    }
    val state = rememberPermissionState(context, permissionResultVersion)
    val vendorSettingsAvailable = AlarmSettingsIntents.hasVendorAutoStartPage(context)
    var showAlarmDiagnostics by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("permission-overview"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.permissions_overview_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.permissions_overview_body), style = MaterialTheme.typography.bodyMedium)
        }
    }

    SettingsSectionHeader(stringResource(R.string.permissions_group_notifications))
    SettingsCard {
        val requestNotification = !state.notification &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            Manifest.permission.POST_NOTIFICATIONS !in blockedRuntimePermissions
        PermissionRow(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.settings_permission_notification),
            granted = state.notification,
            purpose = stringResource(R.string.permissions_notification_purpose),
            runtimeRequest = requestNotification,
            onClick = {
                if (requestNotification) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else launchSettingsIntents(context, AlarmSettingsIntents.notifications(context))
            },
        )
        PermissionRow(
            icon = Icons.Rounded.Fullscreen,
            title = stringResource(R.string.notice_guide_step_overlay_title),
            granted = state.overlay,
            purpose = stringResource(R.string.permissions_overlay_purpose),
            onClick = { context.openOverlayPermissionSettings() },
        )
        if (state.islandSupported) {
            PermissionRow(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.notice_guide_step_island_title),
                granted = state.islandAllowed,
                purpose = stringResource(R.string.permissions_live_notification_purpose),
                onClick = { context.openIslandSettings(ClassNoticeNotifier.channelIdFor(classNotice)) },
            )
        }
    }

    SettingsSectionHeader(stringResource(R.string.permissions_group_alarms))
    SettingsCard {
        PermissionRow(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.settings_permission_exact_alarm),
            granted = state.exactAlarm,
            purpose = stringResource(R.string.permissions_exact_alarm_purpose),
            onClick = { launchSettingsIntents(context, AlarmSettingsIntents.exactAlarm(context)) },
        )
        PermissionRow(
            icon = Icons.Rounded.Fullscreen,
            title = stringResource(R.string.settings_permission_full_screen),
            granted = state.fullScreenIntent,
            purpose = stringResource(R.string.permissions_full_screen_purpose),
            onClick = { launchSettingsIntents(context, AlarmSettingsIntents.fullScreenIntent(context)) },
        )
        PermissionRow(
            icon = Icons.Rounded.BatterySaver,
            title = stringResource(R.string.settings_permission_battery_title),
            granted = state.batteryOptimizationIgnored,
            purpose = stringResource(R.string.permissions_battery_purpose),
            onClick = { launchSettingsIntents(context, AlarmSettingsIntents.batteryOptimization(context)) },
        )
        PermissionRow(
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.settings_dnd_permission_title),
            granted = state.notificationPolicy,
            purpose = stringResource(R.string.permissions_dnd_purpose),
            onClick = { launchSettingsIntent(context, AutoSilenceController.notificationPolicySettingsIntent()) },
        )
        state.backgroundRestricted?.let { restricted ->
            PermissionRow(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.permissions_background_restriction_title),
                granted = !restricted,
                purpose = stringResource(R.string.permissions_background_restriction_purpose),
                statusOverride = if (restricted) R.string.permissions_background_restricted
                    else R.string.permissions_background_unrestricted,
                onClick = { launchSettingsIntent(context, AlarmSettingsIntents.appDetails(context)) },
            )
        }
        if (vendorSettingsAvailable) {
            PermissionRow(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.settings_permission_autostart_title),
                granted = state.miuiAutoStart,
                purpose = stringResource(R.string.settings_permission_autostart_off),
                onClick = { launchSettingsIntents(context, AlarmSettingsIntents.vendorAutoStart(context)) },
            )
            PermissionRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.settings_permission_background_popup_title),
                granted = state.miuiBackgroundPopup,
                purpose = stringResource(R.string.settings_permission_background_popup_off),
                onClick = { launchSettingsIntents(context, AlarmSettingsIntents.backgroundPopup(context)) },
            )
        }
        if (AlarmSettingsIntents.hasVendorBatterySaver()) {
            PermissionRow(
                icon = Icons.Rounded.BatterySaver,
                title = stringResource(R.string.settings_permission_vendor_battery_title),
                granted = null,
                purpose = stringResource(R.string.settings_permission_vendor_battery_off),
                onClick = { launchSettingsIntents(context, AlarmSettingsIntents.vendorBatterySaver(context)) },
            )
        }
    }
    if (vendorSettingsAvailable) VendorKeepAliveGuide()

    SettingsSectionHeader(stringResource(R.string.permissions_group_tools))
    SettingsCard {
        val requestCamera = !state.camera && Manifest.permission.CAMERA !in blockedRuntimePermissions
        PermissionRow(
            icon = Icons.Rounded.QrCodeScanner,
            title = stringResource(R.string.settings_permission_camera),
            granted = state.camera,
            purpose = stringResource(R.string.settings_permission_camera_off),
            runtimeRequest = requestCamera,
            onClick = {
                if (requestCamera) cameraLauncher.launch(Manifest.permission.CAMERA)
                else launchSettingsIntent(context, AlarmSettingsIntents.appDetails(context))
            },
        )
        val requestCalendar = !state.calendar && calendarPermissions.none { it in blockedRuntimePermissions }
        PermissionRow(
            icon = Icons.Rounded.CalendarMonth,
            title = stringResource(R.string.permissions_calendar_title),
            granted = state.calendar,
            purpose = stringResource(R.string.permissions_calendar_purpose),
            runtimeRequest = requestCalendar,
            onClick = {
                if (requestCalendar) calendarLauncher.launch(calendarPermissions.toTypedArray())
                else launchSettingsIntent(context, AlarmSettingsIntents.appDetails(context))
            },
        )
        if (WidgetCatalog.detectLauncherVendor(context) != WidgetCatalog.LauncherVendor.Other) {
            PermissionRow(
                icon = Icons.Rounded.Dashboard,
                title = stringResource(R.string.permissions_shortcut_title),
                granted = state.miuiShortcut,
                purpose = stringResource(R.string.permissions_shortcut_purpose),
                onClick = { WidgetCatalog.openShortcutPermission(context) },
            )
        }
        PermissionRow(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.settings_permission_install),
            granted = state.installPackages,
            purpose = stringResource(R.string.settings_permission_install_off),
            onClick = { launchSettingsIntent(context, unknownAppInstallSettingsIntent(context)) },
        )
    }

    SettingsSectionHeader(stringResource(R.string.settings_section_guard))
    SettingsCard {
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
        if (showDiagnostics) {
            SettingsActionRow(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.settings_alarm_diagnostics_title),
                subtitle = stringResource(R.string.settings_alarm_diagnostics_subtitle),
                onClick = { showAlarmDiagnostics = true },
            )
        }
    }
    if (showAlarmDiagnostics) {
        AlarmDiagnosticsDialog(onDismiss = { showAlarmDiagnostics = false })
    }

    SettingsSectionHeader(stringResource(R.string.settings_section_declared))
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsRowTitle(stringResource(R.string.permissions_declared_title))
            Text(
                stringResource(R.string.permissions_declared_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Load DataStore diagnostics asynchronously and show a loading state. */
@Composable
private fun AlarmDiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val report by produceState<AlarmDiagnosticsReport?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { AlarmDiagnostics.collect(context) }
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
            AppDialogPrimaryButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
        dismissButton = {
            val current = report
            AppDialogSecondaryButton(
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
    val backgroundRestricted: Boolean?,
    val camera: Boolean,
    val installPackages: Boolean,
    val calendar: Boolean,
    val overlay: Boolean,
    val notificationPolicy: Boolean,
    val islandSupported: Boolean,
    val islandAllowed: Boolean?,
    val miuiAutoStart: Boolean?,
    val miuiBackgroundPopup: Boolean?,
    val miuiShortcut: Boolean?,
)

private fun readPermissionState(context: Context): AppPermissionState {
    return AppPermissionState(
        notification = hasNotificationPermission(context),
        exactAlarm = canScheduleExactAlarms(context),
        fullScreenIntent = canUseFullScreenIntent(context),
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
        backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted }.getOrNull()
        } else null,
        camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        installPackages = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls(),
        calendar = com.x500x.cursimple.app.util.SystemCalendarExporter.hasWritePermission(context),
        overlay = Settings.canDrawOverlays(context),
        notificationPolicy = context.getSystemService(android.app.NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true,
        islandSupported = ClassNoticeNotifier.islandAvailable(context),
        islandAllowed = ClassNoticeNotifier.islandPermissionGranted(context),
        miuiAutoStart = MiuiPermissions.autoStart(context),
        miuiBackgroundPopup = MiuiPermissions.backgroundStartActivity(context),
        miuiShortcut = MiuiPermissions.installShortcut(context),
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
    // Guidance sits outside the cards, like the other explanatory notes on settings pages.
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_keepalive_guide_title),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(guideRes),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lastForceStop != null) {
            val whenText = java.time.Instant.ofEpochMilli(lastForceStop)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm"))
            Text(
                text = stringResource(R.string.settings_keepalive_force_stopped, whenText),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Recheck permissions on every foreground entry. */
@Composable
private fun rememberPermissionState(context: Context, resultVersion: Int): AppPermissionState {
    var state by remember(context, resultVersion) { mutableStateOf(readPermissionState(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, resultVersion) {
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
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean?,
    purpose: String,
    runtimeRequest: Boolean = false,
    @androidx.annotation.StringRes statusOverride: Int? = null,
    onClick: () -> Unit,
) {
    val statusRes = statusOverride ?: when (granted) {
        true -> R.string.settings_permission_status_on
        false -> R.string.settings_permission_status_off
        null -> R.string.settings_permission_status_unknown
    }
    val actionRes = when {
        granted == true -> R.string.permissions_action_manage
        runtimeRequest -> R.string.permissions_action_grant
        else -> R.string.permissions_action_settings
    }
    val action = stringResource(actionRes)
    val actionDescription = stringResource(R.string.permissions_action_description, title, action)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsRowTitle(title)
                Text(purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowValue(stringResource(statusRes), highlighted = granted == true)
            Spacer(Modifier.weight(1f))
            AppOutlinedButton(
                onClick = onClick,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = actionDescription },
            ) { Text(action) }
        }
    }
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
        formatRgbHex(background.colorArgb.toInt()),
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
    /** Keeps the icon column aligned with switch/action rows sharing the card. */
    icon: ImageVector? = null,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        NumberInputDialog(
            title = title,
            unit = unit,
            icon = icon,
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
    // Inside a card the row is transparent so the card draws the surface and dividers.
    val inCard = LocalSettingsRowInCard.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (inCard) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(if (inCard) 0.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SettingsRowIcon(icon)
                Spacer(modifier = Modifier.width(14.dp))
            }
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
                    style = if (inCard) {
                        MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp, letterSpacing = 0.sp)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (inCard) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(
                    text = if (editable) {
                        stringResource(R.string.settings_number_tap_to_edit, value, unit)
                    } else {
                        "$value $unit"
                    },
                    modifier = if (inCard) Modifier.padding(top = 2.dp) else Modifier,
                    style = if (inCard) {
                        MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.5.sp)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StepChip(
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.settings_value_decrease),
                enabled = value > min,
                onClick = { onValueChange((value - step).coerceAtLeast(min)) },
            )
            Spacer(modifier = Modifier.width(8.dp))
            StepChip(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.settings_value_increase),
                enabled = value < max,
                onClick = { onValueChange((value + step).coerceAtMost(max)) },
            )
        }
    }
}

/** 44x36 stepper chip matching the reminder design's pill controls. */
@Composable
private fun StepChip(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.size(width = 44.dp, height = 36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
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
    icon: ImageVector?,
    initial: Int,
    min: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in min..max

    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon ?: Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(title) },
        text = {
            Column {
                AppOutlinedTextField(
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
            AppDialogPrimaryButton(enabled = valid, onClick = { onConfirm(parsed!!) }) {
                Text(stringResource(R.string.settings_number_confirm))
            }
        },
        dismissButton = {
            AppDialogSecondaryButton(onClick = onDismiss) {
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
    val hasAccount = username.isNotBlank() && url.isNotBlank()
    return if (hasAccount) {
        stringResource(R.string.settings_webdav_subtitle_configured, url)
    } else {
        stringResource(R.string.settings_webdav_subtitle_unconfigured)
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

/** Weekly automatic backup to the saved WebDAV, with how many copies to keep and a "back up now" button. */
@Composable
private fun WebDavAutoBackupPanel(configComplete: Boolean) {
    val context = LocalContext.current
    val store = remember { com.x500x.cursimple.app.webdav.WebDavAutoBackupStore(context) }
    val settings by store.settingsFlow.collectAsState(initial = com.x500x.cursimple.app.webdav.WebDavAutoBackupSettings())
    val scope = rememberCoroutineScope()
    var running by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.testTag("webdav-auto-backup"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsCard {
            SettingsSwitchRow(
                icon = Icons.Rounded.Storage,
                title = stringResource(R.string.webdav_auto_title),
                subtitle = stringResource(R.string.webdav_auto_subtitle),
                checked = settings.enabled,
                onCheckedChange = { on ->
                    scope.launch { store.setEnabled(on) }
                    com.x500x.cursimple.app.webdav.WebDavAutoBackupWorker.apply(context, on, runNow = on && configComplete)
                },
            )
            SettingsReveal(settings.enabled) {
                NumberStepperRow(
                    title = stringResource(R.string.webdav_auto_keep),
                    value = settings.keep,
                    unit = "",
                    min = com.x500x.cursimple.app.webdav.WebDavAutoBackupSettings.MIN_KEEP,
                    max = com.x500x.cursimple.app.webdav.WebDavAutoBackupSettings.MAX_KEEP,
                    step = 1,
                    onValueChange = { keep -> scope.launch { store.setKeep(keep) } },
                    icon = Icons.Rounded.History,
                )
            }
        }
        if (settings.enabled) {
            Text(
                text = when {
                    settings.lastError.isNotBlank() -> stringResource(R.string.webdav_auto_last_error, settings.lastError)
                    settings.lastSuccessAt > 0 -> stringResource(
                        R.string.webdav_auto_last_success,
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                            .format(java.util.Date(settings.lastSuccessAt)),
                    )
                    else -> stringResource(R.string.webdav_auto_never)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 17.25.sp),
                color = if (settings.lastError.isNotBlank()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 6.dp).testTag("webdav-auto-status"),
            )
            AppOutlinedButton(
                enabled = !running && configComplete,
                onClick = {
                    running = true
                    scope.launch {
                        val app = context.applicationContext as com.x500x.cursimple.app.ClassScheduleApplication
                        val result = withContext(Dispatchers.IO) {
                            runCatching { com.x500x.cursimple.app.webdav.runWebDavBackupNow(app) }
                        }
                        result.onSuccess {
                            store.recordSuccess(System.currentTimeMillis())
                            Toast.makeText(context, context.getString(R.string.webdav_auto_done), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            store.recordFailure(it.message ?: it.javaClass.simpleName)
                            Toast.makeText(context, context.getString(R.string.ie_upload_failed, it.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                        running = false
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("webdav-auto-now"),
            ) {
                Text(stringResource(if (running) R.string.webdav_auto_running else R.string.webdav_auto_now))
            }
            if (!configComplete) {
                SettingsHintText(stringResource(R.string.webdav_auto_need_config))
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
    var urlDraft by rememberSaveable(webDavUrl) { mutableStateOf(webDavUrl) }
    var usernameDraft by rememberSaveable(webDavUsername) { mutableStateOf(webDavUsername) }
    var passwordDraft by rememberSaveable(webDavPassword) { mutableStateOf(webDavPassword) }
    var testing by rememberSaveable { mutableStateOf(false) }

    SettingsEditorPanel(title = stringResource(R.string.settings_webdav_panel_title)) {
        AppOutlinedTextField(
            value = urlDraft,
            onValueChange = { urlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_webdav_url_label)) },
            placeholder = { Text(stringResource(R.string.settings_webdav_url_placeholder)) },
        )
        AppOutlinedTextField(
            value = usernameDraft,
            onValueChange = { usernameDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_account)) },
        )
        AppOutlinedTextField(
            value = passwordDraft,
            onValueChange = { passwordDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_password)) },
        )
        Button(
            onClick = {
                onSave(urlDraft, usernameDraft, passwordDraft)
                onSaved(WebDavConfig(urlDraft.trim(), usernameDraft.trim(), passwordDraft).isComplete)
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

    SettingsEditorPanel(title = stringResource(R.string.settings_ai_import_panel_title)) {
        AppOutlinedTextField(
            value = apiUrlDraft,
            onValueChange = { apiUrlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_ai_import_api_url_label)) },
            placeholder = { Text("https://api.openai.com/v1/chat/completions") },
            supportingText = { Text(stringResource(R.string.settings_ai_import_url_hint)) },
        )
        AppOutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_ai_import_key_label)) },
            placeholder = { Text("sk-...") },
        )
        AppOutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_ai_import_model_label)) },
            placeholder = { Text("gpt-4o-mini") },
        )
        AppOutlinedTextField(
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
    SettingsCard(dividerStart = 16.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                ),
                fontWeight = FontWeight.Bold,
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
            icon = Icons.Rounded.Alarm,
            title = stringResource(R.string.settings_dest_dev_alarm),
            subtitle = stringResource(R.string.settings_dev_hub_alarm_subtitle),
            onClick = { onNavigate(SettingsDestination.DevAlarm) },
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
            AppDialogPrimaryButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(R.string.settings_dev_time_picker_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            AppDialogPrimaryButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            AppDialogSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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
    val haptics = rememberAppHaptics()
    val quickDrag = LocalSettingsQuickDrag.current
    val click = if (quickId != null && quickDrag != null) {
        {
            if (!quickDrag.consumeClickAfterDrag()) {
                haptics.click()
                onClick()
            }
        }
    } else {
        {
            haptics.click()
            onClick()
        }
    }
    if (LocalSettingsRowAsTile.current) {
        SettingsQuickTile(
            tile = SettingsQuickTileSpec(icon, title, subtitle, click, active = tileActive),
            // At large text two columns still need more than two lines for the longer subtitles.
            valueMaxLines = if (LocalDensity.current.fontScale > LARGE_TEXT_FONT_SCALE) Int.MAX_VALUE else 2,
            modifier = Modifier.settingsQuickDragSource(quickId),
        )
        return
    }
    val inCard = LocalSettingsRowInCard.current
    val shape = if (inCard) RectangleShape else RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .settingsQuickDragSource(quickId)
            .clip(shape)
            .clickable(onClick = click),
        color = if (inCard) Color.Transparent else MaterialTheme.colorScheme.surface,
        shape = shape,
        border = if (inCard) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(icon)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                SettingsRowTitle(title)
                SettingsRowSubtitle(subtitle)
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            } else {
                SettingsRowChevron()
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
    val shape = if (inCard) RectangleShape else RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        color = if (inCard) Color.Transparent else MaterialTheme.colorScheme.surface,
        shape = shape,
        border = if (inCard) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // State is carried by the switch; the tile keeps the design's pale chip.
            SettingsRowIcon(icon)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                SettingsRowTitle(title)
                SettingsRowSubtitle(subtitle)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

/** 40dp rounded-square icon chip shared by every settings row. */
@Composable
private fun SettingsRowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsRowTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp, letterSpacing = 0.sp),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingsRowSubtitle(subtitle: String) {
    Text(
        text = subtitle,
        modifier = Modifier.padding(top = 2.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsRowChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier.size(18.dp),
    )
}

/** Compact state value at the end of a row, e.g. "Not needed" or "Idle". */
@Composable
private fun SettingsRowValue(text: String, highlighted: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
        fontWeight = FontWeight.Bold,
        color = if (highlighted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .background(
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        maxLines = 1,
    )
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
private fun WeekStartDayRow(selected: WeekStartDay, onSelect: (WeekStartDay) -> Unit, icon: ImageVector? = null) {
    SettingsControlSurface(icon = icon) {
        SettingsControlHeading(
            title = stringResource(R.string.settings_display_week_start_title),
            subtitle = stringResource(R.string.settings_display_week_start_subtitle),
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
                SettingsOptionChip(
                    text = stringResource(label),
                    selected = day == selected,
                    onClick = { onSelect(day) },
                )
            }
        }
    }
}

/** Style pages that keep the live timetable preview pinned above the content. */
private fun SettingsDestination.showsSchedulePreview(): Boolean = when (this) {
    SettingsDestination.ScheduleAppearance,
    SettingsDestination.StyleCards,
    SettingsDestination.StyleCourseText,
    SettingsDestination.StyleHeader,
    SettingsDestination.StyleAlignment,
    SettingsDestination.StyleExamText,
    SettingsDestination.StyleGrid,
    SettingsDestination.StyleColorAdapt,
    SettingsDestination.ScheduleBackground,
    // Display pages change periods, cell contents and the time column, so they preview too.
    SettingsDestination.ScheduleDisplay,
    SettingsDestination.DisplayLayout,
    SettingsDestination.DisplayCellInfo,
    SettingsDestination.DisplayGridTime,
    SettingsDestination.DisplayInteraction,
    SettingsDestination.DisplayStats,
    SettingsDestination.DisplayCourseEndWarning,
    -> true
    else -> false
}

@Composable
private fun CoursePaletteStyleSelector(
    currentStyle: CoursePaletteStyle,
    onSelect: (CoursePaletteStyle) -> Unit,
    isDark: Boolean,
) {
    val styles = listOf(
        CoursePaletteStyle.Default to R.string.settings_course_palette_default,
        CoursePaletteStyle.Morandi to R.string.settings_course_palette_morandi,
        CoursePaletteStyle.Macaron to R.string.settings_course_palette_macaron,
        CoursePaletteStyle.Vibrant to R.string.settings_course_palette_vibrant,
        CoursePaletteStyle.Vintage to R.string.settings_course_palette_vintage,
    )
    SettingsOptionCard(stringResource(R.string.settings_course_palette_title)) {
        styles.forEach { (style, labelRes) ->
            val palette = remember(style, isDark) { coursePaletteForStyle(style, isDark) }
            SettingsRadioRow(
                title = stringResource(labelRes),
                subtitle = null,
                selected = currentStyle == style,
                onSelect = { onSelect(style) },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        palette.take(6).forEach { entry ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(entry.container)
                                    .border(0.5.dp, entry.onContainer.copy(alpha = 0.28f), CircleShape),
                            )
                        }
                    }
                },
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
        SettingsSectionHeader(stringResource(R.string.settings_background_preview_title))
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
private fun RowFitModeRow(
    selected: ScheduleRowFitMode,
    onSelect: (ScheduleRowFitMode) -> Unit,
    icon: ImageVector? = null,
) {
    val autoScrolling by com.x500x.cursimple.feature.schedule.ScheduleOverflowState.autoScrolling.collectAsState()
    SettingsControlSurface(icon = icon) {
        SettingsControlHeading(
            title = stringResource(R.string.settings_display_row_fit_title),
            subtitle = stringResource(R.string.settings_display_row_fit_subtitle),
            trailing = if (selected == ScheduleRowFitMode.Fit && autoScrolling) {
                {
                    Text(
                        text = stringResource(R.string.settings_display_row_fit_auto_scrolling),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ScheduleRowFitMode.Fit to R.string.settings_display_row_fit_fit,
                ScheduleRowFitMode.Scroll to R.string.settings_display_row_fit_scroll,
            ).forEach { (mode, label) ->
                SettingsOptionChip(
                    text = stringResource(label),
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InactiveCourseModeSelectorRow(
    mode: com.x500x.cursimple.core.data.InactiveCourseDisplayMode,
    onSelect: (com.x500x.cursimple.core.data.InactiveCourseDisplayMode) -> Unit,
    icon: ImageVector? = null,
) {
    SettingsControlSurface(icon = icon) {
        SettingsControlHeading(title = stringResource(R.string.settings_inactive_course_mode_title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                com.x500x.cursimple.core.data.InactiveCourseDisplayMode.Dimmed to R.string.settings_inactive_course_mode_dimmed,
                com.x500x.cursimple.core.data.InactiveCourseDisplayMode.Hidden to R.string.settings_inactive_course_mode_hidden,
                com.x500x.cursimple.core.data.InactiveCourseDisplayMode.Outline to R.string.settings_inactive_course_mode_outline,
            ).forEach { (itemMode, label) ->
                SettingsOptionChip(
                    text = stringResource(label),
                    selected = itemMode == mode,
                    onClick = { onSelect(itemMode) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetThemeModeRow(
    selected: WidgetThemeMode,
    onSelect: (WidgetThemeMode) -> Unit,
    icon: ImageVector? = null,
) {
    SettingsControlSurface(icon = icon) {
        SettingsControlHeading(title = stringResource(R.string.widget_theme_mode_title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                WidgetThemeMode.System to R.string.widget_theme_mode_system,
                WidgetThemeMode.Light to R.string.widget_theme_mode_light,
                WidgetThemeMode.Dark to R.string.widget_theme_mode_dark,
            ).forEach { (mode, label) ->
                SettingsOptionChip(
                    text = stringResource(label),
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                )
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
    icon: ImageVector? = null,
) {
    val selected = when {
        weekendVisible -> 7
        saturdayVisible -> 6
        else -> 5
    }
    SettingsControlSurface(icon = icon) {
        SettingsControlHeading(
            title = stringResource(R.string.settings_display_days_title),
            subtitle = stringResource(R.string.settings_display_days_subtitle),
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
                SettingsOptionChip(
                    text = stringResource(label),
                    selected = days == selected,
                    onClick = { onSelect(days) },
                )
            }
        }
    }
}

private const val SCHEDULE_BACKGROUND_FRAME_ASPECT = 0.62f

private const val WIDGET_BACKGROUND_FRAME_ASPECT = 2.0f

@Composable
internal fun SettingsHintText(text: String) {
    // Explanatory note under a card: smaller than row text, aligned with the card's own padding.
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 6.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 17.25.sp),
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
                ClassNoticeOverlay.show(context, ClassNoticeNotifier.previewContent(context), forced, noticeTheme)
            }
        },
    )
    AdvancedActionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_dev_notice_system_title),
        subtitle = stringResource(R.string.settings_dev_notice_system_subtitle),
        onClick = {
            val forced = classNotice.copy(skin = ClassNoticeSkin.System, headsUpEnabled = true, focusNotificationEnabled = false)
            warnIfBlocked(forced)
            ClassNoticeNotifier.notifyPlainTest(context, forced, noticeTheme)
        },
    )
    AdvancedActionRow(
        icon = Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_dev_notice_plain_title),
        subtitle = stringResource(R.string.settings_dev_notice_plain_subtitle),
        onClick = {
            val forced = classNotice.copy(skin = ClassNoticeSkin.System, headsUpEnabled = false, focusNotificationEnabled = false)
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
            AppDialogPrimaryButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
        dismissButton = {
            val current = lines
            AppDialogSecondaryButton(
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

private const val ROOT_SCROLL_RESTORE_TIMEOUT_MS = 500L
