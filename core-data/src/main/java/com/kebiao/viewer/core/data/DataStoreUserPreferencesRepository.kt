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

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class DataStoreUserPreferencesRepository(context: Context) : UserPreferencesRepository {
    private val store = context.applicationContext.userPreferencesDataStore

    override val preferencesFlow: Flow<UserPreferences> = store.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.Light,
            termStartDate = prefs[KEY_TERM_START_EPOCH_DAY]?.let(LocalDate::ofEpochDay),
            developerModeEnabled = prefs[KEY_DEVELOPER_MODE] ?: false,
            timeZoneId = prefs[KEY_TIME_ZONE_ID] ?: UserPreferences.DEFAULT_TIME_ZONE_ID,
            enabledPluginIds = prefs[KEY_ENABLED_PLUGIN_IDS].orEmpty().toSet(),
            pluginsSeeded = prefs[KEY_PLUGINS_SEEDED] ?: false,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
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

    override suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[KEY_ENABLED_PLUGIN_IDS].orEmpty().toMutableSet()
            if (enabled) current += pluginId else current -= pluginId
            prefs[KEY_ENABLED_PLUGIN_IDS] = current
        }
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
        val KEY_TERM_START_EPOCH_DAY = longPreferencesKey("term_start_epoch_day")
        val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val KEY_TIME_ZONE_ID = stringPreferencesKey("time_zone_id")
        val KEY_ENABLED_PLUGIN_IDS = stringSetPreferencesKey("enabled_plugin_ids")
        val KEY_PLUGINS_SEEDED = booleanPreferencesKey("plugins_seeded")
    }
}
