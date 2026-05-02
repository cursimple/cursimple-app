package com.kebiao.viewer.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.kebiao.viewer.core.data.ThemeAccent
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.feature.schedule.theme.CoursePaletteEntry
import com.kebiao.viewer.feature.schedule.theme.LocalScheduleAccents
import com.kebiao.viewer.feature.schedule.theme.ScheduleAccents

private data class AccentSwatch(
    val light: ColorScheme,
    val dark: ColorScheme,
)

private val GreenSwatch = AccentSwatch(
    light = lightColorScheme(
        primary = Color(0xFF3FA277),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCBEBD9),
        onPrimaryContainer = Color(0xFF0E3A26),
        secondary = Color(0xFF4A86CC),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD7E5F8),
        onSecondaryContainer = Color(0xFF0F2F52),
        tertiary = Color(0xFFD89A4A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCE5C7),
        onTertiaryContainer = Color(0xFF40260A),
        background = Color(0xFFF4F8F4),
        onBackground = Color(0xFF1A2620),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A2620),
        surfaceVariant = Color(0xFFE6EEE7),
        onSurfaceVariant = Color(0xFF566B5F),
        outline = Color(0xFFB6C4BB),
        outlineVariant = Color(0xFFD4DDD7),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF8AD7AF),
        onPrimary = Color(0xFF003824),
        primaryContainer = Color(0xFF1F5A3E),
        onPrimaryContainer = Color(0xFFCBEBD9),
        secondary = Color(0xFF9CC2EC),
        onSecondary = Color(0xFF0F2F52),
        secondaryContainer = Color(0xFF274A73),
        onSecondaryContainer = Color(0xFFD7E5F8),
        tertiary = Color(0xFFEEC086),
        onTertiary = Color(0xFF40260A),
        tertiaryContainer = Color(0xFF614021),
        onTertiaryContainer = Color(0xFFFCE5C7),
        background = Color(0xFF0F1612),
        onBackground = Color(0xFFE2EBE5),
        surface = Color(0xFF161E1A),
        onSurface = Color(0xFFE2EBE5),
        surfaceVariant = Color(0xFF1F2A24),
        onSurfaceVariant = Color(0xFFA9BBB0),
        outline = Color(0xFF566B5F),
        outlineVariant = Color(0xFF374239),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    ),
)

private val BlueSwatch = AccentSwatch(
    light = lightColorScheme(
        primary = Color(0xFF3F6FB5),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E3FB),
        onPrimaryContainer = Color(0xFF0C2A52),
        secondary = Color(0xFF55879A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD3EAF1),
        onSecondaryContainer = Color(0xFF143844),
        tertiary = Color(0xFFD89A4A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCE5C7),
        onTertiaryContainer = Color(0xFF40260A),
        background = Color(0xFFF3F6FB),
        onBackground = Color(0xFF192029),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF192029),
        surfaceVariant = Color(0xFFE3EAF2),
        onSurfaceVariant = Color(0xFF566372),
        outline = Color(0xFFB1BCCB),
        outlineVariant = Color(0xFFD2D9E1),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFA9C7F5),
        onPrimary = Color(0xFF0A2647),
        primaryContainer = Color(0xFF234471),
        onPrimaryContainer = Color(0xFFD3E3FB),
        secondary = Color(0xFFA0CCDB),
        onSecondary = Color(0xFF143844),
        secondaryContainer = Color(0xFF2C5562),
        onSecondaryContainer = Color(0xFFD3EAF1),
        tertiary = Color(0xFFEEC086),
        onTertiary = Color(0xFF40260A),
        tertiaryContainer = Color(0xFF614021),
        onTertiaryContainer = Color(0xFFFCE5C7),
        background = Color(0xFF0F141B),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF161D26),
        onSurface = Color(0xFFE2E8F0),
        surfaceVariant = Color(0xFF1F2832),
        onSurfaceVariant = Color(0xFFAAB6C4),
        outline = Color(0xFF566372),
        outlineVariant = Color(0xFF374253),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    ),
)

private val PurpleSwatch = AccentSwatch(
    light = lightColorScheme(
        primary = Color(0xFF7259B5),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE3DAF7),
        onPrimaryContainer = Color(0xFF2B1D4F),
        secondary = Color(0xFFB35591),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFAD7EC),
        onSecondaryContainer = Color(0xFF4F1740),
        tertiary = Color(0xFFD89A4A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCE5C7),
        onTertiaryContainer = Color(0xFF40260A),
        background = Color(0xFFF6F4FB),
        onBackground = Color(0xFF1F1B29),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1F1B29),
        surfaceVariant = Color(0xFFEAE5F2),
        onSurfaceVariant = Color(0xFF625873),
        outline = Color(0xFFB9B1CB),
        outlineVariant = Color(0xFFD7D2E1),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFC0AEF0),
        onPrimary = Color(0xFF2B1D4F),
        primaryContainer = Color(0xFF453072),
        onPrimaryContainer = Color(0xFFE3DAF7),
        secondary = Color(0xFFEDA5D2),
        onSecondary = Color(0xFF4F1740),
        secondaryContainer = Color(0xFF6E325A),
        onSecondaryContainer = Color(0xFFFAD7EC),
        tertiary = Color(0xFFEEC086),
        onTertiary = Color(0xFF40260A),
        tertiaryContainer = Color(0xFF614021),
        onTertiaryContainer = Color(0xFFFCE5C7),
        background = Color(0xFF14101D),
        onBackground = Color(0xFFE6E2F0),
        surface = Color(0xFF1B1726),
        onSurface = Color(0xFFE6E2F0),
        surfaceVariant = Color(0xFF231E32),
        onSurfaceVariant = Color(0xFFB1A8C4),
        outline = Color(0xFF625873),
        outlineVariant = Color(0xFF3E364D),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    ),
)

private val OrangeSwatch = AccentSwatch(
    light = lightColorScheme(
        primary = Color(0xFFD0763B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFCDBC2),
        onPrimaryContainer = Color(0xFF4A1F08),
        secondary = Color(0xFF7B6042),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEEDBC4),
        onSecondaryContainer = Color(0xFF2A1B0A),
        tertiary = Color(0xFF4A86CC),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD7E5F8),
        onTertiaryContainer = Color(0xFF0F2F52),
        background = Color(0xFFFAF5EE),
        onBackground = Color(0xFF24201A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF24201A),
        surfaceVariant = Color(0xFFEFE5D6),
        onSurfaceVariant = Color(0xFF6E6555),
        outline = Color(0xFFC2B59C),
        outlineVariant = Color(0xFFE0D6C2),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFEFB48A),
        onPrimary = Color(0xFF4A1F08),
        primaryContainer = Color(0xFF6D391A),
        onPrimaryContainer = Color(0xFFFCDBC2),
        secondary = Color(0xFFD8C0A0),
        onSecondary = Color(0xFF2A1B0A),
        secondaryContainer = Color(0xFF4D3D26),
        onSecondaryContainer = Color(0xFFEEDBC4),
        tertiary = Color(0xFF9CC2EC),
        onTertiary = Color(0xFF0F2F52),
        tertiaryContainer = Color(0xFF274A73),
        onTertiaryContainer = Color(0xFFD7E5F8),
        background = Color(0xFF1A1611),
        onBackground = Color(0xFFEEE7DC),
        surface = Color(0xFF221C15),
        onSurface = Color(0xFFEEE7DC),
        surfaceVariant = Color(0xFF2D261C),
        onSurfaceVariant = Color(0xFFC0B49E),
        outline = Color(0xFF6E6555),
        outlineVariant = Color(0xFF463E2F),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    ),
)

private val PinkSwatch = AccentSwatch(
    light = lightColorScheme(
        primary = Color(0xFFC25B7D),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFAD7DF),
        onPrimaryContainer = Color(0xFF4A0F1F),
        secondary = Color(0xFF8C5687),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF1D7EC),
        onSecondaryContainer = Color(0xFF361432),
        tertiary = Color(0xFFD89A4A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCE5C7),
        onTertiaryContainer = Color(0xFF40260A),
        background = Color(0xFFFAF4F6),
        onBackground = Color(0xFF261A20),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF261A20),
        surfaceVariant = Color(0xFFF1E4E9),
        onSurfaceVariant = Color(0xFF735865),
        outline = Color(0xFFC9B0BA),
        outlineVariant = Color(0xFFE5D2DA),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFEFA9BD),
        onPrimary = Color(0xFF4A0F1F),
        primaryContainer = Color(0xFF73314A),
        onPrimaryContainer = Color(0xFFFAD7DF),
        secondary = Color(0xFFD7AED2),
        onSecondary = Color(0xFF361432),
        secondaryContainer = Color(0xFF553050),
        onSecondaryContainer = Color(0xFFF1D7EC),
        tertiary = Color(0xFFEEC086),
        onTertiary = Color(0xFF40260A),
        tertiaryContainer = Color(0xFF614021),
        onTertiaryContainer = Color(0xFFFCE5C7),
        background = Color(0xFF1B1216),
        onBackground = Color(0xFFEFE0E5),
        surface = Color(0xFF231A1F),
        onSurface = Color(0xFFEFE0E5),
        surfaceVariant = Color(0xFF302229),
        onSurfaceVariant = Color(0xFFC4ADB6),
        outline = Color(0xFF735865),
        outlineVariant = Color(0xFF483038),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    ),
)

private fun swatchFor(accent: ThemeAccent): AccentSwatch = when (accent) {
    ThemeAccent.Green -> GreenSwatch
    ThemeAccent.Blue -> BlueSwatch
    ThemeAccent.Purple -> PurpleSwatch
    ThemeAccent.Orange -> OrangeSwatch
    ThemeAccent.Pink -> PinkSwatch
}

private val LightAccents = ScheduleAccents(
    gridBackground = Color(0xFFF6F8F6),
    gridLine = Color(0xFFE2E7E3),
    coursePalette = listOf(
        CoursePaletteEntry(Color(0xFFDDEBFA), Color(0xFF2C5587)),
        CoursePaletteEntry(Color(0xFFDCEFD7), Color(0xFF325E2A)),
        CoursePaletteEntry(Color(0xFFFBE0E4), Color(0xFF872E48)),
        CoursePaletteEntry(Color(0xFFE5DEF6), Color(0xFF4F388B)),
        CoursePaletteEntry(Color(0xFFFBEFCE), Color(0xFF7E5B14)),
        CoursePaletteEntry(Color(0xFFD5EBE6), Color(0xFF1F5C50)),
        CoursePaletteEntry(Color(0xFFFBE0CB), Color(0xFF8C4A1F)),
    ),
    inactiveContainer = Color(0xFFEEF1ED),
    inactiveOnContainer = Color(0xFF8E988F),
    todayContainer = Color(0xFF1F2A24),
    todayOnContainer = Color(0xFFFFFFFF),
)

private val DarkAccents = ScheduleAccents(
    gridBackground = Color(0xFF181E1B),
    gridLine = Color(0xFF272F2B),
    coursePalette = listOf(
        CoursePaletteEntry(Color(0xFF243A52), Color(0xFFB5D3F0)),
        CoursePaletteEntry(Color(0xFF263F23), Color(0xFFB8DDB1)),
        CoursePaletteEntry(Color(0xFF4A2B33), Color(0xFFF1B8C2)),
        CoursePaletteEntry(Color(0xFF3A2F58), Color(0xFFCFC2EE)),
        CoursePaletteEntry(Color(0xFF4D3F1A), Color(0xFFEFD795)),
        CoursePaletteEntry(Color(0xFF1F3A36), Color(0xFFA8D7CC)),
        CoursePaletteEntry(Color(0xFF4A3220), Color(0xFFF4C49E)),
    ),
    inactiveContainer = Color(0xFF23292A),
    inactiveOnContainer = Color(0xFF6B7479),
    todayContainer = Color(0xFFE5EAE6),
    todayOnContainer = Color(0xFF1A1A1A),
)

@Composable
fun ClassScheduleTheme(
    themeMode: ThemeMode,
    themeAccent: ThemeAccent = ThemeAccent.Green,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val swatch = swatchFor(themeAccent)
    val colors = if (isDark) swatch.dark else swatch.light
    val accents = if (isDark) DarkAccents else LightAccents
    CompositionLocalProvider(LocalScheduleAccents provides accents) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
