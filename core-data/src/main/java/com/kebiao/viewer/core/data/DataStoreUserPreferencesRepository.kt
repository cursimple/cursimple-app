package com.kebiao.viewer.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class DataStoreUserPreferencesRepository(context: Context) : UserPreferencesRepository {
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
            debugForcedDateTime = prefs[KEY_DEBUG_FORCED_DATETIME]?.let { raw ->
                runCatching { LocalDateTime.parse(raw) }.getOrNull()
            },
            disclaimerAccepted = prefs[KEY_DISCLAIMER_ACCEPTED] ?: false,
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

    override suspend fun seedEnabledPlugins(pluginIds: Set<String>) {
        store.edit { prefs ->
            if (prefs[KEY_PLUGINS_SEEDED] == true) return@edit
            val current = prefs[KEY_ENABLED_PLUGIN_IDS].orEmpty().toMutableSet()
            current += pluginIds
            prefs[KEY_ENABLED_PLUGIN_IDS] = current
            prefs[KEY_PLUGINS_SEEDED] = true
        }
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
        val KEY_DEBUG_FORCED_DATE_EPOCH_DAY = longPreferencesKey("debug_forced_date_epoch_day")
        val KEY_DEBUG_FORCED_DATETIME = stringPreferencesKey("debug_forced_datetime")
        val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
    }
}
