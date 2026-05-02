package com.kebiao.viewer.core.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class ThemeMode { System, Light, Dark }

enum class ThemeAccent { Green, Blue, Purple, Orange, Pink }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.Light,
    val themeAccent: ThemeAccent = ThemeAccent.Green,
    val termStartDate: LocalDate? = null,
    val advancedToolsEnabled: Boolean = false,
    val timeZoneId: String = DEFAULT_TIME_ZONE_ID,
    val enabledPluginIds: Set<String> = emptySet(),
    val pluginsSeeded: Boolean = false,
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
    suspend fun setAdvancedToolsEnabled(enabled: Boolean)
    suspend fun setTimeZoneId(timeZoneId: String)
    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean)
    suspend fun seedEnabledPlugins(pluginIds: Set<String>)
}
