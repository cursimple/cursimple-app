package com.x500x.cursimple.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "schedule_store")

class DataStoreScheduleRepository(
    context: Context,
    private val termProfileRepository: TermProfileRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ScheduleRepository {

    private val store = context.applicationContext.scheduleDataStore

    override val scheduleFlow: Flow<TermSchedule?> = if (termProfileRepository != null) {
        combine(store.data, termProfileRepository.activeTermIdFlow) { preferences, termId ->
            decodeSchedule(preferences, termId)
        }
    } else {
        store.data.map { decodeSchedule(it, "") }
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
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { preferences ->
            preferences[scheduleKey(termId)] = json.encodeToString(schedule)
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
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { preferences ->
            preferences.remove(scheduleKey(termId))
        }
    }

    private fun decodeSchedule(preferences: Preferences, termId: String): TermSchedule? {
        // Prefer per-term key, but fall back to legacy single-key payload to keep
        // pre-multi-term installs working through the migration window.
        val raw = preferences[scheduleKey(termId)] ?: preferences[KEY_LEGACY_SCHEDULE_JSON]
        return raw?.let { runCatching { json.decodeFromString<TermSchedule>(it) }.getOrNull() }
    }

    /** One-shot legacy migration: copy the global schedule_json into the active term, then drop it. */
    suspend fun migrateLegacyScheduleIfNeeded(targetTermId: String) {
        if (targetTermId.isBlank()) return
        store.edit { preferences ->
            val legacy = preferences[KEY_LEGACY_SCHEDULE_JSON] ?: return@edit
            val perTerm = preferences[scheduleKey(targetTermId)]
            if (perTerm.isNullOrBlank()) {
                preferences[scheduleKey(targetTermId)] = legacy
            }
            preferences.remove(KEY_LEGACY_SCHEDULE_JSON)
        }
    }

    private fun scheduleKey(termId: String) =
        stringPreferencesKey(if (termId.isBlank()) "schedule_json" else "schedule_json__$termId")

    private companion object {
        val KEY_LEGACY_SCHEDULE_JSON = stringPreferencesKey("schedule_json")
        val KEY_PLUGIN_ID = stringPreferencesKey("plugin_id")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_TERM_ID = stringPreferencesKey("term_id")
        const val DEFAULT_PLUGIN_ID = "yangtzeu-eams-v2"
        const val DEFAULT_TERM_ID = ""
    }
}
