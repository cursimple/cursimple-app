package com.x500x.cursimple.feature.plugin.extension

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One theme contract for owned pages, desktop widgets and their previews. */
fun componentTheme(colors: ColorScheme) = buildJsonObject {
    fun color(key: String, value: Color) = put(key, "#%06x".format(value.toArgb() and 0xffffff))
    put("dark", colors.background.luminance() < 0.5f)
    color("background", colors.background); color("surface", colors.surface)
    color("surfaceVariant", colors.surfaceVariant); color("surfaceContainerLow", colors.surfaceContainerLow)
    color("onSurface", colors.onSurface); color("onSurfaceVariant", colors.onSurfaceVariant)
    color("outlineVariant", colors.outlineVariant); color("primary", colors.primary)
    color("onPrimary", colors.onPrimary); color("primaryContainer", colors.primaryContainer)
    color("onPrimaryContainer", colors.onPrimaryContainer); color("error", colors.error)
    color("errorContainer", colors.errorContainer); color("onError", colors.onError)
}
