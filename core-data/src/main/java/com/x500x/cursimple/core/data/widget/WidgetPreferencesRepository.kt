package com.x500x.cursimple.core.data.widget

import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TimingProfileLibrary
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

    /** 选中那一套作息补上开学日期后的形态；一套都没有时为 null。 */
    val timingProfileFlow: Flow<TermTimingProfile?>

    val timingProfileLibraryFlow: Flow<TimingProfileLibrary>

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

    /** 新建一套作息并选中它，返回新建项的 id。 */
    suspend fun createTimingProfile(name: String, slotTimes: List<ClassSlotTime>): String

    /** 复制一套作息，副本不自动选中；源不存在时返回 null。 */
    suspend fun duplicateTimingProfile(id: String, name: String): String?

    suspend fun renameTimingProfile(id: String, name: String)

    /** 删除一套作息；只剩一套时不做任何事。 */
    suspend fun deleteTimingProfile(id: String)

    suspend fun activateTimingProfile(id: String)

    suspend fun setWidgetThemeAccent(accent: ThemeAccent)

    suspend fun setWidgetBackgroundImageUri(uri: String)

    suspend fun clearWidgetBackgroundImage()

    suspend fun setWidgetOpenAppOnDoubleClickEnabled(enabled: Boolean)

    suspend fun resetWidgetThemePreferences()
}
