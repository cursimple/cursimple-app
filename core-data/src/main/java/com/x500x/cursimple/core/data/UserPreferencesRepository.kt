package com.x500x.cursimple.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.x500x.cursimple.core.kernel.time.ScheduleRowFitMode
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.core.reminder.ReminderDayPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.SyncedHolidayYear
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_COUNT
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_RING_DURATION_SECONDS
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.pow

enum class ThemeMode { System, Light, Dark }

/**
 * [Custom] stores its source color in [UserPreferences.themeCustomColorArgb]; derive remaining
 * colors from it.
 */
enum class ThemeAccent { Green, Blue, Purple, Orange, Pink, Custom }

enum class AppLanguage(val tag: String) {
    System(""),
    Chinese("zh-CN"),
    TraditionalChinese("zh-TW"),
    English("en"),
}

enum class ScheduleBackgroundType { Color, Image, Header }

enum class AutoSilenceMode {
    /** Vibrate mode requires MODIFY_AUDIO_SETTINGS. */
    Vibrate,

    Silent,

    /** Priority-only DND requires policy access. */
    DoNotDisturb,
}

object RingerModeValues {
    const val UNKNOWN = -1
    const val SILENT = 0
    const val VIBRATE = 1
    const val NORMAL = 2
}

/** Values match NotificationManager; UNKNOWN denotes no saved state. */
object InterruptionFilterValues {
    const val UNKNOWN = 0
    const val ALL = 1
    const val PRIORITY = 2
    const val NONE = 3
    const val ALARMS = 4
}

/**
 * System, RemoteViews card and overlay skins. Only overlays provide animation and blur,
 * requiring separate permission and lock-screen fallback.
 */
enum class ClassNoticeSkin { System, Card, Overlay }

enum class ClassNoticeAnimation { None, Slide, Spring }

/**
 * Non-ringing class notices with independent heads-up, lock-screen privacy and supported chip
 * controls.
 */
data class ClassNoticePreferences(
    val enabled: Boolean = true,
    val advanceMinutes: Int = DEFAULT_ADVANCE_MINUTES,
    val headsUpEnabled: Boolean = true,
    val vibrationEnabled: Boolean = false,
    val lockScreenEnabled: Boolean = true,
    val focusNotificationEnabled: Boolean = true,
    val skin: ClassNoticeSkin = ClassNoticeSkin.Overlay,
    val animation: ClassNoticeAnimation = ClassNoticeAnimation.Slide,
    /** Fall back to translucency without cross-window blur support. */
    val blurEnabled: Boolean = true,
    val blurStrength: Int = DEFAULT_BLUR_STRENGTH,
    val bannerDurationSeconds: Int = DEFAULT_BANNER_DURATION_SECONDS,
) {
    companion object {
        const val DEFAULT_ADVANCE_MINUTES = 20
        const val MIN_ADVANCE_MINUTES = 1
        /** Bound advance time to 60 minutes to keep notices relevant to the class. */
        const val MAX_ADVANCE_MINUTES = 60

        const val DEFAULT_BLUR_STRENGTH = 80
        const val MIN_BLUR_STRENGTH = 10
        const val MAX_BLUR_STRENGTH = 100

        const val DEFAULT_BANNER_DURATION_SECONDS = 15
        const val MIN_BANNER_DURATION_SECONDS = 5
        const val MAX_BANNER_DURATION_SECONDS = 60

        const val BLUR_RADIUS_DP_AT_FULL = 40f

        fun coerceAdvanceMinutes(value: Int): Int =
            value.coerceIn(MIN_ADVANCE_MINUTES, MAX_ADVANCE_MINUTES)

        fun coerceBlurStrength(value: Int): Int =
            value.coerceIn(MIN_BLUR_STRENGTH, MAX_BLUR_STRENGTH)

        fun coerceBannerDurationSeconds(value: Int): Int =
            value.coerceIn(MIN_BANNER_DURATION_SECONDS, MAX_BANNER_DURATION_SECONDS)
    }

    val bannerDurationMillis: Long
        get() = coerceBannerDurationSeconds(bannerDurationSeconds) * 1_000L
}

/**
 * Optional pre-alarm notification, disabled by default; appearance follows class-notice
 * settings.
 */
data class AlarmPreNoticePreferences(
    val enabled: Boolean = false,
    val advanceMinutes: Int = DEFAULT_ADVANCE_MINUTES,
) {
    companion object {
        const val DEFAULT_ADVANCE_MINUTES = 5
        const val MIN_ADVANCE_MINUTES = 1
        const val MAX_ADVANCE_MINUTES = 60

        fun coerceAdvanceMinutes(value: Int): Int =
            value.coerceIn(MIN_ADVANCE_MINUTES, MAX_ADVANCE_MINUTES)
    }
}

data class AutoSilencePreferences(
    val enabled: Boolean = false,
    val mode: AutoSilenceMode = AutoSilenceMode.Vibrate,
)

/**
 * Capture previous and applied device modes for conditional restoration. Planned end provides
 * schedule-independent recovery; suppression prevents reapplying after manual restore.
 */
data class AutoSilenceSession(
    val active: Boolean = false,
    val mode: AutoSilenceMode = AutoSilenceMode.Vibrate,
    val previousRingerMode: Int = RingerModeValues.UNKNOWN,
    val previousInterruptionFilter: Int = InterruptionFilterValues.UNKNOWN,
    val appliedRingerMode: Int = RingerModeValues.UNKNOWN,
    val appliedInterruptionFilter: Int = InterruptionFilterValues.UNKNOWN,
    val startedAtMillis: Long = 0L,
    val plannedEndAtMillis: Long = 0L,
    val suppressedUntilMillis: Long = 0L,
)

const val DEFAULT_PLUGIN_REGISTRY_REPO = "cursimple/cursimple-plugins"

const val DEFAULT_COMPONENT_REGISTRY_REPO = "cursimple/cursimple-components"

const val DEFAULT_COMPONENT_MARKET_INDEX_URL =
    "https://raw.githubusercontent.com/cursimple/cursimple-components/refs/heads/main/manifest.json"

const val DEFAULT_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"

const val WEBDAV_PASSWORD_PREFERENCE_KEY = "webdav_password"
const val AI_IMPORT_API_KEY_PREFERENCE_KEY = "ai_import_api_key"

/** Exclude credential keys from exported backups and preserve local values during restore. */
val USER_PREFERENCES_CREDENTIAL_KEYS: Set<String> = setOf(
    WEBDAV_PASSWORD_PREFERENCE_KEY,
    AI_IMPORT_API_KEY_PREFERENCE_KEY,
)

const val DEFAULT_AI_IMPORT_TIMEOUT_SECONDS = 120
const val MIN_AI_IMPORT_TIMEOUT_SECONDS = 10
const val MAX_AI_IMPORT_TIMEOUT_SECONDS = 600

fun coerceAiImportTimeoutSeconds(seconds: Int): Int =
    seconds.coerceIn(MIN_AI_IMPORT_TIMEOUT_SECONDS, MAX_AI_IMPORT_TIMEOUT_SECONDS)

data class ScheduleTextStylePreferences(
    val courseTextSizeSp: Int = DEFAULT_COURSE_TEXT_SIZE_SP,
    val courseTextColorArgb: Long = DEFAULT_TEXT_COLOR_ARGB,
    val examTextSizeSp: Int = DEFAULT_EXAM_TEXT_SIZE_SP,
    val examTextColorArgb: Long = DEFAULT_TEXT_COLOR_ARGB,
    val headerTextSizeSp: Int = DEFAULT_HEADER_TEXT_SIZE_SP,
    val headerTextColorArgb: Long = DEFAULT_HEADER_TEXT_COLOR_ARGB,
    val headerTextColorCustomized: Boolean = false,
    val todayHeaderBackgroundColorArgb: Long = DEFAULT_TODAY_HEADER_BACKGROUND_COLOR_ARGB,
    val todayHeaderBackgroundColorCustomized: Boolean = false,
    val horizontalCenter: Boolean = false,
    val verticalCenter: Boolean = false,
    /** Opt-in title shrinking may produce different font sizes across course cards. */
    val autoShrinkLongTitles: Boolean = false,
    /** Optional ellipsis consumes text space; default clipping maximizes visible characters. */
    val truncationEllipsis: Boolean = false,
) {
    companion object {
        const val DEFAULT_COURSE_TEXT_SIZE_SP = 13
        const val DEFAULT_EXAM_TEXT_SIZE_SP = 13
        const val DEFAULT_HEADER_TEXT_SIZE_SP = 12
        const val DEFAULT_TEXT_COLOR_ARGB = 0xFFFFFFFFL
        const val DEFAULT_HEADER_TEXT_COLOR_ARGB = 0xFF000000L
        const val DEFAULT_DARK_HEADER_TEXT_COLOR_ARGB = 0xFFFFFFFFL
        const val DEFAULT_TODAY_HEADER_BACKGROUND_COLOR_ARGB = 0xFF000000L
        const val DEFAULT_DARK_TODAY_HEADER_BACKGROUND_COLOR_ARGB = 0xFFFFFFFFL
        const val MIN_TEXT_SIZE_SP = 8
        const val MAX_TEXT_SIZE_SP = 32

        fun coerceTextSizeSp(value: Int): Int = value.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        fun coerceArgb(value: Long): Long = value and 0xFFFF_FFFFL
    }
}

data class ScheduleCardStylePreferences(
    val courseCornerRadiusDp: Int = DEFAULT_COURSE_CORNER_RADIUS_DP,
    val courseCardHeightDp: Int = DEFAULT_COURSE_CARD_HEIGHT_DP,
    val scheduleOpacityPercent: Int = DEFAULT_SCHEDULE_OPACITY_PERCENT,
    val inactiveCourseOpacityPercent: Int = DEFAULT_INACTIVE_COURSE_OPACITY_PERCENT,
    val gridBorderColorArgb: Long = DEFAULT_GRID_BORDER_COLOR_ARGB,
    val gridBorderOpacityPercent: Int = DEFAULT_GRID_BORDER_OPACITY_PERCENT,
    val gridBorderWidthDp: Float = DEFAULT_GRID_BORDER_WIDTH_DP,
    val gridBorderDashed: Boolean = false,
) {
    companion object {
        const val DEFAULT_COURSE_CORNER_RADIUS_DP = 10
        const val DEFAULT_COURSE_CARD_HEIGHT_DP = 100
        const val DEFAULT_SCHEDULE_OPACITY_PERCENT = 0
        const val DEFAULT_INACTIVE_COURSE_OPACITY_PERCENT = 50
        const val DEFAULT_GRID_BORDER_COLOR_ARGB = 0xFFCFD8DCL
        const val DEFAULT_GRID_BORDER_OPACITY_PERCENT = 100
        const val DEFAULT_GRID_BORDER_WIDTH_DP = 0.5f
        const val MIN_CORNER_RADIUS_DP = 0
        const val MAX_CORNER_RADIUS_DP = 32
        const val MIN_CARD_HEIGHT_DP = 56
        const val MAX_CARD_HEIGHT_DP = 160
        const val MIN_OPACITY_PERCENT = 0
        const val MAX_OPACITY_PERCENT = 100
        const val MIN_BORDER_WIDTH_DP = 0f
        const val MAX_BORDER_WIDTH_DP = 4f

        fun coerceCornerRadiusDp(value: Int): Int = value.coerceIn(MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP)
        fun coerceCardHeightDp(value: Int): Int = value.coerceIn(MIN_CARD_HEIGHT_DP, MAX_CARD_HEIGHT_DP)
        fun coerceOpacityPercent(value: Int): Int = value.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)
        fun coerceBorderWidthDp(value: Float): Float = value.coerceIn(MIN_BORDER_WIDTH_DP, MAX_BORDER_WIDTH_DP)
        fun coerceArgb(value: Long): Long = value and 0xFFFF_FFFFL
    }
}

/** Vendor permission keys requiring user confirmation. */
object VendorPermissionKey {
    const val AUTO_START = "vendor_auto_start"

    const val BACKGROUND_POPUP = "vendor_background_popup"

    const val BATTERY_SAVER = "vendor_battery_saver"

    const val SHORTCUT_PIN = "vendor_shortcut_pin"
}

data class ScheduleBackgroundPreferences(
    val type: ScheduleBackgroundType = DEFAULT_BACKGROUND_TYPE,
    val colorArgb: Long = DEFAULT_BACKGROUND_COLOR_ARGB,
    val imageUri: String? = null,
    val imageTransparencyPercent: Int = DEFAULT_IMAGE_TRANSPARENCY_PERCENT,
) {
    companion object {
        val DEFAULT_BACKGROUND_TYPE = ScheduleBackgroundType.Header
        const val DEFAULT_BACKGROUND_COLOR_ARGB = 0xFFFFFFFFL
        const val DEFAULT_IMAGE_TRANSPARENCY_PERCENT = 0
        fun coerceArgb(value: Long): Long = value and 0xFFFF_FFFFL
        fun coerceImageTransparencyPercent(value: Int): Int = value.coerceIn(0, 100)
    }
}

data class ScheduleDisplayPreferences(
    val nodeColumnTimeEnabled: Boolean = true,
    val saturdayVisible: Boolean = true,
    val weekendVisible: Boolean = true,
    val rowFitMode: ScheduleRowFitMode = ScheduleRowFitMode.Fit,
    val locationVisible: Boolean = true,
    val teacherVisible: Boolean = true,
    val totalScheduleDisplayEnabled: Boolean = true,
    val weekStartDay: WeekStartDay = WeekStartDay.Monday,
    /** Drag rescheduling is opt-in to prevent accidental timetable changes. */
    val courseDragEnabled: Boolean = false,
    /** Optional pinch zoom and panning; disabled mode preserves ordinary gestures. */
    val pinchZoomEnabled: Boolean = false,
    /** Whether to show today's overview in today's day view. */
    val todayOverviewEnabled: Boolean = true,
)

fun adaptScheduleForegroundColorArgb(argb: Long, darkTheme: Boolean, enabled: Boolean): Long =
    adaptScheduleCustomColorArgb(
        argb = argb,
        shouldInvert = enabled && if (darkTheme) {
            argbLuminance(argb) < COLOR_POLARITY_THRESHOLD
        } else {
            argbLuminance(argb) >= COLOR_POLARITY_THRESHOLD
        },
    )

fun adaptScheduleBackgroundColorArgb(argb: Long, darkTheme: Boolean, enabled: Boolean): Long =
    adaptScheduleCustomColorArgb(
        argb = argb,
        shouldInvert = enabled && if (darkTheme) {
            argbLuminance(argb) >= COLOR_POLARITY_THRESHOLD
        } else {
            argbLuminance(argb) < COLOR_POLARITY_THRESHOLD
        },
    )

private const val COLOR_POLARITY_THRESHOLD = 0.5

private fun adaptScheduleCustomColorArgb(argb: Long, shouldInvert: Boolean): Long {
    val normalized = argb and 0xFFFF_FFFFL
    if (!shouldInvert) return normalized
    val alpha = normalized and 0xFF00_0000L
    val invertedRgb = (normalized xor 0x00FF_FFFFL) and 0x00FF_FFFFL
    return alpha or invertedRgb
}

private fun argbLuminance(argb: Long): Double {
    val normalized = argb and 0xFFFF_FFFFL
    val red = srgbChannelToLinear(((normalized ushr 16) and 0xFF).toInt())
    val green = srgbChannelToLinear(((normalized ushr 8) and 0xFF).toInt())
    val blue = srgbChannelToLinear((normalized and 0xFF).toInt())
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

private fun srgbChannelToLinear(channelByte: Int): Double {
    val channel = channelByte.coerceIn(0, 255) / 255.0
    return if (channel <= 0.03928) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.Light,
    val themeAccent: ThemeAccent = ThemeAccent.Green,
    /** Opaque custom ARGB; preserve it when selecting a built-in accent. */
    val themeCustomColorArgb: Int = com.x500x.cursimple.core.data.theme.AccentColors.DEFAULT_CUSTOM_ARGB,
    val appLanguage: AppLanguage = AppLanguage.System,
    val termStartDate: LocalDate? = null,
    /** Explicit user date choices prevent plugin timing defaults from replacing them. */
    val termStartUserDecided: Boolean = false,
    val advancedToolsEnabled: Boolean = false,
    val scheduleTextStyle: ScheduleTextStylePreferences = ScheduleTextStylePreferences(),
    val scheduleCardStyle: ScheduleCardStylePreferences = ScheduleCardStylePreferences(),
    val scheduleBackground: ScheduleBackgroundPreferences = ScheduleBackgroundPreferences(),
    val scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    val scheduleCustomColorsAdaptToTheme: Boolean = false,
    val enabledPluginIds: Set<String> = emptySet(),
    val temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    val holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings(),
    /** Skip holiday alarms by default, with global and per-alarm opt-outs. */
    val skipRemindersOnHoliday: Boolean = true,
    /**
     * Silent guard uses alarms and jobs without persistent notifications; its independent
     * preference defaults on.
     */
    val alarmKeepAliveEnabled: Boolean = true,
    /**
     * Remember the initial runtime notification request so denial is not repeated at startup.
     */
    val notificationPermissionStartupAsked: Boolean = false,
    /** Remember the one-time chip setup prompt; later guidance lives in settings. */
    val islandStartupPromptShown: Boolean = false,
    /**
     * User-confirmed vendor permissions whose status cannot be queried; see
     * [VendorPermissionKey].
     */
    val vendorPermissionAcks: Set<String> = emptySet(),
    /**
     * Remember observed widget-pinning failure; request acceptance alone does not prove
     * launcher placement.
     */
    val widgetPinUnsupportedOnDevice: Boolean = false,
    val reminderMutedDates: Set<String> = emptySet(),
    val debugForcedDateTime: LocalDateTime? = null,
    val disclaimerAccepted: Boolean = false,
    val firstRunGuideCompleted: Boolean = false,
    val alarmBackend: ReminderAlarmBackend = ReminderAlarmBackend.AppAlarmClock,
    val alarmRingtoneUri: String? = null,
    val alarmAlertMode: AlarmAlertMode = AlarmAlertMode.RingAndVibrate,
    val alarmRingDurationSeconds: Int = DEFAULT_APP_ALARM_RING_DURATION_SECONDS,
    val alarmRepeatIntervalSeconds: Int = DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS,
    val alarmRepeatCount: Int = DEFAULT_APP_ALARM_REPEAT_COUNT,
    val autoSilence: AutoSilencePreferences = AutoSilencePreferences(),
    val classNotice: ClassNoticePreferences = ClassNoticePreferences(),
    val alarmPreNotice: AlarmPreNoticePreferences = AlarmPreNoticePreferences(),
    val autoSilenceSession: AutoSilenceSession = AutoSilenceSession(),
    val autoUpdateEnabled: Boolean = true,
    val pluginAutoUpdateCheckEnabled: Boolean = true,
    val pluginUpdateBadgeEnabled: Boolean = true,
    val pluginUpdateCheckIntervalHours: Int = 6,
    val betaUpdatesEnabled: Boolean = false,
    val lastSeenVersionCode: Int = 0,
    val appTimeZoneId: String? = null,
    val ignoredUpdateVersionCode: Int? = null,
    val updateNoticeVersionCode: Int = 0,
    val updateNoticeVersionName: String = "",
    val mutedUpdateVersionCode: Int? = null,
    val pluginRegistryRepo: String = DEFAULT_PLUGIN_REGISTRY_REPO,
    /**
     * Ordered registry sources; first duplicate wins. Removing the public source leaves only
     * user-selected sources.
     */
    val pluginSources: List<String> = listOf(DEFAULT_PLUGIN_REGISTRY_REPO),
    val componentSources: List<String> = listOf(DEFAULT_COMPONENT_REGISTRY_REPO),
    val pluginMarketCacheJson: String = "",
    val pluginMarketCachedAtMillis: Long = 0L,
    val pluginMarketCachedRegistry: String = "",
    val componentMarketIndexUrl: String = DEFAULT_COMPONENT_MARKET_INDEX_URL,
    val privateFilesProviderEnabled: Boolean = false,
    val webDavUrl: String = DEFAULT_WEBDAV_URL,
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val aiImportApiUrl: String = "",
    val aiImportApiKey: String = "",
    val aiImportModel: String = "",
    val aiImportTimeoutSeconds: Int = DEFAULT_AI_IMPORT_TIMEOUT_SECONDS,
    /** True once the persisted prefs have been read at least once. False = still loading. */
    val loaded: Boolean = false,
)

interface UserPreferencesRepository {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeAccent(accent: ThemeAccent)

    suspend fun setThemeCustomColor(argb: Int)

    /** Record or revoke manual vendor-permission confirmation. */
    suspend fun setVendorPermissionAck(key: String, acked: Boolean)

    suspend fun setWidgetPinUnsupportedOnDevice(unsupported: Boolean)
    suspend fun setAppLanguage(language: AppLanguage)
    suspend fun setTermStartDate(date: LocalDate?)

    suspend fun setTermStartUserDecided(decided: Boolean)
    suspend fun setAdvancedToolsEnabled(enabled: Boolean)
    suspend fun setScheduleCourseTextSizeSp(sizeSp: Int)
    suspend fun setScheduleCourseTextColorArgb(argb: Long)
    suspend fun setScheduleExamTextSizeSp(sizeSp: Int)
    suspend fun setScheduleExamTextColorArgb(argb: Long)
    suspend fun setScheduleHeaderTextSizeSp(sizeSp: Int)
    suspend fun setScheduleHeaderTextColorArgb(argb: Long)
    suspend fun setScheduleTodayHeaderBackgroundColorArgb(argb: Long)
    suspend fun setScheduleTextHorizontalCenter(enabled: Boolean)
    suspend fun setScheduleTextVerticalCenter(enabled: Boolean)
    suspend fun setScheduleAutoShrinkLongTitles(enabled: Boolean)

    suspend fun setScheduleTruncationEllipsis(enabled: Boolean)

    suspend fun setClassNoticeEnabled(enabled: Boolean)

    suspend fun setClassNoticeAdvanceMinutes(minutes: Int)

    suspend fun setClassNoticeHeadsUpEnabled(enabled: Boolean)
    suspend fun setClassNoticeVibrationEnabled(enabled: Boolean)

    suspend fun setClassNoticeLockScreenEnabled(enabled: Boolean)

    suspend fun setClassNoticeFocusNotificationEnabled(enabled: Boolean)

    suspend fun setClassNoticeSkin(skin: ClassNoticeSkin)

    suspend fun setClassNoticeAnimation(animation: ClassNoticeAnimation)

    suspend fun setClassNoticeBlurEnabled(enabled: Boolean)

    suspend fun setClassNoticeBlurStrength(strength: Int)

    suspend fun setClassNoticeBannerDurationSeconds(seconds: Int)

    suspend fun setAlarmPreNoticeEnabled(enabled: Boolean)

    suspend fun setAlarmPreNoticeAdvanceMinutes(minutes: Int)
    suspend fun setScheduleCourseCornerRadiusDp(radiusDp: Int)
    suspend fun setScheduleCourseCardHeightDp(heightDp: Int)
    suspend fun setScheduleOpacityPercent(percent: Int)
    suspend fun setScheduleInactiveCourseOpacityPercent(percent: Int)
    suspend fun setScheduleGridBorderColorArgb(argb: Long)
    suspend fun setScheduleGridBorderOpacityPercent(percent: Int)
    suspend fun setScheduleGridBorderWidthDp(widthDp: Float)
    suspend fun setScheduleGridBorderDashed(enabled: Boolean)
    suspend fun setScheduleBackgroundColorArgb(argb: Long)
    suspend fun setScheduleBackgroundImageUri(uri: String)

    suspend fun setScheduleBackgroundImageTransparencyPercent(percent: Int)
    suspend fun clearScheduleBackgroundImage()
    suspend fun setScheduleBackgroundUseHeaderColor()
    suspend fun setScheduleCustomColorsAdaptToTheme(enabled: Boolean)
    suspend fun setScheduleNodeColumnTimeEnabled(enabled: Boolean)
    suspend fun setTodayOverviewEnabled(enabled: Boolean)
    suspend fun setScheduleSaturdayVisible(visible: Boolean)
    suspend fun setScheduleWeekendVisible(visible: Boolean)

    suspend fun setScheduleRowFitMode(mode: ScheduleRowFitMode)

    suspend fun setScheduleWeekStartDay(day: WeekStartDay)

    suspend fun setCourseDragEnabled(enabled: Boolean)
    suspend fun setSchedulePinchZoomEnabled(enabled: Boolean)
    suspend fun setScheduleLocationVisible(visible: Boolean)
    suspend fun setScheduleTeacherVisible(visible: Boolean)
    suspend fun setTotalScheduleDisplayEnabled(enabled: Boolean)
    suspend fun setPluginEnabled(pluginKey: String, enabled: Boolean)
    suspend fun seedEnabledPlugins(pluginKeys: Set<String>)
    suspend fun upsertTemporaryScheduleOverride(override: TemporaryScheduleOverride)
    suspend fun removeTemporaryScheduleOverride(id: String)
    suspend fun clearTemporaryScheduleOverrides()
    suspend fun setHolidayCalendarBuiltInEnabled(enabled: Boolean)
    suspend fun upsertHolidayCalendarEntry(entry: HolidayCalendarEntry)
    suspend fun removeHolidayCalendarEntry(date: String)
    suspend fun clearHolidayCalendarEntries()

    suspend fun putSyncedHolidayYears(years: List<SyncedHolidayYear>)

    suspend fun clearSyncedHolidayYears()

    suspend fun setSkipRemindersOnHoliday(enabled: Boolean)

    /** Silent-guard setting; see [UserPreferences.alarmKeepAliveEnabled]. */
    suspend fun setAlarmKeepAliveEnabled(enabled: Boolean)

    /** Remember the startup request even after denial. */
    suspend fun markNotificationPermissionStartupAsked()

    /** Remember the startup chip prompt. */
    suspend fun markIslandStartupPromptShown()

    /** Set or remove date muting for course reminders. */
    suspend fun setReminderMuted(date: String, muted: Boolean)
    suspend fun setDebugForcedDateTime(dateTime: LocalDateTime?)
    suspend fun setDisclaimerAccepted(accepted: Boolean)

    suspend fun setFirstRunGuideCompleted(completed: Boolean)
    suspend fun setAlarmBackend(backend: ReminderAlarmBackend)
    suspend fun setAlarmRingtoneUri(uri: String?)
    suspend fun setAlarmAlertMode(mode: AlarmAlertMode)
    suspend fun setAlarmRingDurationSeconds(seconds: Int)
    suspend fun setAlarmRepeatIntervalSeconds(seconds: Int)
    suspend fun setAlarmRepeatCount(count: Int)
    suspend fun markAlarmPollAt(millis: Long)
    suspend fun tryClaimAlarmPoll(nowMillis: Long, minIntervalMillis: Long): Boolean
    suspend fun setAutoSilenceEnabled(enabled: Boolean)
    suspend fun setAutoSilenceMode(mode: AutoSilenceMode)
    suspend fun saveAutoSilenceSession(session: AutoSilenceSession)
    suspend fun clearAutoSilenceSession(suppressedUntilMillis: Long)
    suspend fun setBetaUpdatesEnabled(enabled: Boolean)

    suspend fun setLastSeenVersionCode(versionCode: Int)

    suspend fun setAppTimeZoneId(zoneId: String?)

    suspend fun setAutoUpdateEnabled(enabled: Boolean)
    suspend fun setPluginUpdateOptions(autoCheck: Boolean, badge: Boolean, intervalHours: Int)
    suspend fun setIgnoredUpdateVersionCode(versionCode: Int?)

    suspend fun setUpdateNotice(versionCode: Int, versionName: String)

    suspend fun clearUpdateNotice()

    suspend fun setMutedUpdateVersionCode(versionCode: Int?)
    suspend fun setPluginRegistryRepo(repo: String)
    suspend fun setPluginSources(sources: List<String>)
    suspend fun setComponentSources(sources: List<String>)
    suspend fun setPluginMarketCache(json: String, atMillis: Long, registry: String)
    suspend fun setComponentMarketIndexUrl(url: String)
    suspend fun setPrivateFilesProviderEnabled(enabled: Boolean)
    suspend fun setWebDavSettings(url: String, username: String, password: String)
    suspend fun setAiImportSettings(apiUrl: String, apiKey: String, model: String, timeoutSeconds: Int)
    suspend fun resetScheduleAppearanceAndDisplay()
    suspend fun resetAllSettings()
}

/** Require [version] and [stores] so unrelated JSON cannot be accepted as an empty backup. */
@Serializable
data class AppBackupPayload(
    @SerialName("version") val version: Int,
    @SerialName("createdAt") val createdAt: Long? = null,
    @SerialName("stores") val stores: List<PreferencesStoreSnapshot>,
) {
    fun store(name: String): PreferencesStoreSnapshot? = stores.firstOrNull { it.storeName == name }

    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_EXTENSION = ".json"
    }
}

object AppBackupStores {
    const val USER_PREFERENCES = "user_preferences"
    const val SCHEDULE = "schedule_store"
    const val MANUAL_COURSES = "manual_courses_store"
    const val COURSE_NOTES = "course_notes_store"
    const val TERM_PROFILES = "term_profiles"
    const val WIDGET_PREFERENCES = "widget_preferences"
    const val REMINDERS = "reminder_store"
    const val PLUGIN_REGISTRY = "plugin_registry_store"
    const val PLUGIN_COMPONENTS = "plugin_component_store"
    const val SCHEDULE_EVENTS = "schedule_events_store"
    const val MEMOS = "memo_store"

    val ALL: Set<String> = setOf(
        USER_PREFERENCES,
        SCHEDULE,
        MANUAL_COURSES,
        COURSE_NOTES,
        TERM_PROFILES,
        WIDGET_PREFERENCES,
        REMINDERS,
        PLUGIN_REGISTRY,
        PLUGIN_COMPONENTS,
        SCHEDULE_EVENTS,
        MEMOS,
    )
}

@Serializable
data class PreferencesStoreSnapshot(
    @SerialName("store") val storeName: String,
    @SerialName("entries") val entries: List<PreferencesBackupEntry>,
)

@Serializable
data class PreferencesBackupEntry(
    @SerialName("name") val name: String,
    @SerialName("type") val type: PreferencesBackupValueType,
    @SerialName("string") val stringValue: String? = null,
    @SerialName("strings") val stringSetValue: Set<String>? = null,
    @SerialName("int") val intValue: Int? = null,
    @SerialName("long") val longValue: Long? = null,
    @SerialName("float") val floatValue: Float? = null,
    @SerialName("double") val doubleValue: Double? = null,
    @SerialName("boolean") val booleanValue: Boolean? = null,
)

@Serializable
enum class PreferencesBackupValueType {
    @SerialName("string")
    String,

    @SerialName("string_set")
    StringSet,

    @SerialName("int")
    Int,

    @SerialName("long")
    Long,

    @SerialName("float")
    Float,

    @SerialName("double")
    Double,

    @SerialName("boolean")
    Boolean,
}

fun Preferences.toBackupEntries(): List<PreferencesBackupEntry> = asMap()
    .mapNotNull { (key, value) -> value.toBackupEntry(key.name) }
    .sortedBy { it.name }

fun excludeBackupEntries(
    entries: List<PreferencesBackupEntry>,
    excludedKeyNames: Set<String>,
): List<PreferencesBackupEntry> =
    if (excludedKeyNames.isEmpty()) entries else entries.filterNot { it.name in excludedKeyNames }

/**
 * Restore snapshot entries except [preservedKeyNames], which retain [localEntries] or remain
 * unset.
 */
fun mergeRestoredBackupEntries(
    snapshotEntries: List<PreferencesBackupEntry>,
    localEntries: List<PreferencesBackupEntry>,
    preservedKeyNames: Set<String>,
): List<PreferencesBackupEntry> {
    if (preservedKeyNames.isEmpty()) return snapshotEntries
    val preserved = localEntries.filter { it.name in preservedKeyNames }
    return excludeBackupEntries(snapshotEntries, preservedKeyNames) + preserved
}

suspend fun DataStore<Preferences>.exportSnapshot(
    storeName: String,
    excludedKeyNames: Set<String> = emptySet(),
): PreferencesStoreSnapshot = PreferencesStoreSnapshot(
    storeName = storeName,
    entries = excludeBackupEntries(data.first().toBackupEntries(), excludedKeyNames),
)

suspend fun DataStore<Preferences>.restoreSnapshot(
    snapshot: PreferencesStoreSnapshot,
    preservedKeyNames: Set<String> = emptySet(),
) {
    edit { preferences ->
        val entries = mergeRestoredBackupEntries(
            snapshotEntries = snapshot.entries,
            localEntries = preferences.toBackupEntries(),
            preservedKeyNames = preservedKeyNames,
        )
        preferences.clear()
        entries.forEach(preferences::restoreEntry)
    }
}

/** Keep [previousUri] permission when restored data still references it. */
fun shouldReleasePersistedUriPermission(previousUri: String?, restoredUri: String?): Boolean =
    !previousUri.isNullOrBlank() && previousUri != restoredUri

private fun Any.toBackupEntry(name: String): PreferencesBackupEntry? = when (this) {
    is String -> PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.String,
        stringValue = this,
    )

    is Set<*> -> PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.StringSet,
        stringSetValue = filterIsInstance<String>().toSet(),
    )

    is Int -> PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.Int,
        intValue = this,
    )

    is Long -> PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.Long,
        longValue = this,
    )

    is Float -> PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.Float,
        floatValue = this,
    )

    is Double -> PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.Double,
        doubleValue = this,
    )

    is Boolean -> PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.Boolean,
        booleanValue = this,
    )

    else -> null
}

private fun MutablePreferences.restoreEntry(entry: PreferencesBackupEntry) {
    when (entry.type) {
        PreferencesBackupValueType.String -> entry.stringValue?.let {
            this[stringPreferencesKey(entry.name)] = it
        }

        PreferencesBackupValueType.StringSet -> {
            this[stringSetPreferencesKey(entry.name)] = entry.stringSetValue.orEmpty()
        }

        PreferencesBackupValueType.Int -> entry.intValue?.let {
            this[intPreferencesKey(entry.name)] = it
        }

        PreferencesBackupValueType.Long -> entry.longValue?.let {
            this[longPreferencesKey(entry.name)] = it
        }

        PreferencesBackupValueType.Float -> entry.floatValue?.let {
            this[floatPreferencesKey(entry.name)] = it
        }

        PreferencesBackupValueType.Double -> entry.doubleValue?.let {
            this[doublePreferencesKey(entry.name)] = it
        }

        PreferencesBackupValueType.Boolean -> entry.booleanValue?.let {
            this[booleanPreferencesKey(entry.name)] = it
        }
    }
}

fun UserPreferences.reminderDayPolicy(): ReminderDayPolicy = ReminderDayPolicy(
    skipOnHoliday = skipRemindersOnHoliday,
    mutedDates = reminderMutedDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
)
