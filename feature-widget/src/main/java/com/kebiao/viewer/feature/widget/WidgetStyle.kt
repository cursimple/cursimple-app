@file:OptIn(androidx.glance.ExperimentalGlanceApi::class)

package com.kebiao.viewer.feature.widget

import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

internal object WidgetStyle {
    val outerPadding = 12.dp
    val cardCorner = 22.dp
    val rowCorner = 14.dp
    val rowPaddingV = 8.dp
    val rowPaddingH = 10.dp
    val accentWidth = 4.dp
}

@Composable
internal fun WidgetCard(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(WidgetStyle.cardCorner)
            .padding(WidgetStyle.outerPadding),
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
internal fun AccentRow(
    accent: ColorProvider,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(if (dimmed) GlanceTheme.colors.surfaceVariant else GlanceTheme.colors.surface)
            .cornerRadius(WidgetStyle.rowCorner)
            .padding(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(WidgetStyle.accentWidth)
                .fillMaxHeight()
                .background(accent),
        ) { Spacer(GlanceModifier.height(36.dp)) }
        Spacer(GlanceModifier.width(8.dp))
        Box(
            modifier = GlanceModifier
                .padding(end = WidgetStyle.rowPaddingH, top = WidgetStyle.rowPaddingV, bottom = WidgetStyle.rowPaddingV)
                .fillMaxWidth(),
        ) {
            content()
        }
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
    onClick: Action,
) {
    // Use a single Box with both background and clickable on the same node — Glance maps
    // this cleanly to a single FrameLayout that the launcher's RemoteViews host can hit-test
    // reliably. Nesting clickable + background on different layers caused flaky taps.
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(20.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
