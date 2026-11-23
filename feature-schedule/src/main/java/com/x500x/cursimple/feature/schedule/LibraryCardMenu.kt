package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton

@Composable
internal fun LibraryCardMenu(
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: @Composable (dismiss: () -> Unit) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppCircleIconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.memo_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibraryMenuAction(stringResource(R.string.schedule_action_edit), Icons.Rounded.Edit) {
                expanded = false
                onEdit()
            }
            LibraryMenuAction(stringResource(R.string.library_management_select), Icons.Rounded.CheckCircle) {
                expanded = false
                onSelect()
            }
            extraActions { expanded = false }
            LibraryMenuAction(
                stringResource(R.string.schedule_action_delete), Icons.Rounded.DeleteOutline, destructive = true,
            ) {
                expanded = false
                onDelete()
            }
        }
    }
}

@Composable
internal fun LibraryMenuAction(
    title: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val textColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (destructive) textColor else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(title, color = textColor) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = iconColor) },
        onClick = onClick,
    )
}
