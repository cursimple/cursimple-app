package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 全应用统一的带框按钮。
 *
 * 为什么不直接用 OutlinedButton：Material3 新版把它的默认配色改成了
 * onSurfaceVariant 文字 + outlineVariant 边框，也就是灰字配浅灰框，
 * 放在绿色 / 蓝色主题里看不出是可点的主题色按钮。这里把文字和边框都换回主题主色，
 * 形状沿用两头全圆的胶囊。
 *
 * 签名与 OutlinedButton 一致，调用方要单独改边框或配色时照常传参覆盖即可。
 */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    ),
    border: BorderStroke? = BorderStroke(
        width = 1.dp,
        color = if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}
