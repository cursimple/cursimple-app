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
    /** 没单独给小组件挑过主题色时跟着应用主题走，应用换色小组件底色一起换。 */
    val followsAppThemeAccent: Boolean = true,
    /** 背景图透明度，0 为不透明；与课表背景那一套同义。 */
    val backgroundImageTransparencyPercent: Int = DEFAULT_BACKGROUND_IMAGE_TRANSPARENCY_PERCENT,
) {
    companion object {
        const val DEFAULT_BACKGROUND_IMAGE_TRANSPARENCY_PERCENT = 0

        fun coerceBackgroundImageTransparencyPercent(value: Int): Int = value.coerceIn(0, 100)
    }
}

/** 小组件实际使用的主题色：跟随应用时用 [appThemeAccent]，单独挑过就用挑定的那个。 */
fun WidgetThemePreferences.resolveAccent(appThemeAccent: ThemeAccent): WidgetThemePreferences =
    if (followsAppThemeAccent) copy(themeAccent = appThemeAccent) else this

interface WidgetPreferencesRepository {
    val widgetDayOffsetFlow: Flow<Int>

    /** 选中那一套作息补上开学日期后的形态；一套都没有时为 null。 */
    val timingProfileFlow: Flow<TermTimingProfile?>

    val timingProfileLibraryFlow: Flow<TimingProfileLibrary>

    val timingProfileManuallyEditedFlow: Flow<Boolean>

    val themePreferencesFlow: Flow<WidgetThemePreferences>

    suspend fun setWidgetDayOffset(offset: Int, anchorDateIso: String? = null)

    suspend fun shiftWidgetDayOffset(delta: Int, anchorDateIso: String? = null)

    suspend fun widgetDayOffset(appWidgetId: Int): Int

    /**
     * 手动翻页后的实际偏移。
     *
     * 偏移是「按下那天」往前往后数的天数，[todayIso] 与记下的锚点不同就说明已经跨过零点，
     * 这时偏移作废按 0 返回，日期不会自己再往后顺延一天。
     * [appWidgetId] 传 0 表示所有实例共用的那一份偏移。
     */
    suspend fun effectiveWidgetDayOffset(appWidgetId: Int, todayIso: String): Int

    suspend fun setWidgetDayOffset(appWidgetId: Int, offset: Int, anchorDateIso: String? = null)

    suspend fun shiftWidgetDayOffset(appWidgetId: Int, delta: Int, anchorDateIso: String? = null): Int

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

    /** 取消单独指定的小组件主题色，重新跟随应用主题。 */
    suspend fun followAppThemeAccent()

    suspend fun setWidgetBackgroundImageUri(uri: String)

    suspend fun setWidgetBackgroundImageTransparencyPercent(percent: Int)

    suspend fun clearWidgetBackgroundImage()

    suspend fun setWidgetOpenAppOnDoubleClickEnabled(enabled: Boolean)

    suspend fun resetWidgetThemePreferences()
}
