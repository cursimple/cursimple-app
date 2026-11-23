package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Label on the left, value flush right, hairline underneath; rows live inside a card rather than
 * in their own box. Callers turn [showDivider] off on the last row of a card.
 */
@Composable
fun AppInfoBlock(
    label: String?,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    valueModifier: Modifier = Modifier,
    showDivider: Boolean = true,
    /** Dialog panels use a tighter row than page cards. */
    dense: Boolean = false,
    /** Identifiers, entry points and hosts are set in monospace in the design. */
    monospace: Boolean = false,
) {
    val rowStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = if (dense) 12.5.sp else 13.sp,
        lineHeight = if (dense) 18.75.sp else 19.5.sp,
    )
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = if (dense) 8.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = rowStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                modifier = valueModifier.weight(1f),
                style = if (monospace) {
                    rowStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
                } else {
                    rowStyle
                },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                softWrap = !singleLine,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
