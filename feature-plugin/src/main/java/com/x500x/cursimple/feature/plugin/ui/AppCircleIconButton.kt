package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A smaller [size] only shrinks the drawn circle; the touch target stays 48dp. */
@Composable
fun AppCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.minimumInteractiveComponentSize().size(size).clip(CircleShape), shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        content = content,
    )
}
