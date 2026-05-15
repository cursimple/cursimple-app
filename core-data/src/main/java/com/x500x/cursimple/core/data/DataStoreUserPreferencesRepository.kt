package com.x500x.cursimple.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_COUNT
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_RING_DURATION_SECONDS
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class DataStoreUserPreferencesRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : UserPreferencesRepository {
    private val appContext = context.applicationContext
    private val store = appContext.userPreferencesDataStore

    override val preferencesFlow: Flow<UserPreferences> = store.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.Light,
            themeAccent = prefs[KEY_THEME_ACCENT]
                ?.let { runCatching { ThemeAccent.valueOf(it) }.getOrNull() }
                ?: ThemeAccent.Green,
            termStartDate = prefs[KEY_TERM_START_EPOCH_DAY]?.let(LocalDate::ofEpochDay),
            developerModeEnabled = prefs[KEY_DEVELOPER_MODE] ?: false,
            scheduleTextStyle = prefs.toScheduleTextStyle(),
            scheduleCardStyle = prefs.toScheduleCardStyle(),
            scheduleBackground = prefs.toScheduleBackground(),
            scheduleDisplay = prefs.toScheduleDisplay(),
            enabledPluginIds = prefs[KEY_ENABLED_PLUGIN_IDS].orEmpty().toSet(),
            pluginsSeeded = prefs[KEY_PLUGINS_SEEDED] ?: false,
            temporaryScheduleOverrides = decodeTemporaryScheduleOverrides(
                prefs[KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON],
            ),
            debugForcedDateTime = prefs[KEY_DEBUG_FORCED_DATETIME]?.let { raw ->
                runCatching { LocalDateTime.parse(raw) }.getOrNull()
            },
            disclaimerAccepted = prefs[KEY_DISCLAIMER_ACCEPTED] ?: false,
            alarmBackend = prefs[KEY_ALARM_BACKEND]
                ?.let { runCatching { ReminderAlarmBackend.valueOf(it) }.getOrNull() }
                ?: ReminderAlarmBackend.AppAlarmClock,
            alarmRingtoneUri = prefs[KEY_ALARM_RINGTONE_URI]?.takeIf { it.isNotBlank() },
            alarmAlertMode = prefs[KEY_ALARM_ALERT_MODE]
                ?.let { runCatching { AlarmAlertMode.valueOf(it) }.getOrNull() }
                ?: AlarmAlertMode.RingAndVibrate,
            alarmRingDurationSeconds = (prefs[KEY_ALARM_RING_DURATION_SECONDS] ?: DEFAULT_RING_DURATION_SECONDS)
                .coerceIn(MIN_RING_DURATION_SECONDS, MAX_RING_DURATION_SECONDS),
            alarmRepeatIntervalSeconds = (prefs[KEY_ALARM_REPEAT_INTERVAL_SECONDS] ?: DEFAULT_REPEAT_INTERVAL_SECONDS)
                .coerceIn(MIN_REPEAT_INTERVAL_SECONDS, MAX_REPEAT_INTERVAL_SECONDS),
            alarmRepeatCount = (prefs[KEY_ALARM_REPEAT_COUNT] ?: DEFAULT_REPEAT_COUNT)
                .coerceIn(MIN_REPEAT_COUNT, MAX_REPEAT_COUNT),
            lastAlarmPollAtMillis = prefs[KEY_LAST_ALARM_POLL_AT_MILLIS] ?: 0L,
            autoUpdateEnabled = prefs[KEY_AUTO_UPDATE_ENABLED] ?: false,
            ignoredUpdateVersionCode = prefs[KEY_IGNORED_UPDATE_VERSION_CODE],
            pluginMarketIndexUrl = prefs[KEY_PLUGIN_MARKET_INDEX_URL]
                ?.takeIf(String::isNotBlank)
                ?: DEFAULT_PLUGIN_MARKET_INDEX_URL,
            componentMarketIndexUrl = prefs[KEY_COMPONENT_MARKET_INDEX_URL]
                ?.takeIf(String::isNotBlank)
                ?: DEFAULT_COMPONENT_MARKET_INDEX_URL,
            privateFilesProviderEnabled = prefs[KEY_PRIVATE_FILES_PROVIDER_ENABLED] ?: false,
            webDavUrl = prefs[KEY_WEBDAV_URL]
                ?.takeIf(String::isNotBlank)
                ?: DEFAULT_WEBDAV_URL,
            webDavUsername = prefs[KEY_WEBDAV_USERNAME].orEmpty(),
            webDavPassword = prefs[KEY_WEBDAV_PASSWORD].orEmpty(),
            aiImportApiUrl = prefs[KEY_AI_IMPORT_API_URL].orEmpty(),
            aiImportApiKey = prefs[KEY_AI_IMPORT_API_KEY].orEmpty(),
            aiImportModel = prefs[KEY_AI_IMPORT_MODEL].orEmpty(),
            loaded = true,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setThemeAccent(accent: ThemeAccent) {
        store.edit { prefs -> prefs[KEY_THEME_ACCENT] = accent.name }
    }

    override suspend fun setTermStartDate(date: LocalDate?) {
        store.edit { prefs ->
            if (date == null) {
                prefs.remove(KEY_TERM_START_EPOCH_DAY)
            } else {
                prefs[KEY_TERM_START_EPOCH_DAY] = date.toEpochDay()
            }
        }
    }

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_DEVELOPER_MODE] = enabled }
    }

    override suspend fun setScheduleCourseTextSizeSp(sizeSp: Int) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_COURSE_TEXT_SIZE_SP] = ScheduleTextStylePreferences.coerceTextSizeSp(sizeSp) }
    }

    override suspend fun setScheduleCourseTextColorArgb(argb: Long) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_COURSE_TEXT_COLOR_ARGB] = ScheduleTextStylePreferences.coerceArgb(argb) }
    }

    override suspend fun setScheduleExamTextSizeSp(sizeSp: Int) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_EXAM_TEXT_SIZE_SP] = ScheduleTextStylePreferences.coerceTextSizeSp(sizeSp) }
    }

    override suspend fun setScheduleExamTextColorArgb(argb: Long) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_EXAM_TEXT_COLOR_ARGB] = ScheduleTextStylePreferences.coerceArgb(argb) }
    }

    override suspend fun setScheduleHeaderTextSizeSp(sizeSp: Int) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_HEADER_TEXT_SIZE_SP] = ScheduleTextStylePreferences.coerceTextSizeSp(sizeSp) }
    }

    override suspend fun setScheduleHeaderTextColorArgb(argb: Long) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_HEADER_TEXT_COLOR_ARGB] = ScheduleTextStylePreferences.coerceArgb(argb) }
    }

    override suspend fun setScheduleTodayHeaderBackgroundColorArgb(argb: Long) {
        store.edit { prefs ->
            prefs[KEY_SCHEDULE_TODAY_HEADER_BACKGROUND_COLOR_ARGB] =
                ScheduleTextStylePreferences.coerceArgb(argb)
        }
    }

    override suspend fun setScheduleTextHorizontalCenter(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_TEXT_HORIZONTAL_CENTER] = enabled }
    }

    override suspend fun setScheduleTextVerticalCenter(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_TEXT_VERTICAL_CENTER] = enabled }
    }

    override suspend fun setScheduleTextFullCenter(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_TEXT_FULL_CENTER] = enabled }
    }

    override suspend fun setScheduleCourseCornerRadiusDp(radiusDp: Int) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_COURSE_CORNER_RADIUS_DP] = ScheduleCardStylePreferences.coerceCornerRadiusDp(radiusDp) }
    }

    override suspend fun setScheduleCourseCardHeightDp(heightDp: Int) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_COURSE_CARD_HEIGHT_DP] = ScheduleCardStylePreferences.coerceCardHeightDp(heightDp) }
    }

    override suspend fun setScheduleOpacityPercent(percent: Int) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_OPACITY_PERCENT] = ScheduleCardStylePreferences.coerceOpacityPercent(percent) }
    }

    override suspend fun setScheduleInactiveCourseOpacityPercent(percent: Int) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_INACTIVE_COURSE_OPACITY_PERCENT] = ScheduleCardStylePreferences.coerceOpacityPercent(percent) }
    }

    override suspend fun setScheduleGridBorderColorArgb(argb: Long) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_GRID_BORDER_COLOR_ARGB] = ScheduleCardStylePreferences.coerceArgb(argb) }
    }

    override suspend fun setScheduleGridBorderOpacityPercent(percent: Int) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_GRID_BORDER_OPACITY_PERCENT] = ScheduleCardStylePreferences.coerceOpacityPercent(percent) }
    }

    override suspend fun setScheduleGridBorderWidthDp(widthDp: Float) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_GRID_BORDER_WIDTH_DP] = ScheduleCardStylePreferences.coerceBorderWidthDp(widthDp) }
    }

    override suspend fun setScheduleGridBorderDashed(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_GRID_BORDER_DASHED] = enabled }
    }

    override suspend fun setScheduleBackgroundColorArgb(argb: Long) {
        var previousImageUri: String? = null
        store.edit { prefs ->
            previousImageUri = prefs[KEY_SCHEDULE_BACKGROUND_IMAGE_URI]
            prefs[KEY_SCHEDULE_BACKGROUND_TYPE] = ScheduleBackgroundType.Color.name
            prefs[KEY_SCHEDULE_BACKGROUND_COLOR_ARGB] = ScheduleBackgroundPreferences.coerceArgb(argb)
            prefs.remove(KEY_SCHEDULE_BACKGROUND_IMAGE_URI)
        }
        releasePersistedReadPermission(previousImageUri)
    }

    override suspend fun setScheduleBackgroundImageUri(uri: String) {
        var previousImageUri: String? = null
        store.edit { prefs ->
            previousImageUri = prefs[KEY_SCHEDULE_BACKGROUND_IMAGE_URI]
            prefs[KEY_SCHEDULE_BACKGROUND_TYPE] = ScheduleBackgroundType.Image.name
            prefs[KEY_SCHEDULE_BACKGROUND_IMAGE_URI] = uri
        }
        if (previousImageUri != uri) {
            releasePersistedReadPermission(previousImageUri)
        }
    }

    override suspend fun clearScheduleBackgroundImage() {
        var previousImageUri: String? = null
        store.edit { prefs ->
            previousImageUri = prefs[KEY_SCHEDULE_BACKGROUND_IMAGE_URI]
            prefs[KEY_SCHEDULE_BACKGROUND_TYPE] = ScheduleBackgroundType.Color.name
            prefs.remove(KEY_SCHEDULE_BACKGROUND_IMAGE_URI)
        }
        releasePersistedReadPermission(previousImageUri)
    }

    override suspend fun setScheduleBackgroundUseHeaderColor() {
        var previousImageUri: String? = null
        store.edit { prefs ->
            previousImageUri = prefs[KEY_SCHEDULE_BACKGROUND_IMAGE_URI]
            prefs[KEY_SCHEDULE_BACKGROUND_TYPE] = ScheduleBackgroundType.Header.name
            prefs.remove(KEY_SCHEDULE_BACKGROUND_IMAGE_URI)
        }
        releasePersistedReadPermission(previousImageUri)
    }

    override suspend fun setScheduleNodeColumnTimeEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_DISPLAY_NODE_COLUMN_TIME_ENABLED] = enabled }
    }

    override suspend fun setScheduleSaturdayVisible(visible: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_DISPLAY_SATURDAY_VISIBLE] = visible }
    }

    override suspend fun setScheduleWeekendVisible(visible: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_DISPLAY_WEEKEND_VISIBLE] = visible }
    }

    override suspend fun setScheduleLocationVisible(visible: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_DISPLAY_LOCATION_VISIBLE] = visible }
    }

    override suspend fun setScheduleLocationPrefixAtEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_DISPLAY_LOCATION_PREFIX_AT_ENABLED] = enabled }
    }

    override suspend fun setScheduleTeacherVisible(visible: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_DISPLAY_TEACHER_VISIBLE] = visible }
    }

    override suspend fun setTotalScheduleDisplayEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_SCHEDULE_DISPLAY_TOTAL_SCHEDULE_DISPLAY_ENABLED] = enabled }
    }

    override suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[KEY_ENABLED_PLUGIN_IDS].orEmpty().toMutableSet()
            if (enabled) current += pluginId else current -= pluginId
            prefs[KEY_ENABLED_PLUGIN_IDS] = current
        }
    }

    override suspend fun setDebugForcedDateTime(dateTime: LocalDateTime?) {
        store.edit { prefs ->
            // Drop legacy date-only key whenever forced time is touched.
            prefs.remove(KEY_DEBUG_FORCED_DATE_EPOCH_DAY)
            if (dateTime == null) {
                prefs.remove(KEY_DEBUG_FORCED_DATETIME)
            } else {
                prefs[KEY_DEBUG_FORCED_DATETIME] = dateTime.toString()
            }
        }
    }

    override suspend fun setDisclaimerAccepted(accepted: Boolean) {
        store.edit { prefs -> prefs[KEY_DISCLAIMER_ACCEPTED] = accepted }
    }

    override suspend fun setAlarmBackend(backend: ReminderAlarmBackend) {
        store.edit { prefs -> prefs[KEY_ALARM_BACKEND] = backend.name }
    }

    override suspend fun setAlarmRingtoneUri(uri: String?) {
        var previousUri: String? = null
        store.edit { prefs ->
            previousUri = prefs[KEY_ALARM_RINGTONE_URI]
            val normalized = uri?.takeIf { it.isNotBlank() }
            if (normalized == null) {
                prefs.remove(KEY_ALARM_RINGTONE_URI)
            } else {
                prefs[KEY_ALARM_RINGTONE_URI] = normalized
            }
        }
        if (previousUri != uri) {
            releasePersistedReadPermission(previousUri)
        }
    }

    override suspend fun setAlarmAlertMode(mode: AlarmAlertMode) {
        store.edit { prefs -> prefs[KEY_ALARM_ALERT_MODE] = mode.name }
    }

    override suspend fun setAlarmRingDurationSeconds(seconds: Int) {
        store.edit { prefs ->
            prefs[KEY_ALARM_RING_DURATION_SECONDS] = seconds.coerceIn(
                MIN_RING_DURATION_SECONDS,
                MAX_RING_DURATION_SECONDS,
            )
        }
    }

    override suspend fun setAlarmRepeatIntervalSeconds(seconds: Int) {
        store.edit { prefs ->
            prefs[KEY_ALARM_REPEAT_INTERVAL_SECONDS] = seconds.coerceIn(
                MIN_REPEAT_INTERVAL_SECONDS,
                MAX_REPEAT_INTERVAL_SECONDS,
            )
        }
    }

    override suspend fun setAlarmRepeatCount(count: Int) {
        store.edit { prefs ->
            prefs[KEY_ALARM_REPEAT_COUNT] = count.coerceIn(MIN_REPEAT_COUNT, MAX_REPEAT_COUNT)
        }
    }

    override suspend fun markAlarmPollAt(millis: Long) {
        store.edit { prefs -> prefs[KEY_LAST_ALARM_POLL_AT_MILLIS] = millis.coerceAtLeast(0L) }
    }

    override suspend fun tryClaimAlarmPoll(nowMillis: Long, minIntervalMillis: Long): Boolean {
        var claimed = false
        store.edit { prefs ->
            val previous = prefs[KEY_LAST_ALARM_POLL_AT_MILLIS] ?: 0L
            val elapsed = nowMillis - previous
            if (previous == 0L || elapsed < 0L || elapsed >= minIntervalMillis) {
                prefs[KEY_LAST_ALARM_POLL_AT_MILLIS] = nowMillis.coerceAtLeast(0L)
                claimed = true
            }
        }
        return claimed
    }

    override suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_AUTO_UPDATE_ENABLED] = enabled }
    }

    override suspend fun setIgnoredUpdateVersionCode(versionCode: Int?) {
        store.edit { prefs ->
            if (versionCode == null) {
                prefs.remove(KEY_IGNORED_UPDATE_VERSION_CODE)
            } else {
                prefs[KEY_IGNORED_UPDATE_VERSION_CODE] = versionCode
            }
        }
    }

    override suspend fun setPluginMarketIndexUrl(url: String) {
        store.edit { prefs -> prefs[KEY_PLUGIN_MARKET_INDEX_URL] = url.trim() }
    }

    override suspend fun setComponentMarketIndexUrl(url: String) {
        store.edit { prefs -> prefs[KEY_COMPONENT_MARKET_INDEX_URL] = url.trim() }
    }

    override suspend fun setPrivateFilesProviderEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_PRIVATE_FILES_PROVIDER_ENABLED] = enabled }
    }

    override suspend fun setWebDavSettings(url: String, username: String, password: String) {
        store.edit { prefs ->
            val normalizedUrl = url.trim().ifBlank { DEFAULT_WEBDAV_URL }
            prefs[KEY_WEBDAV_URL] = normalizedUrl
            prefs[KEY_WEBDAV_USERNAME] = username.trim()
            prefs[KEY_WEBDAV_PASSWORD] = password
        }
    }

    override suspend fun setAiImportSettings(apiUrl: String, apiKey: String, model: String) {
        store.edit { prefs ->
            val normalizedApiUrl = apiUrl.trim()
            if (normalizedApiUrl.isBlank()) {
                prefs.remove(KEY_AI_IMPORT_API_URL)
            } else {
                prefs[KEY_AI_IMPORT_API_URL] = normalizedApiUrl
            }
            val normalizedApiKey = apiKey.trim()
            if (normalizedApiKey.isBlank()) {
                prefs.remove(KEY_AI_IMPORT_API_KEY)
            } else {
                prefs[KEY_AI_IMPORT_API_KEY] = normalizedApiKey
            }
            val normalizedModel = model.trim()
            if (normalizedModel.isBlank()) {
                prefs.remove(KEY_AI_IMPORT_MODEL)
            } else {
                prefs[KEY_AI_IMPORT_MODEL] = normalizedModel
            }
        }
    }

    override suspend fun resetScheduleAppearanceAndDisplay() {
        var previousImageUri: String? = null
        store.edit { prefs ->
            previousImageUri = prefs[KEY_SCHEDULE_BACKGROUND_IMAGE_URI]
            prefs.removeScheduleAppearanceAndDisplay()
        }
        releasePersistedReadPermission(previousImageUri)
    }

    override suspend fun resetAllSettings() {
        var previousImageUri: String? = null
        var previousAlarmRingtoneUri: String? = null
        store.edit { prefs ->
            previousImageUri = prefs[KEY_SCHEDULE_BACKGROUND_IMAGE_URI]
            previousAlarmRingtoneUri = prefs[KEY_ALARM_RINGTONE_URI]
            prefs.remove(KEY_THEME_MODE)
            prefs.remove(KEY_THEME_ACCENT)
            prefs.remove(KEY_DEVELOPER_MODE)
            prefs.remove(KEY_DEBUG_FORCED_DATE_EPOCH_DAY)
            prefs.remove(KEY_DEBUG_FORCED_DATETIME)
            prefs.remove(KEY_ALARM_BACKEND)
            prefs.remove(KEY_ALARM_RINGTONE_URI)
            prefs.remove(KEY_ALARM_ALERT_MODE)
            prefs.remove(KEY_ALARM_RING_DURATION_SECONDS)
            prefs.remove(KEY_ALARM_REPEAT_INTERVAL_SECONDS)
            prefs.remove(KEY_ALARM_REPEAT_COUNT)
            prefs.remove(KEY_LAST_ALARM_POLL_AT_MILLIS)
            prefs.remove(KEY_AUTO_UPDATE_ENABLED)
            prefs.remove(KEY_IGNORED_UPDATE_VERSION_CODE)
            prefs.remove(KEY_PLUGIN_MARKET_INDEX_URL)
            prefs.remove(KEY_COMPONENT_MARKET_INDEX_URL)
            prefs.remove(KEY_PRIVATE_FILES_PROVIDER_ENABLED)
            prefs.remove(KEY_WEBDAV_URL)
            prefs.remove(KEY_WEBDAV_USERNAME)
            prefs.remove(KEY_WEBDAV_PASSWORD)
            prefs.remove(KEY_AI_IMPORT_API_URL)
            prefs.remove(KEY_AI_IMPORT_API_KEY)
            prefs.remove(KEY_AI_IMPORT_MODEL)
            prefs.removeScheduleAppearanceAndDisplay()
        }
        releasePersistedReadPermission(previousImageUri)
        releasePersistedReadPermission(previousAlarmRingtoneUri)
    }

    override suspend fun seedEnabledPlugins(pluginIds: Set<String>) {
        store.edit { prefs ->
            if (prefs[KEY_PLUGINS_SEEDED] == true) return@edit
            val current = prefs[KEY_ENABLED_PLUGIN_IDS].orEmpty().toMutableSet()
            current += pluginIds
            prefs[KEY_ENABLED_PLUGIN_IDS] = current
            prefs[KEY_PLUGINS_SEEDED] = true
        }
    }

    override suspend fun upsertTemporaryScheduleOverride(override: TemporaryScheduleOverride) {
        store.edit { prefs ->
            val current = decodeTemporaryScheduleOverrides(prefs[KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON])
                .filterNot { it.id == override.id }
            prefs[KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON] = json.encodeToString(current + override)
        }
    }

    override suspend fun removeTemporaryScheduleOverride(id: String) {
        store.edit { prefs ->
            val next = decodeTemporaryScheduleOverrides(prefs[KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON])
                .filterNot { it.id == id }
            if (next.isEmpty()) {
                prefs.remove(KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON)
            } else {
                prefs[KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON] = json.encodeToString(next)
            }
        }
    }

    override suspend fun clearTemporaryScheduleOverrides() {
        store.edit { prefs ->
            prefs.remove(KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON)
        }
    }

    private fun decodeTemporaryScheduleOverrides(raw: String?): List<TemporaryScheduleOverride> {
        return raw
            ?.let { value -> runCatching { json.decodeFromString<List<TemporaryScheduleOverride>>(value) }.getOrNull() }
            .orEmpty()
    }

    private fun Preferences.toScheduleTextStyle(): ScheduleTextStylePreferences {
        return ScheduleTextStylePreferences(
            courseTextSizeSp = ScheduleTextStylePreferences.coerceTextSizeSp(
                this[KEY_SCHEDULE_COURSE_TEXT_SIZE_SP] ?: ScheduleTextStylePreferences.DEFAULT_COURSE_TEXT_SIZE_SP,
            ),
            courseTextColorArgb = ScheduleTextStylePreferences.coerceArgb(
                this[KEY_SCHEDULE_COURSE_TEXT_COLOR_ARGB] ?: ScheduleTextStylePreferences.DEFAULT_TEXT_COLOR_ARGB,
            ),
            examTextSizeSp = ScheduleTextStylePreferences.coerceTextSizeSp(
                this[KEY_SCHEDULE_EXAM_TEXT_SIZE_SP] ?: ScheduleTextStylePreferences.DEFAULT_EXAM_TEXT_SIZE_SP,
            ),
            examTextColorArgb = ScheduleTextStylePreferences.coerceArgb(
                this[KEY_SCHEDULE_EXAM_TEXT_COLOR_ARGB] ?: ScheduleTextStylePreferences.DEFAULT_TEXT_COLOR_ARGB,
            ),
            headerTextSizeSp = ScheduleTextStylePreferences.coerceTextSizeSp(
                this[KEY_SCHEDULE_HEADER_TEXT_SIZE_SP] ?: ScheduleTextStylePreferences.DEFAULT_HEADER_TEXT_SIZE_SP,
            ),
            headerTextColorArgb = ScheduleTextStylePreferences.coerceArgb(
                this[KEY_SCHEDULE_HEADER_TEXT_COLOR_ARGB] ?: ScheduleTextStylePreferences.DEFAULT_HEADER_TEXT_COLOR_ARGB,
            ),
            headerTextColorCustomized = this.contains(KEY_SCHEDULE_HEADER_TEXT_COLOR_ARGB),
            todayHeaderBackgroundColorArgb = ScheduleTextStylePreferences.coerceArgb(
                this[KEY_SCHEDULE_TODAY_HEADER_BACKGROUND_COLOR_ARGB]
                    ?: ScheduleTextStylePreferences.DEFAULT_TODAY_HEADER_BACKGROUND_COLOR_ARGB,
            ),
            todayHeaderBackgroundColorCustomized =
                this.contains(KEY_SCHEDULE_TODAY_HEADER_BACKGROUND_COLOR_ARGB),
            horizontalCenter = this[KEY_SCHEDULE_TEXT_HORIZONTAL_CENTER] ?: false,
            verticalCenter = this[KEY_SCHEDULE_TEXT_VERTICAL_CENTER] ?: false,
            fullCenter = this[KEY_SCHEDULE_TEXT_FULL_CENTER] ?: false,
        )
    }

    private fun Preferences.toScheduleCardStyle(): ScheduleCardStylePreferences {
        return ScheduleCardStylePreferences(
            courseCornerRadiusDp = ScheduleCardStylePreferences.coerceCornerRadiusDp(
                this[KEY_SCHEDULE_COURSE_CORNER_RADIUS_DP] ?: ScheduleCardStylePreferences.DEFAULT_COURSE_CORNER_RADIUS_DP,
            ),
            courseCardHeightDp = ScheduleCardStylePreferences.coerceCardHeightDp(
                this[KEY_SCHEDULE_COURSE_CARD_HEIGHT_DP] ?: ScheduleCardStylePreferences.DEFAULT_COURSE_CARD_HEIGHT_DP,
            ),
            scheduleOpacityPercent = ScheduleCardStylePreferences.coerceOpacityPercent(
                this[KEY_SCHEDULE_OPACITY_PERCENT] ?: ScheduleCardStylePreferences.DEFAULT_SCHEDULE_OPACITY_PERCENT,
            ),
            inactiveCourseOpacityPercent = ScheduleCardStylePreferences.coerceOpacityPercent(
                this[KEY_SCHEDULE_INACTIVE_COURSE_OPACITY_PERCENT]
                    ?: ScheduleCardStylePreferences.DEFAULT_INACTIVE_COURSE_OPACITY_PERCENT,
            ),
            gridBorderColorArgb = ScheduleCardStylePreferences.coerceArgb(
                this[KEY_SCHEDULE_GRID_BORDER_COLOR_ARGB] ?: ScheduleCardStylePreferences.DEFAULT_GRID_BORDER_COLOR_ARGB,
            ),
            gridBorderOpacityPercent = ScheduleCardStylePreferences.coerceOpacityPercent(
                this[KEY_SCHEDULE_GRID_BORDER_OPACITY_PERCENT]
                    ?: ScheduleCardStylePreferences.DEFAULT_GRID_BORDER_OPACITY_PERCENT,
            ),
            gridBorderWidthDp = ScheduleCardStylePreferences.coerceBorderWidthDp(
                this[KEY_SCHEDULE_GRID_BORDER_WIDTH_DP] ?: ScheduleCardStylePreferences.DEFAULT_GRID_BORDER_WIDTH_DP,
            ),
            gridBorderDashed = this[KEY_SCHEDULE_GRID_BORDER_DASHED] ?: false,
        )
    }

    private fun Preferences.toScheduleBackground(): ScheduleBackgroundPreferences {
        return ScheduleBackgroundPreferences(
            type = this[KEY_SCHEDULE_BACKGROUND_TYPE]
                ?.let { runCatching { ScheduleBackgroundType.valueOf(it) }.getOrNull() }
                ?: ScheduleBackgroundPreferences.DEFAULT_BACKGROUND_TYPE,
            colorArgb = ScheduleBackgroundPreferences.coerceArgb(
                this[KEY_SCHEDULE_BACKGROUND_COLOR_ARGB]
                    ?: ScheduleBackgroundPreferences.DEFAULT_BACKGROUND_COLOR_ARGB,
            ),
            imageUri = this[KEY_SCHEDULE_BACKGROUND_IMAGE_URI]?.takeIf(String::isNotBlank),
        )
    }

    private fun Preferences.toScheduleDisplay(): ScheduleDisplayPreferences {
        return ScheduleDisplayPreferences(
            nodeColumnTimeEnabled = this[KEY_SCHEDULE_DISPLAY_NODE_COLUMN_TIME_ENABLED] ?: true,
            saturdayVisible = this[KEY_SCHEDULE_DISPLAY_SATURDAY_VISIBLE] ?: true,
            weekendVisible = this[KEY_SCHEDULE_DISPLAY_WEEKEND_VISIBLE] ?: true,
            locationVisible = this[KEY_SCHEDULE_DISPLAY_LOCATION_VISIBLE] ?: true,
            locationPrefixAtEnabled = this[KEY_SCHEDULE_DISPLAY_LOCATION_PREFIX_AT_ENABLED] ?: true,
            teacherVisible = this[KEY_SCHEDULE_DISPLAY_TEACHER_VISIBLE] ?: true,
            totalScheduleDisplayEnabled = this[KEY_SCHEDULE_DISPLAY_TOTAL_SCHEDULE_DISPLAY_ENABLED] ?: true,
        )
    }

    private fun MutablePreferences.removeScheduleAppearanceAndDisplay() {
        remove(KEY_SCHEDULE_COURSE_TEXT_SIZE_SP)
        remove(KEY_SCHEDULE_COURSE_TEXT_COLOR_ARGB)
        remove(KEY_SCHEDULE_EXAM_TEXT_SIZE_SP)
        remove(KEY_SCHEDULE_EXAM_TEXT_COLOR_ARGB)
        remove(KEY_SCHEDULE_HEADER_TEXT_SIZE_SP)
        remove(KEY_SCHEDULE_HEADER_TEXT_COLOR_ARGB)
        remove(KEY_SCHEDULE_TODAY_HEADER_BACKGROUND_COLOR_ARGB)
        remove(KEY_SCHEDULE_TEXT_HORIZONTAL_CENTER)
        remove(KEY_SCHEDULE_TEXT_VERTICAL_CENTER)
        remove(KEY_SCHEDULE_TEXT_FULL_CENTER)
        remove(KEY_SCHEDULE_COURSE_CORNER_RADIUS_DP)
        remove(KEY_SCHEDULE_COURSE_CARD_HEIGHT_DP)
        remove(KEY_SCHEDULE_OPACITY_PERCENT)
        remove(KEY_SCHEDULE_INACTIVE_COURSE_OPACITY_PERCENT)
        remove(KEY_SCHEDULE_GRID_BORDER_COLOR_ARGB)
        remove(KEY_SCHEDULE_GRID_BORDER_OPACITY_PERCENT)
        remove(KEY_SCHEDULE_GRID_BORDER_WIDTH_DP)
        remove(KEY_SCHEDULE_GRID_BORDER_DASHED)
        remove(KEY_SCHEDULE_BACKGROUND_TYPE)
        remove(KEY_SCHEDULE_BACKGROUND_COLOR_ARGB)
        remove(KEY_SCHEDULE_BACKGROUND_IMAGE_URI)
        remove(KEY_SCHEDULE_DISPLAY_NODE_COLUMN_TIME_ENABLED)
        remove(KEY_SCHEDULE_DISPLAY_SATURDAY_VISIBLE)
        remove(KEY_SCHEDULE_DISPLAY_WEEKEND_VISIBLE)
        remove(KEY_SCHEDULE_DISPLAY_LOCATION_VISIBLE)
        remove(KEY_SCHEDULE_DISPLAY_LOCATION_PREFIX_AT_ENABLED)
        remove(KEY_SCHEDULE_DISPLAY_TEACHER_VISIBLE)
        remove(KEY_SCHEDULE_DISPLAY_TOTAL_SCHEDULE_DISPLAY_ENABLED)
    }

    private fun releasePersistedReadPermission(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_ACCENT = stringPreferencesKey("theme_accent")
        val KEY_TERM_START_EPOCH_DAY = longPreferencesKey("term_start_epoch_day")
        val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val KEY_SCHEDULE_COURSE_TEXT_SIZE_SP = intPreferencesKey("schedule_course_text_size_sp")
        val KEY_SCHEDULE_COURSE_TEXT_COLOR_ARGB = longPreferencesKey("schedule_course_text_color_argb")
        val KEY_SCHEDULE_EXAM_TEXT_SIZE_SP = intPreferencesKey("schedule_exam_text_size_sp")
        val KEY_SCHEDULE_EXAM_TEXT_COLOR_ARGB = longPreferencesKey("schedule_exam_text_color_argb")
        val KEY_SCHEDULE_HEADER_TEXT_SIZE_SP = intPreferencesKey("schedule_header_text_size_sp")
        val KEY_SCHEDULE_HEADER_TEXT_COLOR_ARGB = longPreferencesKey("schedule_header_text_color_argb")
        val KEY_SCHEDULE_TODAY_HEADER_BACKGROUND_COLOR_ARGB =
            longPreferencesKey("schedule_today_header_background_color_argb")
        val KEY_SCHEDULE_TEXT_HORIZONTAL_CENTER = booleanPreferencesKey("schedule_text_horizontal_center")
        val KEY_SCHEDULE_TEXT_VERTICAL_CENTER = booleanPreferencesKey("schedule_text_vertical_center")
        val KEY_SCHEDULE_TEXT_FULL_CENTER = booleanPreferencesKey("schedule_text_full_center")
        val KEY_SCHEDULE_COURSE_CORNER_RADIUS_DP = intPreferencesKey("schedule_course_corner_radius_dp")
        val KEY_SCHEDULE_COURSE_CARD_HEIGHT_DP = intPreferencesKey("schedule_course_card_height_dp")
        val KEY_SCHEDULE_OPACITY_PERCENT = intPreferencesKey("schedule_opacity_percent")
        val KEY_SCHEDULE_INACTIVE_COURSE_OPACITY_PERCENT = intPreferencesKey("schedule_inactive_course_opacity_percent")
        val KEY_SCHEDULE_GRID_BORDER_COLOR_ARGB = longPreferencesKey("schedule_grid_border_color_argb")
        val KEY_SCHEDULE_GRID_BORDER_OPACITY_PERCENT = intPreferencesKey("schedule_grid_border_opacity_percent")
        val KEY_SCHEDULE_GRID_BORDER_WIDTH_DP = floatPreferencesKey("schedule_grid_border_width_dp")
        val KEY_SCHEDULE_GRID_BORDER_DASHED = booleanPreferencesKey("schedule_grid_border_dashed")
        val KEY_SCHEDULE_BACKGROUND_TYPE = stringPreferencesKey("schedule_background_type")
        val KEY_SCHEDULE_BACKGROUND_COLOR_ARGB = longPreferencesKey("schedule_background_color_argb")
        val KEY_SCHEDULE_BACKGROUND_IMAGE_URI = stringPreferencesKey("schedule_background_image_uri")
        val KEY_SCHEDULE_DISPLAY_NODE_COLUMN_TIME_ENABLED = booleanPreferencesKey("schedule_display_node_column_time_enabled")
        val KEY_SCHEDULE_DISPLAY_SATURDAY_VISIBLE = booleanPreferencesKey("schedule_display_saturday_visible")
        val KEY_SCHEDULE_DISPLAY_WEEKEND_VISIBLE = booleanPreferencesKey("schedule_display_weekend_visible")
        val KEY_SCHEDULE_DISPLAY_LOCATION_VISIBLE = booleanPreferencesKey("schedule_display_location_visible")
        val KEY_SCHEDULE_DISPLAY_LOCATION_PREFIX_AT_ENABLED =
            booleanPreferencesKey("schedule_display_location_prefix_at_enabled")
        val KEY_SCHEDULE_DISPLAY_TEACHER_VISIBLE = booleanPreferencesKey("schedule_display_teacher_visible")
        val KEY_SCHEDULE_DISPLAY_TOTAL_SCHEDULE_DISPLAY_ENABLED =
            booleanPreferencesKey("schedule_display_total_schedule_display_enabled")
        val KEY_ENABLED_PLUGIN_IDS = stringSetPreferencesKey("enabled_plugin_ids")
        val KEY_PLUGINS_SEEDED = booleanPreferencesKey("plugins_seeded")
        val KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON = stringPreferencesKey("temporary_schedule_overrides_json")
        val KEY_DEBUG_FORCED_DATE_EPOCH_DAY = longPreferencesKey("debug_forced_date_epoch_day")
        val KEY_DEBUG_FORCED_DATETIME = stringPreferencesKey("debug_forced_datetime")
        val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val KEY_ALARM_BACKEND = stringPreferencesKey("alarm_backend")
        val KEY_ALARM_RINGTONE_URI = stringPreferencesKey("alarm_ringtone_uri")
        val KEY_ALARM_ALERT_MODE = stringPreferencesKey("alarm_alert_mode")
        val KEY_ALARM_RING_DURATION_SECONDS = intPreferencesKey("alarm_ring_duration_seconds")
        val KEY_ALARM_REPEAT_INTERVAL_SECONDS = intPreferencesKey("alarm_repeat_interval_seconds")
        val KEY_ALARM_REPEAT_COUNT = intPreferencesKey("alarm_repeat_count")
        val KEY_LAST_ALARM_POLL_AT_MILLIS = longPreferencesKey("last_alarm_poll_at_millis")
        val KEY_AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val KEY_IGNORED_UPDATE_VERSION_CODE = intPreferencesKey("ignored_update_version_code")
        val KEY_PLUGIN_MARKET_INDEX_URL = stringPreferencesKey("plugin_market_index_url")
        val KEY_COMPONENT_MARKET_INDEX_URL = stringPreferencesKey("component_market_index_url")
        val KEY_PRIVATE_FILES_PROVIDER_ENABLED = booleanPreferencesKey("private_files_provider_enabled")
        val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val KEY_AI_IMPORT_API_URL = stringPreferencesKey("ai_import_api_url")
        val KEY_AI_IMPORT_API_KEY = stringPreferencesKey("ai_import_api_key")
        val KEY_AI_IMPORT_MODEL = stringPreferencesKey("ai_import_model")

        const val DEFAULT_RING_DURATION_SECONDS = DEFAULT_APP_ALARM_RING_DURATION_SECONDS
        const val DEFAULT_REPEAT_INTERVAL_SECONDS = DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS
        const val DEFAULT_REPEAT_COUNT = DEFAULT_APP_ALARM_REPEAT_COUNT
        const val MIN_RING_DURATION_SECONDS = 5
        const val MAX_RING_DURATION_SECONDS = 600
        const val MIN_REPEAT_INTERVAL_SECONDS = 5
        const val MAX_REPEAT_INTERVAL_SECONDS = 3600
        const val MIN_REPEAT_COUNT = 1
        const val MAX_REPEAT_COUNT = 10
    }
}
