package com.x500x.cursimple.core.data.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import com.x500x.cursimple.core.data.exportSnapshot
import com.x500x.cursimple.core.data.restoreSnapshot
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
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
    private val appContext = context.applicationContext
    private val store = appContext.widgetPreferencesStore

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

    override val themePreferencesFlow: Flow<WidgetThemePreferences> = store.data.map { preferences ->
        WidgetThemePreferences(
            themeAccent = preferences[KEY_WIDGET_THEME_ACCENT]
                ?.let { runCatching { ThemeAccent.valueOf(it) }.getOrNull() }
                ?: ThemeAccent.Green,
            backgroundMode = preferences[KEY_WIDGET_BACKGROUND_MODE]
                ?.let { runCatching { WidgetBackgroundMode.valueOf(it) }.getOrNull() }
                ?: WidgetBackgroundMode.Theme,
            backgroundImageUri = preferences[KEY_WIDGET_BACKGROUND_IMAGE_URI]?.takeIf(String::isNotBlank),
            openAppOnDoubleClickEnabled = preferences[KEY_WIDGET_OPEN_APP_ON_DOUBLE_CLICK] ?: false,
        )
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

    override suspend fun setWidgetThemeAccent(accent: ThemeAccent) {
        var previousImageUri: String? = null
        store.edit { preferences ->
            previousImageUri = preferences[KEY_WIDGET_BACKGROUND_IMAGE_URI]
            preferences[KEY_WIDGET_THEME_ACCENT] = accent.name
            preferences[KEY_WIDGET_BACKGROUND_MODE] = WidgetBackgroundMode.Theme.name
            preferences.remove(KEY_WIDGET_BACKGROUND_IMAGE_URI)
        }
        releasePersistedReadPermission(previousImageUri)
    }

    override suspend fun setWidgetBackgroundImageUri(uri: String) {
        var previousImageUri: String? = null
        store.edit { preferences ->
            previousImageUri = preferences[KEY_WIDGET_BACKGROUND_IMAGE_URI]
            preferences[KEY_WIDGET_BACKGROUND_MODE] = WidgetBackgroundMode.Image.name
            preferences[KEY_WIDGET_BACKGROUND_IMAGE_URI] = uri
        }
        if (previousImageUri != uri) {
            releasePersistedReadPermission(previousImageUri)
        }
    }

    override suspend fun clearWidgetBackgroundImage() {
        var previousImageUri: String? = null
        store.edit { preferences ->
            previousImageUri = preferences[KEY_WIDGET_BACKGROUND_IMAGE_URI]
            preferences[KEY_WIDGET_BACKGROUND_MODE] = WidgetBackgroundMode.Theme.name
            preferences.remove(KEY_WIDGET_BACKGROUND_IMAGE_URI)
        }
        releasePersistedReadPermission(previousImageUri)
    }

    override suspend fun setWidgetOpenAppOnDoubleClickEnabled(enabled: Boolean) {
        store.edit { preferences ->
            preferences[KEY_WIDGET_OPEN_APP_ON_DOUBLE_CLICK] = enabled
        }
    }

    override suspend fun resetWidgetThemePreferences() {
        var previousImageUri: String? = null
        store.edit { preferences ->
            previousImageUri = preferences[KEY_WIDGET_BACKGROUND_IMAGE_URI]
            preferences.remove(KEY_WIDGET_THEME_ACCENT)
            preferences.remove(KEY_WIDGET_BACKGROUND_MODE)
            preferences.remove(KEY_WIDGET_BACKGROUND_IMAGE_URI)
            preferences.remove(KEY_WIDGET_OPEN_APP_ON_DOUBLE_CLICK)
        }
        releasePersistedReadPermission(previousImageUri)
    }

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot =
        store.exportSnapshot(AppBackupStores.WIDGET_PREFERENCES)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        var previousImageUri: String? = null
        store.edit { preferences ->
            previousImageUri = preferences[KEY_WIDGET_BACKGROUND_IMAGE_URI]
        }
        store.restoreSnapshot(snapshot)
        releasePersistedReadPermission(previousImageUri)
    }

    private fun releasePersistedReadPermission(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private companion object {
        val KEY_WIDGET_DAY = stringPreferencesKey("widget_day")
        val KEY_WIDGET_DAY_OFFSET = intPreferencesKey("widget_day_offset")
        val KEY_TIMING_PROFILE_JSON = stringPreferencesKey("widget_timing_profile_json")
        val KEY_WIDGET_THEME_ACCENT = stringPreferencesKey("widget_theme_accent")
        val KEY_WIDGET_BACKGROUND_MODE = stringPreferencesKey("widget_background_mode")
        val KEY_WIDGET_BACKGROUND_IMAGE_URI = stringPreferencesKey("widget_background_image_uri")
        val KEY_WIDGET_OPEN_APP_ON_DOUBLE_CLICK = booleanPreferencesKey("widget_open_app_on_double_click")
        const val MIN_OFFSET = -3650
        const val MAX_OFFSET = 3650

        fun widgetDayOffsetKey(appWidgetId: Int) = intPreferencesKey("widget_day_offset__$appWidgetId")
    }
}
