package com.kebiao.viewer.core.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class ThemeMode { System, Light, Dark }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.Light,
    val termStartDate: LocalDate? = null,
    val advancedToolsEnabled: Boolean = false,
)

interface UserPreferencesRepository {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setTermStartDate(date: LocalDate?)
    suspend fun setAdvancedToolsEnabled(enabled: Boolean)
}
