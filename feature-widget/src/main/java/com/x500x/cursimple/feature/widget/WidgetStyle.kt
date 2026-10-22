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
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.widget.WidgetBackgroundMode
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.theme.AccentColors

internal enum class WidgetSurfaceTone(
    val customLightness: Float,
) {
    Card(0.90f),
    Row(0.79f),
    RowVariant(0.69f),
}

internal fun resolveWidgetIsDark(context: Context, mode: com.x500x.cursimple.core.data.widget.WidgetThemeMode): Boolean = when (mode) {
    com.x500x.cursimple.core.data.widget.WidgetThemeMode.Light -> false
    com.x500x.cursimple.core.data.widget.WidgetThemeMode.Dark -> true
    com.x500x.cursimple.core.data.widget.WidgetThemeMode.System -> {
        val uiMode = context.resources.configuration.uiMode
        (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

/**
 * Tint custom backgrounds on supported APIs; use closest resources otherwise. Clear old tint
 * when switching back to a built-in theme.
 */
internal fun RemoteViews.applyAccentBackground(
    viewId: Int,
    theme: WidgetThemePreferences,
    tone: WidgetSurfaceTone,
    isDark: Boolean = false,
) {
    val canTint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val opacity = theme.backgroundOpacityPercent.coerceIn(0, 100)
    val alphaInt = (opacity * 255 / 100)
    val lightness = if (isDark) {
        when (tone) {
            WidgetSurfaceTone.Card -> 0.16f
            WidgetSurfaceTone.Row -> 0.24f
            WidgetSurfaceTone.RowVariant -> 0.30f
        }
    } else {
        tone.customLightness
    }
    if ((theme.themeAccent == ThemeAccent.Custom || isDark) && canTint) {
        val base = when (tone) {
            WidgetSurfaceTone.Card -> R.drawable.widget_bg_card_tintable
            WidgetSurfaceTone.Row, WidgetSurfaceTone.RowVariant -> R.drawable.widget_bg_surface_tintable_clickable
        }
        val rawColor = if (theme.themeAccent == ThemeAccent.Custom) {
            AccentColors.tone(
                theme.customColorArgb,
                lightness,
                maxSaturation = 0.45f,
                minSaturation = 0.15f,
            )
        } else {
            val preset = theme.themeAccent.takeIf { it != ThemeAccent.Custom }
                ?: AccentColors.nearestPreset(theme.customColorArgb)
            AccentColors.tone(AccentColors.presetPrimary(preset), lightness, maxSaturation = 0.45f, minSaturation = 0.15f)
        }
        val color = (rawColor and 0x00FFFFFF) or (alphaInt shl 24)
        setInt(viewId, "setBackgroundResource", base)
        setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))
        if (tone == WidgetSurfaceTone.Card && opacity < 100) {
            setFloat(viewId, "setAlpha", opacity / 100f)
        }
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
    if (canTint) {
        if (opacity < 100) {
            val color = (0xFFFFFFFF.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
            setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))
            if (tone == WidgetSurfaceTone.Card) {
                setFloat(viewId, "setAlpha", opacity / 100f)
            }
        } else {
            setColorStateList(viewId, "setBackgroundTintList", null)
        }
    }
}

/** Derive emphasized row text from the resolved accent with readable lightness. */
internal fun widgetAccentTextColor(theme: WidgetThemePreferences, isDark: Boolean = false): Int {
    val base = if (theme.themeAccent == ThemeAccent.Custom) {
        theme.customColorArgb
    } else {
        AccentColors.presetPrimary(theme.themeAccent)
    }
    val lightness = if (isDark) 0.82f else 0.36f
    return AccentColors.tone(base, lightness, maxSaturation = 0.75f, minSaturation = 0.3f)
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
    val isDark = resolveWidgetIsDark(context, theme.widgetThemeMode)
    applyAccentBackground(rootId, theme, WidgetSurfaceTone.Card, isDark = isDark)
    val imageUri = theme.backgroundImageUri
        ?.takeIf(String::isNotBlank)
        ?.takeIf { theme.backgroundMode == WidgetBackgroundMode.Image }
    val rawBitmap = imageUri?.let { loadWidgetBackgroundBitmap(context, it) }
    val bitmap = if (rawBitmap != null && theme.frostedGlassEnabled) {
        blurWidgetBitmap(rawBitmap, 18)
    } else {
        rawBitmap
    }
    if (bitmap != null) {
        setImageViewBitmap(R.id.widget_background_image, bitmap)
        val opaque = 100 - theme.backgroundImageTransparencyPercent.coerceIn(0, 100)
        val combinedAlpha = (opaque * theme.backgroundOpacityPercent.coerceIn(0, 100)) / 100
        setInt(R.id.widget_background_image, "setImageAlpha", (combinedAlpha * 255 / 100))
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

internal fun blurWidgetBitmap(source: Bitmap, radius: Int = 18): Bitmap {
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return source
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(width * height)
    out.getPixels(pixels, 0, width, 0, 0, width, height)
    val r = radius.coerceIn(1, 25)
    boxBlur(pixels, width, height, r)
    boxBlur(pixels, width, height, r)
    out.setPixels(pixels, 0, width, 0, 0, width, height)
    return out
}

private fun boxBlur(pixels: IntArray, w: Int, h: Int, r: Int) {
    val temp = IntArray(w * h)
    val div = 2 * r + 1
    for (y in 0 until h) {
        val rowOffset = y * w
        var rSum = 0; var gSum = 0; var bSum = 0; var aSum = 0
        for (i in -r..r) {
            val p = pixels[rowOffset + i.coerceIn(0, w - 1)]
            aSum += (p ushr 24) and 0xff
            rSum += (p ushr 16) and 0xff
            gSum += (p ushr 8) and 0xff
            bSum += p and 0xff
        }
        for (x in 0 until w) {
            temp[rowOffset + x] = ((aSum / div) shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
            val pOut = pixels[rowOffset + (x - r).coerceIn(0, w - 1)]
            val pIn = pixels[rowOffset + (x + r + 1).coerceIn(0, w - 1)]
            aSum += ((pIn ushr 24) and 0xff) - ((pOut ushr 24) and 0xff)
            rSum += ((pIn ushr 16) and 0xff) - ((pOut ushr 16) and 0xff)
            gSum += ((pIn ushr 8) and 0xff) - ((pOut ushr 8) and 0xff)
            bSum += (pIn and 0xff) - (pOut and 0xff)
        }
    }
    for (x in 0 until w) {
        var rSum = 0; var gSum = 0; var bSum = 0; var aSum = 0
        for (i in -r..r) {
            val p = temp[i.coerceIn(0, h - 1) * w + x]
            aSum += (p ushr 24) and 0xff
            rSum += (p ushr 16) and 0xff
            gSum += (p ushr 8) and 0xff
            bSum += p and 0xff
        }
        for (y in 0 until h) {
            pixels[y * w + x] = ((aSum / div) shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
            val pOut = temp[(y - r).coerceIn(0, h - 1) * w + x]
            val pIn = temp[(y + r + 1).coerceIn(0, h - 1) * w + x]
            aSum += ((pIn ushr 24) and 0xff) - ((pOut ushr 24) and 0xff)
            rSum += ((pIn ushr 16) and 0xff) - ((pOut ushr 16) and 0xff)
            gSum += ((pIn ushr 8) and 0xff) - ((pOut ushr 8) and 0xff)
            bSum += (pIn and 0xff) - (pOut and 0xff)
        }
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
