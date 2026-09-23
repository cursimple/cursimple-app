package com.x500x.cursimple.app.notice

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.LocalAppThemeChoice
import com.x500x.cursimple.app.theme.appColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.theme.AccentColors

/**
 * 上课提醒跟着 App 的主题色与深浅色走。
 *
 * 通知和悬浮窗都不在 Compose 里画，拿不到 MaterialTheme，所以这里先把要用的几种颜色
 * 折成 ARGB 带过去。以前两种皮肤的颜色写死成 logo 的蓝，换了主题色也还是那块蓝，
 * 看不出是自家 App 的提醒。
 */
data class NoticeTheme(
    val accent: ThemeAccent,
    val dark: Boolean,
    val primary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    /** 自选主题色（[ThemeAccent.Custom]）的颜色；内置主题用不到。 */
    val customArgb: Int = AccentColors.DEFAULT_CUSTOM_ARGB,
) {
    /**
     * 自选色时品牌卡片的底色；内置主题为 null，用各自那张渐变图。
     *
     * 自选色没法事先备一张图，只能拿一张白底圆角图再着色（Android 12 起 RemoteViews 才能着色），
     * 明度取内置那几张渐变的中段，白字照样看得清。
     */
    val cardTintArgb: Int?
        get() = if (accent == ThemeAccent.Custom) {
            AccentColors.tone(customArgb, 0.40f, maxSaturation = 0.65f, minSaturation = 0.25f)
        } else {
            null
        }

    /**
     * 品牌卡片的底：主题色的渐变圆角块。
     *
     * RemoteViews 在系统进程里 inflate，颜色没法当参数传，圆角渐变也不能靠
     * setBackgroundColor 换色（会丢圆角），所以每个主题色各备一份 drawable。
     */
    @get:DrawableRes
    val cardBackgroundRes: Int
        get() = when (accent) {
            ThemeAccent.Green -> R.drawable.bg_class_notice_card_green
            ThemeAccent.Blue -> R.drawable.bg_class_notice_card_blue
            ThemeAccent.Purple -> R.drawable.bg_class_notice_card_purple
            ThemeAccent.Orange -> R.drawable.bg_class_notice_card_orange
            ThemeAccent.Pink -> R.drawable.bg_class_notice_card_pink
            // 着不了色的旧系统上退回色相最接近的那张；能着色时由 ClassNoticeNotifier 换成白底再着色
            ThemeAccent.Custom -> presetCardBackground(AccentColors.nearestPreset(customArgb))
        }

    private fun presetCardBackground(preset: ThemeAccent): Int = when (preset) {
        ThemeAccent.Green, ThemeAccent.Custom -> R.drawable.bg_class_notice_card_green
        ThemeAccent.Blue -> R.drawable.bg_class_notice_card_blue
        ThemeAccent.Purple -> R.drawable.bg_class_notice_card_purple
        ThemeAccent.Orange -> R.drawable.bg_class_notice_card_orange
        ThemeAccent.Pink -> R.drawable.bg_class_notice_card_pink
    }

    companion object {
        fun of(
            accent: ThemeAccent,
            dark: Boolean,
            customArgb: Int = AccentColors.DEFAULT_CUSTOM_ARGB,
        ): NoticeTheme = from(accent, dark, appColorScheme(accent, dark, customArgb)).copy(customArgb = customArgb)

        /** 界面里预览用：和当前界面同一个主题色与深浅色。 */
        @Composable
        fun current(): NoticeTheme {
            val choice = LocalAppThemeChoice.current
            return remember(choice) { of(choice.accent, choice.dark, choice.customArgb) }
        }

        /** 按偏好里的主题色与深浅色模式取；跟随系统时看 [context] 当前是不是夜间模式。 */
        fun resolve(
            context: Context,
            accent: ThemeAccent,
            mode: ThemeMode,
            customArgb: Int = AccentColors.DEFAULT_CUSTOM_ARGB,
        ): NoticeTheme {
            val dark = when (mode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System ->
                    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
            }
            return of(accent, dark, customArgb)
        }

        private fun from(accent: ThemeAccent, dark: Boolean, scheme: ColorScheme) = NoticeTheme(
            accent = accent,
            dark = dark,
            primary = scheme.primary.toArgb(),
            primaryContainer = scheme.primaryContainer.toArgb(),
            onPrimaryContainer = scheme.onPrimaryContainer.toArgb(),
            surface = scheme.surface.toArgb(),
            onSurface = scheme.onSurface.toArgb(),
            onSurfaceVariant = scheme.onSurfaceVariant.toArgb(),
        )
    }
}
