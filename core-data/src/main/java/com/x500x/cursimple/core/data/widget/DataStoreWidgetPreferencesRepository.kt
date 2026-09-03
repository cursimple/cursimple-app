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
import com.x500x.cursimple.core.data.shouldReleasePersistedUriPermission
import com.x500x.cursimple.core.data.ThemeAccent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.DEFAULT_TIMING_PROFILE_ID
import com.x500x.cursimple.core.kernel.model.DEFAULT_TIMING_PROFILE_NAME_KEY
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TimingProfileEntry
import com.x500x.cursimple.core.kernel.model.TimingProfileLibrary
import com.x500x.cursimple.core.kernel.model.activating
import com.x500x.cursimple.core.kernel.model.active
import com.x500x.cursimple.core.kernel.model.duplicating
import com.x500x.cursimple.core.kernel.model.legacyTimingProfileLibrary
import com.x500x.cursimple.core.kernel.model.removing
import com.x500x.cursimple.core.kernel.model.renaming
import com.x500x.cursimple.core.kernel.model.resolveWith
import com.x500x.cursimple.core.kernel.model.updatingActive
import com.x500x.cursimple.core.kernel.model.upserting
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

    override val widgetDayOffsetFlow: Flow<Int> = store.data.map { preferences ->
        (preferences[KEY_WIDGET_DAY_OFFSET] ?: 0).coerceIn(MIN_OFFSET, MAX_OFFSET)
    }

    override val timingProfileFlow: Flow<TermTimingProfile?> = store.data.map { preferences ->
        preferences.timingProfileLibrary().active?.resolveWith(preferences.termStartDate())
    }

    override val timingProfileLibraryFlow: Flow<TimingProfileLibrary> = store.data.map { preferences ->
        preferences.timingProfileLibrary()
    }

    override val timingProfileManuallyEditedFlow: Flow<Boolean> = store.data.map { preferences ->
        preferences.timingProfileLibrary().active?.manuallyEdited ?: false
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
                preferences.writeTimingProfileLibrary(TimingProfileLibrary())
                preferences.remove(KEY_TERM_START_DATE)
            } else {
                preferences.writeActiveSlotTimes(profile, manuallyEdited = null)
            }
        }
    }

    override suspend fun saveManualTimingProfile(profile: TermTimingProfile) {
        store.edit { preferences ->
            preferences.writeActiveSlotTimes(profile, manuallyEdited = true)
        }
    }

    override suspend fun clearManualTimingProfileFlag() {
        store.edit { preferences ->
            val library = preferences.timingProfileLibrary()
            preferences.writeTimingProfileLibrary(library.updatingActive { it.copy(manuallyEdited = false) })
        }
    }

    override suspend fun createTimingProfile(name: String, slotTimes: List<ClassSlotTime>): String {
        val id = newTimingProfileId()
        store.edit { preferences ->
            val library = preferences.timingProfileLibrary()
            val entry = TimingProfileEntry(
                id = id,
                name = name.trim(),
                slotTimes = slotTimes,
                timezone = library.active?.timezone.orEmpty(),
                manuallyEdited = true,
            )
            preferences.writeTimingProfileLibrary(library.upserting(entry).activating(id))
        }
        return id
    }

    override suspend fun duplicateTimingProfile(id: String, name: String): String? {
        val newId = newTimingProfileId()
        var created = false
        store.edit { preferences ->
            val library = preferences.timingProfileLibrary()
            val next = library.duplicating(id, newId, name)
            created = next != library
            if (created) preferences.writeTimingProfileLibrary(next)
        }
        return newId.takeIf { created }
    }

    override suspend fun renameTimingProfile(id: String, name: String) {
        store.edit { preferences ->
            preferences.writeTimingProfileLibrary(preferences.timingProfileLibrary().renaming(id, name))
        }
    }

    override suspend fun deleteTimingProfile(id: String) {
        store.edit { preferences ->
            preferences.writeTimingProfileLibrary(preferences.timingProfileLibrary().removing(id))
        }
    }

    override suspend fun activateTimingProfile(id: String) {
        store.edit { preferences ->
            preferences.writeTimingProfileLibrary(preferences.timingProfileLibrary().activating(id))
        }
    }

    /**
     * 作息库缺失时按旧的单份数据推导，读到什么就是什么，不回写。
     * 任何一次写入都会落到作息库键上，此后旧键不再参与。
     */
    private fun Preferences.timingProfileLibrary(): TimingProfileLibrary {
        this[KEY_TIMING_PROFILE_LIBRARY_JSON]
            ?.let { raw -> runCatching { json.decodeFromString<TimingProfileLibrary>(raw) }.getOrNull() }
            ?.let { return it }
        return legacyTimingProfileLibrary(legacyTimingProfile(), this[KEY_TIMING_PROFILE_MANUAL] ?: false)
    }

    private fun Preferences.legacyTimingProfile(): TermTimingProfile? =
        this[KEY_TIMING_PROFILE_JSON]
            ?.let { raw -> runCatching { json.decodeFromString<TermTimingProfile>(raw) }.getOrNull() }

    private fun Preferences.termStartDate(): String =
        this[KEY_TERM_START_DATE] ?: legacyTimingProfile()?.termStartDate.orEmpty()

    private fun MutablePreferences.writeTimingProfileLibrary(library: TimingProfileLibrary) {
        this[KEY_TIMING_PROFILE_LIBRARY_JSON] = json.encodeToString(library)
        remove(KEY_TIMING_PROFILE_JSON)
        remove(KEY_TIMING_PROFILE_MANUAL)
    }

    /** 把整份节次时间表写进选中项；一套都没有时先建一套。 */
    private fun MutablePreferences.writeActiveSlotTimes(profile: TermTimingProfile, manuallyEdited: Boolean?) {
        val library = timingProfileLibrary()
        val current = library.active ?: TimingProfileEntry(
            id = DEFAULT_TIMING_PROFILE_ID,
            nameKey = DEFAULT_TIMING_PROFILE_NAME_KEY,
        )
        val updated = current.copy(
            slotTimes = profile.slotTimes,
            timezone = profile.timezone,
            manuallyEdited = manuallyEdited ?: current.manuallyEdited,
        )
        writeTimingProfileLibrary(library.upserting(updated))
        this[KEY_TERM_START_DATE] = profile.termStartDate
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
        val previousImageUri = store.data.first()[KEY_WIDGET_BACKGROUND_IMAGE_URI]
        store.restoreSnapshot(snapshot)
        val restoredImageUri = store.data.first()[KEY_WIDGET_BACKGROUND_IMAGE_URI]
        if (shouldReleasePersistedUriPermission(previousImageUri, restoredImageUri)) {
            releasePersistedReadPermission(previousImageUri)
        }
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
        val KEY_WIDGET_DAY_OFFSET = intPreferencesKey("widget_day_offset")
        val KEY_TIMING_PROFILE_JSON = stringPreferencesKey("widget_timing_profile_json")
        val KEY_TIMING_PROFILE_MANUAL = booleanPreferencesKey("widget_timing_profile_manual")
        val KEY_TIMING_PROFILE_LIBRARY_JSON = stringPreferencesKey("widget_timing_profile_library_json")
        val KEY_TERM_START_DATE = stringPreferencesKey("widget_timing_term_start_date")
        val KEY_WIDGET_THEME_ACCENT = stringPreferencesKey("widget_theme_accent")
        val KEY_WIDGET_BACKGROUND_MODE = stringPreferencesKey("widget_background_mode")
        val KEY_WIDGET_BACKGROUND_IMAGE_URI = stringPreferencesKey("widget_background_image_uri")
        val KEY_WIDGET_OPEN_APP_ON_DOUBLE_CLICK = booleanPreferencesKey("widget_open_app_on_double_click")
        const val MIN_OFFSET = -3650
        const val MAX_OFFSET = 3650

        fun widgetDayOffsetKey(appWidgetId: Int) = intPreferencesKey("widget_day_offset__$appWidgetId")

        fun newTimingProfileId(): String = "timing-" + java.util.UUID.randomUUID().toString()
    }
}
