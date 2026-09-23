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
) {
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
        }

    companion object {
        fun of(accent: ThemeAccent, dark: Boolean): NoticeTheme = from(accent, dark, appColorScheme(accent, dark))

        /** 界面里预览用：和当前界面同一个主题色与深浅色。 */
        @Composable
        fun current(): NoticeTheme {
            val choice = LocalAppThemeChoice.current
            return remember(choice) { of(choice.accent, choice.dark) }
        }

        /** 按偏好里的主题色与深浅色模式取；跟随系统时看 [context] 当前是不是夜间模式。 */
        fun resolve(context: Context, accent: ThemeAccent, mode: ThemeMode): NoticeTheme {
            val dark = when (mode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System ->
                    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
            }
            return of(accent, dark)
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
