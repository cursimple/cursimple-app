package com.x500x.cursimple.core.data.widget

import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TimingProfileLibrary
import kotlinx.coroutines.flow.Flow

enum class WidgetBackgroundMode { Theme, Image }

data class WidgetThemePreferences(
    val themeAccent: ThemeAccent = ThemeAccent.Green,
    /** Custom widget accent, inherited from the app when following its theme. */
    val customColorArgb: Int = com.x500x.cursimple.core.data.theme.AccentColors.DEFAULT_CUSTOM_ARGB,
    val backgroundMode: WidgetBackgroundMode = WidgetBackgroundMode.Theme,
    val backgroundImageUri: String? = null,
    val openAppOnDoubleClickEnabled: Boolean = true,
    val followsAppThemeAccent: Boolean = true,
    val backgroundImageTransparencyPercent: Int = DEFAULT_BACKGROUND_IMAGE_TRANSPARENCY_PERCENT,
) {
    companion object {
        const val DEFAULT_BACKGROUND_IMAGE_TRANSPARENCY_PERCENT = 0

        fun coerceBackgroundImageTransparencyPercent(value: Int): Int = value.coerceIn(0, 100)
    }
}

/** Resolve independent widget accents or inherit [appThemeAccent] and [appCustomColorArgb]. */
fun WidgetThemePreferences.resolveAccent(
    appThemeAccent: ThemeAccent,
    appCustomColorArgb: Int = customColorArgb,
): WidgetThemePreferences =
    if (followsAppThemeAccent) copy(themeAccent = appThemeAccent, customColorArgb = appCustomColorArgb) else this

interface WidgetPreferencesRepository {
    val widgetDayOffsetFlow: Flow<Int>

    val timingProfileFlow: Flow<TermTimingProfile?>

    val timingProfileLibraryFlow: Flow<TimingProfileLibrary>

    val timingProfileManuallyEditedFlow: Flow<Boolean>

    val themePreferencesFlow: Flow<WidgetThemePreferences>

    suspend fun setWidgetDayOffset(offset: Int, anchorDateIso: String? = null)

    suspend fun shiftWidgetDayOffset(delta: Int, anchorDateIso: String? = null)

    suspend fun widgetDayOffset(appWidgetId: Int): Int

    /**
     * Day offsets are anchored to [todayIso] and expire after midnight; [appWidgetId] zero
     * shares the offset across instances.
     */
    suspend fun effectiveWidgetDayOffset(appWidgetId: Int, todayIso: String): Int

    suspend fun setWidgetDayOffset(appWidgetId: Int, offset: Int, anchorDateIso: String? = null)

    suspend fun shiftWidgetDayOffset(appWidgetId: Int, delta: Int, anchorDateIso: String? = null): Int

    suspend fun clearWidgetDayOffset(appWidgetId: Int)

    suspend fun saveTimingProfile(profile: TermTimingProfile?)

    suspend fun saveManualTimingProfile(profile: TermTimingProfile)

    suspend fun clearManualTimingProfileFlag()

    suspend fun createTimingProfile(name: String, slotTimes: List<ClassSlotTime>): String

    suspend fun duplicateTimingProfile(id: String, name: String): String?

    suspend fun renameTimingProfile(id: String, name: String)

    /** Keep at least one timing profile. */
    suspend fun deleteTimingProfile(id: String)

    suspend fun activateTimingProfile(id: String)

    suspend fun setWidgetThemeAccent(accent: ThemeAccent)

    suspend fun setWidgetThemeCustomColor(argb: Int)

    /** Clear the independent widget accent to follow the app theme. */
    suspend fun followAppThemeAccent()

    suspend fun setWidgetBackgroundImageUri(uri: String)

    suspend fun setWidgetBackgroundImageTransparencyPercent(percent: Int)

    suspend fun clearWidgetBackgroundImage()

    suspend fun setWidgetOpenAppOnDoubleClickEnabled(enabled: Boolean)

    suspend fun resetWidgetThemePreferences()
}
