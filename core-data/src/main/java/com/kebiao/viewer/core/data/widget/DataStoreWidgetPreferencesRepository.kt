package com.kebiao.viewer.core.data.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.widgetPreferencesStore by preferencesDataStore(name = "widget_preferences")

class DataStoreWidgetPreferencesRepository(
    context: Context,
) : WidgetPreferencesRepository {
    private val store = context.applicationContext.widgetPreferencesStore

    override val widgetDayFlow: Flow<WidgetDay> = store.data.map { preferences ->
        when (preferences[KEY_WIDGET_DAY]) {
            WidgetDay.Tomorrow.name -> WidgetDay.Tomorrow
            else -> WidgetDay.Today
        }
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

    private companion object {
        val KEY_WIDGET_DAY = stringPreferencesKey("widget_day")
    }
}
