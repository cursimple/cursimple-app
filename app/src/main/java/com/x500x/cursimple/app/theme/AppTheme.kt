package com.x500x.cursimple.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.core.data.CoursePaletteStyle
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.theme.AccentColors
import androidx.compose.runtime.remember
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
import com.x500x.cursimple.feature.schedule.theme.ScheduleAccents
import com.x500x.cursimple.feature.schedule.theme.coursePaletteForStyle

private data class AccentSwatch(
    val light: ColorScheme,
    val dark: ColorScheme,
)

// Neutral surfaces shared by every accent: a sage page behind white cards.
private val LightBackground = Color(0xFFF0F4EC)
private val LightSurfaceVariant = Color(0xFFE8EEE6)
private val LightInk = Color(0xFF16241D)
private val LightInkMuted = Color(0xFF4F6458)
private val LightOutline = Color(0xFFB7C4B9)
private val LightOutlineVariant = Color(0xFFDDE6DC)

private val DarkBackground = Color(0xFF0E1411)
private val DarkSurface = Color(0xFF161E1A)
private val DarkSurfaceVariant = Color(0xFF1F2A24)
private val DarkInk = Color(0xFFE3EBE4)
private val DarkInkMuted = Color(0xFFA9BBB0)
private val DarkOutline = Color(0xFF56655B)
private val DarkOutlineVariant = Color(0xFF2C3731)

/** Accent-independent roles: secondary text, amber tertiary and the error red of the design. */
private fun lightScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = primary,
    secondary = Color(0xFF3D5F4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E9DF),
    onSecondaryContainer = Color(0xFF22352A),
    tertiary = Color(0xFF8A6416),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBEFCE),
    onTertiaryContainer = Color(0xFF40260A),
    background = LightBackground,
    onBackground = LightInk,
    surface = Color(0xFFFFFFFF),
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkMuted,
    surfaceTint = primary,
    inverseSurface = Color(0xFF22302A),
    inverseOnSurface = Color(0xFFF2F6F1),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFB3372F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3E1),
    onErrorContainer = Color(0xFF4A0F0B),
)

private fun darkScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF08251A),
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = primary,
    secondary = Color(0xFFA9C7B5),
    onSecondary = Color(0xFF13251C),
    secondaryContainer = Color(0xFF2A3A31),
    onSecondaryContainer = Color(0xFFD7E5DB),
    tertiary = Color(0xFFEEC086),
    onTertiary = Color(0xFF3A2A08),
    tertiaryContainer = Color(0xFF614021),
    onTertiaryContainer = Color(0xFFFCE5C7),
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    surfaceTint = primary,
    inverseSurface = Color(0xFFE3EBE4),
    inverseOnSurface = Color(0xFF17201B),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val GreenSwatch = AccentSwatch(
    light = lightScheme(Color(0xFF26805A), Color(0xFFDCEFE2), Color(0xFF0A3222)),
    dark = darkScheme(Color(0xFF7FD3A9), Color(0xFF1E5540), Color(0xFFCBEBD9)),
)

private val BlueSwatch = AccentSwatch(
    light = lightScheme(Color(0xFF2563A8), Color(0xFFDDEBFA), Color(0xFF0B2A47)),
    dark = darkScheme(Color(0xFF9CC2F0), Color(0xFF1D4066), Color(0xFFDDEBFA)),
)

private val PurpleSwatch = AccentSwatch(
    light = lightScheme(Color(0xFF7A4FC4), Color(0xFFE5DEF6), Color(0xFF251347)),
    dark = darkScheme(Color(0xFFC4AEF2), Color(0xFF3E2A6B), Color(0xFFE5DEF6)),
)

private val OrangeSwatch = AccentSwatch(
    light = lightScheme(Color(0xFFC2410C), Color(0xFFFBE0CB), Color(0xFF431404)),
    dark = darkScheme(Color(0xFFF3A97B), Color(0xFF5C2A10), Color(0xFFFBE0CB)),
)

private val PinkSwatch = AccentSwatch(
    light = lightScheme(Color(0xFFC25B7D), Color(0xFFFAD7DF), Color(0xFF4A0F1F)),
    dark = darkScheme(Color(0xFFEFA9BD), Color(0xFF73314A), Color(0xFFFAD7DF)),
)

/**
 * Complete colors for non-Compose surfaces; [customArgb] applies only to [ThemeAccent.Custom].
 */
fun appColorScheme(
    accent: ThemeAccent,
    dark: Boolean,
    customArgb: Int = AccentColors.DEFAULT_CUSTOM_ARGB,
): ColorScheme = swatchFor(accent, customArgb).let { if (dark) it.dark else it.light }.withThemedContainers()

private fun swatchFor(accent: ThemeAccent, customArgb: Int): AccentSwatch = when (accent) {
    ThemeAccent.Green -> GreenSwatch
    ThemeAccent.Blue -> BlueSwatch
    ThemeAccent.Purple -> PurpleSwatch
    ThemeAccent.Orange -> OrangeSwatch
    ThemeAccent.Pink -> PinkSwatch
    ThemeAccent.Custom -> customSwatch(customArgb)
}

/**
 * Derive a readable light and dark accent from the selected hue on the shared sage-neutral
 * surfaces, so custom themes keep the design's background, cards and text hierarchy.
 */
private fun customSwatch(seed: Int): AccentSwatch {
    fun tone(lightness: Float, maxSat: Float, minSat: Float = 0f, hueShift: Float = 0f): Color {
        val (hue, saturation) = AccentColors.toHsl(seed)
        val s = saturation.coerceIn(minSat.coerceAtMost(maxSat), maxSat)
        return Color(AccentColors.fromHsl(hue + hueShift, s, lightness))
    }
    return AccentSwatch(
        light = lightScheme(
            primary = Color(AccentColors.primary(seed, dark = false)),
            primaryContainer = tone(0.90f, 0.45f, 0.18f),
            onPrimaryContainer = tone(0.18f, 0.60f, 0.18f),
        ),
        dark = darkScheme(
            primary = Color(AccentColors.primary(seed, dark = true)),
            primaryContainer = tone(0.26f, 0.45f, 0.18f),
            onPrimaryContainer = tone(0.90f, 0.45f, 0.18f),
        ),
    )
}

private val LightAccents = ScheduleAccents(
    gridBackground = Color(0xFFFFFFFF),
    gridLine = Color(0xFFE0E8DE),
    coursePalette = listOf(
        CoursePaletteEntry(Color(0xFFDAE7FB), Color(0xFF173A70)),
        CoursePaletteEntry(Color(0xFFD6EFDC), Color(0xFF1B5632)),
        CoursePaletteEntry(Color(0xFFFBDCE3), Color(0xFF7E1A39)),
        CoursePaletteEntry(Color(0xFFE5DEF8), Color(0xFF43278A)),
        CoursePaletteEntry(Color(0xFFFAE9BD), Color(0xFF5E4108)),
        CoursePaletteEntry(Color(0xFFD8EFF0), Color(0xFF145A5C)),
        CoursePaletteEntry(Color(0xFFFBE0CB), Color(0xFF7A3A12)),
    ),
    inactiveContainer = Color(0xFFF0F5EF),
    inactiveOnContainer = Color(0xFF8FA196),
    todayContainer = Color(0xFF26805A),
    todayOnContainer = Color(0xFFFFFFFF),
)

private val DarkAccents = ScheduleAccents(
    gridBackground = Color(0xFF161E1A),
    gridLine = Color(0xFF26312B),
    coursePalette = listOf(
        CoursePaletteEntry(Color(0xFF243A52), Color(0xFFB5D3F0)),
        CoursePaletteEntry(Color(0xFF263F23), Color(0xFFB8DDB1)),
        CoursePaletteEntry(Color(0xFF4A2B33), Color(0xFFF1B8C2)),
        CoursePaletteEntry(Color(0xFF3A2F58), Color(0xFFCFC2EE)),
        CoursePaletteEntry(Color(0xFF4D3F1A), Color(0xFFEFD795)),
        CoursePaletteEntry(Color(0xFF1F3A36), Color(0xFFA8D7CC)),
        CoursePaletteEntry(Color(0xFF4A3220), Color(0xFFF4C49E)),
    ),
    inactiveContainer = Color(0xFF232B27),
    inactiveOnContainer = Color(0xFF6B7770),
    todayContainer = Color(0xFF7FD3A9),
    todayOnContainer = Color(0xFF08251A),
)

/** Compact, heavy type scale of the redesign: bold 13.5–17sp titles over 11–13.5sp support text. */
private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
        ),
        titleMedium = titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
        ),
        titleSmall = titleSmall.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp,
        ),
    )
}

/** Rounded card surfaces: 16dp list cards, 22dp tiles, 28dp sheets and dialogs. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ClassScheduleTheme(
    themeMode: ThemeMode,
    themeAccent: ThemeAccent = ThemeAccent.Green,
    /** Used only with [ThemeAccent.Custom]. */
    customAccentArgb: Int = AccentColors.DEFAULT_CUSTOM_ARGB,
    paletteStyle: CoursePaletteStyle = CoursePaletteStyle.Default,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = remember(themeAccent, customAccentArgb, isDark) { appColorScheme(themeAccent, isDark, customAccentArgb) }
    val baseAccents = if (isDark) DarkAccents else LightAccents
    val coursePalette = remember(paletteStyle, isDark) { coursePaletteForStyle(paletteStyle, isDark) }
    val accents = remember(baseAccents, coursePalette) { baseAccents.copy(coursePalette = coursePalette) }
    CompositionLocalProvider(
        LocalScheduleAccents provides accents,
        LocalAppThemeChoice provides AppThemeChoice(themeAccent, isDark, customAccentArgb),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Current resolved theme for notification previews outside Compose. */
data class AppThemeChoice(
    val accent: ThemeAccent,
    val dark: Boolean,
    val customArgb: Int = AccentColors.DEFAULT_CUSTOM_ARGB,
)

val LocalAppThemeChoice = staticCompositionLocalOf { AppThemeChoice(ThemeAccent.Green, dark = false) }

/** Interpolate surface-container colors from the theme to avoid fallback Material colors. */
private fun ColorScheme.withThemedContainers(): ColorScheme {
    fun mix(t: Float) = lerp(surface, surfaceVariant, t)
    return copy(
        surfaceContainerLowest = surface,
        surfaceContainerLow = mix(0.25f),
        surfaceContainer = mix(0.45f),
        surfaceContainerHigh = mix(0.65f),
        surfaceContainerHighest = surfaceVariant,
        surfaceBright = surface,
        surfaceDim = mix(0.85f),
    )
}
