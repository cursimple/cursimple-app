@file:OptIn(androidx.glance.ExperimentalGlanceApi::class)

package com.x500x.cursimple.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.widget.WidgetBackgroundMode
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences

internal object WidgetStyle {
    val outerPadding = 12.dp
    val cardCorner = 22.dp
    val rowCorner = 14.dp
    val rowPaddingV = 8.dp
    val rowPaddingH = 10.dp
    val disabledContent = ColorProvider(Color(0xFF9AA3AE))
}

internal fun widgetCardBackground(accent: ThemeAccent): Int = when (accent) {
    ThemeAccent.Green -> R.drawable.widget_bg_card_green
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
    setInt(rootId, "setBackgroundResource", widgetCardBackground(theme.themeAccent))
    val imageUri = theme.backgroundImageUri
        ?.takeIf(String::isNotBlank)
        ?.takeIf { theme.backgroundMode == WidgetBackgroundMode.Image }
    val bitmap = imageUri?.let { loadWidgetBackgroundBitmap(context, it) }
    if (bitmap != null) {
        setImageViewBitmap(R.id.widget_background_image, bitmap)
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
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            BitmapFactory.decodeStream(input, null, bounds)
        }
    }.getOrNull()
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateWidgetBackgroundSampleSize(bounds.outWidth, bounds.outHeight)
    }
    return runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            BitmapFactory.decodeStream(input, null, decodeOptions)
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

private const val WIDGET_BACKGROUND_MAX_EDGE = 768

internal fun widgetBackgroundFor(accent: ThemeAccent): ColorProvider = ColorProvider(
    when (accent) {
        ThemeAccent.Green -> Color(0xFFDDEFE4)
        ThemeAccent.Blue -> Color(0xFFDDE5F1)
        ThemeAccent.Purple -> Color(0xFFE4DDED)
        ThemeAccent.Orange -> Color(0xFFF1E2D3)
        ThemeAccent.Pink -> Color(0xFFEDDADF)
    },
)

internal fun widgetSurfaceFor(accent: ThemeAccent): ColorProvider = ColorProvider(
    when (accent) {
        ThemeAccent.Green -> Color(0xFFB8DCC9)
        ThemeAccent.Blue -> Color(0xFFB8C9DD)
        ThemeAccent.Purple -> Color(0xFFC8B8D9)
        ThemeAccent.Orange -> Color(0xFFE5C8A6)
        ThemeAccent.Pink -> Color(0xFFDDB8C2)
    },
)

internal fun widgetSurfaceVariantFor(accent: ThemeAccent): ColorProvider = ColorProvider(
    when (accent) {
        ThemeAccent.Green -> Color(0xFF98C9AE)
        ThemeAccent.Blue -> Color(0xFF98AECB)
        ThemeAccent.Purple -> Color(0xFFAE98C5)
        ThemeAccent.Orange -> Color(0xFFD8B17A)
        ThemeAccent.Pink -> Color(0xFFCD98A6)
    },
)

@Composable
internal fun WidgetCard(
    sizeClass: WidgetSizeClass = WidgetSizeClass.Regular,
    accent: ThemeAccent = ThemeAccent.Green,
    content: @Composable () -> Unit,
) {
    val outerPadding = when (sizeClass) {
        WidgetSizeClass.Compact -> 8.dp
        WidgetSizeClass.Regular -> WidgetStyle.outerPadding
        WidgetSizeClass.Expanded -> WidgetStyle.outerPadding
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackgroundFor(accent))
            .cornerRadius(WidgetStyle.cardCorner)
            .padding(outerPadding),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun AccentRow(
    accent: ColorProvider,
    dimmed: Boolean = false,
    themeAccent: ThemeAccent = ThemeAccent.Green,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(if (dimmed) widgetSurfaceVariantFor(themeAccent) else widgetSurfaceFor(themeAccent))
            .cornerRadius(WidgetStyle.rowCorner)
            .padding(0.dp),
    ) {
        Box(
            modifier = GlanceModifier
                .padding(horizontal = WidgetStyle.rowPaddingH, vertical = WidgetStyle.rowPaddingV)
                .fillMaxWidth(),
        ) { content() }
    }
}

@Composable
internal fun PillBadge(text: String, container: ColorProvider, onContainer: ColorProvider) {
    Box(
        modifier = GlanceModifier
            .background(container)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = onContainer,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun IconCircleButton(
    label: String,
    onClick: Action?,
) {
    val baseModifier = GlanceModifier
        .size(40.dp)
        .background(GlanceTheme.colors.surfaceVariant)
        .cornerRadius(20.dp)
    // Use a single Box with both background and clickable on the same node — Glance maps
    // this cleanly to a single FrameLayout that the launcher's RemoteViews host can hit-test
    // reliably. Nesting clickable + background on different layers caused flaky taps.
    Box(
        modifier = if (onClick == null) baseModifier else baseModifier.clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (onClick == null) WidgetStyle.disabledContent else GlanceTheme.colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
