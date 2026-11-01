package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
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

/** Tint only the rounded image layer; both RemoteViews methods are supported on API 24+. */
internal fun RemoteViews.applyAccentBackground(
    theme: WidgetThemePreferences,
    tone: WidgetSurfaceTone,
    isDark: Boolean,
) {
    val rootId = when (layoutId) {
        R.layout.widget_schedule_today, R.layout.widget_schedule_today_surface -> R.id.widget_root
        R.layout.widget_next_course, R.layout.widget_next_course_surface -> R.id.next_course_root
        R.layout.widget_reminder, R.layout.widget_reminder_surface -> R.id.reminder_root
        R.layout.widget_calendar, R.layout.widget_calendar_surface -> R.id.calendar_root
        R.layout.widget_memo, R.layout.widget_memo_surface -> R.id.memo_root
        R.layout.widget_course_capsule, R.layout.widget_course_capsule_surface,
        R.layout.widget_course_capsule_compact_surface -> R.id.capsule_root
        R.layout.widget_schedule_course_row, R.layout.widget_schedule_course_row_surface -> R.id.course_row_root
        R.layout.widget_next_course_row, R.layout.widget_next_course_row_surface -> R.id.next_course_row_root
        R.layout.widget_reminder_row, R.layout.widget_reminder_row_surface,
        R.layout.widget_reminder_compact_row, R.layout.widget_reminder_compact_row_surface -> R.id.reminder_row_root
        R.layout.widget_empty_surface -> R.id.widget_empty_surface_root
        else -> error("Unsupported widget surface layout: $layoutId")
    }
    // Reset legacy root actions on reapply. The foreground ripple belongs to the layout.
    setInt(rootId, "setBackgroundResource", if (tone == WidgetSurfaceTone.Card) {
        R.drawable.widget_bg_card_outline
    } else {
        R.drawable.widget_bg_surface_outline
    })
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // View.setAlpha became remotely callable in Android 12. Older hosts never
        // applied the legacy alpha action, and the new layout starts fully opaque.
        setFloat(rootId, "setAlpha", 1f)
        setColorStateList(rootId, "setBackgroundTintList", null)
    }
    val opacity = theme.backgroundOpacityPercent.coerceIn(0, 100)
    setInt(R.id.widget_surface_background, "setColorFilter", widgetSurfaceColor(theme, tone, isDark))
    setInt(R.id.widget_surface_background, "setImageAlpha", opacity * 255 / 100)
}

internal fun widgetSurfaceColor(
    theme: WidgetThemePreferences,
    tone: WidgetSurfaceTone,
    isDark: Boolean,
): Int {
    // Preserve the built-in light palette at every opacity, including 100%.
    if (!isDark && theme.themeAccent != ThemeAccent.Custom) {
        val colors = when (theme.themeAccent) {
            ThemeAccent.Green, ThemeAccent.Custom -> listOf(0xFFDDEFE4, 0xFFB8DCC9, 0xFF98C9AE)
            ThemeAccent.Blue -> listOf(0xFFDDE5F1, 0xFFB8C9DD, 0xFF98AECB)
            ThemeAccent.Purple -> listOf(0xFFE4DDED, 0xFFC8B8D9, 0xFFAE98C5)
            ThemeAccent.Orange -> listOf(0xFFF1E2D3, 0xFFE5C8A6, 0xFFD8B17A)
            ThemeAccent.Pink -> listOf(0xFFEDDADF, 0xFFDDB8C2, 0xFFCD98A6)
        }
        return colors[tone.ordinal].toInt()
    }
    val base = if (theme.themeAccent == ThemeAccent.Custom) {
        theme.customColorArgb
    } else {
        AccentColors.presetPrimary(theme.themeAccent)
    }
    val lightness = if (isDark) {
        when (tone) {
            WidgetSurfaceTone.Card -> 0.16f
            WidgetSurfaceTone.Row -> 0.24f
            WidgetSurfaceTone.RowVariant -> 0.30f
        }
    } else {
        tone.customLightness
    }
    return AccentColors.tone(base, lightness, maxSaturation = 0.45f, minSaturation = 0.15f)
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

internal fun RemoteViews.applyWidgetBackground(
    context: Context,
    theme: WidgetThemePreferences,
) {
    val isDark = resolveWidgetIsDark(context, theme.widgetThemeMode)
    applyAccentBackground(theme, WidgetSurfaceTone.Card, isDark = isDark)
    applyWidgetTextColors(context, theme)
    val scrimAlpha = 0x66 * theme.backgroundOpacityPercent.coerceIn(0, 100) / 100
    setInt(R.id.widget_background_scrim, "setBackgroundColor", (scrimAlpha shl 24) or 0x00FFFFFF)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setFloat(R.id.widget_background_scrim, "setAlpha", 1f)
    }
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

/** Apply actions before insertion so duplicate surface IDs stay scoped to their own subtree. */
internal fun widgetEmptySurface(
    context: Context,
    theme: WidgetThemePreferences,
    text: String,
    secondary: Boolean = false,
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_empty_surface).apply {
    val dark = resolveWidgetIsDark(context, theme.widgetThemeMode)
    applyAccentBackground(theme, WidgetSurfaceTone.RowVariant, dark)
    setTextViewText(R.id.widget_empty_text, text)
    setTextColor(R.id.widget_empty_text, if (secondary) {
        if (dark) 0xFFCAC4D0.toInt() else 0xFF66707F.toInt()
    } else {
        if (dark) 0xFFE6E1E5.toInt() else 0xFF20242A.toInt()
    })
}

/** Set both palettes explicitly: RemoteViews can reuse a light view after a mode change. */
internal fun RemoteViews.applyWidgetTextColors(context: Context, theme: WidgetThemePreferences) {
    val dark = resolveWidgetIsDark(context, theme.widgetThemeMode)
    val primary = if (dark) 0xFFE6E1E5.toInt() else 0xFF20242A.toInt()
    val secondary = if (dark) 0xFFCAC4D0.toInt() else 0xFF66707F.toInt()
    val ids = when (layoutId) {
        R.layout.widget_schedule_today, R.layout.widget_schedule_today_surface -> listOf(R.id.widget_title) to listOf(R.id.widget_subtitle)
        R.layout.widget_next_course, R.layout.widget_next_course_surface -> listOf(R.id.next_course_title) to emptyList()
        R.layout.widget_reminder, R.layout.widget_reminder_surface -> listOf(R.id.reminder_title) to emptyList()
        R.layout.widget_memo, R.layout.widget_memo_surface -> listOf(R.id.memo_title) to listOf(R.id.memo_subtitle, R.id.memo_empty_text)
        R.layout.widget_calendar, R.layout.widget_calendar_surface -> listOf(R.id.calendar_title) to listOf(R.id.calendar_subtitle, R.id.calendar_legend, R.id.calendar_mode)
        R.layout.widget_next_course_row, R.layout.widget_next_course_row_surface -> listOf(R.id.next_course_name) to listOf(R.id.next_course_time, R.id.next_course_sub)
        R.layout.widget_reminder_row, R.layout.widget_reminder_row_surface -> listOf(R.id.reminder_row_title) to listOf(R.id.reminder_date, R.id.reminder_row_message)
        R.layout.widget_reminder_compact_row, R.layout.widget_reminder_compact_row_surface -> listOf(R.id.reminder_row_title) to emptyList()
        R.layout.widget_memo_row, R.layout.widget_memo_loading_row -> listOf(R.id.memo_row_title) to listOf(R.id.memo_row_source)
        else -> emptyList<Int>() to emptyList()
    }
    ids.first.forEach { setTextColor(it, primary) }
    ids.second.forEach { setTextColor(it, secondary) }
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
