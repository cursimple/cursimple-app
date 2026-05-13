package com.x500x.cursimple.core.data.widget

import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import kotlinx.coroutines.flow.Flow

enum class WidgetDay {
    Today,
    Tomorrow,
}

enum class WidgetBackgroundMode { Theme, Image }

data class WidgetThemePreferences(
    val themeAccent: ThemeAccent = ThemeAccent.Green,
    val backgroundMode: WidgetBackgroundMode = WidgetBackgroundMode.Theme,
    val backgroundImageUri: String? = null,
    val openAppOnDoubleClickEnabled: Boolean = false,
) {
    fun selectTheme(accent: ThemeAccent): WidgetThemePreferences =
        copy(themeAccent = accent, backgroundMode = WidgetBackgroundMode.Theme, backgroundImageUri = null)

    fun selectBackgroundImage(uri: String): WidgetThemePreferences =
        copy(backgroundMode = WidgetBackgroundMode.Image, backgroundImageUri = uri)

    fun clearBackgroundImage(): WidgetThemePreferences =
        copy(backgroundMode = WidgetBackgroundMode.Theme, backgroundImageUri = null)
}

interface WidgetPreferencesRepository {
    val widgetDayFlow: Flow<WidgetDay>

    val widgetDayOffsetFlow: Flow<Int>

    val timingProfileFlow: Flow<TermTimingProfile?>

    val themePreferencesFlow: Flow<WidgetThemePreferences>

    suspend fun setWidgetDay(day: WidgetDay)

    suspend fun toggleWidgetDay()

    suspend fun setWidgetDayOffset(offset: Int)

    suspend fun shiftWidgetDayOffset(delta: Int)

    suspend fun widgetDayOffset(appWidgetId: Int): Int

    suspend fun setWidgetDayOffset(appWidgetId: Int, offset: Int)

    suspend fun shiftWidgetDayOffset(appWidgetId: Int, delta: Int): Int

    suspend fun clearWidgetDayOffset(appWidgetId: Int)

    suspend fun saveTimingProfile(profile: TermTimingProfile?)

    suspend fun setWidgetThemeAccent(accent: ThemeAccent)

    suspend fun setWidgetBackgroundImageUri(uri: String)

    suspend fun clearWidgetBackgroundImage()

    suspend fun setWidgetOpenAppOnDoubleClickEnabled(enabled: Boolean)

    suspend fun resetWidgetThemePreferences()
}
