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

enum class ThemeAccent { Green, Blue, Purple, Orange, Pink }

/** 界面语言。[System] 跟随系统设置，其余为用户显式选定。 */
enum class AppLanguage(val tag: String) {
    System(""),
    Chinese("zh-CN"),
    English("en"),
}

enum class ScheduleBackgroundType { Color, Image, Header }

/** 上课时段自动静音采用的手段。 */
enum class AutoSilenceMode {
    /** 铃声模式切到仅震动，只需 MODIFY_AUDIO_SETTINGS。 */
    Vibrate,

    /** 铃声模式切到静音，需要勿扰访问授权。 */
    Silent,

    /** 打开勿扰（仅优先级），需要勿扰访问授权。 */
    DoNotDisturb,
}

/** 铃声模式取值与 AudioManager 保持一致，UNKNOWN 表示没有记录。 */
object RingerModeValues {
    const val UNKNOWN = -1
    const val SILENT = 0
    const val VIBRATE = 1
    const val NORMAL = 2
}

/** 勿扰级别取值与 NotificationManager 保持一致，UNKNOWN 表示没有记录。 */
object InterruptionFilterValues {
    const val UNKNOWN = 0
    const val ALL = 1
    const val PRIORITY = 2
    const val NONE = 3
    const val ALARMS = 4
}

data class AutoSilencePreferences(
    val enabled: Boolean = false,
    val mode: AutoSilenceMode = AutoSilenceMode.Vibrate,
)

/**
 * 一次自动静音的现场记录。
 *
 * [previousRingerMode] 与 [previousInterruptionFilter] 是切换之前手机的状态，下课后照此恢复。
 * [appliedRingerMode] 与 [appliedInterruptionFilter] 是本次实际写进系统的值，恢复前用来确认
 * 用户中途没有手动改过。[plannedEndAtMillis] 是本次静音的兜底截止时刻，即使课表数据读不出来，
 * 超过它也一律恢复。[suppressedUntilMillis] 之前不再重新静音，供用户手动恢复后使用。
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

const val DEFAULT_COMPONENT_MARKET_INDEX_URL =
    "https://raw.githubusercontent.com/cursimple/cursimple-components/refs/heads/main/manifest.json"

const val DEFAULT_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"

const val WEBDAV_PASSWORD_PREFERENCE_KEY = "webdav_password"
const val AI_IMPORT_API_KEY_PREFERENCE_KEY = "ai_import_api_key"

/**
 * 用户设置里可直接用于认证的键。
 * 导出备份时不写入这些键，恢复备份时也不覆盖本机现值。
 */
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
    val fullCenter: Boolean = false,
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

data class ScheduleBackgroundPreferences(
    val type: ScheduleBackgroundType = DEFAULT_BACKGROUND_TYPE,
    val colorArgb: Long = DEFAULT_BACKGROUND_COLOR_ARGB,
    val imageUri: String? = null,
) {
    companion object {
        val DEFAULT_BACKGROUND_TYPE = ScheduleBackgroundType.Header
        const val DEFAULT_BACKGROUND_COLOR_ARGB = 0xFFFFFFFFL
        fun coerceArgb(value: Long): Long = value and 0xFFFF_FFFFL
    }
}

data class ScheduleDisplayPreferences(
    val nodeColumnTimeEnabled: Boolean = true,
    val saturdayVisible: Boolean = true,
    val weekendVisible: Boolean = true,
    val locationVisible: Boolean = true,
    val locationPrefixAtEnabled: Boolean = true,
    val teacherVisible: Boolean = true,
    val totalScheduleDisplayEnabled: Boolean = true,
    /** 一周从哪天开始显示，不影响教学周编号。 */
    val weekStartDay: WeekStartDay = WeekStartDay.Monday,
    /** 允许在课表上拖动调整课程。默认关闭，避免误触改动课表。 */
    val courseDragEnabled: Boolean = false,
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
    val appLanguage: AppLanguage = AppLanguage.System,
    val termStartDate: LocalDate? = null,
    val developerModeEnabled: Boolean = false,
    val scheduleTextStyle: ScheduleTextStylePreferences = ScheduleTextStylePreferences(),
    val scheduleCardStyle: ScheduleCardStylePreferences = ScheduleCardStylePreferences(),
    val scheduleBackground: ScheduleBackgroundPreferences = ScheduleBackgroundPreferences(),
    val scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    val scheduleCustomColorsAdaptToTheme: Boolean = false,
    val enabledPluginIds: Set<String> = emptySet(),
    val pluginsSeeded: Boolean = false,
    val temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    val holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings(),
    /** 放假当天是否跳过提醒。默认照常提醒，安静与否交给用户决定。 */
    val skipRemindersOnHoliday: Boolean = false,
    /** 单独静音的日期，ISO 日期字符串。 */
    val reminderMutedDates: Set<String> = emptySet(),
    val debugForcedDateTime: LocalDateTime? = null,
    val disclaimerAccepted: Boolean = false,
    val alarmBackend: ReminderAlarmBackend = ReminderAlarmBackend.AppAlarmClock,
    val alarmRingtoneUri: String? = null,
    val alarmAlertMode: AlarmAlertMode = AlarmAlertMode.RingAndVibrate,
    val alarmRingDurationSeconds: Int = DEFAULT_APP_ALARM_RING_DURATION_SECONDS,
    val alarmRepeatIntervalSeconds: Int = DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS,
    val alarmRepeatCount: Int = DEFAULT_APP_ALARM_REPEAT_COUNT,
    val lastAlarmPollAtMillis: Long = 0L,
    val autoSilence: AutoSilencePreferences = AutoSilencePreferences(),
    val autoSilenceSession: AutoSilenceSession = AutoSilenceSession(),
    val autoUpdateEnabled: Boolean = false,
    val ignoredUpdateVersionCode: Int? = null,
    val pluginRegistryRepo: String = DEFAULT_PLUGIN_REGISTRY_REPO,
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
    suspend fun setAppLanguage(language: AppLanguage)
    suspend fun setTermStartDate(date: LocalDate?)
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    suspend fun setScheduleCourseTextSizeSp(sizeSp: Int)
    suspend fun setScheduleCourseTextColorArgb(argb: Long)
    suspend fun setScheduleExamTextSizeSp(sizeSp: Int)
    suspend fun setScheduleExamTextColorArgb(argb: Long)
    suspend fun setScheduleHeaderTextSizeSp(sizeSp: Int)
    suspend fun setScheduleHeaderTextColorArgb(argb: Long)
    suspend fun setScheduleTodayHeaderBackgroundColorArgb(argb: Long)
    suspend fun setScheduleTextHorizontalCenter(enabled: Boolean)
    suspend fun setScheduleTextVerticalCenter(enabled: Boolean)
    suspend fun setScheduleTextFullCenter(enabled: Boolean)
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
    suspend fun clearScheduleBackgroundImage()
    suspend fun setScheduleBackgroundUseHeaderColor()
    suspend fun setScheduleCustomColorsAdaptToTheme(enabled: Boolean)
    suspend fun setScheduleNodeColumnTimeEnabled(enabled: Boolean)
    suspend fun setScheduleSaturdayVisible(visible: Boolean)
    suspend fun setScheduleWeekendVisible(visible: Boolean)

    suspend fun setScheduleWeekStartDay(day: WeekStartDay)

    suspend fun setCourseDragEnabled(enabled: Boolean)
    suspend fun setScheduleLocationVisible(visible: Boolean)
    suspend fun setScheduleLocationPrefixAtEnabled(enabled: Boolean)
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

    /** 写入同步下来的放假安排，按年覆盖同一年的旧数据。 */
    suspend fun putSyncedHolidayYears(years: List<SyncedHolidayYear>)

    suspend fun clearSyncedHolidayYears()

    suspend fun setSkipRemindersOnHoliday(enabled: Boolean)

    /** 把某一天设为静音或取消静音，当天不再下发任何课程提醒。 */
    suspend fun setReminderMuted(date: String, muted: Boolean)
    suspend fun setDebugForcedDateTime(dateTime: LocalDateTime?)
    suspend fun setDisclaimerAccepted(accepted: Boolean)
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
    suspend fun setAutoUpdateEnabled(enabled: Boolean)
    suspend fun setIgnoredUpdateVersionCode(versionCode: Int?)
    suspend fun setPluginRegistryRepo(repo: String)
    suspend fun setPluginMarketCache(json: String, atMillis: Long, registry: String)
    suspend fun setComponentMarketIndexUrl(url: String)
    suspend fun setPrivateFilesProviderEnabled(enabled: Boolean)
    suspend fun setWebDavSettings(url: String, username: String, password: String)
    suspend fun setAiImportSettings(apiUrl: String, apiKey: String, model: String, timeoutSeconds: Int)
    suspend fun resetScheduleAppearanceAndDisplay()
    suspend fun resetAllSettings()
}

/**
 * [version] 与 [stores] 没有默认值：缺了它们的 JSON 不是备份文件，
 * 有默认值会让任意 JSON 都解析成一份没有内容的备份，恢复流程随后报成功却什么都没写。
 */
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

    /** 备份里出现过的全部存储名，用于判断一份文件是否真的属于本应用。 */
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

/** 去掉 [excludedKeyNames] 命中的条目，其余条目原样保留。 */
fun excludeBackupEntries(
    entries: List<PreferencesBackupEntry>,
    excludedKeyNames: Set<String>,
): List<PreferencesBackupEntry> =
    if (excludedKeyNames.isEmpty()) entries else entries.filterNot { it.name in excludedKeyNames }

/**
 * 计算恢复时最终写回的条目。
 *
 * [preservedKeyNames] 命中的键一律取本机现值 [localEntries]：备份里的同名条目（老备份仍带着）被丢弃，
 * 本机没有该键时结果里也不出现，保持未设置状态。其余键完全按 [snapshotEntries] 覆盖。
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

/**
 * 备份恢复后是否释放 [previousUri] 的持久化读取授权。
 * 恢复结果仍指向同一个 URI 时保留授权，否则该 URI 之后无法再被读取。
 */
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

/** 把偏好里的假日与静音设置换成提醒侧的判定策略。 */
fun UserPreferences.reminderDayPolicy(): ReminderDayPolicy = ReminderDayPolicy(
    skipOnHoliday = skipRemindersOnHoliday,
    mutedDates = reminderMutedDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
)
