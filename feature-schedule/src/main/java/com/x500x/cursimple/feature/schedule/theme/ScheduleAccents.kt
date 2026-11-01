package com.x500x.cursimple.feature.schedule.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.x500x.cursimple.core.data.CoursePaletteStyle

data class CoursePaletteEntry(
    val container: Color,
    val onContainer: Color,
)

/** App-supplied secondary course palette; defaults support isolated previews. */
data class ScheduleAccents(
    val gridBackground: Color,
    val gridLine: Color,
    val coursePalette: List<CoursePaletteEntry>,
    val inactiveContainer: Color,
    val inactiveOnContainer: Color,
    val todayContainer: Color,
    val todayOnContainer: Color,
)

val DefaultCoursePaletteLight: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFFDDEBFA), Color(0xFF2C5587)),
    CoursePaletteEntry(Color(0xFFDCEFD7), Color(0xFF325E2A)),
    CoursePaletteEntry(Color(0xFFFBE0E4), Color(0xFF872E48)),
    CoursePaletteEntry(Color(0xFFE5DEF6), Color(0xFF4F388B)),
    CoursePaletteEntry(Color(0xFFFBEFCE), Color(0xFF7E5B14)),
    CoursePaletteEntry(Color(0xFFD5EBE6), Color(0xFF1F5C50)),
    CoursePaletteEntry(Color(0xFFFBE0CB), Color(0xFF8C4A1F)),
)

val DefaultCoursePaletteDark: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFF243A52), Color(0xFFB5D3F0)),
    CoursePaletteEntry(Color(0xFF263F23), Color(0xFFB8DDB1)),
    CoursePaletteEntry(Color(0xFF4A2B33), Color(0xFFF1B8C2)),
    CoursePaletteEntry(Color(0xFF3A2F58), Color(0xFFCFC2EE)),
    CoursePaletteEntry(Color(0xFF4D3F1A), Color(0xFFEFD795)),
    CoursePaletteEntry(Color(0xFF1F3A36), Color(0xFFA8D7CC)),
    CoursePaletteEntry(Color(0xFF4A3220), Color(0xFFF4C49E)),
)

val MorandiCoursePaletteLight: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFFE8D7D8), Color(0xFF5D4042)),
    CoursePaletteEntry(Color(0xFFD8E2DC), Color(0xFF3E5246)),
    CoursePaletteEntry(Color(0xFFD5DDE5), Color(0xFF38495C)),
    CoursePaletteEntry(Color(0xFFEBE3D5), Color(0xFF5C4E38)),
    CoursePaletteEntry(Color(0xFFDDD5E0), Color(0xFF4E4054)),
    CoursePaletteEntry(Color(0xFFE5DDD8), Color(0xFF59483F)),
    CoursePaletteEntry(Color(0xFFDFE3D5), Color(0xFF48523A)),
)

val MorandiCoursePaletteDark: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFF3E2C2E), Color(0xFFE2B8BB)),
    CoursePaletteEntry(Color(0xFF2C3932), Color(0xFFBDCEBF)),
    CoursePaletteEntry(Color(0xFF293542), Color(0xFFB8C8D9)),
    CoursePaletteEntry(Color(0xFF3D3629), Color(0xFFDED0B9)),
    CoursePaletteEntry(Color(0xFF362C3B), Color(0xFFD1BED8)),
    CoursePaletteEntry(Color(0xFF3B3029), Color(0xFFD6BEB2)),
    CoursePaletteEntry(Color(0xFF2F3628), Color(0xFFC5CEA8)),
)

val MacaronCoursePaletteLight: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFFFCE4EC), Color(0xFF880E4F)),
    CoursePaletteEntry(Color(0xFFE8F5E9), Color(0xFF1B5E20)),
    CoursePaletteEntry(Color(0xFFFFF9C4), Color(0xFF826200)),
    CoursePaletteEntry(Color(0xFFE1F5FE), Color(0xFF01579B)),
    CoursePaletteEntry(Color(0xFFF3E5F5), Color(0xFF4A148C)),
    CoursePaletteEntry(Color(0xFFFFE0B2), Color(0xFF943800)),
    CoursePaletteEntry(Color(0xFFE0F2F1), Color(0xFF004D40)),
)

val MacaronCoursePaletteDark: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFF4A1528), Color(0xFFF8BBD0)),
    CoursePaletteEntry(Color(0xFF1B3B22), Color(0xFFC8E6C9)),
    CoursePaletteEntry(Color(0xFF453B12), Color(0xFFFFF59D)),
    CoursePaletteEntry(Color(0xFF123851), Color(0xFFB3E5FC)),
    CoursePaletteEntry(Color(0xFF38194D), Color(0xFFE1BEE7)),
    CoursePaletteEntry(Color(0xFF4D2712), Color(0xFFFFCC80)),
    CoursePaletteEntry(Color(0xFF123D38), Color(0xFF80CBC4)),
)

val VibrantCoursePaletteLight: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFFFEF08A), Color(0xFF854D0E)),
    CoursePaletteEntry(Color(0xFFFED7AA), Color(0xFF9A3412)),
    CoursePaletteEntry(Color(0xFFBAE6FD), Color(0xFF0369A1)),
    CoursePaletteEntry(Color(0xFFA7F3D0), Color(0xFF065F46)),
    CoursePaletteEntry(Color(0xFFFBCFE8), Color(0xFF9D174D)),
    CoursePaletteEntry(Color(0xFFDDD6FE), Color(0xFF5B21B6)),
    CoursePaletteEntry(Color(0xFFD9F99D), Color(0xFF3F6212)),
)

val VibrantCoursePaletteDark: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFF594200), Color(0xFFFEF08A)),
    CoursePaletteEntry(Color(0xFF5C2009), Color(0xFFFFC4A1)),
    CoursePaletteEntry(Color(0xFF083F66), Color(0xFF7DD3FC)),
    CoursePaletteEntry(Color(0xFF064E3B), Color(0xFF6EE7B7)),
    CoursePaletteEntry(Color(0xFF5E1236), Color(0xFFF472B6)),
    CoursePaletteEntry(Color(0xFF3B1C7A), Color(0xFFC4B5FD)),
    CoursePaletteEntry(Color(0xFF2A4505), Color(0xFFBEF264)),
)

val VintageCoursePaletteLight: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFFF5E6D3), Color(0xFF6D3814)),
    CoursePaletteEntry(Color(0xFFE5E8D8), Color(0xFF434D2E)),
    CoursePaletteEntry(Color(0xFFF2DDD5), Color(0xFF743627)),
    CoursePaletteEntry(Color(0xFFE8DFD8), Color(0xFF523F35)),
    CoursePaletteEntry(Color(0xFFDCE3EB), Color(0xFF2F435E)),
    CoursePaletteEntry(Color(0xFFEDE6D0), Color(0xFF5B4F1F)),
    CoursePaletteEntry(Color(0xFFE4DCE4), Color(0xFF4B344D)),
)

val VintageCoursePaletteDark: List<CoursePaletteEntry> = listOf(
    CoursePaletteEntry(Color(0xFF4A2A14), Color(0xFFEDCFB1)),
    CoursePaletteEntry(Color(0xFF2D3520), Color(0xFFCBD3B2)),
    CoursePaletteEntry(Color(0xFF4A241B), Color(0xFFE8B8AC)),
    CoursePaletteEntry(Color(0xFF382C26), Color(0xFFD4C2B8)),
    CoursePaletteEntry(Color(0xFF212E3F), Color(0xFFB7C7DC)),
    CoursePaletteEntry(Color(0xFF3B3419), Color(0xFFDCD0A8)),
    CoursePaletteEntry(Color(0xFF332335), Color(0xFFD1BFD2)),
)

fun coursePaletteForStyle(
    style: CoursePaletteStyle,
    isDark: Boolean,
): List<CoursePaletteEntry> = when (style) {
    CoursePaletteStyle.Default -> if (isDark) DefaultCoursePaletteDark else DefaultCoursePaletteLight
    CoursePaletteStyle.Morandi -> if (isDark) MorandiCoursePaletteDark else MorandiCoursePaletteLight
    CoursePaletteStyle.Macaron -> if (isDark) MacaronCoursePaletteDark else MacaronCoursePaletteLight
    CoursePaletteStyle.Vibrant -> if (isDark) VibrantCoursePaletteDark else VibrantCoursePaletteLight
    CoursePaletteStyle.Vintage -> if (isDark) VintageCoursePaletteDark else VintageCoursePaletteLight
}

private val DefaultAccents = ScheduleAccents(
    gridBackground = Color(0xFFF1F5F2),
    gridLine = Color(0xFFD4DDD7),
    coursePalette = DefaultCoursePaletteLight,
    inactiveContainer = Color(0xFFEEF2EF),
    inactiveOnContainer = Color(0xFF8E988F),
    todayContainer = Color(0xFF1F2A24),
    todayOnContainer = Color(0xFFFFFFFF),
)

val LocalScheduleAccents = staticCompositionLocalOf { DefaultAccents }

val LocalScheduleLocationSuffix = staticCompositionLocalOf { "" }
