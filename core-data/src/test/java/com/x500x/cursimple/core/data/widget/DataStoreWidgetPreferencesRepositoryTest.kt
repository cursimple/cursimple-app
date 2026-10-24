package com.x500x.cursimple.core.data.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository.Companion.removeWidgetThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreWidgetPreferencesRepositoryTest {
    @Test
    fun `theme reset clears opacity glass and mode while retaining widget navigation and timing data`() {
        val retained = mutablePreferencesOf(
            intPreferencesKey("widget_day_offset") to 2,
            intPreferencesKey("widget_day_offset__42") to 3,
            stringPreferencesKey("widget_timing_profile_library_json") to "{\"profiles\":[]}",
            stringPreferencesKey("widget_timing_term_start_date") to "2026-09-01",
        )
        val prefs = retained.toMutablePreferences().apply {
            this[stringPreferencesKey("widget_theme_accent")] = "Blue"
            this[stringPreferencesKey("widget_background_mode")] = "Image"
            this[stringPreferencesKey("widget_background_image_uri")] = "content://images/42"
            this[booleanPreferencesKey("widget_open_app_on_double_click")] = false
            this[booleanPreferencesKey("widget_show_events")] = false
            this[intPreferencesKey("widget_background_opacity_percent")] = 35
            this[booleanPreferencesKey("widget_frosted_glass_enabled")] = true
            this[stringPreferencesKey("widget_theme_mode")] = "Dark"
        }

        prefs.removeWidgetThemePreferences()

        assertEquals(retained.asMap(), prefs.asMap())
    }
}
