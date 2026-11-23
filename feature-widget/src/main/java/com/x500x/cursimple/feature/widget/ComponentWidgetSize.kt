package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF

internal data class ComponentWidgetSize(val widthDp: Int, val heightDp: Int)

internal fun componentWidgetSizes(
    advertised: List<ComponentWidgetSize>,
    fallback: ComponentWidgetSize,
): List<ComponentWidgetSize> = advertised.filter { it.widthDp > 0 && it.heightDp > 0 }.distinct().take(4).ifEmpty { listOf(fallback) }

internal fun componentWidgetSizes(options: Bundle): List<ComponentWidgetSize> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        @Suppress("DEPRECATION")
        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            .orEmpty()
            .map { size ->
                if (size.width > 0f && size.height > 0f) {
                    ComponentWidgetSize(size.width.toInt(), size.height.toInt())
                } else ComponentWidgetSize(0, 0)
            }
        return componentWidgetSizes(sizes, componentWidgetSize(options))
    }
    return listOf(componentWidgetSize(options))
}

internal fun componentWidgetSize(options: Bundle): ComponentWidgetSize {
    return componentWidgetSize(
        minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
        maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
        minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
        maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
    )
}

internal fun componentWidgetSize(
    minWidth: Int,
    maxWidth: Int,
    minHeight: Int,
    maxHeight: Int,
): ComponentWidgetSize {
    val width = minWidth.takeIf { it > 0 } ?: maxWidth.takeIf { it > 0 } ?: 330
    val height = minHeight.takeIf { it > 0 } ?: maxHeight.takeIf { it > 0 } ?: 148
    return ComponentWidgetSize(width, height)
}
