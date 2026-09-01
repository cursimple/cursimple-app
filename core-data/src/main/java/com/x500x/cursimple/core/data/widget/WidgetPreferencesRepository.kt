package com.x500x.cursimple.core.data.widget

import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import kotlinx.coroutines.flow.Flow

enum class WidgetBackgroundMode { Theme, Image }

data class WidgetThemePreferences(
    val themeAccent: ThemeAccent = ThemeAccent.Green,
    val backgroundMode: WidgetBackgroundMode = WidgetBackgroundMode.Theme,
    val backgroundImageUri: String? = null,
    val openAppOnDoubleClickEnabled: Boolean = false,
)

interface WidgetPreferencesRepository {
    val widgetDayOffsetFlow: Flow<Int>

    val timingProfileFlow: Flow<TermTimingProfile?>

    val timingProfileManuallyEditedFlow: Flow<Boolean>

    val themePreferencesFlow: Flow<WidgetThemePreferences>

    suspend fun setWidgetDayOffset(offset: Int)

    suspend fun shiftWidgetDayOffset(delta: Int)

    suspend fun widgetDayOffset(appWidgetId: Int): Int

    suspend fun setWidgetDayOffset(appWidgetId: Int, offset: Int)

    suspend fun shiftWidgetDayOffset(appWidgetId: Int, delta: Int): Int

    suspend fun clearWidgetDayOffset(appWidgetId: Int)

    suspend fun saveTimingProfile(profile: TermTimingProfile?)

    suspend fun saveManualTimingProfile(profile: TermTimingProfile)

    suspend fun clearManualTimingProfileFlag()

    suspend fun setWidgetThemeAccent(accent: ThemeAccent)

    suspend fun setWidgetBackgroundImageUri(uri: String)

    suspend fun clearWidgetBackgroundImage()

    suspend fun setWidgetOpenAppOnDoubleClickEnabled(enabled: Boolean)

    suspend fun resetWidgetThemePreferences()
}
