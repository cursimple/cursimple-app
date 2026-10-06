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

/** Resolve theme colors to ARGB for notification and overlay rendering outside Compose. */
data class NoticeTheme(
    val accent: ThemeAccent,
    val dark: Boolean,
    val primary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val customArgb: Int = AccentColors.DEFAULT_CUSTOM_ARGB,
) {
    /**
     * Tint custom card backgrounds on supported systems; built-in themes use gradient
     * resources.
     */
    val cardTintArgb: Int?
        get() = if (accent == ThemeAccent.Custom) {
            AccentColors.tone(customArgb, 0.40f, maxSaturation = 0.65f, minSaturation = 0.25f)
        } else {
            null
        }

    /** Predefined gradient drawables preserve rounded RemoteViews backgrounds. */
    @get:DrawableRes
    val cardBackgroundRes: Int
        get() = when (accent) {
            ThemeAccent.Green -> R.drawable.bg_class_notice_card_green
            ThemeAccent.Blue -> R.drawable.bg_class_notice_card_blue
            ThemeAccent.Purple -> R.drawable.bg_class_notice_card_purple
            ThemeAccent.Orange -> R.drawable.bg_class_notice_card_orange
            ThemeAccent.Pink -> R.drawable.bg_class_notice_card_pink
            // Use the closest built-in hue when RemoteViews tinting is unsupported.
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

        @Composable
        fun current(): NoticeTheme {
            val choice = LocalAppThemeChoice.current
            return remember(choice) { of(choice.accent, choice.dark, choice.customArgb) }
        }

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
