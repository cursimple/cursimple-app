package com.kebiao.viewer.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kebiao.viewer.core.kernel.model.TermSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "schedule_store")

class DataStoreScheduleRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ScheduleRepository {

    private val store = context.applicationContext.scheduleDataStore

    override val scheduleFlow: Flow<TermSchedule?> = store.data.map { preferences ->
        preferences[KEY_SCHEDULE_JSON]
            ?.let { raw -> runCatching { json.decodeFromString<TermSchedule>(raw) }.getOrNull() }
    }

    override val lastPluginIdFlow: Flow<String> = store.data.map { preferences ->
        preferences[KEY_PLUGIN_ID] ?: DEFAULT_PLUGIN_ID
    }

    override val lastUsernameFlow: Flow<String> = store.data.map { preferences ->
        preferences[KEY_USERNAME].orEmpty()
    }

    override val lastTermIdFlow: Flow<String> = store.data.map { preferences ->
        preferences[KEY_TERM_ID] ?: DEFAULT_TERM_ID
    }

    override suspend fun saveSchedule(schedule: TermSchedule) {
        store.edit { preferences ->
            preferences[KEY_SCHEDULE_JSON] = json.encodeToString(schedule)
        }
    }

    override suspend fun saveLastInput(pluginId: String, username: String, termId: String) {
        store.edit { preferences ->
            preferences[KEY_PLUGIN_ID] = pluginId
            preferences[KEY_USERNAME] = username
            preferences[KEY_TERM_ID] = termId
        }
    }

    override suspend fun clearSchedule() {
        store.edit { preferences ->
            preferences.remove(KEY_SCHEDULE_JSON)
        }
    }

    private companion object {
        val KEY_SCHEDULE_JSON = stringPreferencesKey("schedule_json")
        val KEY_PLUGIN_ID = stringPreferencesKey("plugin_id")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_TERM_ID = stringPreferencesKey("term_id")
        const val DEFAULT_PLUGIN_ID = "yangtzeu-eams-v2"
        const val DEFAULT_TERM_ID = ""
    }
}
