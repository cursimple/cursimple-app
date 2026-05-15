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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

enum class ThemeMode { System, Light, Dark }

enum class ThemeAccent { Green, Blue, Purple, Orange, Pink }

enum class ScheduleBackgroundType { Color, Image, Header }

const val DEFAULT_PLUGIN_MARKET_INDEX_URL =
    "https://raw.githubusercontent.com/cursimple/cursimple-plugins/refs/heads/main/manifest.json"

const val DEFAULT_COMPONENT_MARKET_INDEX_URL =
    "https://raw.githubusercontent.com/cursimple/cursimple-components/refs/heads/main/manifest.json"

const val DEFAULT_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"

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
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.Light,
    val themeAccent: ThemeAccent = ThemeAccent.Green,
    val termStartDate: LocalDate? = null,
    val developerModeEnabled: Boolean = false,
    val scheduleTextStyle: ScheduleTextStylePreferences = ScheduleTextStylePreferences(),
    val scheduleCardStyle: ScheduleCardStylePreferences = ScheduleCardStylePreferences(),
    val scheduleBackground: ScheduleBackgroundPreferences = ScheduleBackgroundPreferences(),
    val scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    val enabledPluginIds: Set<String> = emptySet(),
    val pluginsSeeded: Boolean = false,
    val temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    val debugForcedDateTime: LocalDateTime? = null,
    val disclaimerAccepted: Boolean = false,
    val alarmBackend: ReminderAlarmBackend = ReminderAlarmBackend.AppAlarmClock,
    val alarmRingtoneUri: String? = null,
    val alarmAlertMode: AlarmAlertMode = AlarmAlertMode.RingAndVibrate,
    val alarmRingDurationSeconds: Int = DEFAULT_APP_ALARM_RING_DURATION_SECONDS,
    val alarmRepeatIntervalSeconds: Int = DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS,
    val alarmRepeatCount: Int = DEFAULT_APP_ALARM_REPEAT_COUNT,
    val lastAlarmPollAtMillis: Long = 0L,
    val autoUpdateEnabled: Boolean = false,
    val ignoredUpdateVersionCode: Int? = null,
    val pluginMarketIndexUrl: String = DEFAULT_PLUGIN_MARKET_INDEX_URL,
    val componentMarketIndexUrl: String = DEFAULT_COMPONENT_MARKET_INDEX_URL,
    val privateFilesProviderEnabled: Boolean = false,
    val webDavUrl: String = DEFAULT_WEBDAV_URL,
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val aiImportApiUrl: String = "",
    val aiImportApiKey: String = "",
    val aiImportModel: String = "",
    /** True once the persisted prefs have been read at least once. False = still loading. */
    val loaded: Boolean = false,
)

interface UserPreferencesRepository {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeAccent(accent: ThemeAccent)
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
    suspend fun setScheduleNodeColumnTimeEnabled(enabled: Boolean)
    suspend fun setScheduleSaturdayVisible(visible: Boolean)
    suspend fun setScheduleWeekendVisible(visible: Boolean)
    suspend fun setScheduleLocationVisible(visible: Boolean)
    suspend fun setScheduleLocationPrefixAtEnabled(enabled: Boolean)
    suspend fun setScheduleTeacherVisible(visible: Boolean)
    suspend fun setTotalScheduleDisplayEnabled(enabled: Boolean)
    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean)
    suspend fun seedEnabledPlugins(pluginIds: Set<String>)
    suspend fun upsertTemporaryScheduleOverride(override: TemporaryScheduleOverride)
    suspend fun removeTemporaryScheduleOverride(id: String)
    suspend fun clearTemporaryScheduleOverrides()
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
    suspend fun setAutoUpdateEnabled(enabled: Boolean)
    suspend fun setIgnoredUpdateVersionCode(versionCode: Int?)
    suspend fun setPluginMarketIndexUrl(url: String)
    suspend fun setComponentMarketIndexUrl(url: String)
    suspend fun setPrivateFilesProviderEnabled(enabled: Boolean)
    suspend fun setWebDavSettings(url: String, username: String, password: String)
    suspend fun setAiImportSettings(apiUrl: String, apiKey: String, model: String)
    suspend fun resetScheduleAppearanceAndDisplay()
    suspend fun resetAllSettings()
}

@Serializable
data class AppBackupPayload(
    @SerialName("version") val version: Int = CURRENT_VERSION,
    @SerialName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("stores") val stores: List<PreferencesStoreSnapshot> = emptyList(),
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
    const val TERM_PROFILES = "term_profiles"
    const val WIDGET_PREFERENCES = "widget_preferences"
    const val REMINDERS = "reminder_store"
    const val PLUGIN_REGISTRY = "plugin_registry_store"
    const val PLUGIN_COMPONENTS = "plugin_component_store"
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

suspend fun DataStore<Preferences>.exportSnapshot(storeName: String): PreferencesStoreSnapshot {
    val entries = data.first()
        .asMap()
        .mapNotNull { (key, value) -> value.toBackupEntry(key.name) }
        .sortedBy { it.name }
    return PreferencesStoreSnapshot(storeName = storeName, entries = entries)
}

suspend fun DataStore<Preferences>.restoreSnapshot(snapshot: PreferencesStoreSnapshot) {
    edit { preferences ->
        preferences.clear()
        snapshot.entries.forEach(preferences::restoreEntry)
    }
}

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
