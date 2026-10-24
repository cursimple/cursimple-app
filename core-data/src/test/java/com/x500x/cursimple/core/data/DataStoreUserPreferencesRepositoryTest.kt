package com.x500x.cursimple.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository.Companion.decodeCustomCourseColors
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository.Companion.encodeCustomCourseColors
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository.Companion.removeAllSettings
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository.Companion.removeScheduleAppearanceAndDisplay
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository.Companion.updateCustomCourseColor
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreUserPreferencesRepositoryTest {
    private val colorsKey = stringPreferencesKey("schedule_custom_course_colors")

    @Test
    fun `JSON colors preserve legal course ids and unsigned ARGB values`() {
        val colors = mapOf(
            "CS=01" to 0xFFFFFFFFL,
            "CS;01" to 0xFF123456L,
            "引号\"和反斜线\\与换行\n课程🎨" to 0x80123456L,
            " padded id " to 0L,
        )

        val encoded = encodeCustomCourseColors(colors)

        assertEquals(colors, Json.decodeFromString<Map<String, Long>>(encoded))
        assertEquals(colors, decodeCustomCourseColors(encoded))
        assertEquals("{}", encodeCustomCourseColors(emptyMap()))
    }

    @Test
    fun `legacy colors retain valid entries and last duplicate while ignoring malformed entries`() {
        assertEquals(
            mapOf("course" to 0xFFFFFFFFL, "课程" to 42L, "{course" to -1L),
            decodeCustomCourseColors(
                "course=1;broken;bad=NaN;extra=equals=2;课程=42;course=4294967295;{course=-1;",
            ),
        )
    }

    @Test
    fun `absent or malformed color data does not prevent preference loading`() {
        listOf(null, "", " \n ", "{}", "not-json", "{", "{\"course\":", "{\"course\":\"bad\"}", "[1,2]")
            .forEach { raw -> assertEquals("input: $raw", emptyMap<String, Long>(), decodeCustomCourseColors(raw)) }
    }

    @Test
    fun `editing legacy colors migrates to JSON and preserves other courses through updates and removals`() {
        val prefs = mutablePreferencesOf(colorsKey to "alpha=4294967295;beta=4279383126")
        val specialId = "CS;=\"\\\n课程"
        val migrated = mapOf("alpha" to 0xFFFFFFFFL, "beta" to 0xFF123456L, specialId to 0x80123456L)

        prefs.updateCustomCourseColor(specialId, 0x80123456L)
        assertEquals(migrated, Json.decodeFromString<Map<String, Long>>(prefs[colorsKey]!!))

        prefs.updateCustomCourseColor("alpha", 0L)
        assertEquals(migrated + ("alpha" to 0L), decodeCustomCourseColors(prefs[colorsKey]))

        prefs.updateCustomCourseColor(specialId, null)
        assertEquals(mapOf("alpha" to 0L, "beta" to 0xFF123456L), decodeCustomCourseColors(prefs[colorsKey]))
    }

    @Test
    fun `removing a legacy color migrates remaining colors without recreating the removed entry`() {
        val prefs = mutablePreferencesOf(colorsKey to "alpha=1;beta=2")

        prefs.updateCustomCourseColor("alpha", null)
        assertEquals(mapOf("beta" to 2L), Json.decodeFromString<Map<String, Long>>(prefs[colorsKey]!!))

        prefs.updateCustomCourseColor("missing", null)
        assertEquals(mapOf("beta" to 2L), decodeCustomCourseColors(prefs[colorsKey]))

        prefs.updateCustomCourseColor("beta", null)
        assertEquals("{}", prefs[colorsKey])
        assertEquals(emptyMap<String, Long>(), decodeCustomCourseColors(prefs[colorsKey]))
    }

    @Test
    fun `schedule reset clears new display switches and palette but retains notification and term preferences`() {
        val retained = mutablePreferencesOf(
            longPreferencesKey("term_start_epoch_day") to 20_000L,
            stringPreferencesKey("theme_mode") to "Dark",
            booleanPreferencesKey("class_notice_evening_briefing_enabled") to true,
        )
        val prefs = retained.toMutablePreferences().apply {
            this[booleanPreferencesKey("schedule_display_timeline_indicator_enabled")] = true
            this[booleanPreferencesKey("schedule_display_grid_dashed_lines_enabled")] = true
            this[stringPreferencesKey("schedule_course_palette_style")] = "Pastel"
            this[colorsKey] = encodeCustomCourseColors(mapOf("CS;=01" to 0xFFFFFFFFL))
        }

        prefs.removeScheduleAppearanceAndDisplay()

        assertEquals(retained.asMap(), prefs.asMap())
    }

    @Test
    fun `all settings reset clears all new notice parameters and schedule settings while retaining user data`() {
        val retained = mutablePreferencesOf(
            longPreferencesKey("term_start_epoch_day") to 20_000L,
            stringSetPreferencesKey("enabled_plugin_ids") to setOf("plugin-a"),
            stringPreferencesKey("temporary_schedule_overrides_json") to "[]",
        )
        val prefs = retained.toMutablePreferences().apply {
            this[booleanPreferencesKey("schedule_display_timeline_indicator_enabled")] = true
            this[booleanPreferencesKey("schedule_display_grid_dashed_lines_enabled")] = true
            this[booleanPreferencesKey("class_notice_evening_briefing_enabled")] = true
            this[intPreferencesKey("class_notice_evening_briefing_hour")] = 18
            this[intPreferencesKey("class_notice_evening_briefing_minute")] = 15
            this[booleanPreferencesKey("class_notice_transit_reminder_enabled")] = true
            this[booleanPreferencesKey("class_notice_exam_alert_enabled")] = true
            this[intPreferencesKey("class_notice_exam_alert_advance_minutes")] = 90
            this[stringPreferencesKey("schedule_course_palette_style")] = "Pastel"
            this[colorsKey] = encodeCustomCourseColors(mapOf("course" to 0xFFFFFFFFL))
        }

        prefs.removeAllSettings()

        assertEquals(retained.asMap(), prefs.asMap())
    }
}
