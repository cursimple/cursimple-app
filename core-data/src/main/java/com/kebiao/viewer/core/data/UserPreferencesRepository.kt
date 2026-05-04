package com.kebiao.viewer.core.data

import kotlinx.coroutines.flow.Flow
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import java.time.LocalDate
import java.time.LocalDateTime

enum class ThemeMode { System, Light, Dark }

enum class ThemeAccent { Green, Blue, Purple, Orange, Pink }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.Light,
    val themeAccent: ThemeAccent = ThemeAccent.Green,
    val termStartDate: LocalDate? = null,
    val developerModeEnabled: Boolean = false,
    val timeZoneId: String = DEFAULT_TIME_ZONE_ID,
    val totalScheduleDisplayEnabled: Boolean = true,
    val enabledPluginIds: Set<String> = emptySet(),
    val pluginsSeeded: Boolean = false,
    val temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    val debugForcedDateTime: LocalDateTime? = null,
    val disclaimerAccepted: Boolean = false,
    /** True once the persisted prefs have been read at least once. False = still loading. */
    val loaded: Boolean = false,
) {
    companion object {
        const val DEFAULT_TIME_ZONE_ID: String = "Asia/Shanghai"
    }
}

interface UserPreferencesRepository {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeAccent(accent: ThemeAccent)
    suspend fun setTermStartDate(date: LocalDate?)
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    suspend fun setTimeZoneId(timeZoneId: String)
    suspend fun setTotalScheduleDisplayEnabled(enabled: Boolean)
    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean)
    suspend fun seedEnabledPlugins(pluginIds: Set<String>)
    suspend fun upsertTemporaryScheduleOverride(override: TemporaryScheduleOverride)
    suspend fun removeTemporaryScheduleOverride(id: String)
    suspend fun clearTemporaryScheduleOverrides()
    suspend fun setDebugForcedDateTime(dateTime: LocalDateTime?)
    suspend fun setDisclaimerAccepted(accepted: Boolean)
}
