package com.kebiao.viewer.core.data.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import kotlinx.coroutines.flow.Flow
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
            val current = preferences[KEY_WIDGET_DAY_OFFSET] ?: 0
            preferences[KEY_WIDGET_DAY_OFFSET] = (current + delta).coerceIn(MIN_OFFSET, MAX_OFFSET)
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

    private companion object {
        val KEY_WIDGET_DAY = stringPreferencesKey("widget_day")
        val KEY_WIDGET_DAY_OFFSET = intPreferencesKey("widget_day_offset")
        val KEY_TIMING_PROFILE_JSON = stringPreferencesKey("widget_timing_profile_json")
        const val MIN_OFFSET = -180
        const val MAX_OFFSET = 180
    }
}
