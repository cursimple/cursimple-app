package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AppConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Info,
    /** Danger gives the dialog the red chip and a red confirm button; Warning only the amber chip. */
    tone: AppDialogTone = AppDialogTone.Primary,
    /** The confirm action deletes or discards, so it gets the red button whatever the chip colour. */
    dangerConfirm: Boolean = tone == AppDialogTone.Danger,
    details: (@Composable () -> Unit)? = null,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        tone = tone,
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                details?.invoke()
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            if (dangerConfirm) {
                AppDialogDangerButton(onClick = onConfirm) { Text(confirmLabel) }
            } else {
                AppDialogPrimaryButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = { AppDialogSecondaryButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}
