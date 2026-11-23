package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The app's dialog. Same arguments as [androidx.compose.material3.AlertDialog], but laid out and
 * typeset as the design does it: a 330dp white card with an icon chip beside the title, a quiet
 * body, and pill buttons at the bottom right.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    /** Dialogs are white cards in the design, not the tinted M3 container. */
    containerColor: Color = MaterialTheme.colorScheme.surface,
    /** Which of the design's three chip colours leads the header; see [AppDialogTone]. */
    tone: AppDialogTone = AppDialogTone.Primary,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    fillBody: Boolean = false,
    titleTrailingContent: @Composable (() -> Unit)? = null,
    stackActions: Boolean = false,
) {
    // The window is as wide as the screen so the card, not the platform, decides its width.
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = properties.decorFitsSystemWindows,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    enabled = properties.dismissOnClickOutside,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 330.dp)
                    .clickable(onClick = {}),
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation,
                shadowElevation = 24.dp,
            ) {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)) {
                    val titleText = title
                    if (icon != null || titleTrailingContent != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (icon != null) DialogIconChip(tone, iconContentColor, icon)
                            if (titleText != null) {
                                DialogTitle(titleText, titleContentColor, Modifier.weight(1f))
                            } else if (titleTrailingContent != null) {
                                Spacer(Modifier.weight(1f))
                            }
                            titleTrailingContent?.invoke()
                        }
                    } else if (titleText != null) {
                        DialogTitle(titleText, titleContentColor)
                    }
                    val body = text
                    if (body != null) {
                        // Weight lets a long body share the card with the buttons instead of pushing
                        // them off the screen; the dialogs that need to scroll bring their own scroll.
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = fillBody)
                                .padding(top = if (titleText != null) 14.dp else 0.dp),
                        ) {
                            // Size only: a colour baked into the text style would win over the
                            // LocalContentColor of any Surface inside the body (for example a
                            // filled button's label), leaving it illegible on its own fill.
                            ProvideTextStyle(
                                TextStyle(
                                    fontSize = 13.5.sp,
                                    lineHeight = 22.sp,
                                ),
                            ) {
                                CompositionLocalProvider(LocalContentColor provides textContentColor) {
                                    body()
                                }
                            }
                        }
                    } else {
                        Box(Modifier.weight(1f, fill = false))
                    }
                    val actionsModifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                    if (stackActions) {
                        Column(
                            modifier = actionsModifier,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            confirmButton()
                            dismissButton?.invoke()
                        }
                    } else {
                        Row(
                            modifier = actionsModifier,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

/**
 * The three header chips the design uses: green for ordinary dialogs, amber for ones that ask the
 * user to take care or grant something, red for ones that delete or discard.
 */
enum class AppDialogTone { Primary, Warning, Danger }

/** The tinted 40dp square that leads the dialog header. */
@Composable
private fun DialogIconChip(tone: AppDialogTone, tint: Color, icon: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (background, content) = when (tone) {
        AppDialogTone.Danger -> scheme.errorContainer to scheme.error
        AppDialogTone.Warning -> scheme.tertiaryContainer to scheme.tertiary
        AppDialogTone.Primary -> scheme.primaryContainer to scheme.primary
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint.takeOrElse { content }) { icon() }
    }
}

@Composable
private fun DialogTitle(title: @Composable () -> Unit, color: Color, modifier: Modifier = Modifier) {
    ProvideTextStyle(
        TextStyle(
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            color = color,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Box(modifier) { title() }
        }
    }
}

/** A filled accent pill for the dialog's main action. */
@Composable
fun AppDialogPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(21.dp),
    content: @Composable RowScope.() -> Unit,
) {
    DialogButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = null,
        content = content,
    )
}

/** An outlined pill for the action that backs out, or the quieter of two. */
@Composable
fun AppDialogSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(21.dp),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    border: BorderStroke? = BorderStroke(
        width = 1.5.dp,
        color = if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
    ),
    content: @Composable RowScope.() -> Unit,
) {
    DialogButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = contentColor,
        border = border,
        content = content,
    )
}

/** A red pill for the action that deletes or discards. */
@Composable
fun AppDialogDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(21.dp),
    adaptiveHeight: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    DialogButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        border = null,
        adaptiveHeight = adaptiveHeight,
        content = content,
    )
}

@Composable
private fun DialogButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    adaptiveHeight: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
    val sizedModifier = if (adaptiveHeight) modifier.heightIn(min = 48.dp) else modifier.height(42.dp)
    val padding = PaddingValues(horizontal = 20.dp, vertical = if (adaptiveHeight) 8.dp else 0.dp)
    val label: @Composable RowScope.() -> Unit = {
        ProvideTextStyle(
            TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
        ) {
            content()
        }
    }
    if (border == null) {
        Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            contentPadding = padding,
            content = label,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
            ),
            border = border,
            contentPadding = padding,
            content = label,
        )
    }
}
