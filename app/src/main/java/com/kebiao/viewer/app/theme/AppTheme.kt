package com.kebiao.viewer.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.feature.schedule.theme.CoursePaletteEntry
import com.kebiao.viewer.feature.schedule.theme.LocalScheduleAccents
import com.kebiao.viewer.feature.schedule.theme.ScheduleAccents

private val LightColors: ColorScheme = lightColorScheme(
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
)

private val DarkColors: ColorScheme = darkColorScheme(
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
)

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
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (isDark) DarkColors else LightColors
    val accents = if (isDark) DarkAccents else LightAccents
    CompositionLocalProvider(LocalScheduleAccents provides accents) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
