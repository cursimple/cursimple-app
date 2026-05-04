package com.kebiao.viewer.core.data.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.widgetPreferencesStore by preferencesDataStore(name = "widget_preferences")

class DataStoreWidgetPreferencesRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : WidgetPreferencesRepository {
    private val store = context.applicationContext.widgetPreferencesStore

    override val widgetDayFlow: Flow<WidgetDay> = store.data.map { preferences ->
        when (preferences[KEY_WIDGET_DAY]) {
            WidgetDay.Tomorrow.name -> WidgetDay.Tomorrow
            else -> WidgetDay.Today
        }
    }

    override val widgetDayOffsetFlow: Flow<Int> = store.data.map { preferences ->
        (preferences[KEY_WIDGET_DAY_OFFSET] ?: 0).coerceIn(MIN_OFFSET, MAX_OFFSET)
    }

    override val timingProfileFlow: Flow<TermTimingProfile?> = store.data.map { preferences ->
        preferences[KEY_TIMING_PROFILE_JSON]
            ?.let { raw -> runCatching { json.decodeFromString<TermTimingProfile>(raw) }.getOrNull() }
    }

    override val scheduleSnapshotFlow: Flow<WidgetScheduleSnapshot?> = store.data.map { preferences ->
        preferences[KEY_SCHEDULE_SNAPSHOT_JSON]
            ?.let { raw -> runCatching { json.decodeFromString<WidgetScheduleSnapshot>(raw) }.getOrNull() }
    }

    override suspend fun setWidgetDay(day: WidgetDay) {
        store.edit { preferences ->
            preferences[KEY_WIDGET_DAY] = day.name
        }
    }

    override suspend fun toggleWidgetDay() {
        store.edit { preferences ->
            preferences[KEY_WIDGET_DAY] = when (preferences[KEY_WIDGET_DAY]) {
                WidgetDay.Tomorrow.name -> WidgetDay.Today.name
                else -> WidgetDay.Tomorrow.name
            }
        }
    }

    override suspend fun setWidgetDayOffset(offset: Int) {
        store.edit { preferences ->
            preferences[KEY_WIDGET_DAY_OFFSET] = offset.coerceIn(MIN_OFFSET, MAX_OFFSET)
        }
    }

    override suspend fun shiftWidgetDayOffset(delta: Int) {
        store.edit { preferences ->
            val current = (preferences[KEY_WIDGET_DAY_OFFSET] ?: 0).coerceIn(MIN_OFFSET, MAX_OFFSET)
            preferences[KEY_WIDGET_DAY_OFFSET] = (current + delta).coerceIn(MIN_OFFSET, MAX_OFFSET)
        }
    }

    override suspend fun widgetDayOffset(appWidgetId: Int): Int {
        val preferences = store.data.first()
        return (preferences[widgetDayOffsetKey(appWidgetId)] ?: preferences[KEY_WIDGET_DAY_OFFSET] ?: 0)
            .coerceIn(MIN_OFFSET, MAX_OFFSET)
    }

    override suspend fun setWidgetDayOffset(appWidgetId: Int, offset: Int) {
        store.edit { preferences ->
            preferences[widgetDayOffsetKey(appWidgetId)] = offset.coerceIn(MIN_OFFSET, MAX_OFFSET)
        }
    }

    override suspend fun shiftWidgetDayOffset(appWidgetId: Int, delta: Int): Int {
        var next = 0
        store.edit { preferences ->
            val current = (preferences[widgetDayOffsetKey(appWidgetId)] ?: preferences[KEY_WIDGET_DAY_OFFSET] ?: 0)
                .coerceIn(MIN_OFFSET, MAX_OFFSET)
            next = (current + delta).coerceIn(MIN_OFFSET, MAX_OFFSET)
            preferences[widgetDayOffsetKey(appWidgetId)] = next
        }
        return next
    }

    override suspend fun clearWidgetDayOffset(appWidgetId: Int) {
        store.edit { preferences ->
            preferences.remove(widgetDayOffsetKey(appWidgetId))
        }
    }

    override suspend fun saveTimingProfile(profile: TermTimingProfile?) {
        store.edit { preferences ->
            if (profile == null) {
                preferences.remove(KEY_TIMING_PROFILE_JSON)
            } else {
                preferences[KEY_TIMING_PROFILE_JSON] = json.encodeToString(profile)
            }
        }
    }

    override suspend fun saveScheduleSnapshot(snapshot: WidgetScheduleSnapshot?) {
        store.edit { preferences ->
            if (snapshot == null) {
                preferences.remove(KEY_SCHEDULE_SNAPSHOT_JSON)
            } else {
                preferences[KEY_SCHEDULE_SNAPSHOT_JSON] = json.encodeToString(snapshot)
            }
        }
    }

    private companion object {
        val KEY_WIDGET_DAY = stringPreferencesKey("widget_day")
        val KEY_WIDGET_DAY_OFFSET = intPreferencesKey("widget_day_offset")
        val KEY_TIMING_PROFILE_JSON = stringPreferencesKey("widget_timing_profile_json")
        val KEY_SCHEDULE_SNAPSHOT_JSON = stringPreferencesKey("widget_schedule_snapshot_json")
        const val MIN_OFFSET = -3650
        const val MAX_OFFSET = 3650

        fun widgetDayOffsetKey(appWidgetId: Int) = intPreferencesKey("widget_day_offset__$appWidgetId")
    }
}
