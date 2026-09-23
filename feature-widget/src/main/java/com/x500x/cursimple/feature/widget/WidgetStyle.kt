@file:OptIn(androidx.glance.ExperimentalGlanceApi::class)

package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.glance.layout.size
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.widget.WidgetBackgroundMode
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.theme.AccentColors

/** 小组件里跟着主题色走的三种底：整张卡片、普通一行、突出的一行（正在上、已过）。 */
internal enum class WidgetSurfaceTone(
    /** 自选色时这一档的明度，照着内置五色那几张图的明度取。 */
    val customLightness: Float,
) {
    Card(0.90f),
    Row(0.79f),
    RowVariant(0.69f),
}

/**
 * 给 [viewId] 铺上主题色的底。
 *
 * 内置主题各有一张现成的图；自选色没法事先备图，Android 12 起用白底图再着色，
 * 更早的系统 RemoteViews 不能着色，退回色相最接近的内置那张。
 * 换回内置色时要把之前着的色清掉：启动器刷新时会在原来那个 View 上重放，不清就残留着上次的颜色。
 */
internal fun RemoteViews.applyAccentBackground(
    viewId: Int,
    theme: WidgetThemePreferences,
    tone: WidgetSurfaceTone,
) {
    val canTint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (theme.themeAccent == ThemeAccent.Custom && canTint) {
        val base = when (tone) {
            WidgetSurfaceTone.Card -> R.drawable.widget_bg_card_tintable
            WidgetSurfaceTone.Row, WidgetSurfaceTone.RowVariant -> R.drawable.widget_bg_surface_tintable_clickable
        }
        val color = AccentColors.tone(
            theme.customColorArgb,
            tone.customLightness,
            maxSaturation = 0.45f,
            minSaturation = 0.15f,
        )
        setInt(viewId, "setBackgroundResource", base)
        setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))
        return
    }
    val preset = theme.themeAccent.takeIf { it != ThemeAccent.Custom }
        ?: AccentColors.nearestPreset(theme.customColorArgb)
    val res = when (tone) {
        WidgetSurfaceTone.Card -> widgetCardBackground(preset)
        WidgetSurfaceTone.Row -> widgetRowBackground(preset)
        WidgetSurfaceTone.RowVariant -> widgetRowVariantBackground(preset)
    }
    setInt(viewId, "setBackgroundResource", res)
    if (canTint) setColorStateList(viewId, "setBackgroundTintList", null)
}

/**
 * 行里强调用的字色（节次名、提醒时间）：主题色压到浅底上看得清的明度。
 * 以前写死成薄荷绿，换了主题色、尤其是自选色后，绿字压在别的颜色上很突兀。
 */
internal fun widgetAccentTextColor(theme: WidgetThemePreferences): Int {
    val base = if (theme.themeAccent == ThemeAccent.Custom) {
        theme.customColorArgb
    } else {
        AccentColors.presetPrimary(theme.themeAccent)
    }
    return AccentColors.tone(base, 0.36f, maxSaturation = 0.75f, minSaturation = 0.3f)
}

private fun widgetCardBackground(accent: ThemeAccent): Int = when (accent) {
    ThemeAccent.Green, ThemeAccent.Custom -> R.drawable.widget_bg_card_green
    ThemeAccent.Blue -> R.drawable.widget_bg_card_blue
    ThemeAccent.Purple -> R.drawable.widget_bg_card_purple
    ThemeAccent.Orange -> R.drawable.widget_bg_card_orange
    ThemeAccent.Pink -> R.drawable.widget_bg_card_pink
}

internal fun RemoteViews.applyWidgetBackground(
    context: Context,
    rootId: Int,
    theme: WidgetThemePreferences,
) {
    applyAccentBackground(rootId, theme, WidgetSurfaceTone.Card)
    val imageUri = theme.backgroundImageUri
        ?.takeIf(String::isNotBlank)
        ?.takeIf { theme.backgroundMode == WidgetBackgroundMode.Image }
    val bitmap = imageUri?.let { loadWidgetBackgroundBitmap(context, it) }
    if (bitmap != null) {
        setImageViewBitmap(R.id.widget_background_image, bitmap)
        // 透明度和课表背景那套同义：0 为不透明，100 为完全透明
        val opaque = 100 - theme.backgroundImageTransparencyPercent.coerceIn(0, 100)
        setInt(R.id.widget_background_image, "setImageAlpha", (opaque * 255 / 100))
        setViewVisibility(R.id.widget_background_image, View.VISIBLE)
        setViewVisibility(R.id.widget_background_scrim, View.VISIBLE)
        setViewVisibility(R.id.widget_background_error, View.GONE)
    } else {
        setViewVisibility(R.id.widget_background_image, View.GONE)
        setViewVisibility(R.id.widget_background_scrim, View.GONE)
        setViewVisibility(
            R.id.widget_background_error,
            if (imageUri == null) View.GONE else View.VISIBLE,
        )
    }
}

private fun loadWidgetBackgroundBitmap(context: Context, uriString: String): Bitmap? {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWidgetBackgroundWithImageDecoder(context, uri)?.let { return it }
    }
    return decodeWidgetBackgroundWithBitmapFactory(context, uri)
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWidgetBackgroundWithImageDecoder(context: Context, uri: Uri): Bitmap? =
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (targetWidth, targetHeight) = scaledWidgetBackgroundSize(
                info.size.width,
                info.size.height,
            )
            decoder.setTargetSize(targetWidth, targetHeight)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            } else {
                @Suppress("DEPRECATION")
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM)
        }
    }.getOrNull()

private fun decodeWidgetBackgroundWithBitmapFactory(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            BitmapFactory.decodeStream(input, null, bounds)
        }
    }.getOrNull()
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateWidgetBackgroundSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            BitmapFactory.decodeStream(input, null, decodeOptions)
                ?.scaleDownForWidgetBackground()
        }
    }.getOrNull()
}

private fun calculateWidgetBackgroundSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    while (width / sampleSize > WIDGET_BACKGROUND_MAX_EDGE || height / sampleSize > WIDGET_BACKGROUND_MAX_EDGE) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun scaledWidgetBackgroundSize(width: Int, height: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return WIDGET_BACKGROUND_MAX_EDGE to WIDGET_BACKGROUND_MAX_EDGE
    val maxEdge = maxOf(width, height)
    if (maxEdge <= WIDGET_BACKGROUND_MAX_EDGE) return width to height
    val targetWidth = (width.toLong() * WIDGET_BACKGROUND_MAX_EDGE / maxEdge)
        .toInt()
        .coerceAtLeast(1)
    val targetHeight = (height.toLong() * WIDGET_BACKGROUND_MAX_EDGE / maxEdge)
        .toInt()
        .coerceAtLeast(1)
    return targetWidth to targetHeight
}

private fun Bitmap.scaleDownForWidgetBackground(): Bitmap {
    val (targetWidth, targetHeight) = scaledWidgetBackgroundSize(width, height)
    if (targetWidth == width && targetHeight == height) return this
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true).also {
        if (it != this) recycle()
    }
}

private const val WIDGET_BACKGROUND_MAX_EDGE = 384

/** 读取小组件当前尺寸选项换算尺寸档案；读不到尺寸时按默认值。 */
internal fun widgetSizeClass(manager: AppWidgetManager, appWidgetId: Int): WidgetSizeClass {
    val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
    val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
    val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
    return WidgetSizeClass.fromDp(
        widthDp = if (widthDp > 0) widthDp else DEFAULT_WIDGET_MIN_WIDTH_DP,
        heightDp = if (heightDp > 0) heightDp else DEFAULT_WIDGET_MIN_HEIGHT_DP,
    )
}

private const val DEFAULT_WIDGET_MIN_WIDTH_DP = 220
private const val DEFAULT_WIDGET_MIN_HEIGHT_DP = 180
