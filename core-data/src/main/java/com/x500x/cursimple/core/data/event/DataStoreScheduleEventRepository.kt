package com.x500x.cursimple.core.data.event

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import com.x500x.cursimple.core.data.exportSnapshot
import com.x500x.cursimple.core.data.restoreSnapshot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.scheduleEventsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppBackupStores.SCHEDULE_EVENTS,
)

class DataStoreScheduleEventRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ScheduleEventRepository {

    private val store = context.applicationContext.scheduleEventsDataStore
    private val serializer = ListSerializer(ScheduleEvent.serializer())

    override val eventsFlow: Flow<List<ScheduleEvent>> = store.data.map(::decode)

    override suspend fun upsert(event: ScheduleEvent) {
        store.edit { prefs ->
            val current = decode(prefs)
            val next = if (current.any { it.id == event.id }) {
                current.map { if (it.id == event.id) event else it }
            } else {
                current + event
            }
            prefs[KEY_EVENTS] = json.encodeToString(serializer, next)
        }
    }

    override suspend fun remove(eventId: String) {
        store.edit { prefs ->
            prefs[KEY_EVENTS] = json.encodeToString(serializer, decode(prefs).filterNot { it.id == eventId })
        }
    }

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot =
        store.exportSnapshot(AppBackupStores.SCHEDULE_EVENTS)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        store.restoreSnapshot(snapshot)
    }

    private fun decode(prefs: Preferences): List<ScheduleEvent> =
        prefs[KEY_EVENTS]
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private companion object {
        val KEY_EVENTS = stringPreferencesKey("schedule_events_json")
    }
}
