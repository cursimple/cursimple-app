package com.x500x.cursimple.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.launch

enum class ClearScope { ManualOnly, ImportedOnly, Everything }

private data class ClearOption(
    val scope: ClearScope,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearScheduleSheet(
    manualCourses: List<CourseItem>,
    importedCourses: List<CourseItem>,
    onDismiss: () -> Unit,
    onConfirm: (ClearScope) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var pendingScope by remember { mutableStateOf<ClearScope?>(null) }

    val options = listOf(
        ClearOption(
            scope = ClearScope.ManualOnly,
            title = stringResource(R.string.clear_option_manual_title),
            description = stringResource(R.string.clear_option_manual_desc, manualCourses.size),
            icon = Icons.Rounded.Edit,
        ),
        ClearOption(
            scope = ClearScope.ImportedOnly,
            title = stringResource(R.string.clear_option_imported_title),
            description = stringResource(R.string.clear_option_imported_desc, importedCourses.size),
            icon = Icons.Rounded.CloudDownload,
        ),
        ClearOption(
            scope = ClearScope.Everything,
            title = stringResource(R.string.clear_option_all_title),
            description = stringResource(R.string.clear_option_all_desc),
            icon = Icons.Rounded.CleaningServices,
            accent = true,
        ),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.clear_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.clear_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(2.dp))
            options.forEach { opt ->
                ClearOptionRow(opt = opt, onClick = { pendingScope = opt.scope })
            }
            Spacer(Modifier.size(4.dp))
        }
    }

    pendingScope?.let { selected ->
        ClearConfirmDialog(
            scope = selected,
            manualCourses = manualCourses,
            importedCourses = importedCourses,
            onDismiss = { pendingScope = null },
            onConfirm = {
                pendingScope = null
                scope.launch {
                    sheetState.hide()
                    onConfirm(selected)
                }
            },
        )
    }
}

@Composable
private fun ClearOptionRow(opt: ClearOption, onClick: () -> Unit) {
    val container = if (opt.accent) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (opt.accent) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = container,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (opt.accent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = opt.icon,
                    contentDescription = null,
                    tint = if (opt.accent) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = opt.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (opt.accent) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = opt.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
        }
    }
}

@Composable
private fun ClearConfirmDialog(
    scope: ClearScope,
    manualCourses: List<CourseItem>,
    importedCourses: List<CourseItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val affected: List<CourseItem> = when (scope) {
        ClearScope.ManualOnly -> manualCourses
        ClearScope.ImportedOnly -> importedCourses
        ClearScope.Everything -> manualCourses + importedCourses
    }
    val title = when (scope) {
        ClearScope.ManualOnly -> stringResource(R.string.clear_confirm_manual_title)
        ClearScope.ImportedOnly -> stringResource(R.string.clear_confirm_imported_title)
        ClearScope.Everything -> stringResource(R.string.clear_confirm_all_title)
    }
    val description = when (scope) {
        ClearScope.ManualOnly -> stringResource(R.string.clear_confirm_manual_desc, affected.size)
        ClearScope.ImportedOnly -> stringResource(R.string.clear_confirm_imported_desc, affected.size)
        ClearScope.Everything -> stringResource(R.string.clear_confirm_all_desc, affected.size)
    }

    var expanded by remember { mutableStateOf(false) }
    val previewLimit = 5
    val needsFold = affected.size > previewLimit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                if (affected.isEmpty()) {
                    Text(
                        text = stringResource(R.string.clear_confirm_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val visible = if (expanded || !needsFold) affected
                    else affected.take(previewLimit)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            visible.forEachIndexed { idx, course ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = course.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (idx < visible.lastIndex) Spacer(Modifier.size(4.dp))
                            }
                        }
                    }
                    if (needsFold) {
                        Spacer(Modifier.size(6.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { expanded = !expanded },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (expanded) stringResource(R.string.clear_collapse_list)
                                    else stringResource(R.string.clear_expand_more, affected.size - previewLimit),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.clear_confirm_button),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.clear_cancel)) }
        },
    )
}
