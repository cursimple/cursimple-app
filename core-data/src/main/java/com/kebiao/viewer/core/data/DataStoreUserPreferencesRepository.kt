package com.kebiao.viewer.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
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
    private val store = context.applicationContext.userPreferencesDataStore

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
            timeZoneId = prefs[KEY_TIME_ZONE_ID] ?: UserPreferences.DEFAULT_TIME_ZONE_ID,
            totalScheduleDisplayEnabled = prefs[KEY_TOTAL_SCHEDULE_DISPLAY_ENABLED] ?: true,
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
            alarmRingDurationSeconds = (prefs[KEY_ALARM_RING_DURATION_SECONDS] ?: DEFAULT_RING_DURATION_SECONDS)
                .coerceIn(MIN_RING_DURATION_SECONDS, MAX_RING_DURATION_SECONDS),
            alarmRepeatIntervalSeconds = (prefs[KEY_ALARM_REPEAT_INTERVAL_SECONDS] ?: DEFAULT_REPEAT_INTERVAL_SECONDS)
                .coerceIn(MIN_REPEAT_INTERVAL_SECONDS, MAX_REPEAT_INTERVAL_SECONDS),
            alarmRepeatCount = (prefs[KEY_ALARM_REPEAT_COUNT] ?: DEFAULT_REPEAT_COUNT)
                .coerceIn(MIN_REPEAT_COUNT, MAX_REPEAT_COUNT),
            lastAlarmPollAtMillis = prefs[KEY_LAST_ALARM_POLL_AT_MILLIS] ?: 0L,
            autoUpdateEnabled = prefs[KEY_AUTO_UPDATE_ENABLED] ?: false,
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

    override suspend fun setTimeZoneId(timeZoneId: String) {
        store.edit { prefs -> prefs[KEY_TIME_ZONE_ID] = timeZoneId }
    }

    override suspend fun setTotalScheduleDisplayEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_TOTAL_SCHEDULE_DISPLAY_ENABLED] = enabled }
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

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_ACCENT = stringPreferencesKey("theme_accent")
        val KEY_TERM_START_EPOCH_DAY = longPreferencesKey("term_start_epoch_day")
        val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val KEY_TIME_ZONE_ID = stringPreferencesKey("time_zone_id")
        val KEY_TOTAL_SCHEDULE_DISPLAY_ENABLED = booleanPreferencesKey("total_schedule_display_enabled")
        val KEY_ENABLED_PLUGIN_IDS = stringSetPreferencesKey("enabled_plugin_ids")
        val KEY_PLUGINS_SEEDED = booleanPreferencesKey("plugins_seeded")
        val KEY_TEMPORARY_SCHEDULE_OVERRIDES_JSON = stringPreferencesKey("temporary_schedule_overrides_json")
        val KEY_DEBUG_FORCED_DATE_EPOCH_DAY = longPreferencesKey("debug_forced_date_epoch_day")
        val KEY_DEBUG_FORCED_DATETIME = stringPreferencesKey("debug_forced_datetime")
        val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val KEY_ALARM_BACKEND = stringPreferencesKey("alarm_backend")
        val KEY_ALARM_RING_DURATION_SECONDS = intPreferencesKey("alarm_ring_duration_seconds")
        val KEY_ALARM_REPEAT_INTERVAL_SECONDS = intPreferencesKey("alarm_repeat_interval_seconds")
        val KEY_ALARM_REPEAT_COUNT = intPreferencesKey("alarm_repeat_count")
        val KEY_LAST_ALARM_POLL_AT_MILLIS = longPreferencesKey("last_alarm_poll_at_millis")
        val KEY_AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")

        const val DEFAULT_RING_DURATION_SECONDS = 60
        const val DEFAULT_REPEAT_INTERVAL_SECONDS = 120
        const val DEFAULT_REPEAT_COUNT = 1
        const val MIN_RING_DURATION_SECONDS = 5
        const val MAX_RING_DURATION_SECONDS = 600
        const val MIN_REPEAT_INTERVAL_SECONDS = 5
        const val MAX_REPEAT_INTERVAL_SECONDS = 3600
        const val MIN_REPEAT_COUNT = 1
        const val MAX_REPEAT_COUNT = 10
    }
}
