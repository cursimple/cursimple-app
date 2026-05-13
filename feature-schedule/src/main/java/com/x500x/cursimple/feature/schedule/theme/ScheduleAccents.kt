package com.x500x.cursimple.feature.schedule.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CoursePaletteEntry(
    val container: Color,
    val onContainer: Color,
)

/**
 * 课程网格使用的次级调色板。app 模块根据当前主题为其提供具体值；
 * 默认值用于预览或未提供 CompositionLocal 的场景。
 */
data class ScheduleAccents(
    val gridBackground: Color,
    val gridLine: Color,
    val coursePalette: List<CoursePaletteEntry>,
    val inactiveContainer: Color,
    val inactiveOnContainer: Color,
    val todayContainer: Color,
    val todayOnContainer: Color,
)

private val DefaultAccents = ScheduleAccents(
    gridBackground = Color(0xFFF1F5F2),
    gridLine = Color(0xFFD4DDD7),
    coursePalette = listOf(
        CoursePaletteEntry(Color(0xFFDDEBFA), Color(0xFF2C5587)),
        CoursePaletteEntry(Color(0xFFDCEFD7), Color(0xFF325E2A)),
        CoursePaletteEntry(Color(0xFFFBE0E4), Color(0xFF872E48)),
        CoursePaletteEntry(Color(0xFFE5DEF6), Color(0xFF4F388B)),
        CoursePaletteEntry(Color(0xFFFBEFCE), Color(0xFF7E5B14)),
        CoursePaletteEntry(Color(0xFFD5EBE6), Color(0xFF1F5C50)),
        CoursePaletteEntry(Color(0xFFFBE0CB), Color(0xFF8C4A1F)),
    ),
    inactiveContainer = Color(0xFFEEF2EF),
    inactiveOnContainer = Color(0xFF8E988F),
    todayContainer = Color(0xFF1F2A24),
    todayOnContainer = Color(0xFFFFFFFF),
)

val LocalScheduleAccents = staticCompositionLocalOf { DefaultAccents }
